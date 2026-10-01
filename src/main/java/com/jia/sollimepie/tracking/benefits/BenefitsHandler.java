package com.jia.sollimepie.tracking.benefits;

import com.jia.sollimepie.ConfigHandler;
import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.SOLLimePieConfig;
import com.jia.sollimepie.tracking.CapabilityHandler;
import com.jia.sollimepie.tracking.FoodList;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

/**
 * All updates to food diversity benefits go through this class.
 */
@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public class BenefitsHandler {
    @SubscribeEvent
    public static void tickBenefits(PlayerTickEvent.Post event) {
        if (!checkPlayer(event.getEntity())) {
            return;
        }

        Player player = event.getEntity();

        if (!player.isAlive()) {
           return;
        }

        EffectBenefitsCapability effectBenefits = EffectBenefitsCapability.get(player);
        effectBenefits.forEach(b -> b.onTick(player));
    }

    public static void updateBenefits(Player player, double diversity) {
        if (player.getCommandSenderWorld().isClientSide) {
            return;
        }

        FoodList foodList = FoodList.get(player);
        if (foodList.getFoodsEaten() < SOLLimePieConfig.minFoodsToActivate()) {
            return;
        }

        List<List<Benefit>> benefitsList = ConfigHandler.getBenefitsList();
        List<Double> thresholds = ConfigHandler.thresholds;

        EffectBenefitsCapability effectBenefits = EffectBenefitsCapability.get(player);
        effectBenefits.clear();

        Map<AttributeInstance, Map<ResourceLocation, AttributeModifier>> attributeIndexes = new HashMap<>();

        for (int i = 0; i < thresholds.size(); i++) {
            double thresh = thresholds.get(i);
            if (i >= benefitsList.size()) {
                return;
            }
            benefitsList.get(i).forEach(b -> {
                // != acts as XOR
                boolean active = (diversity >= thresh) != b.isDetriment();
                if (b instanceof AttributeBenefit attributeBenefit) {
                    attributeBenefit.update(player, active, attributeIndexes);
                } else if (active) {
                    b.applyTo(player);
                } else {
                    b.removeFrom(player);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        updatePlayer(event);
        CapabilityHandler.syncFoodList(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
       Player player = event.getEntity();
        removeAllBenefits(player);
    }

    public static void removeAllBenefits(Player player) {
        List<List<Benefit>> benefitsList = ConfigHandler.getBenefitsList();
        benefitsList.forEach(bt -> bt.forEach(b -> b.removeFrom(player)));
    }

    public static void updatePlayer(LivingEvent event) {
        if (!checkEvent(event)) {
            return;
        }

        Player player = (Player) event.getEntity();

        updatePlayer(player);
    }

    public static void updatePlayer(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        FoodList foodList = FoodList.get(player);
        double diversity = foodList.foodDiversity();

        updateBenefits(player, diversity);
    }

    public static boolean checkEvent(LivingEvent event) {
        return event.getEntity() instanceof Player player && checkPlayer(player);
    }

    private static boolean checkPlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return !SOLLimePieConfig.limitProgressionToSurvival() || serverPlayer.gameMode.isSurvival();
    }

    public static Pair<List<BenefitInfo>, List<BenefitInfo>> getBenefitInfo(double active_threshold, int foodEaten) {
        // Can be called on client
        List<BenefitInfo> activeBenefitInfo = new ArrayList<>();
        List<BenefitInfo> inactiveBenefitInfo = new ArrayList<>();

        if (foodEaten < SOLLimePieConfig.minFoodsToActivate()) {
            active_threshold = -1;
        }

        List<List<Benefit>> benefitsList = ConfigHandler.getBenefitsList();
        List<Double> thresholds = ConfigHandler.thresholds;

        for (int i = 0; i < thresholds.size(); i++) {
            double thresh = thresholds.get(i);
            if (i >= benefitsList.size()) {
                break;
            }
            if (active_threshold >= thresh) {
                benefitsList.get(i).forEach(b -> activeBenefitInfo.add(
                        new BenefitInfo(b.getType(), b.getName(), b.getValue(), thresh, b.isDetriment())));
            }
            else {
                benefitsList.get(i).forEach(b -> inactiveBenefitInfo.add(
                        new BenefitInfo(b.getType(), b.getName(), b.getValue(), thresh, b.isDetriment())));
            }
        }

        activeBenefitInfo.sort((bi1, bi2) -> Boolean.compare(bi1.detriment, bi2.detriment));
        inactiveBenefitInfo.sort((bi1, bi2) -> Boolean.compare(bi1.detriment, bi2.detriment));

        return new ImmutablePair<>(activeBenefitInfo, inactiveBenefitInfo);
    }
}
