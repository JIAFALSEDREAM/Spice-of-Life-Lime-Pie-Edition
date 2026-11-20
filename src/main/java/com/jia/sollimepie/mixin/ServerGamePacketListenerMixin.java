package com.jia.sollimepie.mixin;

import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerMixin {
    @WrapOperation(method = "handleUseItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItem(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult sollimepie$useContainer(ServerPlayerGameMode gameMode, ServerPlayer player, Level level,
                                                     ItemStack stack, InteractionHand hand, Operation<InteractionResult> original,
                                                     @Local(argsOnly = true) ServerboundUseItemPacket packet) {
        if (!(stack.getItem() instanceof FoodContainerItem)) {
            return original.call(gameMode, player, level, stack, hand);
        }
        int previous = FoodContainerUse.swapRequestSequence(player, packet.getSequence());
        try {
            return original.call(gameMode, player, level, stack, hand);
        } finally {
            FoodContainerUse.swapRequestSequence(player, previous);
        }
    }
}
