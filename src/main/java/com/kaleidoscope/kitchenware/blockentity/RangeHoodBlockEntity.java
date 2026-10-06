package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.block.RangeHoodBlock;
import com.kaleidoscope.kitchenware.blockentity.FirewoodStoveBlockEntity;
import com.kaleidoscope.kitchenware.config.KitchenwareConfig;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.util.RangeHoodSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Polls once a second: redstone decides whether it runs, the stove below decides whether
 * it has anything to do, the chimney above decides how well it does it.
 */
public class RangeHoodBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 20;
    private int efficiency;
    private FirewoodStoveBlockEntity servedStove;

    public RangeHoodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RANGE_HOOD.get(), pos, state);
    }

    public int efficiency() {
        return efficiency;
    }

    public void serverTick() {
        Level level = getLevel();
        if (level == null || level.getGameTime() % TICK_INTERVAL != 0) {
            return;
        }
        BlockState state = getBlockState();
        boolean powered = state.getValue(RangeHoodBlock.POWERED);

        FirewoodStoveBlockEntity stove = findStove(level);
        boolean working = powered && stove != null;
        efficiency = working
                ? (RangeHoodSupport.hasChimney(level, worldPosition)
                        ? KitchenwareConfig.CHIMNEY_EFFICIENCY.get()
                        : KitchenwareConfig.NO_CHIMNEY_EFFICIENCY.get())
                : 0;

        if (servedStove != stove) {
            if (servedStove != null) {
                servedStove.setHoodBoost(false, 0);
            }
            servedStove = stove;
        }
        if (servedStove != null) {
            servedStove.setHoodBoost(working, efficiency);
        }
        if (state.getValue(RangeHoodBlock.ACTIVE) != working) {
            level.setBlockAndUpdate(worldPosition, state.setValue(RangeHoodBlock.ACTIVE, working));
        }
    }

    private FirewoodStoveBlockEntity findStove(Level level) {
        for (int i = 1; i <= RangeHoodSupport.COLUMN_REACH; i++) {
            BlockEntity below = level.getBlockEntity(worldPosition.below(i));
            if (below instanceof FirewoodStoveBlockEntity stove) {
                return stove;
            }
        }
        return null;
    }

    @Override
    public void setRemoved() {
        if (servedStove != null) {
            servedStove.setHoodBoost(false, 0);
            servedStove = null;
        }
        super.setRemoved();
    }
}
