package com.herkules10.fantasyworldgen.render;

import java.util.Comparator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only headless renderer. When the server is started with {@code -Dfantasyworldgen.render=true} (the
 * {@code runRender} Gradle task), it generates the requested region deterministically, samples every column, writes PNGs and a JSON
 * summary, then stops the server. Exit code is 0 on success and 1 on failure.
 */
public final class TerrainRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TICK_BUDGET_NANOS = 40_000_000L;

    static final int SLICE_SKY = 0xBFD9F2;
    static final int SLICE_CAVE = 0x1E1E24;
    private static final int SLICE_NO_COLOR = 0x9A9A9A;

    /** Holds chunks at CARVERS (not FULL) while they wait for serial decoration. */
    private static final TicketType<ChunkPos> PREFETCH = TicketType.create("fantasyworldgen_render", Comparator.comparingLong(ChunkPos::toLong));
    /** Region-ticket "distance" giving a ticket level that loads chunks to CARVERS; negative because it is below FULL. */
    private static final int PREFETCH_DISTANCE = ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(ChunkStatus.CARVERS);
    private static final int PREFETCH_AHEAD = 512;

    private enum Phase { WAITING, GENERATING, SAMPLING, DONE, FAILED }

    private final RenderSettings settings;
    private volatile Phase phase = Phase.WAITING;
    private MinecraftServer server;
    private ServerLevel level;
    private RenderData data;
    private final LongList decorateOrder = new LongArrayList();
    private int nextDecorate;
    private int nextPrefetch;
    private int decorateRow;
    private int loggedDecile;
    private final LongList chunks = new LongArrayList();
    private int nextChunk;
    private long startNanos;
    private long generatedNanos;

    private TerrainRenderer(RenderSettings settings) {
        this.settings = settings;
    }

    /** Call from the mod constructor; does nothing unless this is a dev render run. */
    public static void registerIfEnabled() {
        if (!RenderSettings.enabled() || FMLLoader.isProduction()) return;
        RenderSettings settings;
        try {
            settings = RenderSettings.fromSystemProperties();
        } catch (IllegalArgumentException e) {
            LOGGER.error("[render] Invalid render settings: {}", e.getMessage());
            Runtime.getRuntime().halt(1);
            return;
        }
        NeoForge.EVENT_BUS.register(new TerrainRenderer(settings));
    }

    /** No spawn chunks: vanilla would generate them in parallel (non-deterministically) before the render starts. */
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        event.getServer().getWorldData().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
    }

    /** Skip the vanilla spawn search, which generates chunks around the origin. */
    @SubscribeEvent
    public void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        event.getSettings().setSpawn(new BlockPos(0, 64, 0), 0f);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        try {
            level = server.getLevel(settings.dimension());
            if (level == null) throw new IllegalArgumentException("Unknown dimension " + settings.dimension().location());
            data = new RenderData(settings, level.getMinBuildHeight(), level.getHeight(), level.getSeaLevel());

            int minCX = SectionPos.blockToSectionCoord(settings.minX());
            int minCZ = SectionPos.blockToSectionCoord(settings.minZ());
            int maxCX = SectionPos.blockToSectionCoord(settings.minX() + settings.size() - 1);
            int maxCZ = SectionPos.blockToSectionCoord(settings.minZ() + settings.size() - 1);
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                for (int cx = minCX; cx <= maxCX; cx++) chunks.add(ChunkPos.asLong(cx, cz));
            }
            // Features such as trees spill into neighbouring chunks, so decorate a 1-chunk margin around the region too.
            decorateRow = maxCX - minCX + 3;
            for (int cz = minCZ - 1; cz <= maxCZ + 1; cz++) {
                for (int cx = minCX - 1; cx <= maxCX + 1; cx++) decorateOrder.add(ChunkPos.asLong(cx, cz));
            }

            LOGGER.info("[render] seed {} | {} | blocks x[{}, {}) z[{}, {}) | scale {} | decorating {} chunks",
                    level.getSeed(), settings.dimension().location(), settings.minX(), settings.minX() + settings.size(),
                    settings.minZ(), settings.minZ() + settings.size(), settings.scale(), decorateOrder.size());
            startNanos = System.nanoTime();
            phase = Phase.GENERATING;
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Generation and sampling run in slices on the server thread so chunks can unload between ticks.
     * <p>
     * Decoration (the FEATURES step) is requested strictly one chunk at a time in a fixed order. Features such as trees
     * write into neighbouring chunks, so when vanilla decorates chunks in parallel the result depends on thread timing.
     * Earlier steps (noise, surface, carvers) only touch their own chunk and are prefetched in parallel. The output
     * is identical for identical arguments; a differently framed region decorates in a different order, so pixels
     * where features cross chunk borders can differ slightly between differently framed renders.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (phase != Phase.GENERATING && phase != Phase.SAMPLING) return;
        try {
            long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
            if (phase == Phase.GENERATING) {
                ServerChunkCache cache = level.getChunkSource();
                while (nextDecorate < decorateOrder.size() && System.nanoTime() < deadline) {
                    prefetch(cache);
                    long pos = decorateOrder.getLong(nextDecorate);
                    cache.getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos), ChunkStatus.FEATURES, true);
                    // keep one row behind the cursor loaded: the next row's decoration reads it
                    int release = nextDecorate - decorateRow - 1;
                    if (release >= 0) releasePrefetch(cache, release);
                    nextDecorate++;
                    int decile = nextDecorate * 10 / decorateOrder.size();
                    if (decile > loggedDecile) {
                        loggedDecile = decile;
                        LOGGER.info("[render] decorated {}%", decile * 10);
                    }
                }
                if (nextDecorate < decorateOrder.size()) return;
                for (int i = Math.max(0, nextDecorate - decorateRow - 1); i < decorateOrder.size(); i++) releasePrefetch(cache, i);
                generatedNanos = System.nanoTime();
                phase = Phase.SAMPLING;
                return;
            }

            while (nextChunk < chunks.size() && System.nanoTime() < deadline) {
                long pos = chunks.getLong(nextChunk++);
                // FEATURES, not FULL: blocks, heightmaps, biomes and structures are final after decoration, and FULL
                // would add a slow, serial lighting pass that the images don't need.
                sampleChunk(level.getChunkSource().getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos), ChunkStatus.FEATURES, true));
            }
            if (nextChunk < chunks.size()) return;

            phase = Phase.DONE;
            long end = System.nanoTime();
            RenderOutput.write(data, level, (generatedNanos - startNanos) / 1e9, (end - generatedNanos) / 1e9);
            server.halt(false);
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Keeps the steps before decoration (noise, surface, carvers: each writes only its own chunk, so order doesn't
     * matter) running in parallel ahead of the serial decoration cursor.
     */
    private void prefetch(ServerChunkCache cache) {
        int target = Math.min(decorateOrder.size(), nextDecorate + PREFETCH_AHEAD);
        if (nextPrefetch >= target) return;
        int from = nextPrefetch;
        for (; nextPrefetch < target; nextPrefetch++) {
            ChunkPos pos = new ChunkPos(decorateOrder.getLong(nextPrefetch));
            cache.addRegionTicket(PREFETCH, pos, PREFETCH_DISTANCE, pos);
        }
        cache.tick(() -> false, false); // creates the chunk holders for the new tickets
        for (int i = from; i < target; i++) {
            ChunkHolder holder = cache.chunkMap.getVisibleChunkIfPresent(decorateOrder.getLong(i));
            if (holder != null) holder.scheduleChunkGenerationTask(ChunkStatus.CARVERS, cache.chunkMap);
        }
    }

    private void releasePrefetch(ServerChunkCache cache, int index) {
        ChunkPos pos = new ChunkPos(decorateOrder.getLong(index));
        cache.removeRegionTicket(PREFETCH, pos, PREFETCH_DISTANCE, pos);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (phase != Phase.DONE) {
            LOGGER.error("[render] Server stopped before the render finished (phase {})", phase);
            Runtime.getRuntime().halt(1);
        }
    }

    private void fail(Exception e) {
        LOGGER.error("[render] Render failed", e);
        phase = Phase.FAILED;
        server.halt(false);
    }

    private void sampleChunk(ChunkAccess chunk) {
        int scale = settings.scale();
        int minX = settings.minX();
        int minZ = settings.minZ();
        int end = settings.size();
        int px0 = chunk.getPos().getMinBlockX();
        int pz0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            int dz = pz0 + lz - minZ;
            if (dz < 0 || dz >= end || dz % scale != 0) continue;
            for (int lx = 0; lx < 16; lx++) {
                int dx = px0 + lx - minX;
                if (dx < 0 || dx >= end || dx % scale != 0) continue;
                sampleColumn(chunk, pos, px0 + lx, pz0 + lz, (dz / scale) * data.pixels + dx / scale);
            }
        }

        int sz = settings.sliceZ();
        if (data.slice != null && sz >= pz0 && sz < pz0 + 16) {
            for (int lx = 0; lx < 16; lx++) {
                int dx = px0 + lx - minX;
                if (dx < 0 || dx >= end || dx % scale != 0) continue;
                sampleSliceColumn(chunk, pos, px0 + lx, sz, dx / scale);
            }
        }

        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        chunk.getAllStarts().forEach((structure, start) -> {
            if (start.isValid()) {
                data.structures.add(new RenderData.StructureInfo(String.valueOf(structures.getKey(structure)), start.getBoundingBox()));
            }
        });
    }

    private void sampleColumn(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int idx) {
        int minY = data.minY;
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockState state = chunk.getBlockState(pos.set(x, y, z));
        MapColor color = state.getMapColor(chunk, pos);
        while (color == MapColor.NONE && y > minY) {
            state = chunk.getBlockState(pos.set(x, --y, z));
            color = state.getMapColor(chunk, pos);
        }
        data.surfaceY[idx] = y;
        data.surfaceRgb[idx] = color == MapColor.NONE ? 0 : color.col;

        int depth = 0;
        while (!state.getFluidState().isEmpty() && y - depth > minY) {
            depth++;
            state = chunk.getBlockState(pos.set(x, y - depth, z));
        }
        data.fluidDepth[idx] = depth;

        int ground = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        while (ground > minY && !chunk.getBlockState(pos.set(x, ground, z)).getFluidState().isEmpty()) ground--;
        data.groundY[idx] = ground;

        data.biome[idx] = data.biomeIndex(chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(data.surfaceY[idx]), QuartPos.fromBlock(z)));
    }

    private void sampleSliceColumn(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int column) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int maxY = data.minY + data.worldHeight - 1;
        for (int y = data.minY; y <= maxY; y++) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            int rgb;
            if (state.isAir()) {
                rgb = y > top ? SLICE_SKY : SLICE_CAVE;
            } else {
                MapColor color = state.getMapColor(chunk, pos);
                rgb = color == MapColor.NONE ? SLICE_NO_COLOR : color.col;
            }
            data.slice[(maxY - y) * data.pixels + column] = rgb;
        }
    }
}
