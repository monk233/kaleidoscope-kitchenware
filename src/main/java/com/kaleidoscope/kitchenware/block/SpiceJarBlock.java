package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.item.SpiceJarItem;
import com.kaleidoscope.kitchenware.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Four aligned spice jars, one per corner of the block. Which jar you touch is decided by
 * where on the block you are aiming.
 *
 * <ul>
 *   <li>right-click holding something: the whole held stack goes in</li>
 *   <li>right-click empty-handed: a whole stack comes out</li>
 *   <li>left-click: one item comes out (handled in {@code SpiceJarEvents})</li>
 * </ul>
 */
public class SpiceJarBlock extends Block implements EntityBlock {
    public static final MapCodec<SpiceJarBlock> CODEC = simpleCodec(SpiceJarBlock::new);
    /** Jar presence per corner, in the order north-west, north-east, south-west, south-east. */
    public static final BooleanProperty[] JARS = {
            BooleanProperty.create("jar_nw"),
            BooleanProperty.create("jar_ne"),
            BooleanProperty.create("jar_sw"),
            BooleanProperty.create("jar_se"),
    };
    public static final int JAR_COUNT = SpiceJarBlockEntity.JAR_COUNT;
    /** custom_model_data value that switches the item to the "has seasoning" model. */
    public static final int FILLED_MODEL_DATA = 1;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 6, 7, 6), Block.box(10, 0, 1, 15, 7, 6),
            Block.box(1, 0, 10, 6, 7, 15), Block.box(10, 0, 10, 15, 7, 15));
    private static final double[][] CORNER_CENTRES = {
            {0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75},
    };

    public SpiceJarBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty jar : JARS) {
            state = state.setValue(jar, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JARS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.setValue(JARS[jarIndexAt(context.getClickLocation(), context.getClickedPos())], true);
    }

    /** Corner index 0..3 from the hit position, matching {@link #JARS}. */
    public static int jarIndexAt(Vec3 hit, BlockPos pos) {
        double localX = hit.x - pos.getX();
        double localZ = hit.z - pos.getZ();
        boolean east = localX >= 0.5;
        boolean south = localZ >= 0.5;
        if (!south) {
            return east ? 1 : 0;
        }
        return east ? 3 : 2;
    }

    /**
     * The jar the player means: the corner they aim at, or failing that the nearest corner
     * that actually has a jar. Aiming a couple of pixels off should not make the click do
     * nothing.
     *
     * @return jar index, or -1 when this block carries no jars at all
     */
    public static int resolveJarIndex(BlockState state, Vec3 hit, BlockPos pos) {
        int aimed = jarIndexAt(hit, pos);
        if (state.getValue(JARS[aimed])) {
            return aimed;
        }
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < JAR_COUNT; index++) {
            if (!state.getValue(JARS[index])) {
                continue;
            }
            double dx = CORNER_CENTRES[index][0] - CORNER_CENTRES[aimed][0];
            double dz = CORNER_CENTRES[index][1] - CORNER_CENTRES[aimed][1];
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiceJarBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand == InteractionHand.OFF_HAND || !(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int index = resolveJarIndex(state, hitResult.getLocation(), pos);
        boolean hasJar = index >= 0;

        // a jar in hand goes down as a new jar in the corner that was aimed at.
        // the fallback above must not apply here, or a second jar could never be placed
        if (stack.getItem() instanceof SpiceJarItem) {
            int target = jarIndexAt(hitResult.getLocation(), pos);
            if (!state.getValue(JARS[target])) {
                level.setBlockAndUpdate(pos, state.setValue(JARS[target], true));
                level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
            tell(player, "state.kaleidoscope_kitchenware.jar_taken");
            return ItemInteractionResult.FAIL;
        }

        // everything held goes in at once
        if (!stack.isEmpty()) {
            if (!hasJar) {
                tell(player, "state.kaleidoscope_kitchenware.jar_missing");
                return ItemInteractionResult.FAIL;
            }
            int moved = jar.insert(index, stack, true);
            if (moved == 0) {
                tell(player, "state.kaleidoscope_kitchenware.storage_full");
                return ItemInteractionResult.FAIL;
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            tell(player, "state.kaleidoscope_kitchenware.jar_stored", moved);
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // an empty hand takes a whole stack out
        if (!hasJar) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack taken = jar.extract(index, true);
        if (taken.isEmpty()) {
            tell(player, "state.kaleidoscope_kitchenware.storage_empty");
            return ItemInteractionResult.FAIL;
        }
        player.getInventory().placeItemBackInInventory(taken);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.9F);
        return ItemInteractionResult.SUCCESS;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    /** How many jars stand in this block. */
    public static int jarCount(BlockState state) {
        int count = 0;
        for (BooleanProperty jar : JARS) {
            if (state.getValue(jar)) {
                count++;
            }
        }
        return count;
    }

    /**
     * One corner's jar as an item, carrying only that jar's contents, so contents and block
     * state agree again when it is placed back down.
     */
    public static ItemStack jarStack(SpiceJarBlockEntity jar, int corner, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(ModItems.SPICE_JAR.get());
        if (jar.isJarEmpty(corner)) {
            return stack;
        }
        CompoundTag contents = jar.saveSingleJar(corner, registries);
        contents.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(jar.getType()).toString());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(contents));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(FILLED_MODEL_DATA));
        return stack;
    }

    /**
     * Drops one jar per standing jar, each with its own contents.
     *
     * Dropping happens in playerWillDestroy rather than onRemove because a block entity can
     * already be gone by the time onRemove runs, which silently lost everything.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar) {
            dropJars(level, pos, state, jar);
            jar.markDropped();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Fallback for anything that is not a player breaking the block: explosions, pistons. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar && !jar.isDropped()) {
            dropJars(level, pos, state, jar);
            jar.markDropped();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void dropJars(Level level, BlockPos pos, BlockState state, SpiceJarBlockEntity jar) {
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (state.getValue(JARS[corner])) {
                Block.popResource(level, pos, jarStack(jar, corner, level.registryAccess()));
            }
        }
    }
}
