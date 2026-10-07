package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A two shelf dish rack holding bowls and flower pots, one per place, four places per shelf.
 *
 * Reuses the cupboard's whitelist: anything the cupboard takes, the rack takes. Nothing here is
 * position dependent — a broken rack drops what it held, so there is no state to keep in step
 * with the contents.
 */
public class DishRackBlockEntity extends BlockEntity {
    /** Places on one shelf, drawn side by side. */
    public static final int SHELF_SLOTS = 4;
    public static final int SHELVES = 2;
    public static final int SLOTS = SHELF_SLOTS * SHELVES;

    private final ItemStack[] slots = new ItemStack[SLOTS];

    public DishRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISH_RACK.get(), pos, state);
        for (int slot = 0; slot < SLOTS; slot++) {
            slots[slot] = ItemStack.EMPTY;
        }
    }

    public static int clampShelf(int shelf) {
        return Math.max(0, Math.min(SHELVES - 1, shelf));
    }

    public static int clampSlot(int slot) {
        return Math.max(0, Math.min(SLOTS - 1, slot));
    }

    public static boolean accepts(ItemStack stack) {
        return stack.is(com.kaleidoscope.kitchenware.registry.ModTags.CUPBOARD_STORABLE);
    }

    public ItemStack stored(int slot) {
        return slots[clampSlot(slot)];
    }

    public boolean isSlotEmpty(int slot) {
        return stored(slot).isEmpty();
    }

    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** First free place on a shelf, or -1 when that shelf is full. */
    public int firstFree(int shelf) {
        int start = clampShelf(shelf) * SHELF_SLOTS;
        for (int index = start; index < start + SHELF_SLOTS; index++) {
            if (slots[index].isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    /** Last filled place on a shelf, or -1 when that shelf is empty. */
    public int lastFilled(int shelf) {
        int start = clampShelf(shelf) * SHELF_SLOTS;
        for (int index = start + SHELF_SLOTS - 1; index >= start; index--) {
            if (!slots[index].isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    /** Puts one item on a shelf. Returns the place used, or -1 if it was refused. */
    public int insert(int shelf, ItemStack stack) {
        if (level != null && level.isClientSide) {
            return -1;
        }
        if (stack.isEmpty() || !accepts(stack)) {
            return -1;
        }
        int free = firstFree(shelf);
        if (free < 0) {
            return -1;
        }
        slots[free] = stack.copyWithCount(1);
        changed();
        return free;
    }

    /** Takes the last item off a shelf, or EMPTY when there is nothing there. */
    public ItemStack takeLast(int shelf) {
        if (level != null && level.isClientSide) {
            return ItemStack.EMPTY;
        }
        int filled = lastFilled(shelf);
        if (filled < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = slots[filled];
        slots[filled] = ItemStack.EMPTY;
        changed();
        return taken;
    }

    public int storedCount() {
        int count = 0;
        for (ItemStack stack : slots) {
            count += stack.getCount();
        }
        return count;
    }

    private void changed() {
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet = getUpdatePacket();
            if (packet != null) {
                for (net.minecraft.server.level.ServerPlayer player
                        : server.getChunkSource().chunkMap.getPlayers(
                                new net.minecraft.world.level.ChunkPos(worldPosition), false)) {
                    player.connection.send(packet);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** An empty tag means "the rack is empty", so it has to be applied rather than skipped. */
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        loadAdditional(packet.getTag(), registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!slots[slot].isEmpty()) {
                tag.put("S" + slot, slots[slot].save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int slot = 0; slot < SLOTS; slot++) {
            CompoundTag entry = tag.getCompound("S" + slot);
            slots[slot] = entry.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, entry);
        }
    }

    /** Drops one item per filled place; called before the block leaves the world. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!slots[slot].isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, slots[slot].copy());
                slots[slot] = ItemStack.EMPTY;
            }
        }
    }
}
