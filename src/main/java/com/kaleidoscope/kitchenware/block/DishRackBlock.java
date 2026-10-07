package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wooden dish rack: two shelves, four places each, holding bowls and flower pots.
 *
 * Which shelf you touch is the one you use — aim at the upper or the lower shelf and the item goes
 * on that shelf, filling places from the left. No interface: right click puts one down, an empty
 * hand takes one off, and the kitchen shovel lifts one off as well.
 */
public class DishRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DishRackBlock> CODEC = simpleCodec(DishRackBlock::new);
    private static final VoxelShape SHAPE = Shapes.box(0.0625, 0, 0.125, 0.9375, 1.0, 0.875);

    public DishRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 0 for the upper shelf, 1 for the lower one, from where the player hit the rack. */
    public static int shelfAt(BlockHitResult hit, BlockPos pos) {
        return hit.getLocation().y - pos.getY() >= 0.5D ? 0 : 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DishRackBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof DishRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            // the server owns the contents and answers with a block update
            return ItemInteractionResult.SUCCESS;
        }
        int shelf = shelfAt(hitResult, pos);

        if (!stack.isEmpty()) {
            int placed = rack.insert(shelf, stack);
            if (placed < 0) {
                tell(player, DishRackBlockEntity.accepts(stack)
                        ? "state.kaleidoscope_kitchenware.rack_shelf_full"
                        : "state.kaleidoscope_kitchenware.rack_bowls_only");
                return ItemInteractionResult.FAIL;
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack taken = rack.takeLast(shelf);
        if (taken.isEmpty()) {
            tell(player, "state.kaleidoscope_kitchenware.rack_shelf_empty");
            return ItemInteractionResult.FAIL;
        }
        player.getInventory().placeItemBackInInventory(taken);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.9F);
        return ItemInteractionResult.SUCCESS;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        dropAll(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            dropAll(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void dropAll(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof DishRackBlockEntity rack)) {
            return;
        }
        rack.dropContents(level, pos);
    }
}
