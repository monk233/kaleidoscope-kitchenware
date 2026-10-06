package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.SpiceRackBlockEntity;
import com.kaleidoscope.kitchenware.util.NoGuiStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Spice rack: 8 slots of anything, and the shelf fills up visually as you stock it.
 *
 * It also joins up with its neighbours the way stairs do, so a rack turning a corner
 * grows the extra back panel instead of leaving a gap.
 */
public class SpiceRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SpiceRackBlock> CODEC = simpleCodec(SpiceRackBlock::new);
    /** 0 = empty, 4 = full; each step is two stored items. */
    public static final IntegerProperty FILLED = IntegerProperty.create("filled", 0, 4);
    public static final EnumProperty<StairsShape> SHAPE = StairBlock.SHAPE;

    /** Boards and shelf stop 2 pixels short of the open side, so the shape is a slab. */
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 2, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 14);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 0, 0, 14, 16, 16);
    private static final VoxelShape SHAPE_WEST = Block.box(2, 0, 0, 16, 16, 16);

    public SpiceRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FILLED, 0)
                .setValue(SHAPE, StairsShape.STRAIGHT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILLED, SHAPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.setValue(SHAPE, shapeFor(state, context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                     BlockPos pos, BlockPos facingPos) {
        return facing.getAxis().isHorizontal() ? state.setValue(SHAPE, shapeFor(state, level, pos)) : super.updateShape(
                state, facing, facingState, level, pos, facingPos);
    }

    /** Same rule stairs use: a neighbouring rack turned 90 degrees grows a corner panel. */
    private static StairsShape shapeFor(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState inFront = level.getBlockState(pos.relative(facing));
        if (isRack(inFront) && inFront.getValue(FACING).getAxis() != facing.getAxis()
                && canTakeShape(state, level, pos, inFront.getValue(FACING).getOpposite())) {
            return inFront.getValue(FACING) == facing.getCounterClockWise()
                    ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
        }
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        if (isRack(behind) && behind.getValue(FACING).getAxis() != facing.getAxis()
                && canTakeShape(state, level, pos, behind.getValue(FACING))) {
            return behind.getValue(FACING) == facing.getCounterClockWise()
                    ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        BlockState neighbour = level.getBlockState(pos.relative(face));
        return !isRack(neighbour) || neighbour.getValue(FACING) != state.getValue(FACING);
    }

    public static boolean isRack(BlockState state) {
        return state.getBlock() instanceof SpiceRackBlock;
    }

    /** Left-hand shapes grow the corner panel on one side, right-hand ones on the other. */
    public static boolean isLeftCorner(BlockState state) {
        StairsShape shape = state.getValue(SHAPE);
        return shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;
    }

    public static boolean isCorner(BlockState state) {
        return state.getValue(SHAPE) != StairsShape.STRAIGHT;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiceRackBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof SpiceRackBlockEntity rack) {
            return NoGuiStorage.interact(stack, level, pos, player, hand, rack);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static BlockState withFillLevel(BlockState state, int stored) {
        int level = Math.min(4, (stored + 1) / 2);
        return state.getValue(FILLED) == level ? state : state.setValue(FILLED, level);
    }
}
