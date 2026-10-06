package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 8 slots, anything goes; the blockstate mirrors how full it is. */
public class SpiceRackBlockEntity extends StorageBlockEntity {
    public static final int DEFAULT_CAPACITY = 8;

    public SpiceRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPICE_RACK.get(), pos, state, DEFAULT_CAPACITY);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return true;
    }

    @Override
    protected void syncState() {
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            BlockState updated = com.kaleidoscope.kitchenware.block.SpiceRackBlock.withFillLevel(state, countStored());
            if (updated != state) {
                level.setBlockAndUpdate(worldPosition, updated);
            }
        }
    }
}
