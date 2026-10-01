package com.jia.sollimepie;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.tracking.FoodInstance;
import com.jia.sollimepie.tracking.FoodList;
import com.jia.sollimepie.tracking.benefits.BenefitsHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;

@PrefixGameTestTemplate(false)
public final class HighLimitGameTests {
    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void largeRangesAndPermanentHistory(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            var config = CommentedConfig.inMemory();
            SOLLimePieConfig.SERVER_SPEC.correct(config);
            config.set("Miscellaneous.queueSize", 0);
            config.set("Benefits.minFoodsToActivate", Integer.MAX_VALUE);
            config.set("Advanced.startDecay", Integer.MAX_VALUE);
            config.set("Advanced.endDecay", Integer.MAX_VALUE);
            config.set("Advanced.defaultContribution", 1_000_000.0);
            helper.assertTrue(SOLLimePieConfig.SERVER_SPEC.isCorrect(config), "Expanded ranges rejected");
            config.set("Miscellaneous.queueSize", Integer.MAX_VALUE);
            helper.assertTrue(SOLLimePieConfig.SERVER_SPEC.isCorrect(config), "Large finite history rejected");
            config.set("Miscellaneous.queueSize", -1);
            helper.assertTrue(!SOLLimePieConfig.SERVER_SPEC.isCorrect(config), "Negative history accepted");
            SOLLimePieConfig.SERVER.queueSize.set(0);
            SOLLimePieConfig.SERVER.decayEnabled.set(false);
            SOLLimePieConfig.SERVER.whitelist.set(List.of());
            SOLLimePieConfig.SERVER.blacklist.set(List.of());
            ConfigHandler.complexityMap = Map.of(new FoodInstance(Items.BREAD), 2.0, new FoodInstance(Items.CARROT), 3.0);
            FoodList history = new FoodList();
            history.addFood(Items.BREAD);
            for (int i = 0; i < 2001; i++) history.addFood(Items.CARROT);
            helper.assertTrue(history.hasEaten(Items.BREAD) && history.foodDiversity() == 5 && history.getFoodsEaten() == 2002,
                "Permanent records, full contribution or meal count lost");
            SOLLimePieConfig.SERVER.decayEnabled.set(true);
            helper.assertTrue(history.foodDiversity() == 3, "Permanent records incorrectly disabled decay");
            SOLLimePieConfig.SERVER.decayEnabled.set(false);
            var rules = SOLLimePieConfig.serializeCalculationRules();
            rules.remove("decayEnabled");
            SOLLimePieConfig.applyCalculationRules(rules);
            helper.assertTrue(SOLLimePieConfig.decayEnabled(), "Legacy server must keep decay enabled");
            SOLLimePieConfig.SERVER.decayEnabled.set(false);
            var tag = history.serializeNBT();
            var entries = tag.getList("foodList", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getCompound(i);
                if (entry.getString("food").equals("minecraft:bread")) entry.putFloat("lastEaten", 7);
            }
            history.deserializeNBT(tag);
            helper.assertTrue(history.getLastEaten(Items.BREAD) == 7, "Legacy float age failed to load");
            for (var entry : history.getData()) if (entry.getKey().item == Items.BREAD) entry.setValue(16_777_217);
            tag = history.serializeNBT();
            tag.putInt("foodsEaten", Integer.MAX_VALUE - 1);
            history.deserializeNBT(tag);
            history.addFood(Items.CARROT);
            history.addFood(Items.CARROT);
            helper.assertTrue(history.getLastEaten(Items.BREAD) == 16_777_219 && history.getFoodsEaten() == Integer.MAX_VALUE,
                "Large age lost precision or counter overflowed");
            for (var entry : history.getData()) if (entry.getKey().item == Items.BREAD) entry.setValue(Integer.MAX_VALUE);
            history.addFood(Items.CARROT);
            helper.assertTrue(history.getLastEaten(Items.BREAD) == Integer.MAX_VALUE, "Permanent age overflowed");
            SOLLimePieConfig.SERVER.queueSize.set(2);
            history = new FoodList();
            history.addFood(Items.BREAD);
            history.addFood(Items.CARROT);
            history.addFood(Items.CARROT);
            helper.assertTrue(!history.hasEaten(Items.BREAD) && history.foodDiversity() == 3,
                "Disabling decay prevented finite history expiry");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void simulationMatchesActualMealsAndManyTiers(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            var foods = BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item)
                .has(net.minecraft.core.component.DataComponents.FOOD) && !(item instanceof FoodContainerItem)).toList();
            SOLLimePieConfig.SERVER.whitelist.set(List.of());
            SOLLimePieConfig.SERVER.blacklist.set(List.of("minecraft:b*"));
            for (int size : new int[]{0, 1, 32, 100_000}) for (boolean decay : new boolean[]{false, true}) {
                SOLLimePieConfig.SERVER.queueSize.set(size);
                SOLLimePieConfig.SERVER.decayEnabled.set(decay);
                SOLLimePieConfig.SERVER.startDecay.set(0);
                SOLLimePieConfig.SERVER.endDecay.set(size == 0 ? 32 : size);
                SOLLimePieConfig.SERVER.shouldForbiddenCount.set(decay);
                FoodList history = new FoodList();
                for (var food : foods) history.addFood(food);
                var simulator = history.foodSimulator();
                for (var food : foods) {
                    FoodList after = new FoodList();
                    after.deserializeNBT(history.serializeNBT());
                    after.addFood(food);
                    helper.assertTrue(Math.abs(simulator.applyAsDouble(food) - (after.foodDiversity() - history.foodDiversity())) < 1e-9,
                        "Simulation differs from actual meal: size=" + size + ", decay=" + decay);
                }
            }
            var thresholds = new ArrayList<Double>();
            var rewards = new ArrayList<String>();
            for (int i = 0; i < 1024; i++) { thresholds.add((double) i); rewards.add("attribute,generic.luck,0.001"); }
            SOLLimePieConfig.SERVER.thresholds.set(thresholds);
            SOLLimePieConfig.SERVER.benefitsUnparsed.set(rewards);
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(0);
            ConfigHandler.reloadServerConfig(List.of());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            BenefitsHandler.updateBenefits(player, 1024);
            var luck = Objects.requireNonNull(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK));
            helper.assertTrue(luck.getModifiers().size() == 1024 && BenefitsHandler.getBenefitInfo(1024, 0).getLeft().size() == 1024,
                "Reward tiers were truncated");
            var modifier = luck.getModifiers().iterator().next();
            BenefitsHandler.updateBenefits(player, 1024);
            helper.assertTrue(luck.getModifier(modifier.id()) == modifier, "Unchanged reward was replaced");
            luck.removeModifier(modifier.id());
            luck.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(modifier.id(), 0.5, modifier.operation()));
            BenefitsHandler.updateBenefits(player, 1024);
            helper.assertTrue(luck.getModifier(modifier.id()).amount() == 0.001, "External modifier change was missed");
            ConfigHandler.deserializeConfig(ConfigHandler.serializeConfig());
            helper.assertTrue(ConfigHandler.thresholds.size() == 1024 && ConfigHandler.getBenefitsList().size() == 1024,
                "Tier sync was truncated");
            BenefitsHandler.updateBenefits(player, -1);
            helper.assertTrue(luck.getModifiers().isEmpty(), "Large tier list failed to remove bonuses");
        }
        helper.succeed();
    }

    private static volatile double benchmarkScore;

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void highScaleBenchmark(GameTestHelper helper) {
        if (Boolean.getBoolean("sollimepie.benchmark")) try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var history = FoodList.get(player);
            var items = BuiltInRegistries.ITEM.stream().limit(1000).toList();
            // Registered non-food items stand in for a large modpack's distinct foods, with explicit unit weights.
            ConfigHandler.complexityMap = new HashMap<>();
            for (var item : BuiltInRegistries.ITEM) ConfigHandler.complexityMap.put(new FoodInstance(item), 1.0);
            var foods = BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item)
                .has(net.minecraft.core.component.DataComponents.FOOD) && !(item instanceof FoodContainerItem)).toList();
            var inventory = new ItemStackHandler(27);
            for (int slot = 0; slot < 27; slot++) inventory.setStackInSlot(slot, new ItemStack(foods.get(slot % foods.size())));
            SOLLimePieConfig.SERVER.whitelist.set(List.of());
            SOLLimePieConfig.SERVER.blacklist.set(List.of());
            SOLLimePieConfig.SERVER.startDecay.set(0);
            SOLLimePieConfig.SERVER.endDecay.set(1000);
            for (boolean permanent : new boolean[]{false, true}) for (int count : new int[]{32, 256, 1000}) {
                SOLLimePieConfig.SERVER.queueSize.set(permanent ? 0 : 1000);
                SOLLimePieConfig.SERVER.decayEnabled.set(!permanent);
                history.clearFood();
                for (var item : items.subList(0, Math.min(count, items.size()))) history.addFood(item);
                String mode = permanent ? "permanent-no-decay" : "finite-decay";
                measure(mode + "/score", count, () -> benchmarkScore = history.foodDiversity());
                measure(mode + "/selection-27", count, () -> benchmarkScore = FoodContainerItem.getBestFoodSlot(inventory, player));
                measure(mode + "/save-nbt", count, () -> benchmarkScore = history.serializeNBT().size());
                if (permanent) measure(mode + "/meal", count, () -> history.addFood(items.getFirst()));
            }
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(0);
            for (int count : new int[]{17, 256, 1024}) {
                BenefitsHandler.removeAllBenefits(player);
                var thresholds = new ArrayList<Double>();
                var rewards = new ArrayList<String>();
                for (int i = 0; i < count; i++) { thresholds.add((double) i); rewards.add("attribute,generic.luck,0.001"); }
                SOLLimePieConfig.SERVER.thresholds.set(thresholds);
                SOLLimePieConfig.SERVER.benefitsUnparsed.set(rewards);
                ConfigHandler.reloadServerConfig(List.of());
                measure("tiers/update", count, () -> BenefitsHandler.updateBenefits(player, 1024));
                measure("tiers/book-data", count, () -> benchmarkScore = BenefitsHandler.getBenefitInfo(1024, 0).getLeft().size());
            }
            BenefitsHandler.removeAllBenefits(player);
        }
        helper.succeed();
    }

    private static void measure(String operation, int count, Runnable action) {
        for (int i = 0; i < 100; i++) action.run();
        long[] samples = new long[5];
        for (int round = 0; round < samples.length; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < 200; i++) action.run();
            samples[round] = (System.nanoTime() - start) / 200;
        }
        Arrays.sort(samples);
        SOLLimePie.LOGGER.info("High-limit benchmark: operation={}, count={}, median={} ns/op", operation, count, samples[2]);
    }
}
