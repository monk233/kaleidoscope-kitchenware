package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A seasoning tray: four compartments, each holding one kind of seasoning up to 1024 items.
 *
 * A compartment is sixteen plain stacks because an ItemStack cannot serialise a count above 99 -
 * one stack per compartment threw "Value must be within range [1;99]: 1024" the moment it was
 * saved. Serialisation is a flat list of per-stack tags, so there is no size field to disagree
 * with and no handler to quietly resize.
 *
 * Carrying contents through an item is plain custom data on the stack, deliberately not vanilla's
 * block-entity data component.
 */
public class SeasoningTrayBlockEntity extends BlockEntity {
    public static final int COMPARTMENTS = 4;
    public static final int STACKS_PER_COMPARTMENT = 16;
    public static final int CAPACITY = STACKS_PER_COMPARTMENT * 64;

    private final ItemStack[][] contents = new ItemStack[COMPARTMENTS][STACKS_PER_COMPARTMENT];
    private boolean dropped;
    private boolean syncedOnce;

    public SeasoningTrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEASONING_TRAY.get(), pos, state);
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            for (int stack = 0; stack < STACKS_PER_COMPARTMENT; stack++) {
                contents[slot][stack] = ItemStack.EMPTY;
            }
        }
    }

    public static int clamp(int slot) {
        return Math.max(0, Math.min(COMPARTMENTS - 1, slot));
    }

    public static boolean isSeasoning(ItemStack stack) {
        return stack.is(com.kaleidoscope.kitchenware.registry.ModTags.SEASONING);
    }

    /** What this compartment holds, for rendering and checks. */
    public ItemStack stored(int slot) {
        for (ItemStack stack : contents[clamp(slot)]) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public int storedCount(int slot) {
        int total = 0;
        for (ItemStack stack : contents[clamp(slot)]) {
            total += stack.getCount();
        }
        return total;
    }

    public boolean isEmpty() {
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            if (storedCount(slot) > 0) {
                return false;
            }
        }
        return true;
    }

    /** Stores seasoning, returning how many went in. One compartment, one kind of thing. */
    public int insert(int slot, ItemStack stack, boolean wholeStack) {
        slot = clamp(slot);
        if (stack.isEmpty() || !isSeasoning(stack)) {
            return 0;
        }
        if (level != null && level.isClientSide) {
            return 0;
        }
        ItemStack resident = stored(slot);
        if (!resident.isEmpty() && !ItemStack.isSameItemSameComponents(resident, stack)) {
            return 0;
        }
        int amount = wholeStack ? stack.getCount() : 1;
        int moved = 0;
        for (int index = 0; index < STACKS_PER_COMPARTMENT && moved < amount; index++) {
            ItemStack held = contents[slot][index];
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
                int add = Math.min(amount - moved, 64 - held.getCount());
                held.grow(add);
                moved += add;
            }
        }
        for (int index = 0; index < STACKS_PER_COMPARTMENT && moved < amount; index++) {
            if (contents[slot][index].isEmpty()) {
                int add = Math.min(amount - moved, 64);
                contents[slot][index] = stack.copyWithCount(add);
                moved += add;
            }
        }
        if (moved > 0) {
            changed();
        }
        return moved;
    }

    /** Takes one item out. */
    public ItemStack extractOne(int slot) {
        slot = clamp(slot);
        if (level != null && level.isClientSide) {
            return ItemStack.EMPTY;
        }
        for (int index = STACKS_PER_COMPARTMENT - 1; index >= 0; index--) {
            ItemStack held = contents[slot][index];
            if (held.isEmpty()) {
                continue;
            }
            ItemStack taken = held.copyWithCount(1);
            held.shrink(1);
            if (held.isEmpty()) {
                contents[slot][index] = ItemStack.EMPTY;
            }
            changed();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Takes up to {@code amount} items out of one compartment, never mixing into a half stack.
     * A compartment is handed out a stack at a time because that is all an ItemStack can carry.
     */
    public ItemStack extractUpTo(int slot, int amount) {
        slot = clamp(slot);
        if (level != null && level.isClientSide) {
            return ItemStack.EMPTY;
        }
        for (int index = STACKS_PER_COMPARTMENT - 1; index >= 0; index--) {
            ItemStack held = contents[slot][index];
            if (held.isEmpty()) {
                continue;
            }
            int take = Math.min(amount, held.getCount());
            ItemStack taken = held.copyWithCount(take);
            held.shrink(take);
            if (held.isEmpty()) {
                contents[slot][index] = ItemStack.EMPTY;
            }
            changed();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    /** Empties a compartment, for callers that really want everything. */
    public List<ItemStack> extractAll(int slot) {
        slot = clamp(slot);
        List<ItemStack> drained = new ArrayList<>();
        if (level != null && level.isClientSide) {
            return drained;
        }
        for (int index = 0; index < STACKS_PER_COMPARTMENT; index++) {
            ItemStack held = contents[slot][index];
            if (!held.isEmpty()) {
                drained.add(held.copy());
                contents[slot][index] = ItemStack.EMPTY;
            }
        }
        if (!drained.isEmpty()) {
            changed();
        }
        return drained;
    }

    /** Set once the drop has been produced, so onRemove does not produce a second one. */
    public boolean isDropped() {
        return dropped;
    }

    public void markDropped() {
        dropped = true;
    }

    private void changed() {
        syncNow();
    }

    /**
     * Pushes the contents to clients. Called on every change, and once more on the entity's first
     * tick: a tray filled while it was being placed sent its update before the client had the
     * entity, so the client kept showing an empty tray until the chunk reloaded.
     */
    public void syncNow() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeasoningTrayBlockEntity tray) {
        if (tray.syncedOnce) {
            return;
        }
        tray.syncedOnce = true;
        tray.syncNow();
    }

    /** Writes the contents onto an item so they travel with the tray. */
    public void saveToItem(ItemStack item, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            for (int index = 0; index < STACKS_PER_COMPARTMENT; index++) {
                if (!contents[slot][index].isEmpty()) {
                    tag.put("C" + slot + "_" + index, contents[slot][index].save(registries));
                }
            }
        }
        if (!tag.isEmpty()) {
            item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
        }
    }

    /** Reads contents back from the item a tray was broken into. */
    public void loadFromItem(ItemStack item, HolderLookup.Provider registries) {
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            for (int index = 0; index < STACKS_PER_COMPARTMENT; index++) {
                contents[slot][index] = ItemStack.EMPTY;
            }
        }
        var data = item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            for (int slot = 0; slot < COMPARTMENTS; slot++) {
                for (int index = 0; index < STACKS_PER_COMPARTMENT; index++) {
                    CompoundTag entry = tag.getCompound("C" + slot + "_" + index);
                    if (!entry.isEmpty()) {
                        contents[slot][index] = ItemStack.parseOptional(registries, entry);
                    }
                }
            }
        }
        changed();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            for (int index = 0; index < STACKS_PER_COMPARTMENT; index++) {
                if (!contents[slot][index].isEmpty()) {
                    tag.put("C" + slot + "_" + index, contents[slot][index].save(registries));
                }
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            for (int index = 0; index < STACKS_PER_COMPARTMENT; index++) {
                CompoundTag entry = tag.getCompound("C" + slot + "_" + index);
                contents[slot][index] = entry.isEmpty() ? ItemStack.EMPTY
                        : ItemStack.parseOptional(registries, entry);
            }
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[tray] {} load keys={} counts={},{},{},{}", worldPosition, tag.getAllKeys(),
                storedCount(0), storedCount(1), storedCount(2), storedCount(3));
    }
}
