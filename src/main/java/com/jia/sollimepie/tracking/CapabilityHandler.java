package com.jia.sollimepie.tracking;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.SOLLimePieConfig;
import com.jia.sollimepie.communication.FoodListMessage;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import com.jia.sollimepie.tracking.benefits.BenefitsHandler;
import com.jia.sollimepie.tracking.benefits.EffectBenefitsCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public final class CapabilityHandler {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SOLLimePie.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FoodList>> FOOD =
        ATTACHMENTS.register("food", () -> AttachmentType.serializable(FoodList::new).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EffectBenefitsCapability>> EFFECT_BENEFITS =
        ATTACHMENTS.register("effect_benefits", () -> AttachmentType.serializable(EffectBenefitsCapability::new).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FoodContainerUse>> FOOD_CONTAINER_USE =
        ATTACHMENTS.register("food_container_use", () -> AttachmentType.builder(FoodContainerUse::new).build());

    @SubscribeEvent
    public static void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncFoodList(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && SOLLimePieConfig.shouldResetOnDeath()) {
            return;
        }
        FoodList original = FoodList.get(event.getOriginal());
        FoodList copy = FoodList.get(event.getEntity());
        copy.deserializeNBT(original.serializeNBT());
        BenefitsHandler.updatePlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncFoodList(event.getEntity());
    }

    public static void syncFoodList(Player player) {
        if (player instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(target, new FoodListMessage(FoodList.get(target)));
        }
    }
}
