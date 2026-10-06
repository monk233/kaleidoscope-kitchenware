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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Spice rack: 8 slots of anything, and the shelf fills up visually as you stock it. */
public class SpiceRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SpiceRackBlock> CODEC = simpleCodec(SpiceRackBlock::new);
    /** 0 = empty, 4 = full; each step is two stored items. */
    public static final IntegerProperty FILLED = IntegerProperty.create("filled", 0, 4);

    public SpiceRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FILLED, 0));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
