package com.kaleidoscope.kitchenware.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
            // the client needs its own copy or the jar renders empty; send the update right away
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
    };

    public SpiceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPICE_JAR.get(), pos, state);
    }

    /** A jar holds seasoning only; the list lives in the item tag so datapacks can extend it. */
    public boolean accepts(ItemStack stack) {
        return stack.is(com.kaleidoscope.kitchenware.registry.ModTags.SPICE_JAR_ACCEPTS);
    }

    /**
     * Contents coming from a jar item always arrive in the first slot range, whichever corner the
     * player aimed at. When the block carries a single jar, line those contents up with the corner
     * the block actually shows — that is what makes a jar keep its seasoning wherever it is put
     * down, instead of only when it happens to land on the first corner.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        int shown = -1;
        int standing = 0;
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (state.getValue(com.kaleidoscope.kitchenware.block.SpiceJarBlock.JARS[corner])) {
                if (shown < 0) {
                    shown = corner;
                }
                standing++;
            }
        }
        int holds = -1;
        int holding = 0;
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (!isJarEmpty(corner)) {
                if (holds < 0) {
                    holds = corner;
                }
                holding++;
            }
        }

        // one jar: line it up with the corner the block shows, or light that corner if the
        // placement never set one
        if (holding == 1) {
            if (standing == 0) {
                level.setBlockAndUpdate(worldPosition,
                        state.setValue(com.kaleidoscope.kitchenware.block.SpiceJarBlock.JARS[holds], true));
                return;
            }
            if (shown != holds) {
                moveJarRange(holds, shown);
            }
            return;
        }

        // Never extinguish a standing jar: an empty jar is still a jar, and clearing its flag
        // used to drop the contents with the block. Only light up corners that hold something
        // but are dark, which is what a drifted placement looks like.
        BlockState fixed = state;
        boolean changed = false;
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (!isJarEmpty(corner) && !state.getValue(com.kaleidoscope.kitchenware.block.SpiceJarBlock.JARS[corner])) {
                fixed = fixed.setValue(com.kaleidoscope.kitchenware.block.SpiceJarBlock.JARS[corner], true);
                changed = true;
            }
        }
        if (changed) {
            level.setBlockAndUpdate(worldPosition, fixed);
            com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                    "[jardbg] onLoad lit dark corners holding contents {} -> {}",
                    com.kaleidoscope.kitchenware.block.SpiceJarBlock.dumpFlags(state),
                    com.kaleidoscope.kitchenware.block.SpiceJarBlock.dumpFlags(fixed));
        }
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

    /** Diagnostics: how many individual items one jar holds right now. */
    public int carryingCount(int jar) {
        int count = 0;
        for (int slot = firstSlot(jar); slot <= lastSlot(jar); slot++) {
            count += items.getStackInSlot(slot).getCount();
        }
        return count;
    }

    public boolean isEmpty() {
        for (int jar = 0; jar < JAR_COUNT; jar++) {
            if (!isJarEmpty(jar)) {
                return false;
            }
        }
        return true;
    }

    /** Stores items, returning how many actually went in. One jar holds one kind of thing. */
    public int insert(int jar, ItemStack stack, boolean wholeStack) {
        if (stack.isEmpty() || !accepts(stack)) {
            return 0;
        }
        // a jar is a one-seasoning container: refuse anything that is not already in it
        ItemStack resident = displayItem(jar);
        if (!resident.isEmpty() && !ItemStack.isSameItemSameComponents(resident, stack)) {
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

    /**
     * One jar's own contents as a tag, so a block with four jars drops four jars that each
     * carry their own seasoning.
     */
    public CompoundTag saveSingleJar(int corner, HolderLookup.Provider registries) {
        ItemStackHandler single = new ItemStackHandler(SLOTS_PER_JAR);
        for (int slot = 0; slot < SLOTS_PER_JAR; slot++) {
            ItemStack stack = items.getStackInSlot(firstSlot(corner) + slot);
            if (!stack.isEmpty()) {
                single.setStackInSlot(slot, stack.copy());
            }
        }
        return single.serializeNBT(registries);
    }

    /** Moves one jar's contents to another corner, used when an item is put down. */
    public void moveJarRange(int from, int to) {
        if (from == to) {
            return;
        }
        for (int slot = 0; slot < SLOTS_PER_JAR; slot++) {
            items.setStackInSlot(firstSlot(to) + slot, items.getStackInSlot(firstSlot(from) + slot));
            items.setStackInSlot(firstSlot(from) + slot, ItemStack.EMPTY);
        }
    }

    /**
     * Copies the contents carried by a jar item into one corner. Needed when a stocked jar is
     * placed onto a block that already exists, where vanilla never applies the item's data.
     */
    public void absorbSingleJar(CompoundTag itemsTag, int corner, HolderLookup.Provider registries) {
        ItemStackHandler single = new ItemStackHandler(SLOTS_PER_JAR);
        single.deserializeNBT(registries, itemsTag);
        for (int slot = 0; slot < SLOTS_PER_JAR; slot++) {
            ItemStack stack = single.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.setStackInSlot(firstSlot(corner) + slot, stack);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
    }

    /**
     * Without this the client gets an empty tag and the jar renders with nothing inside, however
     * much the server side is holding.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Empties every corner. Used when a jar item's own contents are written in wholesale. */
    public void clearAll() {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag saved = tag.getCompound("Items");
        items.deserializeNBT(registries, saved);
        // deserializeNBT resizes the handler to whatever Size the data carries (an item holds one
        // jar, so 16); grow it back to the full four-jar layout before anything reads a slot
        items.setSize(JAR_COUNT * SLOTS_PER_JAR);
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jardbg] loadAdditional keys={} savedSize={} savedItems={} slots={} stored={}",
                tag.getAllKeys(), saved.getInt("Size"), saved.getList("Items", 10).size(),
                items.getSlots(), carryingTotal());
    }

    /** Diagnostics: total item count across all four jars. */
    public int carryingTotal() {
        int count = 0;
        for (int jar = 0; jar < JAR_COUNT; jar++) {
            count += carryingCount(jar);
        }
        return count;
    }
}
