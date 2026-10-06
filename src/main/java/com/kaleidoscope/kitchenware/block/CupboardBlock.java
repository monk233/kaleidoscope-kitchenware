package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.CupboardBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Cupboard: 16 slots of bowls and flower pots, opened and closed without any GUI. */
public class CupboardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CupboardBlock> CODEC = simpleCodec(CupboardBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public CupboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CupboardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CUPBOARD.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((CupboardBlockEntity) blockEntity).serverTick();
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CupboardBlockEntity cupboard)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemInteractionResult result = NoGuiStorage.interact(stack, level, pos, player, hand, cupboard);
        if (result == ItemInteractionResult.SUCCESS) {
            cupboard.swingDoors();
        }
        return result;
    }

    public static BlockState withOpen(BlockState state, boolean open) {
        return state.setValue(OPEN, open);
    }
}
