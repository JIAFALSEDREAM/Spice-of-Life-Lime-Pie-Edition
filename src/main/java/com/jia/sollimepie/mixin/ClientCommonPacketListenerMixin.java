package com.jia.sollimepie.mixin;

import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerMixin {
    @Shadow @Final protected Minecraft minecraft;

    @Inject(method = "send", at = @At("HEAD"))
    private void sollimepie$trackMealInput(Packet<?> packet, CallbackInfo ci) {
        var player = minecraft.player;
        if (player == null || !minecraft.isSameThread() || minecraft.getConnection() != (Object) this) {
            return;
        }
        switch (packet) {
            case ServerboundUseItemPacket use -> {
                var stack = player.getItemInHand(use.getHand());
                if (stack.getItem() instanceof FoodContainerItem) {
                    FoodContainerUse.rememberRequest(player, use.getHand(), stack, use.getSequence());
                }
            }
            case ServerboundSetCarriedItemPacket ignored -> FoodContainerUse.cancelRequests(player, InteractionHand.MAIN_HAND);
            case ServerboundPlayerActionPacket action -> {
                switch (action.getAction()) {
                    case RELEASE_USE_ITEM, SWAP_ITEM_WITH_OFFHAND -> FoodContainerUse.cancelRequests(player, null);
                    case DROP_ITEM, DROP_ALL_ITEMS -> FoodContainerUse.cancelRequests(player, InteractionHand.MAIN_HAND);
                    default -> { }
                }
            }
            default -> { }
        }
    }
}
