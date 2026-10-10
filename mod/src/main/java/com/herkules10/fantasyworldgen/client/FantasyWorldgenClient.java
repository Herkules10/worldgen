package com.herkules10.fantasyworldgen.client;

import com.herkules10.fantasyworldgen.FantasyWorldgen;
import com.herkules10.fantasyworldgen.engine.WorldShape;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** Client-only setup. This class never loads on a dedicated server. */
@Mod(value = FantasyWorldgen.MODID, dist = Dist.CLIENT)
public final class FantasyWorldgenClient {
    public FantasyWorldgenClient(IEventBus modEventBus) {
        modEventBus.addListener(FantasyWorldgenClient::registerDimensionEffects);
    }

    /** Sky effects for {@code "effects": "fantasyworldgen:overworld"} in our dimension type. */
    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(FantasyWorldgen.id("overworld"), new OverworldEffects());
    }

    /** Vanilla's overworld sky, with the cloud layer raised to {@link WorldShape#CLOUD_LEVEL}. */
    static final class OverworldEffects extends DimensionSpecialEffects {
        OverworldEffects() {
            super(WorldShape.CLOUD_LEVEL, true, SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    }
}
