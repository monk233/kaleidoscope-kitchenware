package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.util.FuelTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FirewoodStoveBlockEntity extends BlockEntity {
    private ItemStack fuel = ItemStack.EMPTY;
    private FuelTier tier = FuelTier.LOW;
    private int burnTime;
    private int burnTimeTotal;

    public FirewoodStoveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIREWOOD_STOVE.get(), pos, state);
    }

    /** Stores one fuel item if the stove is empty, returning whether it was accepted. */
    public boolean addFuel(ItemStack stack) {
        if (!fuel.isEmpty() || stack.getBurnTime(null) <= 0) {
            return false;
        }
        fuel = stack.copyWithCount(1);
        tier = FuelTier.of(stack);
        burnTimeTotal = tier.burnTicks(stack);
        burnTime = burnTimeTotal;
        setChanged();
        return true;
    }

    public boolean hasFuel() {
        return burnTime > 0;
    }

    public boolean canIgnite() {
        return hasFuel() && !getBlockState().getValue(BlockStateProperties.LIT);
    }

    public FuelTier tier() {
        return tier;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnTimeTotal() {
        return burnTimeTotal;
    }

    public void ignite() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true));
    }

    public void extinguish() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, false));
    }

    /** Server tick: burn down fuel, go out when empty or rained on. */
    public void serverTick() {
        Level level = getLevel();
        if (level == null || !getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        if (level.isRainingAt(worldPosition.above())) {
            extinguish();
            return;
        }
        if (burnTime > 0) {
            burnTime--;
            if (burnTime % 20 == 0) {
                setChanged();
            }
            return;
        }
        fuel = ItemStack.EMPTY;
        extinguish();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fuel.isEmpty()) {
            tag.put("Fuel", fuel.save(registries));
        }
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        tag.putString("Tier", tier.key);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel = tag.contains("Fuel") ? ItemStack.parseOptional(registries, tag.getCompound("Fuel")) : ItemStack.EMPTY;
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        for (FuelTier candidate : FuelTier.values()) {
            if (candidate.key.equals(tag.getString("Tier"))) {
                tier = candidate;
            }
        }
    }

    /** Keeps the lit blockstate honest when the chunk loads with fuel still left. */
    public void syncLit() {
        BlockState state = getBlockState();
        boolean shouldBeLit = burnTime > 0;
        if (state.getValue(BlockStateProperties.LIT) != shouldBeLit) {
            Level level = getLevel();
            if (level != null && !level.isClientSide) {
                level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, shouldBeLit), Block.UPDATE_ALL);
            }
        }
    }
}
