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
import net.minecraft.world.phys.Vec3;
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
     * Compartment 0..3 from the hit position, in the order the player sees the face: 0 top-left,
     * 1 top-right, 2 bottom-left, 3 bottom-right.
     *
     * Left and right follow the player's view rather than world axes, and aiming at the top of the
     * dish uses depth instead of height, so pointing at a compartment always selects it.
     */
    public static int compartmentAt(BlockHitResult hit, BlockPos pos, Direction facing) {
        Direction view = facing.getOpposite();
        Direction right = view.getClockWise();
        Vec3 at = hit.getLocation();
        double dx = at.x - (pos.getX() + 0.5);
        double dz = at.z - (pos.getZ() + 0.5);
        double alongRight = dx * right.getStepX() + dz * right.getStepZ();
        double height = at.y - pos.getY();

        double vertical;
        if (hit.getDirection().getAxis().isVertical()) {
            // looking down into the dish: the far edge is the top row
            vertical = dx * view.getStepX() + dz * view.getStepZ();
        } else {
            vertical = height - 0.5;
        }
        int column = alongRight > 0 ? 1 : 0;
        int row = vertical > 0 ? 0 : 1;
        return row * 2 + column;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeasoningTrayBlockEntity(pos, state);
    }

    /** Server side only: the entity pushes its contents once it has settled into the world. */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos2, st, be) ->
                SeasoningTrayBlockEntity.serverTick(lvl, pos2, st, (SeasoningTrayBlockEntity) be);
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
        // plain take hands over one stack; a compartment holds far more than an ItemStack can
        ItemStack taken = tray.extractUpTo(slot, 64);
        if (taken.isEmpty()) {
            tell(player, "state.kaleidoscope_kitchenware.tray_empty");
            return ItemInteractionResult.FAIL;
        }
        player.getInventory().placeItemBackInInventory(taken);
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
            dropTray(level, pos, tray);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Fallback for explosions and pistons. Guarded so a broken block never drops twice. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SeasoningTrayBlockEntity tray
                && !tray.isDropped()) {
            dropTray(level, pos, tray);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void dropTray(Level level, BlockPos pos, SeasoningTrayBlockEntity tray) {
        if (tray.isDropped()) {
            return;
        }
        ItemStack drop = new ItemStack(ModItems.SEASONING_TRAY.get());
        tray.saveToItem(drop, level.registryAccess());
        tray.markDropped();
        Block.popResource(level, pos, drop);
    }
}
