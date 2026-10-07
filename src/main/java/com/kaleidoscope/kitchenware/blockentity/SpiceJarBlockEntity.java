package com.kaleidoscope.kitchenware.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;

/**
 * Four spice jars share one block, each sitting in its own corner and holding its own
 * 16 stacks. All four live in one handler, sliced into four ranges.
 */
public class SpiceJarBlockEntity extends BlockEntity {
    public static final int JAR_COUNT = 4;
    public static final int SLOTS_PER_JAR = 16;

    private final ItemStackHandler items = new ItemStackHandler(JAR_COUNT * SLOTS_PER_JAR) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public SpiceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPICE_JAR.get(), pos, state);
    }

    public boolean accepts(ItemStack stack) {
        return true;
    }

    private int firstSlot(int jar) {
        return jar * SLOTS_PER_JAR;
    }

    private int lastSlot(int jar) {
        return firstSlot(jar) + SLOTS_PER_JAR - 1;
    }

    /** What the jar shows on top: the first thing stored in it. */
    public ItemStack displayItem(int jar) {
        for (int slot = firstSlot(jar); slot <= lastSlot(jar); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean isJarEmpty(int jar) {
        return displayItem(jar).isEmpty();
    }

    public boolean isEmpty() {
        for (int jar = 0; jar < JAR_COUNT; jar++) {
            if (!isJarEmpty(jar)) {
                return false;
            }
        }
        return true;
    }

    /** Stores items, returning how many actually went in. */
    public int insert(int jar, ItemStack stack, boolean wholeStack) {
        if (stack.isEmpty() || !accepts(stack)) {
            return 0;
        }
        int amount = wholeStack ? stack.getCount() : 1;
        int moved = 0;
        for (int slot = firstSlot(jar); slot <= lastSlot(jar) && moved < amount; slot++) {
            ItemStack existing = items.getStackInSlot(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int add = Math.min(amount - moved, existing.getMaxStackSize() - existing.getCount());
                existing.grow(add);
                moved += add;
            }
        }
        for (int slot = firstSlot(jar); slot <= lastSlot(jar) && moved < amount; slot++) {
            if (items.getStackInSlot(slot).isEmpty()) {
                int add = Math.min(stack.getMaxStackSize(), amount - moved);
                items.setStackInSlot(slot, stack.copyWithCount(add));
                moved += add;
            }
        }
        return moved;
    }

    /** Takes one item, or a whole stack when {@code wholeStack} is set. */
    public ItemStack extract(int jar, boolean wholeStack) {
        for (int slot = lastSlot(jar); slot >= firstSlot(jar); slot--) {
            ItemStack existing = items.getStackInSlot(slot);
            if (existing.isEmpty()) {
                continue;
            }
            ItemStack taken = wholeStack ? existing.copy() : existing.copyWithCount(1);
            existing.shrink(taken.getCount());
            return taken;
        }
        return ItemStack.EMPTY;
    }

    public boolean hasAnyJarContent() {
        return !isEmpty();
    }

    /** Set when the drop has already been produced, so onRemove does not produce a second one. */
    private boolean dropped;

    public boolean isDropped() {
        return dropped;
    }

    public void markDropped() {
        dropped = true;
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
        // a stack handler rebuilt from old data can come back smaller than it is now;
        // jars written before the four-corner rework stored Size=16
        items.setSize(JAR_COUNT * SLOTS_PER_JAR);
    }
}
