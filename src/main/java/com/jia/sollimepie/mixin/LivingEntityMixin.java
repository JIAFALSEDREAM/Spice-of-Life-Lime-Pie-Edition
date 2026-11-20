package com.jia.sollimepie.mixin;

import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements FoodContainerUse.ObservedUse {
    @Shadow @Final protected static EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS;

    @Shadow public abstract void onSyncedDataUpdated(EntityDataAccessor<?> key);

    @Override
    public void sollimepie$refreshObservedUse() {
        onSyncedDataUpdated(DATA_LIVING_ENTITY_FLAGS);
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
            FoodContainerUse.clearObservedAppearance(player);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("TAIL"))
    private void sollimepie$stopMeal(CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            FoodContainerUse.stop(player);
        }
    }
}
