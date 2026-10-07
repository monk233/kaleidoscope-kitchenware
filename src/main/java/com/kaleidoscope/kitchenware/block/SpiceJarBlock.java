package com.kaleidoscope.kitchenware.block;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.item.SpiceJarItem;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
 * Four aligned spice jars sharing one block, one per corner. Each jar has its own storage and
 * holds a single kind of seasoning.
 *
 * The block behaves like a shulker box: breaking it returns one item carrying every jar, and
 * placing that item anywhere brings the jars back with their contents untouched. The corner
 * flags are rebuilt from the stored data when it is placed again, so contents and visible jars
 * cannot drift apart.
 *
 * <ul>
 *   <li>right-click holding something: the whole held stack goes in</li>
 *   <li>right-click empty-handed: a whole stack comes out</li>
 *   <li>sneak + right-click empty-handed: a single item comes out</li>
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

    /**
     * Settles a block that has just been placed from a jar item: the item's contents were loaded
     * into the first slot range, so move them to the corner the player aimed at and then rebuild
     * the corner flags from the actual contents.
     *
     * Kept static and side-effect-only so the startup self test can drive it without a player.
     */
    public static void settlePlacement(Level level, BlockPos pos, int placedCorner) {
        if (!(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return;
        }
        KaleidoscopeKitchenware.LOGGER.info("[jardbg] settle corner={} before flags={} counts={},{},{},{}",
                placedCorner, dumpFlags(level.getBlockState(pos)), jar.carryingCount(0),
                jar.carryingCount(1), jar.carryingCount(2), jar.carryingCount(3));
        jar.moveJarRange(0, placedCorner);
        KaleidoscopeKitchenware.LOGGER.info("[jardbg] settle corner={} after  counts={},{},{},{}",
                placedCorner, jar.carryingCount(0), jar.carryingCount(1),
                jar.carryingCount(2), jar.carryingCount(3));
        BlockState state = level.getBlockState(pos);
        BlockState restored = state;
        boolean anyFilled = false;
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            boolean filled = !jar.isJarEmpty(corner);
            anyFilled |= filled;
            restored = restored.setValue(JARS[corner], filled);
        }
        if (!anyFilled) {
            // an empty jar still needs to stand somewhere
            restored = restored.setValue(JARS[placedCorner], true);
        }
        if (restored != state) {
            level.setBlockAndUpdate(pos, restored);
        }
    }

    /**
     * Writes a jar item's own contents into the corner it was placed at.
     *
     * Deliberately does not rely on vanilla's block entity load: that path resizes the slot
     * handler to the size stored in the item (one jar's worth) and left the two out of step.
     * Here the entity is cleared and filled from the item, so the result is the same whichever
     * corner was aimed at.
     */
    public static void settlePlacedItem(Level level, BlockPos pos, int placedCorner, ItemStack item) {
        if (!(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return;
        }
        jar.clearAll();
        CompoundTag carried = carriedItems(item);
        if (!carried.isEmpty()) {
            jar.absorbSingleJar(carried, placedCorner, level.registryAccess());
        }
        BlockState state = level.getBlockState(pos);
        BlockState restored = state.setValue(JARS[placedCorner], true);
        KaleidoscopeKitchenware.LOGGER.info("[jardbg] settlePlaced corner={} carriedItems={} stored={}",
                placedCorner, carried.getList("Items", 10).size(), jar.carryingTotal());
        if (restored != state) {
            level.setBlockAndUpdate(pos, restored);
        }
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
        boolean takeOne = player.isShiftKeyDown();

        // a jar in hand goes down as a new jar in the corner that was aimed at.
        // the fallback above must not apply here, or a second jar could never be placed
        if (stack.getItem() instanceof SpiceJarItem) {
            int target = jarIndexAt(hitResult.getLocation(), pos);
            if (!state.getValue(JARS[target])) {
                // a stocked jar carries its seasoning in the item and this path never sees
                // vanilla's data application, so copy it in here
                CompoundTag carried = carriedItems(stack);
                if (!carried.isEmpty()) {
                    jar.absorbSingleJar(carried, target, level.registryAccess());
                }
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
            boolean emptyBefore = jar.isJarEmpty(index);
            int moved = jar.insert(index, stack, true);
            if (moved == 0) {
                tell(player, emptyBefore ? "state.kaleidoscope_kitchenware.storage_full"
                        : "state.kaleidoscope_kitchenware.jar_wrong_kind");
                return ItemInteractionResult.FAIL;
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            tell(player, "state.kaleidoscope_kitchenware.jar_stored", moved);
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // an empty hand takes a whole stack, or a single item while sneaking
        if (!hasJar) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack taken = jar.extract(index, !takeOne);
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
     * One corner's jar as an item: a block with four jars drops four of these, each carrying
     * only its own contents (an empty jar drops as a plain item).
     */
    public static ItemStack jarStack(SpiceJarBlockEntity jar, int corner, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(ModItems.SPICE_JAR.get());
        if (jar.isJarEmpty(corner)) {
            return stack;
        }
        // the item carries the block entity's own tag shape: one jar under its "Jar" key
        BlockItem.setBlockEntityData(stack, ModBlockEntities.SPICE_JAR.get(),
                jar.saveSingleJar(corner, registries));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(FILLED_MODEL_DATA));
        return stack;
    }

    /** The tag a jar item carries, empty when it is a plain empty jar. */
    public static CompoundTag carriedItems(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
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

    /** Flags as 1/0, for logging which jars stand where. */
    public static String dumpFlags(BlockState state) {
        StringBuilder text = new StringBuilder();
        for (BooleanProperty jar : JARS) {
            text.append(state.getValue(jar) ? '1' : '0');
        }
        return text.toString();
    }

    /**
     * Drops one item per jar that either stands in the block or holds something.
     *
     * Contents are dropped even when the corner flag says otherwise: the flags are presentation,
     * the contents are the truth, and losing seasoning because the two drifted apart is far worse
     * than dropping an extra empty jar.
     */
    private static void dropJars(Level level, BlockPos pos, BlockState state, SpiceJarBlockEntity jar) {
        KaleidoscopeKitchenware.LOGGER.info("[jardbg] break flags={} counts={},{},{},{}",
                dumpFlags(state), jar.carryingCount(0), jar.carryingCount(1),
                jar.carryingCount(2), jar.carryingCount(3));
        for (int corner = 0; corner < JAR_COUNT; corner++) {
            if (state.getValue(JARS[corner]) || !jar.isJarEmpty(corner)) {
                Block.popResource(level, pos, jarStack(jar, corner, level.registryAccess()));
            }
        }
    }
}
