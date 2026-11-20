package com.jia.sollimepie.item.foodcontainer;

import com.jia.sollimepie.tracking.FoodList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public class FoodContainerItem extends Item {
    private final String displayName;
    private final int nslots;

    public FoodContainerItem(int nslots, String displayName) {
        super(new Properties().stacksTo(1).food(new FoodProperties.Builder().build()));
        this.displayName = displayName;
        this.nslots = nslots;
    }

    public int getSlotCount() { return nslots; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isUsingItem()) {
            return InteractionResultHolder.consume(stack);
        }
        if (player.isCrouching()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new FoodContainerProvider(displayName, stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
        }
        // Disabled: the legacy Origins API does not exist in the tested 1.21.1 NeoForge ports.
        // if (ModList.get().isLoaded("origins") && Origins.hasRestrictedDiet(player)) return InteractionResultHolder.pass(stack);
        var inventory = new FoodContainerInventory(stack, nslots);
        boolean canEat = player.canEat(false);
        int slot = canEat ? getBestFoodSlot(inventory, player) : findFirstFoodSlot(inventory);
        if (slot < 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (!canEat) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (player.isUsingItem() && player.getUseItem() == stack) {
            ItemStack food = inventory.getStackInSlot(slot);
            if (world.isClientSide) {
                FoodContainerUse.predict(player, hand, stack, food);
            } else {
                FoodContainerUse.startServerMeal(player, hand, stack, slot, food);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    private static int findFirstFoodSlot(ItemStackHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (isFood(inventory.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    @Nullable
    public static FoodContainerInventory getInventory(ItemStack bag) {
        if (bag.getItem() instanceof FoodContainerItem item) {
            return new FoodContainerInventory(bag, item.nslots);
        }
        return null;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entity) {
        if (world.isClientSide || !(entity instanceof Player player)) {
            return stack;
        }
        var meal = FoodContainerUse.takeMeal(player);
        if (meal == null || meal.container() != stack || !meal.isActive(player)) {
            return stack;
        }

        var handler = new FoodContainerInventory(stack, nslots);
        int slot = meal.slot();
        ItemStack food = handler.getStackInSlot(slot);
        if (food.isEmpty() || !ItemStack.isSameItemSameComponents(food, meal.food())) {
            return stack;
        }

        ItemStack before = food.copy();
        ItemStack result = food.finishUsingItem(world, entity);
        if (!result.isEmpty() && !FoodSlot.canHold(result)) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        } else {
            handler.setStackInSlot(slot, result);
        }
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, before, 0, result));
        return stack;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (entity instanceof Player player && FoodContainerUse.changedContainer(player)) {
            player.stopUsingItem();
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }

    public static int getBestFoodSlot(ItemStackHandler handler, Player player) {
        FoodList foodList = FoodList.get(player);
        var simulator = foodList.foodSimulator();
        double maxDiversity = -Double.MAX_VALUE;
        int bestSlot = -1;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack food = handler.getStackInSlot(i);
            if (!isFood(food)) {
                continue;
            }
            double change = simulator.applyAsDouble(food.getItem());
            if (change > maxDiversity) {
                maxDiversity = change;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
