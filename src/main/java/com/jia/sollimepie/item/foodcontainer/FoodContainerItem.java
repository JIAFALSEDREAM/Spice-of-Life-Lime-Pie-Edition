package com.jia.sollimepie.item.foodcontainer;

import com.jia.sollimepie.integration.Origins;
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
import net.neoforged.fml.ModList;
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
        if (player.isCrouching()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new FoodContainerProvider(displayName));
            }
            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
        }
        if (isInventoryEmpty(stack) || (ModList.get().isLoaded("origins") && Origins.hasRestrictedDiet(player))) {
            return InteractionResultHolder.pass(stack);
        }
        if (!player.canEat(false)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private static boolean isInventoryEmpty(ItemStack container) {
        ItemStackHandler handler = getInventory(container);
        if (handler == null) {
            return true;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                return false;
            }
        }
        return true;
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
        if (!(entity instanceof Player player)) {
            return stack;
        }
        FoodContainerInventory handler = getInventory(stack);
        if (handler == null) {
            return stack;
        }
        int slot = getBestFoodSlot(handler, player);
        if (slot < 0) {
            return stack;
        }

        ItemStack food = handler.getStackInSlot(slot);
        ItemStack before = food.copy();
        ItemStack result = food.finishUsingItem(world, entity);
        if (!result.has(DataComponents.FOOD) && !result.isEmpty()) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        } else {
            handler.setStackInSlot(slot, result);
        }
        if (!world.isClientSide) {
            NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, before, 0, result));
        }
        return stack;
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
            if (food.isEmpty() || !food.has(DataComponents.FOOD)) {
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
