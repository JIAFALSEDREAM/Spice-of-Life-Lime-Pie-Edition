package com.jia.sollimepie;

import com.jia.sollimepie.tracking.benefits.BenefitsHandler;
import com.jia.sollimepie.tracking.CapabilityHandler;
import com.jia.sollimepie.tracking.FoodList;
import com.jia.sollimepie.tracking.benefits.EffectBenefit;
import com.jia.sollimepie.tracking.benefits.EffectBenefitsCapability;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class EffectBenefitsGameTests {
    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void idleTicksRenewalsAndMilk(GameTestHelper helper) {
        try (var ignored = new Rules()) {
            TestPlayer player = player(helper);
            EffectBenefit benefit = new EffectBenefit("speed", 0, 0, false);
            benefit.applyTo(player);
            player.advance();
            MobEffectInstance initial = player.getEffect(MobEffects.MOVEMENT_SPEED);
            int reads = player.reads;
            player.advance();
            helper.assertTrue(player.reads == reads, "Idle tick scanned effects after its own Added event");
            helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED) == initial, "Idle tick replaced effect");
            for (int i = 2; i < 120; i++) player.advance();
            helper.assertTrue(player.attempts == 2, "Effect was not renewed once in 120 ticks");
            player.removeEffectsCuredBy(EffectCures.MILK);
            helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED) == null, "Milk did not clear effect");
            player.advance();
            helper.assertTrue(player.attempts == 3 && player.hasEffect(MobEffects.MOVEMENT_SPEED), "Milk was not repaired next tick");
            benefit.removeFrom(player);
            player.removeAllEffects();
            player.advance();
            helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED), "Revoked benefit was reapplied");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void strongerAndInfiniteEffectsAreNotReapplied(GameTestHelper helper) {
        try (var ignored = new Rules()) {
            TestPlayer player = player(helper);
            new EffectBenefit("speed", 0, 0, false).applyTo(player);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 3));
            player.attempts = 0;
            for (int i = 0; i < 9; i++) player.advance();
            helper.assertTrue(player.attempts == 0 && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 3,
                "Stronger short effect caused lower-level add attempts");
            player.advance();
            helper.assertTrue(player.attempts == 1 && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 0,
                "Reward did not resume when stronger effect expired");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 0));
            player.attempts = 0;
            for (int i = 0; i < 80; i++) player.advance();
            helper.assertTrue(player.attempts == 0 && player.getEffect(MobEffects.MOVEMENT_SPEED).isInfiniteDuration(),
                "Infinite effect was repeatedly refreshed");
            EffectBenefitsCapability.get(player).clear();
            player.advance();
            helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).isInfiniteDuration(), "Revocation deleted external effect");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void rejectedApplicationsBackOffAndCancelledRemovalIsSafe(GameTestHelper helper) {
        try (var ignored = new Rules()) {
            TestPlayer player = player(helper);
            Listener listener = new Listener(player);
            NeoForge.EVENT_BUS.register(listener);
            try {
                new EffectBenefit("speed", 0, 0, false).applyTo(player);
                for (int i = 0; i < 100; i++) {
                    // Repeated pre-mutation notifications must not bypass the retry deadline.
                    NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(player, null,
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300), null));
                    player.advance();
                }
                helper.assertTrue(player.attempts == 5, "Rejected effect retried more than once per second");
                listener.reject = false;
                player.advance();
                helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Retry did not recover after rejection ended");
                int attempts = player.attempts;
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
                player.advance();
                helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.attempts == attempts,
                    "Cancelled removal caused an unnecessary application");
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void differentialRewardsNbtAndSilentRemoval(GameTestHelper helper) {
        try (var ignored = new Rules(); var snapshot = new OptimizationGameTests.ConfigSnapshot()) {
            TestPlayer player = player(helper);
            SOLLimePieConfig.SERVER.thresholds.set(List.of(0.0, 10.0));
            SOLLimePieConfig.SERVER.benefitsUnparsed.set(List.of("effect,speed,0", "effect,speed,1"));
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(0);
            ConfigHandler.reloadServerConfig(List.of());
            BenefitsHandler.updateBenefits(player, 20);
            player.advance();
            int reads = player.reads;
            BenefitsHandler.updateBenefits(player, 20);
            player.advance();
            helper.assertTrue(player.reads == reads && player.attempts == 1, "Unchanged tiers invalidated runtime state");
            EffectBenefitsCapability effects = EffectBenefitsCapability.get(player);
            effects.deserializeNBT(effects.serializeNBT());
            player.advance();
            helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1 && player.attempts == 1,
                "NBT reload lost tier precedence or reapplied an unchanged effect");
            player.removeEffectNoUpdate(MobEffects.MOVEMENT_SPEED);
            for (int i = 0; i < 20; i++) player.advance();
            helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.attempts == 2,
                "Watchdog did not recover a removal without events");
            BenefitsHandler.updateBenefits(player, 0);
            helper.assertTrue(effects.iterator().next().getValue() == 0, "Tier downgrade retained stale desired reward");
            BenefitsHandler.updateBenefits(player, -1);
            helper.assertTrue(!effects.iterator().hasNext(), "Inactive rewards were retained");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void deathCloneRespectsHistoryReset(GameTestHelper helper) {
        try (var ignored = new Rules(); var snapshot = new OptimizationGameTests.ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.thresholds.set(List.of(0.0));
            SOLLimePieConfig.SERVER.benefitsUnparsed.set(List.of("effect,speed,0"));
            SOLLimePieConfig.SERVER.minFoodsToActivate.set(0);
            ConfigHandler.reloadServerConfig(List.of());
            TestPlayer original = player(helper), retained = player(helper), reset = player(helper);
            FoodList.get(original).addFood(Items.BREAD);
            SOLLimePieConfig.SERVER.shouldResetOnDeath.set(false);
            CapabilityHandler.onClone(new PlayerEvent.Clone(retained, original, true));
            retained.advance();
            helper.assertTrue(retained.hasEffect(MobEffects.MOVEMENT_SPEED), "Clone did not rebuild reward scheduling");
            SOLLimePieConfig.SERVER.shouldResetOnDeath.set(true);
            CapabilityHandler.onClone(new PlayerEvent.Clone(reset, original, true));
            reset.advance();
            helper.assertTrue(!reset.hasEffect(MobEffects.MOVEMENT_SPEED), "Reset clone retained old rewards");
        }
        helper.succeed();
    }

    private static TestPlayer player(GameTestHelper helper) { return new TestPlayer(helper.getLevel()); }

    private static final class Rules implements AutoCloseable {
        private final boolean survivalOnly = SOLLimePieConfig.limitProgressionToSurvival();
        private final boolean resetOnDeath = SOLLimePieConfig.shouldResetOnDeath();
        Rules() { SOLLimePieConfig.SERVER.limitProgressionToSurvival.set(false); }
        @Override public void close() {
            SOLLimePieConfig.SERVER.limitProgressionToSurvival.set(survivalOnly);
            SOLLimePieConfig.SERVER.shouldResetOnDeath.set(resetOnDeath);
        }
    }

    private static final class TestPlayer extends FakePlayer {
        int reads, attempts;
        TestPlayer(ServerLevel level) { super(level, new GameProfile(new UUID(0, 0), "effect-test")); }
        void advance() {
            tickCount++;
            tickEffects();
            BenefitsHandler.tickBenefits(new PlayerTickEvent.Post(this));
        }
        @Override public MobEffectInstance getEffect(Holder<MobEffect> effect) { reads++; return super.getEffect(effect); }
        @Override public boolean addEffect(MobEffectInstance effect, Entity source) { attempts++; return super.addEffect(effect, source); }
    }

    public static final class Listener {
        private final TestPlayer player;
        boolean reject = true;
        Listener(TestPlayer player) { this.player = player; }
        @SubscribeEvent public void applicable(MobEffectEvent.Applicable event) {
            if (event.getEntity() == player && reject) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
        @SubscribeEvent public void remove(MobEffectEvent.Remove event) {
            if (event.getEntity() == player) event.setCanceled(true);
        }
    }
}
