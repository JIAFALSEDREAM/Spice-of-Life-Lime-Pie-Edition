package com.jia.sollimepie.mixin;

import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handleBlockChangedAck", at = @At("TAIL"))
    private void sollimepie$acknowledgeMealRequests(int sequence, CallbackInfo ci) {
        if (minecraft.player != null) {
            FoodContainerUse.acknowledgeRequests(minecraft.player, sequence);
        }
    }
}
