package com.jia.sollimepie;

import com.jia.sollimepie.tracking.FoodInstance;
import com.jia.sollimepie.tracking.FoodList;
import com.jia.sollimepie.tracking.benefits.BenefitList;
import com.jia.sollimepie.tracking.benefits.BenefitsHandler;
import com.jia.sollimepie.tracking.benefits.EffectBenefitsCapability;
import com.jia.sollimepie.communication.ConfigMessage;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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
