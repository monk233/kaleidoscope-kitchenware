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
    /** How many ticks the entity has spent re-sending its contents after being loaded. */
    private int syncTicks;
    /** Ticks spent pushing contents at startup, to cover a client that is not ready yet. */
    private static final int SYNC_BURST = 5;

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

    /** How many stacks a compartment holds. Drives the stacked rendering. */
    public int storedStacks(int slot) {
        int stacks = 0;
        for (ItemStack stack : contents[clamp(slot)]) {
            if (!stack.isEmpty()) {
                stacks++;
            }
        }
        return stacks;
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
            com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                    "[tray] {} insert slot={} moved={} now={}", worldPosition, slot, moved,
                    storedCount(slot));
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
            com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.debug(
                    "[tray] {} took {} from compartment {}", worldPosition, take, slot);
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
     * Pushes the contents to clients.
     *
     * Not sendBlockUpdated: that only makes a client re-read the entity when the block state
     * changed, and taking the last item out of a compartment changes the data and nothing else.
     * The client therefore kept rendering what it had, which is exactly the reported "shows the
     * item after the compartment is empty".
     */
    public void syncNow() {
        setChanged();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet = getUpdatePacket();
        if (packet == null) {
            return;
        }
        int sent = 0;
        for (net.minecraft.server.level.ServerPlayer player
                : server.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition),
                        false)) {
            player.connection.send(packet);
            sent++;
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[tray] {} sync sent={} counts={},{},{},{}", worldPosition, sent,
                storedCount(0), storedCount(1), storedCount(2), storedCount(3));
    }

    /**
     * Keeps re-sending the contents for the first few ticks.
     *
     * A single push on the first tick was not enough: putting something in immediately after
     * placing the tray landed before the client had the entity, so the first insert never showed
     * up until the next change. A short burst covers that window.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SeasoningTrayBlockEntity tray) {
        if (tray.syncTicks >= SYNC_BURST) {
            return;
        }
        tray.syncTicks++;
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

    /**
     * Applies server updates even when the tag is empty.
     *
     * Vanilla's default skips empty tags, but for a tray an empty tag is meaningful: it says
     * nothing is left. Taking the last item out of the last stocked compartment sent an empty tag,
     * the client ignored it, and the compartment went on rendering an item that was already gone.
     */
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        loadAdditional(packet.getTag(), registries);
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[tray] {} client data applied counts={},{},{},{}", worldPosition,
                storedCount(0), storedCount(1), storedCount(2), storedCount(3));
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
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.debug(
                "[tray] {} loaded counts={},{},{},{}", worldPosition,
                storedCount(0), storedCount(1), storedCount(2), storedCount(3));
    }
}
