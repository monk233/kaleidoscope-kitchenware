package com.kaleidoscope.kitchenware.blockentity;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.registry.ModEffects;
import com.kaleidoscope.kitchenware.util.FuelTier;
import com.kaleidoscope.kitchenware.util.RangeHoodSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public class FirewoodStoveBlockEntity extends BlockEntity {
    private static final int SMOKE_INTERVAL = 20;
    private static final double COUGH_RADIUS = 4.0;

    private ItemStack fuel = ItemStack.EMPTY;
    private FuelTier tier = FuelTier.LOW;
    private int burnTime;
    private int burnTimeTotal;
    private boolean hoodBoost;
    private int hoodEfficiency;

    public FirewoodStoveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIREWOOD_STOVE.get(), pos, state);
    }

    /** Stores one fuel item if the stove is empty, returning whether it was accepted. */
    public boolean addFuel(ItemStack stack) {
        if (!fuel.isEmpty() || stack.getBurnTime(null) <= 0) {
            return false;
        }
        fuel = stack.copyWithCount(1);
        tier = FuelTier.of(stack);
        burnTimeTotal = tier.burnTicks(stack);
        burnTime = burnTimeTotal;
        setChanged();
        return true;
    }

    public boolean hasFuel() {
        return burnTime > 0;
    }

    public boolean canIgnite() {
        return hasFuel() && !getBlockState().getValue(BlockStateProperties.LIT);
    }

    public FuelTier tier() {
        return tier;
    }

    /** Tier as shown to the player: a working range hood pushes it one step higher. */
    public FuelTier effectiveTier() {
        if (!hoodBoost || hoodEfficiency <= 0) {
            return tier;
        }
        return switch (tier) {
            case LOW -> FuelTier.MID;
            case MID, HIGH -> FuelTier.HIGH;
        };
    }

    public int burnTime() {
        return burnTime;
    }

    public void setHoodBoost(boolean boosted, int efficiency) {
        if (hoodBoost != boosted || hoodEfficiency != efficiency) {
            hoodBoost = boosted;
            hoodEfficiency = efficiency;
            setChanged();
        }
    }

    public void ignite() {
        Level level = getLevel();
        if (level != null) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, true));
        }
    }

    public void extinguish() {
        Level level = getLevel();
        if (level != null) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlockStateProperties.LIT, false));
        }
    }

    /** Server tick: burn down fuel, vent smoke, go out when empty or rained on. */
    public void serverTick() {
        Level level = getLevel();
        if (level == null || !getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        if (level.isRainingAt(worldPosition.above())) {
            extinguish();
            return;
        }
        if (burnTime > 0) {
            burnTime -= burnRate(level);
            if (burnTime % 20 == 0 || burnTime <= 0) {
                setChanged();
            }
            if (burnTime <= 0) {
                fuel = ItemStack.EMPTY;
                extinguish();
                return;
            }
            tickSmoke(level);
            return;
        }
        fuel = ItemStack.EMPTY;
        extinguish();
    }

    /** A fully efficient hood makes the fire draw harder and burn through fuel faster. */
    private int burnRate(Level level) {
        if (hoodBoost && hoodEfficiency >= 100 && level.random.nextFloat() < 0.25F) {
            return 2;
        }
        return 1;
    }

    private void tickSmoke(Level level) {
        if (level.getGameTime() % SMOKE_INTERVAL != 0 || RangeHoodSupport.hasWorkingHood(level, worldPosition)) {
            return;
        }
        AABB area = new AABB(worldPosition).inflate(COUGH_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            player.addEffect(new MobEffectInstance(ModEffects.SMOKE_COUGH, 60, 0, false, true));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fuel.isEmpty()) {
            tag.put("Fuel", fuel.save(registries));
        }
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        tag.putString("Tier", tier.key);
        tag.putBoolean("HoodBoost", hoodBoost);
        tag.putInt("HoodEfficiency", hoodEfficiency);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel = tag.contains("Fuel") ? ItemStack.parseOptional(registries, tag.getCompound("Fuel")) : ItemStack.EMPTY;
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        for (FuelTier candidate : FuelTier.values()) {
            if (candidate.key.equals(tag.getString("Tier"))) {
                tier = candidate;
            }
        }
        hoodBoost = tag.getBoolean("HoodBoost");
        hoodEfficiency = tag.getInt("HoodEfficiency");
    }

    /** Keeps the lit blockstate honest when the chunk loads with fuel still left. */
    public void syncLit() {
        BlockState state = getBlockState();
        boolean shouldBeLit = burnTime > 0;
        if (state.getValue(BlockStateProperties.LIT) != shouldBeLit) {
            Level level = getLevel();
            if (level != null && !level.isClientSide) {
                level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, shouldBeLit), Block.UPDATE_ALL);
            }
        }
    }
}
