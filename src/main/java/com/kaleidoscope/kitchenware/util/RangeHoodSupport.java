package com.kaleidoscope.kitchenware.util;

import com.kaleidoscope.kitchenware.block.RangeHoodBlock;
import com.kaleidoscope.kitchenware.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Shared lookups for the stove / hood / chimney column. */
public final class RangeHoodSupport {
    /** How far up the column we look for a hood or a chimney. */
    public static final int COLUMN_REACH = 3;

    private RangeHoodSupport() {
    }

    /** True when a working range hood sits above the stove in the same column. */
    public static boolean hasWorkingHood(Level level, BlockPos stovePos) {
        for (int i = 1; i <= COLUMN_REACH; i++) {
            BlockPos pos = stovePos.above(i);
            if (level.getBlockState(pos).getBlock() instanceof RangeHoodBlock) {
                return level.getBlockState(pos).getValue(RangeHoodBlock.ACTIVE);
            }
        }
        return false;
    }

    /** True when a chimney continues upward from the hood, which boosts its efficiency. */
    public static boolean hasChimney(Level level, BlockPos hoodPos) {
        for (int i = 1; i <= COLUMN_REACH; i++) {
            if (level.getBlockState(hoodPos.above(i)).is(ModBlocks.CHIMNEY.get())) {
                return true;
            }
        }
        return false;
    }
}
