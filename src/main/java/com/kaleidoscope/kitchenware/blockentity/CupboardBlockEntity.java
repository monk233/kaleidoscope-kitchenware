package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.block.CupboardBlock;
import com.kaleidoscope.kitchenware.config.KitchenwareConfig;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Cupboard behaviour: doors swing open on use and close themselves a moment later. */
public class CupboardBlockEntity extends StorageBlockEntity {
    private static final long DOOR_OPEN_TICKS = 60;

    private long closeAt = Long.MIN_VALUE;

    public CupboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUPBOARD.get(), pos, state, KitchenwareConfig.CUPBOARD_CAPACITY.get());
    }

    @Override
    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        return stack.is(com.kaleidoscope.kitchenware.registry.ModTags.CUPBOARD_STORABLE);
    }

    /** Called after a successful store or take: swing the doors open. */
    public void swingDoors() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        closeAt = level.getGameTime() + DOOR_OPEN_TICKS;
        if (!getBlockState().getValue(CupboardBlock.OPEN)) {
            level.setBlockAndUpdate(worldPosition, CupboardBlock.withOpen(getBlockState(), true));
        }
    }

    public void serverTick() {
        Level level = getLevel();
        if (level == null || !getBlockState().getValue(CupboardBlock.OPEN)) {
            return;
        }
        if (level.getGameTime() >= closeAt) {
            level.setBlockAndUpdate(worldPosition, CupboardBlock.withOpen(getBlockState(), false));
        }
    }
}
