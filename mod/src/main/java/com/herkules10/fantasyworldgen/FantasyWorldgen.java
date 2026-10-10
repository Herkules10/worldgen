package com.herkules10.fantasyworldgen;

import org.slf4j.Logger;

import com.herkules10.fantasyworldgen.render.TerrainRenderer;
import com.herkules10.fantasyworldgen.worldgen.ModWorldgen;
import com.herkules10.fantasyworldgen.worldgen.WorldSpawn;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Thin adapter between the terrain engine and Minecraft. All creative decisions live in the engine; this side only
 * registers the chunk generator and world preset and hands the engine's blocks to the game.
 */
@Mod(FantasyWorldgen.MODID)
public final class FantasyWorldgen {
    public static final String MODID = "fantasyworldgen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FantasyWorldgen(IEventBus modEventBus) {
        ModWorldgen.register(modEventBus);
        WorldSpawn.register();

        // Headless terrain renders for `./gradlew runRender`; no-op otherwise.
        TerrainRenderer.registerIfEnabled();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
