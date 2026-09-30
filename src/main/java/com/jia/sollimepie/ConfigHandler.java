package com.jia.sollimepie;

import com.jia.sollimepie.communication.ConfigMessage;
import com.jia.sollimepie.tracking.FoodInstance;
import com.jia.sollimepie.tracking.CapabilityHandler;
import com.jia.sollimepie.tracking.benefits.BenefitsHandler;
import com.jia.sollimepie.tracking.benefits.EffectBenefitsCapability;
import com.jia.sollimepie.tracking.benefits.Benefit;
import com.jia.sollimepie.tracking.benefits.BenefitList;
import com.jia.sollimepie.utils.BenefitsParser;
import com.jia.sollimepie.utils.ComplexityParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public class ConfigHandler {
    public static Map<FoodInstance, Double> complexityMap = new HashMap<>();
    public static List<Double> thresholds = new ArrayList<>();
    public static BenefitList benefitsList = new BenefitList(new ArrayList<>());

    public final static String COMPLEXITY_MAP_KEY = "complexity_map";
    public final static String THRESHOLDS_KEY = "thresholds";
    public final static String BENEFITS_KEY = "benefits_list";
    public final static String FOOD_KEY = "food";
    public final static String COMPLEXITY_VALUE_KEY = "complexity";
    public final static String ENTRY_KEY = "entries";
    private static final String CALCULATION_RULES_KEY = "calculation_rules";

    public static boolean isFirstAid = false;

    public static CompoundTag serializeComplexityMap() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<FoodInstance, Double> entry : complexityMap.entrySet()) {
            String encoded = entry.getKey().encode();
            if (encoded == null) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(FOOD_KEY, StringTag.valueOf(encoded));
            entryTag.put(COMPLEXITY_VALUE_KEY, DoubleTag.valueOf(entry.getValue()));
            list.add(entryTag);
        }
        tag.put(ENTRY_KEY, list);
        return tag;
    }

    public static ListTag serializeThresholds() {
       ListTag tag = new ListTag();

        for (double t : thresholds) {
            tag.add(DoubleTag.valueOf(t));
        }

        return tag;
    }

    public static CompoundTag serializeBenefitsList() {
        return benefitsList.serializeNBT();
    }

    public static CompoundTag serializeConfig() {
        CompoundTag tag = new CompoundTag();
        tag.put(COMPLEXITY_MAP_KEY, serializeComplexityMap());
        tag.put(THRESHOLDS_KEY, serializeThresholds());
        tag.put(BENEFITS_KEY, serializeBenefitsList());
        tag.put(CALCULATION_RULES_KEY, SOLLimePieConfig.serializeCalculationRules());
        return tag;
    }

    public static void deserializeConfig(CompoundTag tag) {
        deserializeComplexityMap(tag.getCompound(COMPLEXITY_MAP_KEY));
        deserializeThresholds(tag.getList(THRESHOLDS_KEY, Tag.TAG_DOUBLE));
        deserializeBenefitsList(tag.getCompound(BENEFITS_KEY));
        // Older servers omit this optional NBT field.
        if (tag.contains(CALCULATION_RULES_KEY, Tag.TAG_COMPOUND)) {
            SOLLimePieConfig.applyCalculationRules(tag.getCompound(CALCULATION_RULES_KEY));
        }
    }

    public static void deserializeBenefitsList(CompoundTag tag) {
        benefitsList.deserializeNBT(tag);
    }

    public static void deserializeComplexityMap(CompoundTag tag) {
       ListTag list = tag.getList(ENTRY_KEY, Tag.TAG_COMPOUND);
        Map<FoodInstance, Double> newComplexityMap = new HashMap<>();
        for (Tag nbt : list) {
            CompoundTag cnbt = (CompoundTag) nbt;
            String foodString = cnbt.getString(FOOD_KEY);
            FoodInstance food = FoodInstance.decode(foodString);
            if (food == null) {
                continue;
            }
            double complexity = cnbt.getDouble(COMPLEXITY_VALUE_KEY);
            newComplexityMap.put(food, complexity);
        }
        complexityMap = newComplexityMap;
    }

    public static void deserializeThresholds(ListTag tag) {
        List<Double> newThresholds = new ArrayList<>();
        tag.stream().map(nbt -> (DoubleTag) nbt).map(DoubleTag::getAsDouble).forEach(newThresholds::add);
        thresholds = newThresholds;
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        loadServerConfig();
        isFirstAid = ModList.get().isLoaded("firstaid");
    }

    private static void loadServerConfig() {
        complexityMap = ComplexityParser.parse(SOLLimePieConfig.getComplexityUnparsed());
        thresholds = SOLLimePieConfig.getThresholds();
        List<List<Benefit>> benefits = BenefitsParser.parse(SOLLimePieConfig.getBenefitsUnparsed());
        benefitsList = new BenefitList(benefits);
    }

    // Called on the server thread. Parse before touching active player benefits.
    static void reloadServerConfig(List<? extends Player> players) {
        Map<FoodInstance, Double> newComplexity = ComplexityParser.parse(SOLLimePieConfig.getComplexityUnparsed());
        List<Double> newThresholds = SOLLimePieConfig.getThresholds();
        BenefitList newBenefits = new BenefitList(BenefitsParser.parse(SOLLimePieConfig.getBenefitsUnparsed()));
        float[] health = new float[players.size()];
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            health[i] = player.getHealth();
            BenefitsHandler.removeAllBenefits(player);
            EffectBenefitsCapability.get(player).clear();
        }
        complexityMap = newComplexity;
        thresholds = newThresholds;
        benefitsList = newBenefits;
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            BenefitsHandler.updatePlayer(player);
            if (!isFirstAid) {
                player.setHealth(Math.min(health[i], player.getMaxHealth()));
            }
            syncConfig(player);
            CapabilityHandler.syncFoodList(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        syncConfig(event.getEntity());
    }

    public static List<List<Benefit>> getBenefitsList() {
        return benefitsList.getBenefits();
    }

    public static void syncConfig(Player player) {
        if (player instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(target, new ConfigMessage());
        }
    }
}
