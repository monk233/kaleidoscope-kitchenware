package com.kaleidoscope.kitchenware.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Base for the GUI-less containers (cupboard, spice rack). Everything happens through
 * one-item-at-a-time inserts and extracts driven by right-clicks in the world.
 */
public abstract class StorageBlockEntity extends BlockEntity {
    private final ItemStackHandler items;

    protected StorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        this.items = new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                syncState();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return accepts(stack);
            }
        };
    }

    /** Whether this container may hold the given stack. */
    public abstract boolean accepts(ItemStack stack);

    public ItemStackHandler items() {
        return items;
    }

    public int capacity() {
        return items.getSlots();
    }

    public int countStored() {
        int count = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return countStored() == 0;
    }

    /** Stores exactly one item, stacking onto an existing slot first. */
    public boolean insertOne(ItemStack stack) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack existing = items.getStackInSlot(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                existing.grow(1);
                onInserted();
                return true;
            }
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (items.getStackInSlot(slot).isEmpty()) {
                items.setStackInSlot(slot, stack.copyWithCount(1));
                onInserted();
                return true;
            }
        }
        return false;
    }

    /** Takes exactly one item out, last filled slot first. */
    public ItemStack extractOne() {
        for (int slot = items.getSlots() - 1; slot >= 0; slot--) {
            ItemStack existing = items.getStackInSlot(slot);
            if (existing.isEmpty()) {
                continue;
            }
            ItemStack taken = existing.copyWithCount(1);
            existing.shrink(1);
            onExtracted();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    protected void onInserted() {
    }

    protected void onExtracted() {
    }

    /** Hook for subclasses that mirror their contents into a blockstate property. */
    protected void syncState() {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
    }
}
