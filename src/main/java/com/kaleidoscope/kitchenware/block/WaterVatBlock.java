package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.config.KitchenwareConfig;
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
 * Water vat: fill a bucket or a bottle from it, no GUI anywhere.
 *
 * By default the vat is an endless source and does not drain; set
 * waterVat.consumesLevel to true when refilling the vat should matter.
 */
public class WaterVatBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WaterVatBlock> CODEC = simpleCodec(WaterVatBlock::new);
    /** 0 = empty, 3 = full. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    /** A ring, like a cauldron: hollow, so a player can stand inside it. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 16, 3),
            Block.box(1, 0, 13, 15, 16, 15),
            Block.box(1, 0, 3, 3, 16, 13),
            Block.box(13, 0, 3, 15, 16, 13));

    public WaterVatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LEVEL, 3));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand == InteractionHand.OFF_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(LEVEL) <= 0) {
            tell(player, "state.kaleidoscope_kitchenware.vat_empty");
            return ItemInteractionResult.FAIL;
        }
        if (stack.is(Items.BUCKET)) {
            return fill(stack, new ItemStack(Items.WATER_BUCKET), state, level, pos, player);
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            return fill(stack, new ItemStack(Items.POTION), state, level, pos, player);
        }
        tell(player, "state.kaleidoscope_kitchenware.vat_hint");
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private ItemInteractionResult fill(ItemStack held, ItemStack filled, BlockState state, Level level,
                                       BlockPos pos, Player player) {
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        held.shrink(1);
        player.getInventory().placeItemBackInInventory(filled);
        if (KitchenwareConfig.VAT_CONSUMES_LEVEL.get()) {
            level.setBlockAndUpdate(pos, state.setValue(LEVEL, state.getValue(LEVEL) - 1));
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
