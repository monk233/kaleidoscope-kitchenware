package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.blockentity.CupboardBlockEntity;
import com.kaleidoscope.kitchenware.blockentity.FirewoodStoveBlockEntity;
import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CupboardBlockEntity>> CUPBOARD =
            BLOCK_ENTITIES.register("cupboard",
                    () -> BlockEntityType.Builder.of(CupboardBlockEntity::new, ModBlocks.CUPBOARD.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeasoningTrayBlockEntity>> SEASONING_TRAY =
            BLOCK_ENTITIES.register("seasoning_tray",
                    () -> BlockEntityType.Builder.of(SeasoningTrayBlockEntity::new, ModBlocks.SEASONING_TRAY.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
