package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.registry.ModItems;
import com.kaleidoscope.kitchenware.util.NoGuiStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Spice jar: a small glass jar that sits on the worktop like a teacup, holding 16 stacks.
 *
 * Stock it and empty it with right-clicks (no GUI), and pick the whole jar up again with
 * the base mod's kitchen shovel, contents and all.
 */
public class SpiceJarBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SpiceJarBlock> CODEC = simpleCodec(SpiceJarBlock::new);
    /** Set while the jar holds anything, so the model can show a filled jar. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public static final String MOD_ID = "kaleidoscope_kitchenware";
    private static final TagKey<Item> KITCHEN_SHOVEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "kitchen_shovel"));
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public SpiceJarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FILLED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILLED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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

        // scoop the whole jar up, contents included
        if (stack.is(KITCHEN_SHOVEL) || stack.is(ItemTags.SHOVELS)) {
            if (level instanceof ServerLevel serverLevel) {
                ItemStack picked = jarStack(jar, serverLevel.registryAccess());
                level.removeBlock(pos, false);
                player.getInventory().placeItemBackInInventory(picked);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return NoGuiStorage.interact(stack, level, pos, player, hand, jar);
    }

    /** The jar as an item, carrying whatever it holds. */
    public static ItemStack jarStack(SpiceJarBlockEntity jar, RegistryAccess access) {
        ItemStack stack = new ItemStack(ModItems.SPICE_JAR.get());
        CompoundTag contents = jar.saveCustomOnly(access);
        if (!contents.isEmpty()) {
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(contents));
        }
        return stack;
    }

    /**
     * Drops the jar with its contents, whatever removed the block. The loot table stays
     * empty on purpose so nothing is dropped twice.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar) {
            Block.popResource(level, pos, jarStack(jar, level.registryAccess()));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
