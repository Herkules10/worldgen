package com.herkules10.fantasyworldgen.mixin;

import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.Util;
import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

/**
 * Vanilla picks the bee nest's side of the trunk with {@link Collections#shuffle(List)}, which uses a JVM-wide unseeded
 * {@code Random}: the same seed puts nests on different sides in different runs. Shuffle with the tree's own
 * (seeded) random instead, so decoration depends only on seed and position.
 */
@Mixin(BeehiveDecorator.class)
abstract class BeehiveDecoratorMixin {
    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Ljava/util/Collections;shuffle(Ljava/util/List;)V"))
    private void fantasyworldgen$seededShuffle(List<?> candidates, TreeDecorator.Context context) {
        Util.shuffle(candidates, context.random());
    }
}
