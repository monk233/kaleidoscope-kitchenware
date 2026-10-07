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
            boolean stored = source.insert(2, new ItemStack(Items.REDSTONE, 7), true) > 0;
            ItemStack item = SpiceJarBlock.jarStack(source, 2, registries);
            boolean itemHasData = item.has(DataComponents.BLOCK_ENTITY_DATA);
            boolean itemHasModel = item.has(DataComponents.CUSTOM_MODEL_DATA);

            // 2. unpack by hand, strafing corners
            // 2. the item must carry the block entity tag shape, not a bare handler tag
            var raw = item.get(DataComponents.BLOCK_ENTITY_DATA);
            int handlerSlots = raw == null ? 0
                    : raw.copyTag().getCompound("Items").getList("Items", 10).size();

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

            boolean pass = stored && itemHasData && itemHasModel && handlerSlots == 1
                    && unpackedCount == 7 && placedStored && cornersCorrect && fallbackMoved;
            KaleidoscopeKitchenware.LOGGER.info(
                    "[jartest] {} stored={} itemHasData={} itemHasModel={} handlerSlots={} "
                            + "unpackedIntoCorner1={} placedIntoCorner2={} cornersCorrect={} relocates={}",
                    pass ? "PASS" : "FAIL", stored, itemHasData, itemHasModel, handlerSlots,
                    unpackedCount, placedStored, cornersCorrect, fallbackMoved);

            // 4. one jar holds a single kind of seasoning
            SpiceJarBlockEntity mixed = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            boolean firstAccepted = mixed.insert(0, new ItemStack(Items.REDSTONE, 7), true) > 0;
            boolean secondRefused = mixed.insert(0, new ItemStack(Items.SUGAR, 7), true) == 0;
            int cap = 0;
            while (mixed.insert(0, new ItemStack(Items.REDSTONE, 64), true) > 0 && cap < 40) {
                cap++;
            }
            KaleidoscopeKitchenware.LOGGER.info(
                    "[jartest-kind] {} firstAccepted={} secondRefused={} totalHeld={}",
                    firstAccepted && secondRefused && mixed.carryingCount(0) == 1024 ? "PASS" : "FAIL",
                    firstAccepted, secondRefused, mixed.carryingCount(0));
        } catch (Throwable throwable) {
            KaleidoscopeKitchenware.LOGGER.error("[jartest] FAIL with exception", throwable);
        }
    }
}
