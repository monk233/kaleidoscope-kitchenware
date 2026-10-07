package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.IronWokBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The big iron wok: it only sits on a stove burner, and it cooks both stir fries and soups.
 *
 * The bowl hangs below its own block, down into the burner cavity of the stove underneath, so that
 * the rim ends up flush with the brick counter rather than floating on top of it. That is also why
 * it cannot be placed anywhere else: without a burner under it, half of the model would be buried
 * in the ground.
 *
 * It drops itself and everything in it. The base mod's pot swallows its contents; there is no
 * reason to copy that.
 */
public class IronWokBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<IronWokBlock> CODEC = simpleCodec(IronWokBlock::new);
    /** The rim only: the bowl below is decoration, and a full cube would block the burner. */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.5, 15.0);

    public IronWokBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState below = context.getLevel().getBlockState(pos.below());
        if (!(below.getBlock() instanceof FirewoodStoveBlock)) {
            return null;
        }
        // the wok faces the same way the range does, so the handles line up with the fire mouth
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                below.getValue(FirewoodStoveBlock.FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof FirewoodStoveBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !(neighborState.getBlock() instanceof FirewoodStoveBlock)) {
            // the range went away underneath: the wok goes too, and onRemove hands the player both
            // the wok and whatever was in it
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof IronWokBlockEntity wok) {
            if (!level.isClientSide) {
                wok.dropContents();
                Block.popResource(level, pos, new ItemStack(this));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IronWokBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.IRON_WOK.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((IronWokBlockEntity) blockEntity).serverTick();
    }
}
