package com.kaleidoscope.kitchenware.event;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.SeasoningTrayBlock;
import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModBlocks;
import com.kaleidoscope.kitchenware.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Round-trip check for the seasoning tray, run once when a server starts.
 *
 * The one failure that matters: a tray that comes back from an item with its seasoning gone, or
 * takes something that is not seasoning.
 */
@EventBusSubscriber(modid = KaleidoscopeKitchenware.MOD_ID)
public final class TraySelfTest {
    private TraySelfTest() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            var registries = event.getServer().registryAccess();
            var state = ModBlocks.SEASONING_TRAY.get().defaultBlockState();

            SeasoningTrayBlockEntity tray = new SeasoningTrayBlockEntity(BlockPos.ZERO, state);
            boolean stored = tray.insert(0, new ItemStack(Items.SUGAR, 40), true) == 40;
            boolean topsUp = tray.insert(0, new ItemStack(Items.SUGAR, 60), true) == 60;
            boolean nonSeasoningRefused = tray.insert(1, new ItemStack(Items.REDSTONE, 1), true) == 0;

            int rounds = 0;
            while (tray.insert(0, new ItemStack(Items.SUGAR, 64), true) == 64 && rounds < 30) {
                rounds++;
            }
            int capped = tray.storedCount(0);

            // the tray travels as an item and must come back with its contents
            ItemStack travelling = new ItemStack(ModItems.SEASONING_TRAY.get());
            tray.saveToItem(travelling, registries);
            boolean itemCarriesData = travelling.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            SeasoningTrayBlockEntity unpacked = new SeasoningTrayBlockEntity(BlockPos.ZERO, state);
            unpacked.loadFromItem(travelling, registries);
            boolean roundTrip = unpacked.storedCount(0) == capped;

            // aiming at a compartment must select that compartment, from every facing and both
            // by its side wall and from above
            boolean everyCompartmentAimable = true;
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH,
                    Direction.EAST, Direction.WEST}) {
                Direction view = facing.getOpposite();
                Direction right = view.getClockWise();
                for (int slot = 0; slot < SeasoningTrayBlockEntity.COMPARTMENTS; slot++) {
                    double left = (slot % 2 == 0) ? -0.25D : 0.25D;
                    double far = (slot < 2) ? 0.25D : -0.25D;
                    double x = 0.5D + right.getStepX() * left + view.getStepX() * far;
                    double z = 0.5D + right.getStepZ() * left + view.getStepZ() * far;
                    double sideY = (slot < 2) ? 0.7D : 0.3D;
                    everyCompartmentAimable &= aimSelects(facing, slot,
                            new Vec3(x, sideY, z), view);
                    everyCompartmentAimable &= aimSelects(facing, slot,
                            new Vec3(x, 0.9D, z), Direction.UP);
                }
            }

            boolean pass = stored && topsUp && nonSeasoningRefused && capped == 1024
                    && itemCarriesData && roundTrip && everyCompartmentAimable;
            KaleidoscopeKitchenware.LOGGER.info(
                    "[traytest] {} stored={} topsUp={} nonSeasoningRefused={} capped={} "
                            + "itemCarriesData={} roundTrip={} everyCompartmentAimable={}",
                    pass ? "PASS" : "FAIL", stored, topsUp, nonSeasoningRefused, capped,
                    itemCarriesData, roundTrip, everyCompartmentAimable);
        } catch (Throwable throwable) {
            KaleidoscopeKitchenware.LOGGER.error("[traytest] FAIL with exception", throwable);
        }
    }

    private static boolean aimSelects(Direction facing, int slot, Vec3 at, Direction face) {
        BlockHitResult hit = new BlockHitResult(at, face, BlockPos.ZERO, false);
        return SeasoningTrayBlock.compartmentAt(hit, BlockPos.ZERO, facing) == slot;
    }
}
