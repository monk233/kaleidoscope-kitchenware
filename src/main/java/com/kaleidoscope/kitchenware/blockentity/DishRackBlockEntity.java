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
 * A two shelf dish rack holding bowls and flower pots, up to sixty four per shelf.
 *
 * Reuses the cupboard's whitelist: anything the cupboard takes, the rack takes. Nothing here is
 * position dependent — a broken rack drops what it held, so there is no state to keep in step
 * with the contents.
 */
public class DishRackBlockEntity extends BlockEntity {
    public static final int SHELVES = 2;
    /** One kind of thing per shelf, this many of it. */
    public static final int SHELF_CAPACITY = 64;

    private final ItemStack[] shelves = new ItemStack[SHELVES];

    public DishRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISH_RACK.get(), pos, state);
        for (int shelf = 0; shelf < SHELVES; shelf++) {
            shelves[shelf] = ItemStack.EMPTY;
        }
    }

    public static int clampShelf(int shelf) {
        return Math.max(0, Math.min(SHELVES - 1, shelf));
    }

    public static boolean accepts(ItemStack stack) {
        return stack.is(com.kaleidoscope.kitchenware.registry.ModTags.CUPBOARD_STORABLE);
    }

    public ItemStack stored(int shelf) {
        return shelves[clampShelf(shelf)];
    }

    public boolean isEmpty() {
        for (ItemStack stack : shelves) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int storedCount(int shelf) {
        return stored(shelf).getCount();
    }

    /** Adds to a shelf, returning how many went on. One shelf, one kind of thing. */
    public int insert(int shelf, ItemStack stack) {
        shelf = clampShelf(shelf);
        if (level != null && level.isClientSide) {
            return 0;
        }
        if (stack.isEmpty() || !accepts(stack)) {
            return 0;
        }
        ItemStack held = shelves[shelf];
        if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) {
            return 0;
        }
        int room = SHELF_CAPACITY - held.getCount();
        if (room <= 0) {
            return 0;
        }
        int moved = Math.min(room, stack.getCount());
        if (held.isEmpty()) {
            shelves[shelf] = stack.copyWithCount(moved);
        } else {
            held.grow(moved);
        }
        changed();
        return moved;
    }

    /** Takes one item off a shelf, or EMPTY when there is nothing there. */
    public ItemStack takeOne(int shelf) {
        shelf = clampShelf(shelf);
        if (level != null && level.isClientSide) {
            return ItemStack.EMPTY;
        }
        ItemStack held = shelves[shelf];
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = held.copyWithCount(1);
        held.shrink(1);
        if (held.isEmpty()) {
            shelves[shelf] = ItemStack.EMPTY;
        }
        changed();
        return taken;
    }

    /** Takes a whole shelf at once. */
    public ItemStack takeAll(int shelf) {
        shelf = clampShelf(shelf);
        if (level != null && level.isClientSide) {
            return ItemStack.EMPTY;
        }
        ItemStack held = shelves[shelf];
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        shelves[shelf] = ItemStack.EMPTY;
        changed();
        return held;
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
        for (int shelf = 0; shelf < SHELVES; shelf++) {
            if (!shelves[shelf].isEmpty()) {
                tag.put("S" + shelf, shelves[shelf].save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int shelf = 0; shelf < SHELVES; shelf++) {
            CompoundTag entry = tag.getCompound("S" + shelf);
            shelves[shelf] = entry.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, entry);
        }
    }

    /** Drops what the rack held; called before the block leaves the world. */
    public void dropContents(Level level, BlockPos pos) {
        for (int shelf = 0; shelf < SHELVES; shelf++) {
            if (!shelves[shelf].isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, shelves[shelf].copy());
                shelves[shelf] = ItemStack.EMPTY;
            }
        }
    }
}
