package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.item.SpiceJarItem;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.registry.ModItems;
import com.kaleidoscope.kitchenware.util.NoGuiStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Spice jars: up to four squat jars share one block, the way teacups stack.
 *
 * Right-click with a jar to add another one, store and take seasonings with plain
 * right-clicks (no GUI), and scoop the whole lot back up with the kitchen shovel.
 */
public class SpiceJarBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SpiceJarBlock> CODEC = simpleCodec(SpiceJarBlock::new);
    public static final int MAX_COUNT = 4;
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, MAX_COUNT);
    /** Set while the stack holds anything, so the models can show filled jars. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    private static final TagKey<Item> KITCHEN_SHOVEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "kitchen_shovel"));
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 5, 15);

    public SpiceJarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(COUNT, 1)
                .setValue(FILLED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COUNT, FILLED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiceJarBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand == InteractionHand.OFF_HAND || !(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // stack another jar onto the block, like adding a teacup
        if (stack.getItem() instanceof SpiceJarItem) {
            int count = state.getValue(COUNT);
            if (count >= MAX_COUNT) {
                return ItemInteractionResult.CONSUME;
            }
            level.setBlockAndUpdate(pos, state.setValue(COUNT, count + 1));
            level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // scoop every jar up, contents included
        if (stack.is(KITCHEN_SHOVEL) || stack.is(ItemTags.SHOVELS)) {
            if (!level.isClientSide) {
                int count = state.getValue(COUNT);
                Block.popResource(level, pos, jarStack(jar, level.registryAccess()));
                for (int i = 1; i < count; i++) {
                    Block.popResource(level, pos, new ItemStack(ModItems.SPICE_JAR.get()));
                }
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return NoGuiStorage.interact(stack, level, pos, player, hand, jar);
    }

    /**
     * One jar as an item, carrying whatever the block holds.
     *
     * An empty jar stays a plain stack so it stacks normally. A stocked one carries the
     * block entity data, which must include the block entity id or saving the player
     * inventory blows up.
     */
    public static ItemStack jarStack(SpiceJarBlockEntity jar, RegistryAccess access) {
        ItemStack stack = new ItemStack(ModItems.SPICE_JAR.get());
        if (jar.isEmpty()) {
            return stack;
        }
        CompoundTag contents = jar.saveCustomOnly(access);
        contents.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(jar.getType()).toString());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(contents));
        return stack;
    }

    /** Drops one jar with the contents plus the empties; the loot table stays empty. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar) {
            int count = state.getValue(COUNT);
            Block.popResource(level, pos, jarStack(jar, level.registryAccess()));
            for (int i = 1; i < count; i++) {
                Block.popResource(level, pos, new ItemStack(ModItems.SPICE_JAR.get()));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
