package com.jia.sollimepie;

import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.SOLLimePieItems;
import com.jia.sollimepie.tracking.FoodInstance;
import com.jia.sollimepie.tracking.FoodList;
import com.jia.sollimepie.tracking.benefits.AttributeBenefit;
import com.jia.sollimepie.tracking.benefits.BenefitList;
import com.jia.sollimepie.tracking.benefits.BenefitsHandler;
import com.jia.sollimepie.tracking.benefits.EffectBenefitsCapability;
import com.jia.sollimepie.communication.FoodListMessage;
import com.jia.sollimepie.communication.ConfigMessage;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class OptimizationGameTests {
    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void configPacketRefreshesClientCalculationRules(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.queueSize.set(16);
            SOLLimePieConfig.SERVER.startDecay.set(4);
            SOLLimePieConfig.SERVER.endDecay.set(12);
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(3);
            SOLLimePieConfig.SERVER.minContribution.set(0.2);
            SOLLimePieConfig.SERVER.defaultContribution.set(2.0);
            SOLLimePieConfig.SERVER.shouldForbiddenCount.set(false);
            SOLLimePieConfig.SERVER.whitelist.set(List.of("minecraft:bread"));
            SOLLimePieConfig.SERVER.blacklist.set(List.of("minecraft:c*"));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ConfigMessage.STREAM_CODEC.encode(buffer, new ConfigMessage());
                SOLLimePieConfig.SERVER.queueSize.set(32);
                SOLLimePieConfig.SERVER.startDecay.set(0);
                SOLLimePieConfig.SERVER.endDecay.set(32);
                SOLLimePieConfig.SERVER.minFoodsToActivate.set(0);
                SOLLimePieConfig.SERVER.minContribution.set(0.0);
                SOLLimePieConfig.SERVER.defaultContribution.set(1.0);
                SOLLimePieConfig.SERVER.shouldForbiddenCount.set(true);
                SOLLimePieConfig.SERVER.whitelist.set(List.of("minecraft:carrot"));
                SOLLimePieConfig.SERVER.blacklist.set(List.of());
                ConfigMessage delayed = ConfigMessage.STREAM_CODEC.decode(buffer);
                Connection local = new Connection(PacketFlow.CLIENTBOUND) {
                    @Override public boolean isMemoryConnection() { return true; }
                };
                IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                    new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                        if (method.getName().equals("connection")) return local;
                        throw new AssertionError("Local config packet must not enqueue or apply work: " + method.getName());
                    });
                var authoritativeMap = ConfigHandler.complexityMap;
                ConfigMessage.handle(delayed, context);
                helper.assertTrue(SOLLimePieConfig.size() == 32 && SOLLimePieConfig.defaultContribution() == 1
                    && ConfigHandler.complexityMap == authoritativeMap, "Delayed local packet overwrote server config");
                ConfigHandler.deserializeConfig(delayed.tag());
            } finally {
                buffer.release();
            }
            helper.assertTrue(SOLLimePieConfig.size() == 16 && SOLLimePieConfig.startDecay() == 4 && SOLLimePieConfig.endDecay() == 12
                && SOLLimePieConfig.minFoodsToActivate() == 3 && SOLLimePieConfig.minContribution() == 0.2
                && SOLLimePieConfig.defaultContribution() == 2 && !SOLLimePieConfig.shouldForbiddenCount(), "Client calculation rules stayed stale");
            helper.assertTrue(SOLLimePieConfig.isAllowed(Items.BREAD) && !SOLLimePieConfig.isAllowed(Items.CARROT)
                && SOLLimePieConfig.getBlacklist().equals(List.of("minecraft:c*")), "Client filtering rules stayed stale");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void lunchbagConsumptionTracksFoodOnServer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "sollimepie-test"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        // The embedded connection has no client handshake; advertise this mod's two play payloads before login.
        ChannelAttributes.getOrCreateAdHocChannels(connection).add(FoodListMessage.TYPE.id());
        ChannelAttributes.getOrCreateAdHocChannels(connection).add(ConfigMessage.TYPE.id());
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        try {
            FoodList.get(player).clearFood();
            FoodList.get(player).resetFoodsEaten();
            player.getFoodData().setFoodLevel(5);
            ItemStack bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
            var inventory = Objects.requireNonNull(FoodContainerItem.getInventory(bag));
            inventory.setStackInSlot(0, new ItemStack(Items.BREAD, 3));
            bag.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(player.getFoodData().getFoodLevel() == 10, "Lunchbag did not feed the player");
            helper.assertTrue(FoodList.get(player).hasEaten(Items.BREAD) && FoodList.get(player).getFoodsEaten() == 1,
                "Consumption was not tracked exactly once");
            helper.assertTrue(Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0).getCount() == 2,
                "Consumed food did not persist");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
            player.discard();
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void reloadUpdatesBenefitsWithoutHealing(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.thresholds.set(List.of(0.0));
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(0);
            SOLLimePieConfig.SERVER.benefitsUnparsed.set(List.of("attribute,generic.max_health,10;effect,speed,0"));
            ConfigHandler.reloadServerConfig(List.of());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            FoodList.get(player).addFood(Items.BREAD);
            BenefitsHandler.updatePlayer(player);
            player.setHealth(15);
            helper.assertTrue(player.getMaxHealth() == 30, "Initial health benefit missing");
            SOLLimePieConfig.SERVER.benefitsUnparsed.set(List.of("attribute,generic.max_health,20"));
            SOLLimePieConfig.SERVER.complexityUnparsed.set(List.of("minecraft:bread,7"));
            ConfigHandler.reloadServerConfig(List.of(player));
            helper.assertTrue(player.getMaxHealth() == 40 && player.getHealth() == 15,
                "Reload retained an old modifier or healed the player");
            helper.assertTrue(!EffectBenefitsCapability.get(player).iterator().hasNext(), "Old effect still being refreshed");
            helper.assertTrue(FoodList.get(player).foodDiversity() == 7 && FoodList.get(player).getFoodsEaten() == 1,
                "Complexity did not reload or history changed");
            helper.assertTrue(ConfigHandler.serializeConfig().getCompound(ConfigHandler.COMPLEXITY_MAP_KEY)
                .getList(ConfigHandler.ENTRY_KEY, 10).getCompound(0).getDouble(ConfigHandler.COMPLEXITY_VALUE_KEY) == 7,
                "Client config payload still contains old complexity");
            ConfigHandler.reloadServerConfig(List.of(player));
            helper.assertTrue(player.getHealth() == 15 && player.getMaxHealth() == 40, "Repeated reload changed health");
            player.setHealth(35);
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(100);
            ConfigHandler.reloadServerConfig(List.of(player));
            helper.assertTrue(player.getMaxHealth() == 20 && player.getHealth() == 20,
                "Removing benefits did not clamp health to the new maximum");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void repeatedAttributeApplicationIsStable(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AttributeBenefit benefit = new AttributeBenefit("generic.max_health", 10, 0, false);
        benefit.applyTo(player);
        player.setHealth(15);
        var attribute = Objects.requireNonNull(player.getAttribute(Attributes.MAX_HEALTH));
        var modifier = attribute.getModifiers().iterator().next();
        benefit.applyTo(player);
        helper.assertTrue(player.getMaxHealth() == 30 && player.getHealth() == 15, "Repeated application changed health");
        helper.assertTrue(attribute.getModifier(modifier.id()) == modifier, "Identical modifier was replaced");
        attribute.removeModifier(modifier.id());
        attribute.addPermanentModifier(new AttributeModifier(modifier.id(), 5, modifier.operation()));
        benefit.applyTo(player);
        helper.assertTrue(attribute.getModifier(modifier.id()).amount() == 10, "Changed modifier was incorrectly skipped");
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void filteringRefreshesWithoutChangingGlobRules(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.whitelist.set(List.of());
            SOLLimePieConfig.SERVER.blacklist.set(List.of("minecraft:b*"));
            helper.assertTrue(!SOLLimePieConfig.isAllowed(Items.BREAD) && SOLLimePieConfig.isAllowed(Items.CARROT), "Blacklist glob failed");
            SOLLimePieConfig.SERVER.whitelist.set(List.of("minecraft:bread"));
            helper.assertTrue(SOLLimePieConfig.isAllowed(Items.BREAD) && !SOLLimePieConfig.isAllowed(Items.CARROT), "Whitelist precedence failed");
            SOLLimePieConfig.SERVER.whitelist.set(List.of("minecraft:carrot"));
            helper.assertTrue(!SOLLimePieConfig.isAllowed(Items.BREAD) && SOLLimePieConfig.isAllowed(Items.CARROT), "Changed rules stayed cached");
            for (String glob : List.of("", "*", "mine*:*", "minecraft:b?ead", "minecraft:bread", "minecraft:bread*", "minecraft:br**d")) {
                SOLLimePieConfig.SERVER.whitelist.set(List.of(glob));
                boolean expected = List.of("*", "mine*:*", "minecraft:bread", "minecraft:bread*", "minecraft:br**d").contains(glob);
                helper.assertTrue(SOLLimePieConfig.isAllowed(Items.BREAD) == expected, "Glob meaning changed: " + glob);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void cachedSelectionMatchesOriginalArithmetic(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            List<Item> foods = BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item)
                .has(net.minecraft.core.component.DataComponents.FOOD) && !(item instanceof FoodContainerItem)).toList();
            Random random = new Random(61821);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            FoodList history = FoodList.get(player);
            ItemStackHandler inventory = new ItemStackHandler(27);
            SOLLimePieConfig.SERVER.whitelist.set(List.of());
            for (int scenario = 0; scenario < 40; scenario++) {
                int size = scenario % 3 == 0 ? 1 : scenario % 3 == 1 ? 32 : 1000;
                SOLLimePieConfig.SERVER.queueSize.set(size);
                SOLLimePieConfig.SERVER.startDecay.set(scenario % 2 == 0 ? 0 : size / 2);
                SOLLimePieConfig.SERVER.endDecay.set(size);
                SOLLimePieConfig.SERVER.minContribution.set(scenario % 2 == 0 ? 0.0 : 0.4);
                SOLLimePieConfig.SERVER.blacklist.set(scenario % 2 == 0 ? List.of() : List.of("minecraft:b*"));
                SOLLimePieConfig.SERVER.shouldForbiddenCount.set(scenario % 3 != 0);
                history.clearFood();
                for (int meal = 0; meal < 100; meal++) history.addFood(foods.get(random.nextInt(foods.size())));
                var simulator = history.foodSimulator();
                for (Item food : foods) {
                    helper.assertTrue(Double.doubleToLongBits(simulator.applyAsDouble(food)) == Double.doubleToLongBits(originalChange(history, food)),
                        "Simulation changed for " + BuiltInRegistries.ITEM.getKey(food));
                }
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    inventory.setStackInSlot(slot, slot % 7 == 0 ? ItemStack.EMPTY : new ItemStack(foods.get(random.nextInt(foods.size()))));
                }
                helper.assertTrue(FoodContainerItem.getBestFoodSlot(inventory, player) == originalBestSlot(inventory, history),
                    "Selection or tie order changed");
            }
            SOLLimePieConfig.SERVER.blacklist.set(List.of());
            for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, new ItemStack(Items.BREAD));
            helper.assertTrue(FoodContainerItem.getBestFoodSlot(inventory, player) == 0, "Equal foods must select the first slot");
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(FoodContainerItem.getBestFoodSlot(inventory, player) == 1, "Selection changed with an empty first slot");
            if (Boolean.getBoolean("sollimepie.benchmark")) benchmarkSelection(player, foods, inventory);
        }
        helper.succeed();
    }

    // The pre-optimization algorithm is the oracle, including its floating-point addition order.
    static double originalChange(FoodList history, Item food) {
        if (!SOLLimePieConfig.shouldCount(food) && !SOLLimePieConfig.shouldForbiddenCount()) return 0;
        double change = 0;
        for (var entry : history.getData()) {
            double before = FoodList.calculateDiversityContribution(entry.getKey(), entry.getValue());
            int age = entry.getValue() + 1;
            if (entry.getKey().getItem().equals(food) || age >= SOLLimePieConfig.size()) change -= before;
            else change += FoodList.calculateDiversityContribution(entry.getKey(), age) - before;
        }
        if (SOLLimePieConfig.shouldCount(food)) change += FoodList.calculateDiversityContribution(new FoodInstance(food), 0);
        return change;
    }

    static int originalBestSlot(ItemStackHandler inventory, FoodList history) {
        double best = -Double.MAX_VALUE;
        int slot = -1;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack food = inventory.getStackInSlot(i);
            if (food.isEmpty() || !food.has(net.minecraft.core.component.DataComponents.FOOD)) continue;
            double change = originalChange(history, food.getItem());
            if (change > best) { best = change; slot = i; }
        }
        return slot;
    }

    private static volatile int benchmarkSink;

    private static void benchmarkSelection(Player player, List<Item> foods, ItemStackHandler inventory) {
        SOLLimePieConfig.SERVER.queueSize.set(32);
        SOLLimePieConfig.SERVER.endDecay.set(32);
        SOLLimePieConfig.SERVER.startDecay.set(0);
        SOLLimePieConfig.SERVER.minContribution.set(0.0);
        FoodList history = FoodList.get(player);
        history.clearFood();
        for (Item food : foods) history.addFood(food);
        for (int distinct : new int[]{1, 5, 27}) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, new ItemStack(foods.get(slot % distinct)));
            for (int warmup = 0; warmup < 800; warmup++) {
                benchmarkSink = originalBestSlot(inventory, history);
                benchmarkSink = FoodContainerItem.getBestFoodSlot(inventory, player);
            }
            long[] original = new long[5], cached = new long[5];
            for (int round = 0; round < 5; round++) {
                long start = System.nanoTime();
                for (int i = 0; i < 1200; i++) benchmarkSink = originalBestSlot(inventory, history);
                original[round] = System.nanoTime() - start;
                start = System.nanoTime();
                for (int i = 0; i < 1200; i++) benchmarkSink = FoodContainerItem.getBestFoodSlot(inventory, player);
                cached[round] = System.nanoTime() - start;
            }
            Arrays.sort(original);
            Arrays.sort(cached);
            SOLLimePie.LOGGER.info("Selection benchmark: history={}, slots=27, distinct={}, original={} ns/op, cached={} ns/op",
                history.getData().size(), distinct, original[2] / 1200, cached[2] / 1200);
        }
    }

    private static final class ConfigSnapshot implements AutoCloseable {
        private final List<? extends String> whitelist = SOLLimePieConfig.SERVER.whitelist.get();
        private final List<? extends String> blacklist = SOLLimePieConfig.SERVER.blacklist.get();
        private final List<? extends String> benefits = SOLLimePieConfig.SERVER.benefitsUnparsed.get();
        private final List<? extends String> complexity = SOLLimePieConfig.SERVER.complexityUnparsed.get();
        private final List<? extends Double> thresholds = SOLLimePieConfig.SERVER.thresholds.get();
        private final int minimum = SOLLimePieConfig.minFoodsToActivate(), size = SOLLimePieConfig.size();
        private final int start = SOLLimePieConfig.startDecay(), end = SOLLimePieConfig.endDecay();
        private final double contribution = SOLLimePieConfig.minContribution();
        private final double defaultContribution = SOLLimePieConfig.defaultContribution();
        private final boolean forbidden = SOLLimePieConfig.shouldForbiddenCount();
        private final Map<FoodInstance, Double> parsedComplexity = ConfigHandler.complexityMap;
        private final List<Double> parsedThresholds = ConfigHandler.thresholds;
        private final BenefitList parsedBenefits = ConfigHandler.benefitsList;

        @Override
        public void close() {
            SOLLimePieConfig.SERVER.whitelist.set(whitelist);
            SOLLimePieConfig.SERVER.blacklist.set(blacklist);
            SOLLimePieConfig.SERVER.benefitsUnparsed.set(benefits);
            SOLLimePieConfig.SERVER.complexityUnparsed.set(complexity);
            SOLLimePieConfig.SERVER.thresholds.set(thresholds);
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(minimum);
            SOLLimePieConfig.SERVER.queueSize.set(size);
            SOLLimePieConfig.SERVER.startDecay.set(start);
            SOLLimePieConfig.SERVER.endDecay.set(end);
            SOLLimePieConfig.SERVER.minContribution.set(contribution);
            SOLLimePieConfig.SERVER.defaultContribution.set(defaultContribution);
            SOLLimePieConfig.SERVER.shouldForbiddenCount.set(forbidden);
            ConfigHandler.complexityMap = parsedComplexity;
            ConfigHandler.thresholds = parsedThresholds;
            ConfigHandler.benefitsList = parsedBenefits;
        }
    }
}
