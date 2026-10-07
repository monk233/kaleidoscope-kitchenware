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
 * Four spice jars share one block, one per corner. Each jar holds a single kind of seasoning,
 * up to {@link #JAR_CAPACITY} items.
 *
 * Storage is deliberately four plain item stacks rather than a slot handler: a jar is "one kind
 * of thing, one big pile", and a handler's size-versus-data dance kept losing contents on the
 * way through an item.
 */
public class SpiceJarBlockEntity extends BlockEntity {
    public static final int JAR_COUNT = 4;
    /** One jar: a single seasoning, up to 16 stacks of 64. */
    public static final int JAR_CAPACITY = 1024;

    private final ItemStack[] jars = new ItemStack[JAR_COUNT];
    private boolean dropped;

    public SpiceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPICE_JAR.get(), pos, state);
        clearAll();
    }

    public boolean accepts(ItemStack stack) {
        return stack.is(com.kaleidoscope.kitchenware.registry.ModTags.SPICE_JAR_ACCEPTS);
    }

    private static int clampCorner(int corner) {
        return Math.max(0, Math.min(JAR_COUNT - 1, corner));
    }

    public ItemStack jar(int corner) {
        return jars[clampCorner(corner)];
    }

    /** What the jar shows on top. */
    public ItemStack displayItem(int corner) {
        return jar(corner);
    }

    public boolean isJarEmpty(int corner) {
        return jar(corner).isEmpty();
    }

    public boolean isEmpty() {
        for (ItemStack stack : jars) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int carryingCount(int corner) {
        return jar(corner).getCount();
    }

    public int carryingTotal() {
        int total = 0;
        for (ItemStack stack : jars) {
            total += stack.getCount();
        }
        return total;
    }

    public void clearAll() {
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            jars[corner] = ItemStack.EMPTY;
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info("[jaraudit] {} clearAll",
                worldPosition);
    }

    /** Stores seasoning, returning how many actually went in. One jar holds one kind of thing. */
    public int insert(int corner, ItemStack stack, boolean wholeStack) {
        corner = clampCorner(corner);
        if (stack.isEmpty() || !accepts(stack)) {
            return 0;
        }
        ItemStack held = jars[corner];
        if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) {
            return 0;
        }
        int room = JAR_CAPACITY - held.getCount();
        if (room <= 0) {
            return 0;
        }
        int moved = Math.min(room, wholeStack ? stack.getCount() : 1);
        if (held.isEmpty()) {
            jars[corner] = stack.copyWithCount(moved);
        } else {
            held.grow(moved);
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jaraudit] insert corner={} moved={} now={}", corner, moved, carryingCount(corner));
        setChangedAndSynced();
        return moved;
    }

    /** Takes a whole jar's worth, or a single item. */
    public ItemStack extract(int corner, boolean wholeStack) {
        corner = clampCorner(corner);
        ItemStack held = jars[corner];
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = wholeStack ? held.copy() : held.copyWithCount(1);
        held.shrink(taken.getCount());
        if (held.isEmpty()) {
            jars[corner] = ItemStack.EMPTY;
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jaraudit] extract corner={} took={} now={}", corner, taken.getCount(),
                carryingCount(corner));
        setChangedAndSynced();
        return taken;
    }

    /**
     * Moves one jar's contents to another corner, used when an item is put down.
     *
     * Never overwrites: same seasoning is merged up to capacity, anything else is left where it
     * is. A blind assignment here silently ate a full jar during corner alignment.
     */
    public void moveJarRange(int from, int to) {
        from = clampCorner(from);
        to = clampCorner(to);
        if (from == to || jars[from].isEmpty()) {
            return;
        }
        if (!jars[to].isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(jars[to], jars[from])) {
                return;
            }
            int room = JAR_CAPACITY - jars[to].getCount();
            int moved = Math.min(room, jars[from].getCount());
            if (moved <= 0) {
                return;
            }
            jars[to].grow(moved);
            jars[from].shrink(moved);
            if (jars[from].isEmpty()) {
                jars[from] = ItemStack.EMPTY;
            }
            com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                    "[jaraudit] {} merge {}->{} moved={} now={},{},{}",
                    worldPosition, from, to, moved, carryingCount(from), carryingCount(to),
                    carryingTotal());
            setChangedAndSynced();
            return;
        }
        jars[to] = jars[from];
        jars[from] = ItemStack.EMPTY;
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jaraudit] {} move {}->{} count={} total={}", worldPosition, from, to,
                carryingCount(to), carryingTotal());
        setChangedAndSynced();
    }

    /**
     * Copies a single jar carried by an item into one corner.
     *
     * A corner that already holds a different seasoning is left alone rather than overwritten.
     */
    public void absorbSingleJar(CompoundTag jarTag, int corner, HolderLookup.Provider registries) {
        corner = clampCorner(corner);
        ItemStack carried = ItemStack.parseOptional(registries, jarTag.getCompound("Jar"));
        if (!carried.isEmpty() && !isJarEmpty(corner)
                && !ItemStack.isSameItemSameComponents(jars[corner], carried)) {
            return;
        }
        jars[corner] = carried.isEmpty() ? ItemStack.EMPTY : carried;
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jaraudit] {} absorb corner={} got={} total={} keys={}", worldPosition, corner,
                carryingCount(corner), carryingTotal(), jarTag.getAllKeys());
        setChangedAndSynced();
    }

    /** One jar's own contents as a tag, for the item form. */
    public CompoundTag saveSingleJar(int corner, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ItemStack held = jar(corner);
        if (!held.isEmpty()) {
            tag.put("Jar", held.save(registries));
        }
        return tag;
    }

    private void setChangedAndSynced() {
        setChanged();
        // the client needs its own copy or the jar renders empty; push the update right away
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
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

    public boolean isDropped() {
        return dropped;
    }

    public void markDropped() {
        dropped = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (!jars[corner].isEmpty()) {
                tag.put("Jar" + corner, jars[corner].save(registries));
            }
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jaraudit] save total={} counts={},{},{},{}", carryingTotal(), carryingCount(0),
                carryingCount(1), carryingCount(2), carryingCount(3));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearAll();
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            CompoundTag jarTag = tag.getCompound("Jar" + corner);
            if (!jarTag.isEmpty()) {
                jars[corner] = ItemStack.parseOptional(registries, jarTag);
            }
        }
        com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.LOGGER.info(
                "[jaraudit] load keys={} total={} counts={},{},{},{}",
                tag.getAllKeys(), carryingTotal(), carryingCount(0), carryingCount(1),
                carryingCount(2), carryingCount(3));
    }

    /**
     * Contents coming from a jar item arrive in the first corner; when the block carries a single
     * jar, line it up with the corner the block shows. A standing jar is never extinguished: an
     * empty jar is still a jar.
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
        BlockState fixed = state;
        boolean changed = false;
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (!isJarEmpty(corner)
                    && !state.getValue(com.kaleidoscope.kitchenware.block.SpiceJarBlock.JARS[corner])) {
                fixed = fixed.setValue(com.kaleidoscope.kitchenware.block.SpiceJarBlock.JARS[corner], true);
                changed = true;
            }
        }
        if (changed) {
            level.setBlockAndUpdate(worldPosition, fixed);
        }
    }
}
