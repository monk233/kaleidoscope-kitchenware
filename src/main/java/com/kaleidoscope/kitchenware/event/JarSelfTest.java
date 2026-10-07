package com.kaleidoscope.kitchenware.event;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Round-trip check for the jar item, run once when a server starts.
 *
 * Guards the two ways a stocked jar has come back empty: the item not actually carrying the
 * contents, and the contents landing in a different corner from the one the block shows.
 */
@EventBusSubscriber(modid = KaleidoscopeKitchenware.MOD_ID)
public final class JarSelfTest {
    private JarSelfTest() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            var registries = event.getServer().registryAccess();
            BlockState state = ModBlocks.SPICE_JAR.get().defaultBlockState();

            // 1. put seasoning in, pack it into an item
            SpiceJarBlockEntity source = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            boolean stored = source.insert(2, new ItemStack(Items.SUGAR, 7), true) > 0;
            ItemStack item = SpiceJarBlock.jarStack(source, 2, registries);
            boolean itemHasData = item.has(DataComponents.BLOCK_ENTITY_DATA);
            boolean itemHasModel = item.has(DataComponents.CUSTOM_MODEL_DATA);

            // 2. unpack by hand, strafing corners
            // 2. the item must carry the block entity tag shape, one jar under its own key
            var raw = item.get(DataComponents.BLOCK_ENTITY_DATA);
            boolean itemCarriesJar = raw != null && raw.copyTag().getCompound("Jar").contains("id");

            SpiceJarBlockEntity unpacked = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            unpacked.absorbSingleJar(SpiceJarBlock.carriedItems(item), 1, registries);
            int unpackedCount = unpacked.carryingCount(1);

            // 3. walk the real placement path: block down, data loaded into the first range the
            //    way vanilla does it, then the settling code the item runs
            ServerLevel level = event.getServer().overworld();
            BlockPos pos = new BlockPos(0, 200, 0);
            level.setBlock(pos, state, 3);
            boolean placedStored = false;
            boolean cornersCorrect = false;
            if (level.getBlockEntity(pos) instanceof SpiceJarBlockEntity placed) {
                placed.absorbSingleJar(SpiceJarBlock.carriedItems(item), 0, registries);
                SpiceJarBlock.settlePlacement(level, pos, 2);
                BlockState after = level.getBlockState(pos);
                placedStored = placed.carryingCount(2) == 7;
                cornersCorrect = after.getValue(SpiceJarBlock.JARS[2])
                        && !after.getValue(SpiceJarBlock.JARS[0]);
            }
            level.removeBlock(pos, false);

            // 3b. the same, but with no help from the placement path: contents sitting in the first
            //     range must line up with the corner the block shows when the entity loads
            BlockPos pos2 = new BlockPos(2, 200, 0);
            level.setBlock(pos2, state.setValue(SpiceJarBlock.JARS[3], true), 3);
            boolean fallbackMoved = false;
            if (level.getBlockEntity(pos2) instanceof SpiceJarBlockEntity loose) {
                loose.absorbSingleJar(SpiceJarBlock.carriedItems(item), 0, registries);
                loose.onLoad();
                fallbackMoved = loose.carryingCount(3) == 7;
            }
            level.removeBlock(pos2, false);

            // 3c. an empty jar must survive a load: clearing its flag used to drop the block's
            //     contents with it, because drops follow the flags
            BlockPos pos3 = new BlockPos(4, 200, 0);
            level.setBlock(pos3, state.setValue(SpiceJarBlock.JARS[1], true), 3);
            boolean emptyStaysStanding = false;
            if (level.getBlockEntity(pos3) instanceof SpiceJarBlockEntity emptyJar) {
                emptyJar.onLoad();
                emptyStaysStanding = level.getBlockState(pos3).getValue(SpiceJarBlock.JARS[1]);
            }
            level.removeBlock(pos3, false);

