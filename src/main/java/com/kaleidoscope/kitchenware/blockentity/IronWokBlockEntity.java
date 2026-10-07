package com.kaleidoscope.kitchenware.blockentity;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot;
import com.github.ysbbbbbb.kaleidoscopecookery.api.recipe.soupbase.ISoupBase;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.SimpleInput;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotInput;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.BaseRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexPotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModSoupBases;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import com.github.ysbbbbbb.kaleidoscopecookery.item.KitchenShovelItem;
import com.github.ysbbbbbb.kaleidoscopecookery.item.OilPotItem;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.QualityEvaluator;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.QualityUtils;
import com.github.ysbbbbbb.kaleidoscopecookery.util.ItemUtils;
import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.FirewoodStoveBlock;
import com.kaleidoscope.kitchenware.block.IronWokBlock;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The big iron wok. One block, two cooking modes: what goes in first decides which one it is - oil
 * starts a stir fry, a soup base starts a soup. An empty wok can still change its mind.
 *
 * The timings, the order the states move in, the carrier handling and the "suspicious" fallbacks
 * are all copied from the base mod's pot and stockpot on purpose, so a player who knows those pots
 * already knows this wok. The differences are the mode switch and the heat check, which reads the
 * stove's lit state directly rather than going through the base mod's pot API.
 *
 * The status numbers come from two different interfaces and only make sense together with the mode:
 * a stir fry uses {@link IPot} numbering, a soup uses the stockpot numbering. Both start at zero.
 */
public class IronWokBlockEntity extends BlockEntity implements IPot {
    /** What the wok is being used for. An empty wok is undecided. */
    public enum Mode {
        NONE,
        STIR_FRY,
        SOUP
    }

    // stir fry timings, straight out of the base mod's pot
    private static final int PUT_INGREDIENT_TIME = 60 * 20;
    private static final int TAKEOUT_TIME = 40 * 20;
    private static final int BURNT_TIME = 20 * 20;
    private static final int SUSPICIOUS_TIME = 10 * 20;
    // soup timings, straight out of the base mod's stockpot
    private static final int SOUP_SUSPICIOUS_TIME = 300;
    private static final int MAX_TAKEOUT_COUNT = 9;
    // soup states, numbered for the wok rather than borrowed: sold as 0 = base in and waiting,
    // 1 = cooking, 2 = done. The stir fry numbering follows IPot, since that is the interface the
    // shovel and the rest of the world see.
    private static final int SOUP_WAITING = 0;
    private static final int SOUP_COOKING = 1;
    private static final int SOUP_FINISHED = 2;

