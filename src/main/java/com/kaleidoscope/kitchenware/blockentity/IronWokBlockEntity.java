package com.kaleidoscope.kitchenware.blockentity;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.BaseRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModSoupBases;
import com.kaleidoscope.kitchenware.block.FirewoodStoveBlock;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * The big iron wok. One block, two cooking modes: what the player puts in first decides which one
 * it is - oil starts a stir fry, a soup base starts a soup. An empty wok can change its mind.
 *
 * Everything in here is copied from the base mod's own pot and stockpot, deliberately: the timings,
 * the order the states move in, the carrier handling and the "suspicious" fallbacks all match, so a
 * player who knows the base mod's pots already knows this one. What is new is the mode switch, the
 * fact that a wok without a lit range under it does nothing, and that breaking it hands the
 * contents back.
 */
public class IronWokBlockEntity extends BlockEntity {
    /** What the wok is being used for. An empty wok is undecided. */
    public enum Mode {
        NONE,
        STIR_FRY,
        SOUP
    }

    private static final String INPUTS = "Inputs";
    private static final String MODE = "Mode";
    private static final String RESULT = "Result";
    private static final String STATUS = "Status";
    private static final String CURRENT_TICK = "CurrentTick";
    private static final String STIR_FRY_COUNT = "StirFryCount";
    private static final String SOUP_BASE_ID = "SoupBaseId";
    private static final String TAKEOUT_COUNT = "TakeoutCount";

    private Mode mode = Mode.NONE;
    /** Nine slots, the same size the base mod's recipes are written against. */
    private final NonNullList<ItemStack> inputs = NonNullList.withSize(BaseRecipe.RECIPES_SIZE, ItemStack.EMPTY);
    private ItemStack result = ItemStack.EMPTY;
    private int status;
    private int currentTick;
    private int stirFryCount;
    private ResourceLocation soupBaseId = ModSoupBases.WATER;
    private int takeoutCount;

    public IronWokBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_WOK.get(), pos, state);
    }

    public Mode mode() {
        return mode;
    }

    public boolean isEmpty() {
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return result.isEmpty();
    }

    /**
     * The heat contract, the same one the base mod uses: the block below has to be a lit range.
     * Reading the property keeps this working without asking the stove's block entity anything.
     */
    public boolean hasHeatSource(Level level) {
        BlockState below = level.getBlockState(worldPosition.below());
        return below.getBlock() instanceof FirewoodStoveBlock
                && below.hasProperty(BlockStateProperties.LIT)
                && below.getValue(BlockStateProperties.LIT);
    }

    /** Sends the whole state to tracking clients, the way the base mod's own block entities do. */
    public void refresh() {
        setChanged();
        Level level = getLevel();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Hands everything in the wok to the world, used when the wok itself is broken. */
    public void dropContents() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        if (!result.isEmpty()) {
            Block.popResource(level, worldPosition, result.copy());
        }
        inputs.clear();
        result = ItemStack.EMPTY;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(INPUTS, ContainerHelper.saveAllItems(new CompoundTag(), inputs, registries));
        tag.putString(MODE, mode.name());
        tag.put(RESULT, result.saveOptional(registries));
        tag.putInt(STATUS, status);
        tag.putInt(CURRENT_TICK, currentTick);
        tag.putInt(STIR_FRY_COUNT, stirFryCount);
        tag.putString(SOUP_BASE_ID, soupBaseId.toString());
        tag.putInt(TAKEOUT_COUNT, takeoutCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INPUTS)) {
            ContainerHelper.loadAllItems(tag.getCompound(INPUTS), inputs, registries);
        }
        for (Mode candidate : Mode.values()) {
            if (candidate.name().equals(tag.getString(MODE))) {
                mode = candidate;
            }
        }
        result = tag.contains(RESULT) ? ItemStack.parseOptional(registries, tag.getCompound(RESULT)) : ItemStack.EMPTY;
        status = tag.getInt(STATUS);
        currentTick = tag.getInt(CURRENT_TICK);
        stirFryCount = tag.getInt(STIR_FRY_COUNT);
        ResourceLocation storedBase = ResourceLocation.tryParse(tag.getString(SOUP_BASE_ID));
        if (storedBase != null) {
            soupBaseId = storedBase;
        }
        takeoutCount = tag.getInt(TAKEOUT_COUNT);
    }

    /** Placeholder until the cooking half of the wok lands: nothing ticks yet. */
    public void serverTick() {
    }
}
