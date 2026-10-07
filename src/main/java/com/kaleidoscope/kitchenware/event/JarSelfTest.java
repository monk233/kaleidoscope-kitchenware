package com.kaleidoscope.kitchenware.event;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Round-trip check for the jar item, run once when a server starts.
 *
 * The failure this guards against is a stocked jar coming back empty: whether the item really
 * carries the contents, and whether they land in the corner they are written to.
 */
@EventBusSubscriber(modid = KaleidoscopeKitchenware.MOD_ID)
public final class JarSelfTest {
    private JarSelfTest() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            var registries = event.getServer().registryAccess();
            var state = ModBlocks.SPICE_JAR.get().defaultBlockState();

            SpiceJarBlockEntity source = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            boolean stored = source.insert(2, new ItemStack(Items.REDSTONE, 7), true) > 0;

            ItemStack item = SpiceJarBlock.jarStack(source, 2, registries);
            boolean itemHasData = item.has(DataComponents.BLOCK_ENTITY_DATA);
            boolean itemHasModel = item.has(DataComponents.CUSTOM_MODEL_DATA);

            SpiceJarBlockEntity restored = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            CompoundTag carried = SpiceJarBlock.carriedItems(item);
            restored.absorbSingleJar(carried, 1, registries);
            int inCornerOne = restored.carryingCount(1);

            var raw = item.get(DataComponents.BLOCK_ENTITY_DATA);
            KaleidoscopeKitchenware.LOGGER.info("[jartest] raw item data={} carried={}",
                    raw == null ? "null" : raw.copyTag(), carried);

            SpiceJarBlockEntity direct = new SpiceJarBlockEntity(BlockPos.ZERO, state);
            direct.absorbSingleJar(source.saveSingleJar(2, registries), 0, registries);

            boolean pass = stored && itemHasData && itemHasModel
                    && inCornerOne == 7 && direct.carryingCount(0) == 7;
            KaleidoscopeKitchenware.LOGGER.info(
                    "[jartest] {} stored={} itemHasData={} itemHasModel={} restoredIntoCorner1={} directCorner0={}",
                    pass ? "PASS" : "FAIL", stored, itemHasData, itemHasModel, inCornerOne, direct.carryingCount(0));
        } catch (Throwable throwable) {
            KaleidoscopeKitchenware.LOGGER.error("[jartest] FAIL with exception", throwable);
        }
    }
}
