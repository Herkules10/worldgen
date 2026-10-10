package com.herkules10.fantasyworldgen.worldgen;

import com.herkules10.fantasyworldgen.engine.Block;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Engine blocks to Minecraft block states. */
public final class BlockMapping {
    private static final BlockState[] STATES = new BlockState[Block.values().length];

    static {
        for (Block block : Block.values()) STATES[block.id()] = resolve(block);
    }

    private BlockMapping() {}

    public static BlockState state(Block block) {
        return STATES[block.id()];
    }

    private static BlockState resolve(Block block) {
        return switch (block) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case BEDROCK -> Blocks.BEDROCK.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case GRASS_BLOCK -> Blocks.GRASS_BLOCK.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case SNOW_BLOCK -> Blocks.SNOW_BLOCK.defaultBlockState();
            case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case TUFF -> Blocks.TUFF.defaultBlockState();
            case BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
            case CALCITE -> Blocks.CALCITE.defaultBlockState();
            case DIORITE -> Blocks.DIORITE.defaultBlockState();
            case PODZOL -> Blocks.PODZOL.defaultBlockState();
            case PACKED_ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case MUD -> Blocks.MUD.defaultBlockState();
        };
    }
}
