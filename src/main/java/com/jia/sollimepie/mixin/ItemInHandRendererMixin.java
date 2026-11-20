package com.jia.sollimepie.mixin;

import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void sollimepie$renderMeal(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack,
                                       ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                       MultiBufferSource buffer, int light, Operation<Void> original,
                                       @Local(argsOnly = true, ordinal = 0) float partialTick,
                                       @Local(argsOnly = true) InteractionHand hand) {
        if (stack.getItem() instanceof FoodContainerItem && entity instanceof Player player
            && player.getUsedItemHand() == hand && ItemStack.matches(player.getUseItem(), stack)
            && FoodContainerUse.confirmedDuration(player, hand, player.getUseItem()) >= 0) {
            ItemStack food = FoodContainerUse.particleFood(player);
            if (!food.isEmpty()) {
                float progress = FoodContainerUse.pickupProgress(player, partialTick);
                float dip = Mth.sin(progress * Mth.PI);
                float inward = leftHand ? 1.0F : -1.0F;
                pose.pushPose();
                pose.translate(inward * 0.08F * dip, -0.28F * dip, -0.04F * dip);
                pose.mulPose(Axis.ZP.rotationDegrees(inward * 18.0F * dip));
                original.call(renderer, entity, progress < 0.5F ? stack : food, context, leftHand, pose, buffer, light);
                pose.popPose();
                return;
            }
        }
        original.call(renderer, entity, stack, context, leftHand, pose, buffer, light);
    }
}
