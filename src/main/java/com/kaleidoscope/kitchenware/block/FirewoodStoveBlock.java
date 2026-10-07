package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.FirewoodStoveBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The firewood stove: a two-cell brick range, one burner and one fire mouth per cell.
 *
 * The pair lies across the front face. The cell the player clicked is {@code LEFT} and its partner
 * sits clockwise from it; when that side is taken the roles swap, so a stove placed against a wall
 * still finds somewhere to put the second cell. Either half carries the same block, so the loot
 * table only ever rolls once: the other half is removed with {@code setBlock}, which drops nothing.
 *
 * {@code LIT} is the whole heat contract with the base mod, as it was in the single-cell stove.
 */
public class FirewoodStoveBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FirewoodStoveBlock> CODEC = simpleCodec(FirewoodStoveBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<StovePart> PART = EnumProperty.create("part", StovePart.class);

    /** Which half of the pair a cell is. The partner always sits on the named side of the front. */
    public enum StovePart implements StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        private final String name;

        StovePart(String name) {
            this.name = name;
        }

        public StovePart other() {
            return this == LEFT ? RIGHT : LEFT;
        }

        /** Side of the front face the partner cell sits on. */
        public Direction side(Direction facing) {
            return this == LEFT ? facing.getClockWise() : facing.getCounterClockWise();
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public FirewoodStoveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(PART, StovePart.LEFT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, PART);
    }

    public static BlockPos partnerPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(PART).side(state.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // the clicked cell is the left half when the right hand side is free, and the other way
        // round when it is not; a blocked pair can still be built from the other side
        if (isFree(level, pos.relative(facing.getClockWise()))) {
            return defaultBlockState().setValue(FACING, facing).setValue(PART, StovePart.LEFT);
        }
        if (isFree(level, pos.relative(facing.getCounterClockWise()))) {
            return defaultBlockState().setValue(FACING, facing).setValue(PART, StovePart.RIGHT);
        }
        return null;
    }

    private static boolean isFree(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos partner = partnerPos(pos, state);
        if (isFree(level, partner)) {
            level.setBlock(partner, state.setValue(PART, state.getValue(PART).other()), Block.UPDATE_ALL);
        }
    }

    /**
     * Breaking either half takes the other with it. The partner goes through {@code setBlock}, which
     * rolls no loot, so the one item the player sees comes from the half they actually broke.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            removePartner(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Same deal for blasts: the half being destroyed keeps its drop, and the partner is gone before
     * the explosion gets around to it. Without this a blast that catches both cells drops two.
     */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        removePartner(level, pos, state);
        super.onBlockExploded(state, level, pos, explosion);
    }

    private void removePartner(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof FirewoodStoveBlock)) {
            return;
        }
        BlockPos partner = partnerPos(pos, state);
        if (level.getBlockState(partner).is(this)) {
            level.setBlock(partner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Cleans up an orphaned half - one that lost its partner to a command, a blast it was out of
     * range of, or a piston. Deferred by a tick on purpose: during a player's own break the partner
     * is removed first, and removing ourselves inline would swallow the drop.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(partnerPos(pos, state)).is(this)) {
            level.removeBlock(pos, false);
        }
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
