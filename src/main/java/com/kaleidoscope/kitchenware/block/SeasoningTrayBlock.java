package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModItems;
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
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A seasoning tray: one block, four compartments, each holding one kind of seasoning up to 1024.
 *
 * Which compartment you touch follows where you aim (two columns by two rows on the face you are
 * looking at). Breaking it returns the tray carrying its contents; putting it down anywhere brings
 * them back. Contents never depend on the tray's position.
 */
public class SeasoningTrayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SeasoningTrayBlock> CODEC = simpleCodec(SeasoningTrayBlock::new);
    private static final VoxelShape SHAPE = Shapes.box(0.0625, 0, 0.0625, 0.9375, 0.1875, 0.9375);

    public SeasoningTrayBlock(Properties properties) {
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

    /**
     * Compartment 0..3 from the hit position: rows by height, columns by which side of the face was
     * touched. 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right as seen by the player.
     */
    public static int compartmentAt(BlockHitResult hit, BlockPos pos, Direction facing) {
        double localY = hit.getLocation().y - pos.getY();
        int row = localY >= 0.5 ? 0 : 1;
        double localX = hit.getLocation().x - pos.getX() - 0.5;
        double localZ = hit.getLocation().z - pos.getZ() - 0.5;
        double side;
        switch (facing) {
            case SOUTH -> side = -localX;
            case EAST -> side = -localZ;
            case WEST -> side = localZ;
            default -> side = localX;
        }
        int column = side < 0 ? 0 : 1;
        return row * 2 + column;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeasoningTrayBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND
                || !(level.getBlockEntity(pos) instanceof SeasoningTrayBlockEntity tray)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            // server owns the contents and answers with a block update; predicting only misleads
            return ItemInteractionResult.SUCCESS;
        }
        int slot = compartmentAt(hitResult, pos, state.getValue(FACING));

        if (!stack.isEmpty()) {
            int moved = tray.insert(slot, stack, true);
            if (moved == 0) {
                tell(player, tray.storedCount(slot) > 0
                        ? "state.kaleidoscope_kitchenware.tray_wrong_kind"
                        : "state.kaleidoscope_kitchenware.tray_accepts_seasoning_only");
                return ItemInteractionResult.FAIL;
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            tell(player, "state.kaleidoscope_kitchenware.tray_stored", moved);
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            ItemStack taken = tray.extractOne(slot);
            if (taken.isEmpty()) {
                tell(player, "state.kaleidoscope_kitchenware.tray_empty");
                return ItemInteractionResult.FAIL;
            }
            player.getInventory().placeItemBackInInventory(taken);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.9F);
            return ItemInteractionResult.SUCCESS;
        }
        // emptying a compartment hands back several stacks, since one cannot hold 1024
        List<ItemStack> drained = tray.extractAll(slot);
        if (drained.isEmpty()) {
            tell(player, "state.kaleidoscope_kitchenware.tray_empty");
            return ItemInteractionResult.FAIL;
        }
        for (ItemStack drainedStack : drained) {
            player.getInventory().placeItemBackInInventory(drainedStack);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.9F);
        return ItemInteractionResult.SUCCESS;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    /**
     * Drops the tray itself, carrying its contents. Items are written onto the dropped stack, so
     * nothing scatters and nothing is left behind.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SeasoningTrayBlockEntity tray) {
            ItemStack drop = new ItemStack(ModItems.SEASONING_TRAY.get());
            tray.saveToItem(drop, level.registryAccess());
            KaleidoscopeKitchenware.LOGGER.info("[tray] {} break, drop carries {}",
                    pos, drop.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
            Block.popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Fallback for explosions and pistons. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SeasoningTrayBlockEntity tray) {
            ItemStack drop = new ItemStack(ModItems.SEASONING_TRAY.get());
            tray.saveToItem(drop, level.registryAccess());
            Block.popResource(level, pos, drop);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Loot tables would drop an empty tray; the drop is produced above instead. */
    public static List<ItemStack> noLoot() {
        return List.of();
    }
}
