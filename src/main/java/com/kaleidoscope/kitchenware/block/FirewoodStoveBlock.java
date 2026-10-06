package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.FirewoodStoveBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.util.RangeHoodSupport;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.tags.ItemTags;
import org.jetbrains.annotations.Nullable;

/**
 * The firewood stove. It carries the vanilla LIT property, which is all the base mod
 * checks when deciding whether a pot above it has a heat source.
 */
public class FirewoodStoveBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FirewoodStoveBlock> CODEC = simpleCodec(FirewoodStoveBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FirewoodStoveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FirewoodStoveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.FIREWOOD_STOVE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((FirewoodStoveBlockEntity) blockEntity).serverTick();
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand == InteractionHand.OFF_HAND || !(level.getBlockEntity(pos) instanceof FirewoodStoveBlockEntity stove)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // light it
        if ((stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) && stove.canIgnite()) {
            stove.ignite();
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (stack.is(Items.FIRE_CHARGE)) {
                stack.shrink(1);
            } else {
                stack.hurtAndBreak(1, player, player.getEquipmentSlotForItem(stack));
            }
            return ItemInteractionResult.SUCCESS;
        }

        // put it out
        if (state.getValue(LIT) && stack.is(ItemTags.SHOVELS)) {
            stove.extinguish();
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }

        // feed it
        if (stove.addFuel(stack)) {
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            tell(player, "tier", Component.translatable("tier.kaleidoscope_kitchenware." + stove.tier().key));
            return ItemInteractionResult.SUCCESS;
        }

        // report status
        if (stack.isEmpty()) {
            if (stove.hasFuel()) {
                tell(player, "status", Component.translatable("state.kaleidoscope_kitchenware.fuel_left",
                        stove.burnTime() / 20,
                        Component.translatable("tier.kaleidoscope_kitchenware." + stove.tier().key)));
            } else {
                tell(player, "status", Component.translatable("state.kaleidoscope_kitchenware.need_fuel"));
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static void tell(Player player, String prefix, Component message) {
        player.displayClientMessage(Component.translatable("state.kaleidoscope_kitchenware." + prefix, message), true);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (RangeHoodSupport.hasWorkingHood(level, pos)) {
            return; // smoke is being drawn up the hood instead
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(10) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        level.addParticle(ParticleTypes.SMOKE,
                x + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                y + 0.5 + random.nextDouble() / 3,
                z + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                0, 0.02, 0);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && level.isRainingAt(pos.above())) {
            level.setBlockAndUpdate(pos, state.setValue(LIT, false));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }
}
