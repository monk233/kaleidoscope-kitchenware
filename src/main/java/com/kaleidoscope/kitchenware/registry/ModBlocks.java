package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.FirewoodStoveBlock;
import com.kaleidoscope.kitchenware.block.IronWokBlock;
import com.kaleidoscope.kitchenware.block.DishRackBlock;
import com.kaleidoscope.kitchenware.block.SeasoningTrayBlock;
import com.kaleidoscope.kitchenware.block.WaterVatBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(KaleidoscopeKitchenware.MOD_ID);

    // --- 功能方块 / functional blocks ---
    /**
     * Carries the vanilla LIT property, which is exactly what the base mod checks for a heat source.
     * Two cells, one item: {@code noOcclusion} because the burners are open at the top.
     */
    public static final DeferredHolder<Block, FirewoodStoveBlock> FIREWOOD_STOVE = reg("firewood_stove",
            () -> new FirewoodStoveBlock(stone().randomTicks().noOcclusion()));
    /** The wok that sits on a burner; it hangs into the cavity, so it needs its own shape. */
    public static final DeferredHolder<Block, IronWokBlock> IRON_WOK = reg("iron_wok",
            () -> new IronWokBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .strength(1.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));
    public static final DeferredHolder<Block, WaterVatBlock> WATER_VAT = reg("water_vat",
            () -> new WaterVatBlock(stone().noOcclusion().strength(1.5F, 4.0F)));
    /** Two shelf dish rack holding bowls and flower pots, no interface. */
    public static final DeferredHolder<Block, DishRackBlock> DISH_RACK = regNoItem("dish_rack",
            () -> new DishRackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(1.5F, 3.0F)
                    .noOcclusion()));

    /** Four-compartment seasoning tray; contents travel with the item, like a drawer. */
    public static final DeferredHolder<Block, SeasoningTrayBlock> SEASONING_TRAY = regNoItem("seasoning_tray",
            () -> new SeasoningTrayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(1.5F, 3.0F)
                    .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private static <T extends Block> DeferredHolder<Block, T> reg(String name, Supplier<T> supplier) {
        DeferredHolder<Block, T> holder = BLOCKS.register(name, supplier);
        ModItems.blockItem(holder);
        return holder;
    }

    /** For blocks whose item form is a custom class instead of a plain BlockItem. */
    private static <T extends Block> DeferredHolder<Block, T> regNoItem(String name, Supplier<T> supplier) {
        return BLOCKS.register(name, supplier);
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties earth() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .sound(SoundType.GRAVEL)
                .strength(1.2F, 3.0F);
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F, 3.0F);
    }

    private static BlockBehaviour.Properties thinWood() {
        return wood().noOcclusion().strength(0.8F);
    }
}
