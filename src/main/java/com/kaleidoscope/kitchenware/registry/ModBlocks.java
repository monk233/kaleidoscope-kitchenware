package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.CupboardBlock;
import com.kaleidoscope.kitchenware.block.FacingThinBlock;
import com.kaleidoscope.kitchenware.block.FirewoodPileBlock;
import com.kaleidoscope.kitchenware.block.FirewoodStoveBlock;
import com.kaleidoscope.kitchenware.block.DishRackBlock;
import com.kaleidoscope.kitchenware.block.SeasoningTrayBlock;
import com.kaleidoscope.kitchenware.block.WaterVatBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(KaleidoscopeKitchenware.MOD_ID);

    // --- 青砖 / blue brick ---
    public static final DeferredHolder<Block, Block> BLUE_BRICK = reg("blue_brick", () -> new Block(stone()));
    public static final DeferredHolder<Block, StairBlock> BLUE_BRICK_STAIRS = reg("blue_brick_stairs",
            () -> new StairBlock(BLUE_BRICK.get().defaultBlockState(), stone()));
    public static final DeferredHolder<Block, SlabBlock> BLUE_BRICK_SLAB = reg("blue_brick_slab",
            () -> new SlabBlock(stone()));
    public static final DeferredHolder<Block, WallBlock> BLUE_BRICK_WALL = reg("blue_brick_wall",
            () -> new WallBlock(stone()));

    // --- 青瓦 / blue roof tile ---
    public static final DeferredHolder<Block, Block> BLUE_ROOF_TILE = reg("blue_roof_tile", () -> new Block(stone()));
    public static final DeferredHolder<Block, StairBlock> BLUE_ROOF_TILE_STAIRS = reg("blue_roof_tile_stairs",
            () -> new StairBlock(BLUE_ROOF_TILE.get().defaultBlockState(), stone()));
    public static final DeferredHolder<Block, SlabBlock> BLUE_ROOF_TILE_SLAB = reg("blue_roof_tile_slab",
            () -> new SlabBlock(stone()));
    public static final DeferredHolder<Block, Block> ROOF_RIDGE_TILE = reg("roof_ridge_tile", () -> new Block(stone()));

    // --- 墙地 / walls and floors ---
    public static final DeferredHolder<Block, Block> PLASTER_WALL = reg("plaster_wall", () -> new Block(stone()));
    public static final DeferredHolder<Block, Block> RAMMED_EARTH_WALL = reg("rammed_earth_wall",
            () -> new Block(earth()));
    public static final DeferredHolder<Block, Block> STONE_FLOOR_TILE = reg("stone_floor_tile", () -> new Block(stone()));
    public static final DeferredHolder<Block, Block> WOOD_FLOOR_BOARD = reg("wood_floor_board", () -> new Block(wood()));

    // --- 木构 / timber ---
    public static final DeferredHolder<Block, RotatedPillarBlock> WOODEN_BEAM = reg("wooden_beam",
            () -> new RotatedPillarBlock(wood()));
    public static final DeferredHolder<Block, RotatedPillarBlock> WOODEN_RAFTER = reg("wooden_rafter",
            () -> new RotatedPillarBlock(wood()));
    public static final DeferredHolder<Block, FacingThinBlock> LATTICE_WINDOW = reg("lattice_window",
            () -> new FacingThinBlock(thinWood()));
    public static final DeferredHolder<Block, FacingThinBlock> BAMBOO_CURTAIN = reg("bamboo_curtain",
            () -> new FacingThinBlock(thinWood()));

    // --- 功能方块 / functional blocks ---
    /** Carries the vanilla LIT property, which is exactly what the base mod checks for a heat source. */
    public static final DeferredHolder<Block, FirewoodStoveBlock> FIREWOOD_STOVE = reg("firewood_stove",
            () -> new FirewoodStoveBlock(stone().randomTicks()));
    public static final DeferredHolder<Block, FirewoodPileBlock> FIREWOOD_PILE = reg("firewood_pile",
            () -> new FirewoodPileBlock(wood().noOcclusion().strength(0.6F)));
    /** Plain pillar block, decorative: keeps the chimney line above a stove looking finished. */
    public static final DeferredHolder<Block, RotatedPillarBlock> CHIMNEY = reg("chimney",
            () -> new RotatedPillarBlock(stone()));
    public static final DeferredHolder<Block, WaterVatBlock> WATER_VAT = reg("water_vat",
            () -> new WaterVatBlock(stone().noOcclusion().strength(1.5F, 4.0F)));
    /** 16 slots of bowls and flower pots, no GUI anywhere. */
    public static final DeferredHolder<Block, CupboardBlock> CUPBOARD = reg("cupboard",
            () -> new CupboardBlock(wood().strength(2.0F, 3.0F)));
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
    /** Plain block; the boards tile so a row of them reads as one continuous counter. */
    public static final DeferredHolder<Block, Block> KITCHEN_COUNTER = reg("kitchen_counter",
            () -> new Block(wood().noOcclusion()));

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
