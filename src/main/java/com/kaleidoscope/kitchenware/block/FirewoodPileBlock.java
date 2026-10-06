package com.kaleidoscope.kitchenware.block;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stack of firewood, 1 to 4 logs high. Right-click with burnable wood to add one,
 * empty hand to take one back (always as a stick, the pile does not track item types).
 */
public class FirewoodPileBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FirewoodPileBlock> CODEC = simpleCodec(FirewoodPileBlock::new);
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 4);
    private static final VoxelShape[] SHAPES = {
            Shapes.box(0.05, 0, 0.05, 0.95, 0.25, 0.95),
            Shapes.box(0.05, 0, 0.05, 0.95, 0.5, 0.95),
            Shapes.box(0.05, 0, 0.05, 0.95, 0.75, 0.95),
            Shapes.box(0.05, 0, 0.05, 0.95, 1, 0.95),
    };

    public FirewoodPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(COUNT, 1));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(COUNT) - 1];
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand == InteractionHand.OFF_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int count = state.getValue(COUNT);

        if (stack.getBurnTime(null) > 0 && count < 4) {
            level.setBlockAndUpdate(pos, state.setValue(COUNT, count + 1));
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.isEmpty()) {
            if (count > 1) {
                level.setBlockAndUpdate(pos, state.setValue(COUNT, count - 1));
            } else {
                level.removeBlock(pos, false);
            }
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.STICK));
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
            player.displayClientMessage(Component.translatable("state.kaleidoscope_kitchenware.pile_take"), true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
