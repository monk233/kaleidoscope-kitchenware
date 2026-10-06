package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 16 stacks of anything, and the jar model fills up as it gets stocked. */
public class SpiceJarBlockEntity extends StorageBlockEntity {
    public static final int CAPACITY = 16;

    public SpiceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPICE_JAR.get(), pos, state, CAPACITY);
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
            boolean filled = !isEmpty();
            if (state.getValue(SpiceJarBlock.FILLED) != filled) {
                level.setBlockAndUpdate(worldPosition, state.setValue(SpiceJarBlock.FILLED, filled));
            }
        }
    }
}