            // 3d. placing into the first corner must keep the contents there (moving a range onto
            //     itself is a no-op, so this path took a different branch)
            BlockPos pos4 = new BlockPos(6, 200, 0);
            level.setBlock(pos4, state.setValue(SpiceJarBlock.JARS[0], true), 3);
            boolean firstCornerKept = false;
            if (level.getBlockEntity(pos4) instanceof SpiceJarBlockEntity first) {
                first.absorbSingleJar(SpiceJarBlock.carriedItems(item), 0, registries);
                SpiceJarBlock.settlePlacement(level, pos4, 0);
                firstCornerKept = first.carryingCount(0) == 7
                        && level.getBlockState(pos4).getValue(SpiceJarBlock.JARS[0]);
            }
            level.removeBlock(pos4, false);

            // 3e. placing a stocked jar into every corner must keep the contents, corner 0 included
            boolean allCornersKept = true;
            for (int corner = 0; corner < SpiceJarBlock.JAR_COUNT; corner++) {
                BlockPos probe = new BlockPos(20 + corner * 2, 200, 0);
                level.setBlock(probe, state.setValue(SpiceJarBlock.JARS[corner], true), 3);
                if (level.getBlockEntity(probe) instanceof SpiceJarBlockEntity be) {
                    SpiceJarBlock.settlePlacedItem(level, probe, corner, item);
                    boolean kept = be.carryingCount(corner) == 7
                            && level.getBlockState(probe).getValue(SpiceJarBlock.JARS[corner]);
                    allCornersKept &= kept;
                    KaleidoscopeKitchenware.LOGGER.info(
                            "[jartest] corner {} kept={} stored={}", corner, kept, be.carryingCount(corner));
                }
                level.removeBlock(probe, false);
            }

            // 5. moving a jar into an occupied corner must merge, never overwrite. A blind
            //    assignment here silently ate a full jar during corner alignment.
            SpiceJarBlockEntity guard = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            guard.insert(0, new ItemStack(Items.SUGAR, 100), true);
            guard.insert(1, new ItemStack(Items.SUGAR, 50), true);
            guard.moveJarRange(1, 0);
            boolean mergeSafe = guard.carryingCount(0) == 150 && guard.carryingCount(1) == 0;

            boolean pass = stored && itemHasData && itemHasModel && itemCarriesJar
                    && unpackedCount == 7 && placedStored && cornersCorrect && fallbackMoved
                    && emptyStaysStanding && firstCornerKept && allCornersKept && mergeSafe;
            KaleidoscopeKitchenware.LOGGER.info(
                    "[jartest] {} stored={} itemHasData={} itemHasModel={} itemCarriesJar={} "
                            + "unpackedIntoCorner1={} placedIntoCorner2={} cornersCorrect={} "
                            + "relocates={} emptyStaysStanding={} firstCornerKept={} "
                            + "allCornersKept={} mergeSafe={}",
                    pass ? "PASS" : "FAIL", stored, itemHasData, itemHasModel, itemCarriesJar,
                    unpackedCount, placedStored, cornersCorrect, fallbackMoved, emptyStaysStanding,
                    firstCornerKept, allCornersKept, mergeSafe);

            // 4. a jar takes seasoning only, and only one kind of it, up to 1024
            SpiceJarBlockEntity mixed = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            boolean nonSeasoningRefused = mixed.insert(0, new ItemStack(Items.REDSTONE, 7), true) == 0;
            boolean firstAccepted = mixed.insert(0, new ItemStack(Items.SUGAR, 7), true) > 0;
            boolean secondRefused = mixed.insert(0, new ItemStack(Items.REDSTONE, 7), true) == 0;
            int rounds = 0;
            while (mixed.insert(0, new ItemStack(Items.SUGAR, 64), true) > 0 && rounds < 40) {
                rounds++;
            }
            KaleidoscopeKitchenware.LOGGER.info(
                    "[jartest-kind] {} nonSeasoningRefused={} firstAccepted={} secondRefused={} totalHeld={}",
                    nonSeasoningRefused && firstAccepted && secondRefused && mixed.carryingCount(0) == 1024
                            ? "PASS" : "FAIL",
                    nonSeasoningRefused, firstAccepted, secondRefused, mixed.carryingCount(0));
        } catch (Throwable throwable) {
            KaleidoscopeKitchenware.LOGGER.error("[jartest] FAIL with exception", throwable);
        }
    }
}
