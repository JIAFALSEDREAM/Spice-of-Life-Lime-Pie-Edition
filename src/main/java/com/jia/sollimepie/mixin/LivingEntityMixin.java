package com.jia.sollimepie.mixin;

import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements FoodContainerUse.ObservedUse {
    @Shadow @Final protected static EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS;
    @Shadow protected int useItemRemaining;
    @Unique private boolean sollimepie$awaitingMealFinish;
    @Unique private int sollimepie$ticksPastMealDuration;

    @Shadow public abstract void onSyncedDataUpdated(EntityDataAccessor<?> key);

    @Override
    public void sollimepie$refreshObservedUse() {
        onSyncedDataUpdated(DATA_LIVING_ENTITY_FLAGS);
    }

    @Override
    public void sollimepie$adjustUseDuration(int difference) {
        int remaining = sollimepie$awaitingMealFinish ? -sollimepie$ticksPastMealDuration : useItemRemaining;
        sollimepie$setMealRemainingTicks(remaining + difference);
    }

    @Override
    public void sollimepie$setMealRemainingTicks(int remainingTicks) {
        sollimepie$awaitingMealFinish = remainingTicks <= 0;
        sollimepie$ticksPastMealDuration = Math.max(0, -remainingTicks);
        useItemRemaining = Math.max(1, remainingTicks);
    }

    @ModifyExpressionValue(method = "startUsingItem", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/event/EventHooks;onItemUseStart(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;I)I"))
    private int sollimepie$startConfirmedMeal(int duration, @Local(argsOnly = true) InteractionHand hand) {
        if (duration >= 0 && (Object) this instanceof Player player && player.level().isClientSide) {
            int confirmed = FoodContainerUse.confirmedDuration(player, hand, player.getItemInHand(hand));
            if (confirmed >= 0) {
                sollimepie$setMealRemainingTicks(confirmed);
                return useItemRemaining;
            }
        }
        return duration;
    }

    @ModifyExpressionValue(method = "onSyncedDataUpdated", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int sollimepie$startObservedMeal(int duration) {
        if ((Object) this instanceof Player player) {
            int remaining = FoodContainerUse.observedRemainingTicks(player, player.getUsedItemHand(), player.getUseItem());
            if (remaining >= 0) {
                sollimepie$setMealRemainingTicks(remaining);
                return useItemRemaining;
            }
        }
        return duration;
    }

    @Inject(method = "updateUsingItem", at = @At("HEAD"), cancellable = true)
    private void sollimepie$skipFinishedMealTicks(ItemStack stack, CallbackInfo ci) {
        if (sollimepie$awaitingMealFinish && (Object) this instanceof Player player && player.level().isClientSide) {
            if (FoodContainerUse.changedContainer(player)) {
                player.stopUsingItem();
            } else {
                sollimepie$ticksPastMealDuration++;
            }
            ci.cancel();
        }
    }

    @Inject(method = "updateUsingItem", at = @At("TAIL"))
    private void sollimepie$holdConfirmedMealPose(ItemStack stack, CallbackInfo ci) {
        if (useItemRemaining <= 0 && (Object) this instanceof Player player && player.level().isClientSide
            && player.isUsingItem() && FoodContainerUse.hasSelectedMeal(player, stack)) {
            // Keep the final pose without dispatching another use tick while awaiting the server.
            sollimepie$setMealRemainingTicks(0);
        }
    }

    @WrapOperation(method = "triggerItemUseEffects", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;spawnItemParticles(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void sollimepie$foodParticles(LivingEntity entity, ItemStack stack, int amount, Operation<Void> original) {
        if (entity.level().isClientSide && entity instanceof Player player && stack.getItem() instanceof FoodContainerItem) {
            ItemStack food = FoodContainerUse.particleFood(player);
            if (!food.isEmpty()) {
                original.call(entity, food, amount);
            }
        } else {
            original.call(entity, stack, amount);
        }
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void sollimepie$clearObservedMeal(EntityDataAccessor<?> key, CallbackInfo ci) {
        if (key == DATA_LIVING_ENTITY_FLAGS && (Object) this instanceof Player player
            && player.level().isClientSide && !player.isUsingItem()) {
            sollimepie$awaitingMealFinish = false;
            FoodContainerUse.clearObservedAppearance(player);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("TAIL"))
    private void sollimepie$stopMeal(CallbackInfo ci) {
        sollimepie$awaitingMealFinish = false;
        if ((Object) this instanceof Player player) {
            FoodContainerUse.stop(player);
        }
    }
}
