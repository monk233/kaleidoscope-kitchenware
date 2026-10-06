package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.blockentity.CupboardBlockEntity;
import com.kaleidoscope.kitchenware.blockentity.FirewoodStoveBlockEntity;
import com.kaleidoscope.kitchenware.blockentity.RangeHoodBlockEntity;
import com.kaleidoscope.kitchenware.blockentity.SpiceRackBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, KaleidoscopeKitchenware.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FirewoodStoveBlockEntity>> FIREWOOD_STOVE =
            BLOCK_ENTITIES.register("firewood_stove",
                    () -> BlockEntityType.Builder.of(FirewoodStoveBlockEntity::new, ModBlocks.FIREWOOD_STOVE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RangeHoodBlockEntity>> RANGE_HOOD =
            BLOCK_ENTITIES.register("range_hood",
                    () -> BlockEntityType.Builder.of(RangeHoodBlockEntity::new, ModBlocks.RANGE_HOOD.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CupboardBlockEntity>> CUPBOARD =
            BLOCK_ENTITIES.register("cupboard",
                    () -> BlockEntityType.Builder.of(CupboardBlockEntity::new, ModBlocks.CUPBOARD.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpiceRackBlockEntity>> SPICE_RACK =
            BLOCK_ENTITIES.register("spice_rack",
                    () -> BlockEntityType.Builder.of(SpiceRackBlockEntity::new, ModBlocks.SPICE_RACK.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