    private static final ResourceLocation SUSPICIOUS_STIR_FRY =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "suspicious_stir_fry");
    private static final ResourceLocation DARK_CUISINE =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "dark_cuisine");

    private static final String INPUTS = "Inputs";
    private static final String MODE = "Mode";
    private static final String RESULT = "Result";
    private static final String STATUS = "Status";
    private static final String CURRENT_TICK = "CurrentTick";
    private static final String STIR_FRY_COUNT = "StirFryCount";
    private static final String OILED = "Oiled";
    private static final String SOUP_BASE_ID = "SoupBaseId";
    private static final String RECIPE_ID = "RecipeId";
    private static final String TAKEOUT_COUNT = "TakeoutCount";

    private Mode mode = Mode.NONE;
    /** Nine slots, the size the base mod writes its recipes against. */
    private final NonNullList<ItemStack> inputs = NonNullList.withSize(BaseRecipe.RECIPES_SIZE, ItemStack.EMPTY);
    private ItemStack result = ItemStack.EMPTY;
    private int status;
    private int currentTick;
    private int stirFryCount;
    private boolean oiled;
    private ResourceLocation soupBaseId = ModSoupBases.WATER;
    private ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "pot/empty");
    private int takeoutCount;

    private final RecipeManager.CachedCheck<StockpotInput, StockpotRecipe> soupCheck =
            RecipeManager.createCheck(ModRecipes.STOCKPOT_RECIPE);
    private final RecipeManager.CachedCheck<StockpotInput, FlexStockpotRecipe> flexSoupCheck =
            RecipeManager.createCheck(ModRecipes.FLEX_STOCKPOT_RECIPE);

    public IronWokBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_WOK.get(), pos, state);
    }

    // --- state ------------------------------------------------------------------

    public Mode mode() {
        return mode;
    }

    public boolean isSoupMode() {
        return mode == Mode.SOUP;
    }

    public boolean hasOil() {
        return oiled;
    }

    /** True when there is nothing at all in the wok: no liquid, no ingredients, no dish. */
    public boolean isEmpty() {
        return !oiled && mode == Mode.NONE && inputsEmpty() && result.isEmpty();
    }

    private boolean inputsEmpty() {
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** What the renderer draws: the ingredients while it cooks, the dish once it is done. */
    public List<ItemStack> displayedStacks() {
        boolean done = (mode == Mode.SOUP && status == SOUP_FINISHED)
                || (mode == Mode.STIR_FRY && (status == IPot.FINISHED || status == IPot.BURNT));
        List<ItemStack> shown = new ArrayList<>();
        if (done && !result.isEmpty()) {
            shown.add(result);
            return shown;
        }
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                shown.add(stack);
            }
        }
        return shown;
    }

    @Override
    public int getStatus() {
        return status;
    }

    /**
     * The heat contract, the same one the base mod uses: the block below has to be a lit range.
     * Reading the property keeps this working without asking the stove's block entity anything.
     */
    @Override
    public boolean hasHeatSource(Level level) {
        BlockState below = level.getBlockState(worldPosition.below());
        return below.getBlock() instanceof FirewoodStoveBlock
                && below.hasProperty(BlockStateProperties.LIT)
                && below.getValue(BlockStateProperties.LIT);
    }

    /** Sends the whole state to tracking clients, the way the base mod's own block entities do. */
    public void refresh() {
        setChanged();
        Level level = getLevel();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Switches the model between empty, oil and soup. */
    private void setContent(IronWokBlock.WokContent content) {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(IronWokBlock.CONTENT) && state.getValue(IronWokBlock.CONTENT) != content) {
            level.setBlock(worldPosition, state.setValue(IronWokBlock.CONTENT, content), Block.UPDATE_ALL);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Hands everything in the wok to the world, used when the wok itself is broken. */
    public void dropContents() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        if (!result.isEmpty()) {
            Block.popResource(level, worldPosition, result.copy());
        }
        inputs.clear();
        result = ItemStack.EMPTY;
        setChanged();
    }

    // --- ticking ----------------------------------------------------------------

    /** Server tick: nothing burns without fire under the wok, then the mode decides what happens. */
    public void serverTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide || mode == Mode.NONE) {
            return;
        }
        if (!hasHeatSource(level)) {
            return;
        }
        switch (mode) {
            case STIR_FRY -> tickStirFry(level);
            case SOUP -> tickSoup(level);
            default -> {
            }
        }
    }

    private void tickStirFry(Level level) {
        switch (status) {
            case IPot.PUT_INGREDIENT -> {
                // the oil is in: ingredients have a minute to arrive before it is wiped out
                if (currentTick > 0) {
                    currentTick--;
                } else if (inputsEmpty()) {
                    reset();
                } else {
                    startCooking(level);
                }
            }
            case IPot.COOKING -> {
                if (currentTick > 0) {
                    currentTick--;
                }
                if (currentTick <= 0) {
                    level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F,
                            (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
                    // a dish nobody stirred comes out as the mod's own "suspicious" version
                    if (stirFryCount > 0 && !result.isEmpty()) {
                        result = new ItemStack(BuiltInRegistries.ITEM.get(SUSPICIOUS_STIR_FRY));
                        stirFryCount = 0;
                    }
                    status = IPot.FINISHED;
                    currentTick = TAKEOUT_TIME;
                    refresh();
                }
            }
            case IPot.FINISHED -> {
                if (currentTick > 0) {
                    currentTick--;
                } else {
                    status = IPot.BURNT;
                    currentTick = BURNT_TIME;
                    refresh();
                }
            }
            case IPot.BURNT -> {
                if (currentTick > 0) {
                    currentTick--;
                } else {
                    level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                    Block.popResource(level, worldPosition,
                            new ItemStack(Items.CHARCOAL, 1 + level.random.nextInt(3)));
                    reset();
                }
            }
            default -> {
            }
        }
    }

    private void tickSoup(Level level) {
        switch (status) {
            case SOUP_WAITING -> {
                // the base is in: check every few ticks whether there is something to cook
                if (level.getGameTime() % 5 == 0 && !inputsEmpty()) {
                    startSoup(level);
                    refresh();
                }
            }
            case SOUP_COOKING -> {
                if (currentTick > 0) {
                    currentTick--;
                } else {
                    status = SOUP_FINISHED;
                    currentTick = 0;
                    // the ingredients went into the soup, so they are gone from the wok
                    inputs.clear();
                    refresh();
                }
            }
            default -> {
            }
        }
    }

    private void reset() {
        mode = Mode.NONE;
        oiled = false;
        status = IPot.PUT_INGREDIENT;
        currentTick = 0;
        stirFryCount = 0;
        takeoutCount = 0;
        result = ItemStack.EMPTY;
        inputs.clear();
        soupBaseId = ModSoupBases.WATER;
        setContent(IronWokBlock.WokContent.EMPTY);
        refresh();
    }

    // --- stir frying ------------------------------------------------------------

    @Override
    public boolean onPlaceOil(Level level, LivingEntity user, ItemStack stack) {
        if (mode == Mode.SOUP || oiled) {
            return false;
        }
        // the three ways the base mod accepts oil, in its order: the oil item, an oiled shovel, a pot
        if (stack.is(TagMod.OIL)) {
            stack.shrink(1);
        } else if (stack.is(ModItems.KITCHEN_SHOVEL.get()) && KitchenShovelItem.hasOil(stack)) {
            KitchenShovelItem.setHasOil(stack, false);
        } else if (stack.is(ModItems.OIL_POT.get()) && OilPotItem.hasOil(stack)) {
            OilPotItem.shrinkOilCount(stack);
        } else {
            return false;
        }
        mode = Mode.STIR_FRY;
        oiled = true;
        status = IPot.PUT_INGREDIENT;
        currentTick = PUT_INGREDIENT_TIME;
        setContent(IronWokBlock.WokContent.OIL);
        level.playSound(user, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F,
                (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.SMOKE,
                    worldPosition.getX() + 0.5 + level.random.nextDouble() / 3 * (level.random.nextBoolean() ? 1 : -1),
                    worldPosition.getY() + 0.25 + level.random.nextDouble() / 3,
                    worldPosition.getZ() + 0.5 + level.random.nextDouble() / 3 * (level.random.nextBoolean() ? 1 : -1),
                    0, 0.05, 0);
        }
        refresh();
        return true;
    }

    private void startCooking(Level level) {
        SimpleInput input = new SimpleInput(inputs);
        RecipeManager manager = level.getRecipeManager();
        var potRecipe = manager.getRecipeFor(ModRecipes.POT_RECIPE, input, level);
        if (potRecipe.isPresent()) {
            applyRecipe(level, input, potRecipe.get());
            return;
        }
        var flexRecipe = manager.getRecipeFor(ModRecipes.FLEX_POT_RECIPE, input, level);
        if (flexRecipe.isPresent()) {
            applyFlexRecipe(level, input, flexRecipe.get());
            return;
        }
        // nothing matches: the base mod cooks this into its suspicious stir fry after ten seconds
        recipeId = ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "pot/empty");
        result = new ItemStack(BuiltInRegistries.ITEM.get(SUSPICIOUS_STIR_FRY));
        status = IPot.COOKING;
        currentTick = SUSPICIOUS_TIME;
        stirFryCount = 0;
        refresh();
    }

    private void applyRecipe(Level level, SimpleInput input, RecipeHolder<PotRecipe> holder) {
        PotRecipe recipe = holder.value();
        recipeId = holder.id();
        result = recipe.assemble(input, level.registryAccess());
        currentTick = recipe.time();
        stirFryCount = recipe.stirFryCount();
        status = IPot.COOKING;
        refresh();
    }

    private void applyFlexRecipe(Level level, SimpleInput input, RecipeHolder<FlexPotRecipe> holder) {
        FlexPotRecipe recipe = holder.value();
        recipeId = holder.id();
        result = recipe.assemble(input, level.registryAccess());
        currentTick = recipe.time();
        stirFryCount = recipe.stirFryCount();
        if (level instanceof ServerLevel serverLevel) {
            QualityUtils.setQuality(result, QualityEvaluator.evaluate(
                    inputs, recipe.ingredients(), holder.id(), serverLevel.getSeed()));
        }
        status = IPot.COOKING;
        refresh();
    }

    @Override
    public void onShovelHit(Level level, LivingEntity user, ItemStack shovel) {
        if (mode == Mode.SOUP) {
            return;
        }
        if (status == IPot.PUT_INGREDIENT && !inputsEmpty()) {
            // stirring is what actually starts a stir fry; waiting out the timer also works
            startCooking(level);
            return;
        }
        if (status == IPot.COOKING && stirFryCount > 0) {
            stirFryCount--;
            if (stirFryCount <= 0) {
                stirFryCount = 0;
            }
        }
    }

    @Override
    public boolean addIngredient(Level level, LivingEntity user, ItemStack stack) {
        if (mode == Mode.SOUP) {
            return addSoupIngredient(level, user, stack);
        }
        // the base mod only takes ingredients while the pan is waiting for them
        if (status != IPot.PUT_INGREDIENT || stack.isEmpty() || stack.is(TagMod.INGREDIENT_BLOCKLIST)) {
            return false;
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (!inputs.get(slot).isEmpty()) {
                continue;
            }
            Item container = ItemUtils.getContainerItem(stack);
            if (container != Items.AIR) {
                ItemUtils.getItemToLivingEntity(user, container.getDefaultInstance());
            }
            inputs.set(slot, stack.split(1));
            level.playSound(null, worldPosition, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0F, 0.5F);
            refresh();
            return true;
        }
        return false;
    }

    @Override
    public boolean removeIngredient(Level level, LivingEntity user) {
        if (mode == Mode.SOUP) {
            return removeSoupIngredient(level, user);
        }
        if (status != IPot.PUT_INGREDIENT) {
            return false;
        }
        for (int slot = inputs.size() - 1; slot >= 0; slot--) {
            ItemStack stack = inputs.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!containerMatches(user, stack)) {
                return false;
            }
            inputs.set(slot, ItemStack.EMPTY);
            ItemUtils.getItemToLivingEntity(user, stack.copy());
            // fishing something out of a pan on a lit stove burns, exactly like the base mod
            if (hasHeatSource(level)) {
                user.hurt(level.damageSources().inFire(), 1.0F);
            }
            refresh();
            return true;
        }
        return false;
    }

    /** A stack that came in a container needs that container in hand to go back out. */
    private boolean containerMatches(LivingEntity user, ItemStack stack) {
        Item container = ItemUtils.getContainerItem(stack);
        if (container == Items.AIR) {
            return true;
        }
        if (user.getMainHandItem().is(container)) {
            user.getMainHandItem().shrink(1);
            return true;
        }
        if (user instanceof Player player) {
            player.sendSystemMessage(Component.translatable(
                    "state.kaleidoscope_kitchenware.wok_need_carrier",
                    container.getDefaultInstance().getHoverName()));
        }
        return false;
    }

    @Override
    public boolean takeOutProduct(Level level, LivingEntity user, ItemStack stack) {
        return mode == Mode.SOUP ? takeOutSoup(level, user, stack) : takeOutStirFry(level, user, stack);
    }

    private boolean takeOutStirFry(Level level, LivingEntity user, ItemStack stack) {
        if (status != IPot.FINISHED && status != IPot.BURNT) {
            return false;
        }
        // an overdone dish comes out as the base mod's dark cuisine
        ItemStack dish = status == IPot.FINISHED && !result.isEmpty()
                ? result
                : new ItemStack(BuiltInRegistries.ITEM.get(DARK_CUISINE));
        Ingredient carrier = carrierFor(level);
        return carrier.isEmpty()
                ? takeOutWithoutCarrier(level, user, stack, dish)
                : takeOutWithCarrier(level, user, stack, dish, carrier);
    }

    /** No dish in the recipe's carrier: only a sneaking shovel can lift it out. */
    private boolean takeOutWithoutCarrier(Level level, LivingEntity user, ItemStack stack, ItemStack dish) {
        if (stack.is(TagMod.KITCHEN_SHOVEL)) {
            if (user instanceof Player player && !player.isSecondaryUseActive()) {
                tell(user, "state.kaleidoscope_kitchenware.wok_sneak_to_serve");
                return false;
            }
            ItemUtils.getItemToLivingEntity(user, dish.copy());
            reset();
            return true;
        }
        if (hasHeatSource(level)) {
            user.hurt(level.damageSources().inFire(), 1.0F);
        }
        tell(user, "state.kaleidoscope_kitchenware.wok_need_shovel");
        return false;
    }

    /**
     * The dish is served into the carrier its recipe asks for, and the whole dish goes at once -
     * a recipe that yields three servings wants three bowls in hand, because that is what the base
     * mod does.
     */
    private boolean takeOutWithCarrier(Level level, LivingEntity user, ItemStack stack, ItemStack dish,
                                       Ingredient carrier) {
        ItemStack wanted = carrier.getItems().length > 0 ? carrier.getItems()[0] : ItemStack.EMPTY;
        Component wantedName = wanted.getHoverName();
        if (carrier.test(stack)) {
            if (stack.getCount() < dish.getCount()) {
                tell(user, "state.kaleidoscope_kitchenware.wok_carrier_count", dish.getCount(), wantedName);
                return false;
            }
            stack.shrink(dish.getCount());
            ItemUtils.getItemToLivingEntity(user, dish.copy());
            reset();
            return true;
        }
        // fumbling it with neither the dish's carrier nor a shovel in hand burns the hand
        if (!stack.is(TagMod.KITCHEN_SHOVEL)) {
            if (hasHeatSource(level)) {
                user.hurt(level.damageSources().inFire(), 1.0F);
            }
            tell(user, "state.kaleidoscope_kitchenware.wok_need_carrier", wantedName);
        }
        return false;
    }

    /**
     * The carrier comes from the recipe that was matched, looked up by id when the dish is served.
     * Scanning the recipe list beats caching an {@code Ingredient}: it survives a reload, and there
     * is no way for a stale carrier to end up on the wrong dish.
     */
    private Ingredient carrierFor(Level level) {
        for (RecipeHolder<PotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.POT_RECIPE)) {
            if (holder.id().equals(recipeId)) {
                return holder.value().carrier();
            }
        }
        for (RecipeHolder<FlexPotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.FLEX_POT_RECIPE)) {
            if (holder.id().equals(recipeId)) {
                return holder.value().carrier();
            }
        }
        return Ingredient.of(Items.BOWL);
    }

    // --- soup -------------------------------------------------------------------

    /**
     * Puts a soup base in, which is what turns the wok into a soup pot. Returns false for anything
     * that is not a soup base, so the caller can try the next thing in its own order.
     */
    public boolean addSoupBase(Level level, LivingEntity user, ItemStack bucket) {
        if (mode == Mode.STIR_FRY || (mode == Mode.SOUP && status != SOUP_WAITING)) {
            return false;
        }
        for (Map.Entry<ResourceLocation, ISoupBase> entry : SoupBaseManager.getAllSoupBases().entrySet()) {
            ISoupBase soupBase = entry.getValue();
            if (!soupBase.isSoupBase(bucket)) {
                continue;
            }
            mode = Mode.SOUP;
            soupBaseId = entry.getKey();
            status = SOUP_WAITING;
            currentTick = 0;
            takeoutCount = 0;
            ItemStack container = soupBase.getReturnContainer(level, user, bucket);
            bucket.shrink(1);
            ItemUtils.getItemToLivingEntity(user, container);
            setContent(IronWokBlock.WokContent.SOUP);
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
            tell(user, "state.kaleidoscope_kitchenware.wok_soup_base");
            refresh();
            return true;
        }
        return false;
    }

    /** Takes the base back out, but only while nothing has been dropped in yet. */
    public boolean removeSoupBase(Level level, LivingEntity user, ItemStack bucket) {
        if (mode != Mode.SOUP || status != SOUP_WAITING || !inputsEmpty()) {
            return false;
        }
        ISoupBase soupBase = SoupBaseManager.getSoupBase(soupBaseId);
        if (soupBase == null || !soupBase.isContainer(bucket)) {
            return false;
        }
        ItemStack container = soupBase.getReturnSoupBase(level, user, bucket);
        bucket.shrink(1);
        ItemUtils.getItemToLivingEntity(user, container);
        mode = Mode.NONE;
        soupBaseId = ModSoupBases.WATER;
        setContent(IronWokBlock.WokContent.EMPTY);
        level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
        tell(user, "state.kaleidoscope_kitchenware.wok_soup_base_back");
        refresh();
        return true;
    }

    private boolean addSoupIngredient(Level level, LivingEntity user, ItemStack stack) {
        if (status != SOUP_WAITING || stack.isEmpty() || stack.is(TagMod.INGREDIENT_BLOCKLIST)) {
            return false;
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (!inputs.get(slot).isEmpty()) {
                continue;
            }
            Item container = ItemUtils.getContainerItem(stack);
            if (container != Items.AIR) {
                ItemUtils.getItemToLivingEntity(user, container.getDefaultInstance());
            }
            inputs.set(slot, stack.split(1));
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2F,
                    ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            refresh();
            return true;
        }
        tell(user, "state.kaleidoscope_kitchenware.wok_full");
        return false;
    }

    private boolean removeSoupIngredient(Level level, LivingEntity user) {
        if (status != SOUP_WAITING) {
            return false;
        }
        return removeIngredientFromInputs(user);
    }

    private boolean removeIngredientFromInputs(LivingEntity user) {
        for (int slot = inputs.size() - 1; slot >= 0; slot--) {
            ItemStack stack = inputs.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item container = ItemUtils.getContainerItem(stack);
            if (container != Items.AIR && !user.getMainHandItem().is(container)) {
                tell(user, "state.kaleidoscope_kitchenware.wok_need_carrier", container.getDescription());
                return false;
            }
            if (container != Items.AIR) {
                user.getMainHandItem().shrink(1);
            }
            inputs.set(slot, ItemStack.EMPTY);
            ItemUtils.getItemToLivingEntity(user, stack.copy());
            refresh();
            return true;
        }
        return false;
    }

    private void startSoup(Level level) {
        StockpotInput input = new StockpotInput(inputs, soupBaseId);
        var soup = soupCheck.getRecipeFor(input, level);
        if (soup.isPresent()) {
            StockpotRecipe value = soup.get().value();
            applySoup(soup.get().id(), value.time(), value.assemble(input, level.registryAccess()));
            return;
        }
        var flex = flexSoupCheck.getRecipeFor(input, level);
        if (flex.isPresent()) {
            FlexStockpotRecipe value = flex.get().value();
            applySoup(flex.get().id(), value.time(), value.assemble(input, level.registryAccess()));
            return;
        }
        // nothing matches, so it cooks down into the mod's own suspicious stew
        recipeId = ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "stockpot/empty");
        result = new ItemStack(Items.SUSPICIOUS_STEW);
        currentTick = SOUP_SUSPICIOUS_TIME;
        takeoutCount = 1;
        status = SOUP_COOKING;
    }

    private void applySoup(ResourceLocation id, int time, ItemStack dish) {
        recipeId = id;
        result = dish;
        currentTick = time;
        takeoutCount = Math.min(dish.getCount(), MAX_TAKEOUT_COUNT);
        status = SOUP_COOKING;
    }

    private boolean takeOutSoup(Level level, LivingEntity user, ItemStack stack) {
        if (status != SOUP_FINISHED || result.isEmpty() || takeoutCount <= 0) {
            return false;
        }
        Ingredient carrier = soupCarrierFor(level);
        if (!carrier.isEmpty() && !carrier.test(stack)) {
            ItemStack wanted = carrier.getItems().length > 0 ? carrier.getItems()[0] : ItemStack.EMPTY;
            tell(user, "state.kaleidoscope_kitchenware.wok_need_carrier", wanted.getHoverName());
            return false;
        }
        if (!carrier.isEmpty()) {
            stack.shrink(1);
        }
        ItemUtils.getItemToLivingEntity(user, result.copyWithCount(1));
        takeoutCount--;
        if (takeoutCount <= 0) {
            mode = Mode.NONE;
            soupBaseId = ModSoupBases.WATER;
            result = ItemStack.EMPTY;
            inputs.clear();
            status = SOUP_WAITING;
            setContent(IronWokBlock.WokContent.EMPTY);
        }
        refresh();
        return true;
    }

    private Ingredient soupCarrierFor(Level level) {
        for (RecipeHolder<StockpotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
            if (holder.id().equals(recipeId)) {
                return holder.value().carrier();
            }
        }
        for (RecipeHolder<FlexStockpotRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE)) {
            if (holder.id().equals(recipeId)) {
                return holder.value().carrier();
            }
        }
        return Ingredient.of(Items.BOWL);
    }

    // --- plumbing ---------------------------------------------------------------

    private static void tell(LivingEntity user, String key, Object... args) {
        if (user instanceof Player player) {
            player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(INPUTS, ContainerHelper.saveAllItems(new CompoundTag(), inputs, registries));
        tag.putString(MODE, mode.name());
        tag.put(RESULT, result.saveOptional(registries));
        tag.putInt(STATUS, status);
        tag.putInt(CURRENT_TICK, currentTick);
        tag.putInt(STIR_FRY_COUNT, stirFryCount);
        tag.putBoolean(OILED, oiled);
        tag.putString(SOUP_BASE_ID, soupBaseId.toString());
        tag.putString(RECIPE_ID, recipeId.toString());
        tag.putInt(TAKEOUT_COUNT, takeoutCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INPUTS)) {
            ContainerHelper.loadAllItems(tag.getCompound(INPUTS), inputs, registries);
        }
        for (Mode candidate : Mode.values()) {
            if (candidate.name().equals(tag.getString(MODE))) {
                mode = candidate;
            }
        }
        result = tag.contains(RESULT) ? ItemStack.parseOptional(registries, tag.getCompound(RESULT)) : ItemStack.EMPTY;
        status = tag.getInt(STATUS);
        currentTick = tag.getInt(CURRENT_TICK);
        stirFryCount = tag.getInt(STIR_FRY_COUNT);
        oiled = tag.getBoolean(OILED);
        ResourceLocation storedBase = ResourceLocation.tryParse(tag.getString(SOUP_BASE_ID));
        if (storedBase != null) {
            soupBaseId = storedBase;
        }
        ResourceLocation storedRecipe = ResourceLocation.tryParse(tag.getString(RECIPE_ID));
        if (storedRecipe != null) {
            recipeId = storedRecipe;
        }
        takeoutCount = tag.getInt(TAKEOUT_COUNT);
    }
}
