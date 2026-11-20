package com.jia.sollimepie;

import com.jia.sollimepie.client.ContainerScreenRegistry;
import com.jia.sollimepie.communication.FoodContainerMealMessage;
import com.jia.sollimepie.item.SOLLimePieItems;
import com.jia.sollimepie.item.foodcontainer.FoodContainer;
import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodContainerProvider;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import com.jia.sollimepie.item.foodcontainer.FoodSlot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.List;
import java.util.Objects;

@PrefixGameTestTemplate(false)
public final class LunchboxGameTests {
    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void defaultExtrasPersistInEveryContainer(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            var defaults = SOLLimePieConfig.SERVER.lunchboxExtraItems.getDefault();
            helper.assertTrue(defaults.equals(List.of("minecraft:bowl", "minecraft:glass_bottle", "sollimepie:food_book")),
                "Generated config must include the three default extra items");
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(defaults);
            for (Item container : List.of(SOLLimePieItems.LUNCHBAG.get(), SOLLimePieItems.LUNCHBOX.get(), SOLLimePieItems.GOLDEN_LUNCHBOX.get())) {
                ItemStack bag = new ItemStack(container);
                var inventory = Objects.requireNonNull(FoodContainerItem.getInventory(bag));
                Item[] extras = {Items.BOWL, Items.GLASS_BOTTLE, SOLLimePieItems.FOOD_BOOK.get()};
                for (int slot = 0; slot < extras.length; slot++) {
                    ItemStack extra = new ItemStack(extras[slot]);
                    helper.assertTrue(new FoodSlot(inventory, slot, 0, 0).mayPlace(extra), "Menu rejected a default extra item");
                    helper.assertTrue(inventory.insertItem(slot, extra, false).isEmpty(), "Item handler rejected a default extra item");
                }
                var reopened = Objects.requireNonNull(FoodContainerItem.getInventory(bag));
                for (int slot = 0; slot < extras.length; slot++) {
                    helper.assertTrue(reopened.getStackInSlot(slot).is(extras[slot]), "Extra item lost on reopening");
                }
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, bag);
                player.getFoodData().setFoodLevel(5);
                helper.assertTrue(FoodContainerItem.getBestFoodSlot(reopened, player) == -1, "Non-food selected for eating");
                helper.assertTrue(container.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
                    "A container with only extras started eating");
                reopened.insertItem(3, new ItemStack(Items.BREAD), false);
                helper.assertTrue(FoodContainerItem.getBestFoodSlot(reopened, player) == 3, "Extras interfered with food selection");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void invalidIdsAndNestedContainersAreSafe(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of("missing_mod:no_such_item", "INVALID ID!", "", "minecraft:*",
                "#minecraft:logs", "minecraft:air", "sollimepie:lunchbag", "sollimepie:lunchbox", "sollimepie:golden_lunchbox", "minecraft:stick"));
            var bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
            var inventory = Objects.requireNonNull(FoodContainerItem.getInventory(bag));
            for (Item item : List.of(Items.BOWL, Items.STONE, SOLLimePieItems.LUNCHBAG.get(), SOLLimePieItems.LUNCHBOX.get(),
                SOLLimePieItems.GOLDEN_LUNCHBOX.get())) {
                var stack = new ItemStack(item);
                helper.assertTrue(!FoodSlot.canHold(stack) && !inventory.insertItem(0, stack, false).isEmpty(),
                    "Invalid IDs or nested container bypassed filtering");
            }
            helper.assertTrue(!FoodSlot.canHold(ItemStack.EMPTY), "Empty stack matched an extra item ID");
            helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.STICK), false).isEmpty(), "Custom extra item rejected");
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of());
            helper.assertTrue(!FoodSlot.canHold(new ItemStack(Items.STICK)), "Removed extra item still accepted");
            helper.assertTrue(inventory.extractItem(0, 1, false).is(Items.STICK), "Removed extra item could not be extracted");
            helper.assertTrue(inventory.insertItem(1, new ItemStack(Items.BREAD), false).isEmpty(), "Empty extras list disabled food");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void missingSyncFieldKeepsLunchboxRules(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of("minecraft:stick", "missing_mod:cup", "INVALID ID!"));
            var rules = SOLLimePieConfig.serializeCalculationRules();
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of());
            SOLLimePieConfig.applyCalculationRules(rules);
            helper.assertTrue(FoodSlot.canHold(new ItemStack(Items.STICK)), "Custom rules did not survive sync");
            rules.remove("lunchboxExtraItems");
            SOLLimePieConfig.applyCalculationRules(rules);
            helper.assertTrue(FoodSlot.canHold(new ItemStack(Items.STICK)), "An older packet erased lunchbox rules");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void quickMoveRespectsReloadAndAllowsRemoval(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of("minecraft:bowl"));
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SOLLimePieItems.LUNCHBOX.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, bag);
            player.getInventory().setItem(9, new ItemStack(Items.BOWL, 3));
            var provider = new FoodContainerProvider("lunchbag", player.getItemInHand(InteractionHand.OFF_HAND));
            var menu = Objects.requireNonNull(provider.createMenu(1, player.getInventory(), player));
            helper.assertTrue(menu.getStorageSlotCount() == 5, "Menu selected the container from the other hand");
            helper.assertTrue(menu.quickMoveStack(player, menu.getStorageSlotCount()).is(Items.BOWL), "Shift insertion failed");
            helper.assertTrue(Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0).getCount() == 3,
                "Shift insertion did not persist");
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of());
            helper.assertTrue(menu.quickMoveStack(player, 0).is(Items.BOWL), "Removed extra could not be shift-extracted");
            helper.assertTrue(menu.quickMoveStack(player, menu.getStorageSlotCount()).isEmpty(), "Removed extra could be shift-inserted");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void menuUsesServerCapacityAfterHotbarSwitch(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var golden = new ItemStack(SOLLimePieItems.GOLDEN_LUNCHBOX.get());
        var lunchbox = new ItemStack(SOLLimePieItems.LUNCHBOX.get());
        Objects.requireNonNull(FoodContainerItem.getInventory(golden)).setStackInSlot(13, new ItemStack(Items.APPLE));
        player.getInventory().setItem(0, golden);
        player.getInventory().setItem(1, lunchbox);
        var provider = new FoodContainerProvider("golden_lunchbox", player.getItemInHand(InteractionHand.MAIN_HAND));
        var serverMenu = Objects.requireNonNull(provider.createMenu(1, player.getInventory(), player));

        var viewer = helper.makeMockPlayer(GameType.SURVIVAL);
        viewer.getInventory().setItem(0, golden.copy());
        viewer.getInventory().setItem(1, lunchbox.copy());
        viewer.getInventory().selected = 1;

        var data = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            provider.writeClientSideData(serverMenu, data);
            var clientMenu = ContainerScreenRegistry.FOOD_CONTAINER.get().create(1, viewer.getInventory(), data);
            clientMenu.initializeContents(0, serverMenu.getItems().stream().map(ItemStack::copy).toList(), ItemStack.EMPTY);
            helper.assertTrue(clientMenu.getStorageSlotCount() == 14 && clientMenu.slots.size() == 14 + FoodContainer.PLAYERSIZE,
                "Client menu did not use the server's slot layout");
            helper.assertTrue(clientMenu.getSlot(13).getItem().is(Items.APPLE), "Client did not sync the last slot");
            clientMenu.getSlot(13).set(new ItemStack(Items.BREAD));
            var viewerGolden = Objects.requireNonNull(FoodContainerItem.getInventory(viewer.getInventory().getItem(0)));
            helper.assertTrue(viewerGolden.getStackInSlot(13).is(Items.APPLE), "Client menu changed the local container item");
        } finally {
            data.release();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void mealRemaindersStayInsideWithFullInventory(GameTestHelper helper) {
        try (var ignored = new OptimizationGameTests.ConfigSnapshot()) {
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(SOLLimePieConfig.SERVER.lunchboxExtraItems.getDefault());
            for (Item food : List.of(Items.BEETROOT_SOUP, Items.HONEY_BOTTLE)) {
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                for (int i = 0; i < player.getInventory().items.size(); i++) {
                    player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
                }
                var bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
                Objects.requireNonNull(FoodContainerItem.getInventory(bag)).insertItem(0, new ItemStack(food), false);
                player.getFoodData().setFoodLevel(5);
                player.setItemInHand(InteractionHand.OFF_HAND, bag);
                bag.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
                bag.finishUsingItem(helper.getLevel(), player);
                Item remainder = food == Items.HONEY_BOTTLE ? Items.GLASS_BOTTLE : Items.BOWL;
                helper.assertTrue(Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0).is(remainder),
                    "Allowed remainder did not stay in the container");
                helper.assertTrue(player.getInventory().getItem(0).is(Items.STONE), "Remainder modified a full inventory");
            }
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
            Objects.requireNonNull(FoodContainerItem.getInventory(bag)).insertItem(0, new ItemStack(Items.BEETROOT_SOUP), false);
            player.getFoodData().setFoodLevel(5);
            player.setItemInHand(InteractionHand.MAIN_HAND, bag);
            bag.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            bag.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0).isEmpty(),
                "Disallowed remainder stayed inside");
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.BOWL)), "Disallowed remainder was lost");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void mealSelectionSurvivesRepeatUseAndRejectsReplacement(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
        var hand = InteractionHand.MAIN_HAND;
        player.getFoodData().setFoodLevel(5);
        player.setItemInHand(hand, bag);
        Objects.requireNonNull(FoodContainerItem.getInventory(bag)).setStackInSlot(1, new ItemStack(Items.BREAD));

        bag.use(helper.getLevel(), player, hand);
        helper.assertTrue(player.isUsingItem(), "Starting use did not start a meal");

        var inventory = Objects.requireNonNull(FoodContainerItem.getInventory(bag));
        inventory.setStackInSlot(1, ItemStack.EMPTY);
        inventory.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        bag.use(helper.getLevel(), player, hand);
        bag.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 5, "Repeated use substituted food from another slot");
        player.releaseUsingItem();

        bag.use(helper.getLevel(), player, hand);
        var renamedApple = new ItemStack(Items.APPLE, 2);
        renamedApple.set(DataComponents.CUSTOM_NAME, Component.literal("Replacement"));
        Objects.requireNonNull(FoodContainerItem.getInventory(bag)).setStackInSlot(0, renamedApple);
        bag.finishUsingItem(helper.getLevel(), player);
        var retained = Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 5 && ItemStack.matches(retained, renamedApple),
            "Replacing the selected food with changed components still allowed consumption");
        player.releaseUsingItem();

        bag.use(helper.getLevel(), player, hand);
        var replacement = bag.copy();
        player.setItemInHand(hand, replacement);
        bag.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 5, "Replacing the container still allowed consumption");
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void mealAppearanceTracksAttemptAcrossInventorySync(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var bag = new ItemStack(SOLLimePieItems.LUNCHBAG.get());
        var hand = InteractionHand.MAIN_HAND;
        player.setItemInHand(hand, bag);

        FoodContainerUse.rememberRequest(player, hand, bag, 10);
        FoodContainerUse.rememberRequest(player, hand, bag, 11);
        var bread = new ItemStack(Items.BREAD);
        FoodContainerUse.acceptAppearance(player, new FoodContainerMealMessage(player.getId(), 10, hand, bread));
        helper.assertTrue(FoodContainerUse.particleFood(player).isEmpty(), "Confirmation produced particles before use started");
        player.startUsingItem(hand);
        helper.assertTrue(FoodContainerUse.particleFood(player).is(Items.BREAD), "Repeated input discarded the first accepted request");

        var syncedBag = bag.copy();
        player.setItemInHand(hand, syncedBag);
        syncedBag.onUseTick(helper.getLevel(), player, 20);
        helper.assertTrue(player.isUsingItem() && FoodContainerUse.particleFood(player).is(Items.BREAD),
            "A same-slot inventory update stopped the animation or changed the food appearance");
        FoodContainerUse.rememberRequest(player, hand, syncedBag, 12);
        FoodContainerUse.acknowledgeRequests(player, 11);
        player.stopUsingItem();
        FoodContainerUse.acceptAppearance(player, new FoodContainerMealMessage(player.getId(), 11, hand, bread));
        player.startUsingItem(hand);
        helper.assertTrue(FoodContainerUse.particleFood(player).isEmpty(), "An old confirmation changed the new meal's appearance");
        FoodContainerUse.acceptAppearance(player, new FoodContainerMealMessage(player.getId(), 12, hand, new ItemStack(Items.CARROT)));
        helper.assertTrue(FoodContainerUse.particleFood(player).is(Items.CARROT), "A stop for the previous animation cleared the pending request");

        FoodContainerUse.rememberRequest(player, hand, syncedBag, 13);
        FoodContainerUse.cancelRequests(player, null);
        FoodContainerUse.acceptAppearance(player, new FoodContainerMealMessage(player.getId(), 13, hand, bread));
        helper.assertTrue(FoodContainerUse.particleFood(player).is(Items.CARROT), "A canceled request changed the meal appearance");

        player.getInventory().setItem(1, syncedBag.copy());
        player.getInventory().selected = 1;
        syncedBag.onUseTick(helper.getLevel(), player, 20);
        helper.assertTrue(!player.isUsingItem() && FoodContainerUse.particleFood(player).isEmpty(),
            "Switching to another hotbar slot kept the previous meal active");

        var offHand = InteractionHand.OFF_HAND;
        player.setItemInHand(offHand, syncedBag.copy());
        FoodContainerUse.rememberRequest(player, hand, player.getMainHandItem(), 14);
        FoodContainerUse.rememberRequest(player, offHand, player.getOffhandItem(), 15);
        player.getInventory().selected = 0;
        FoodContainerUse.cancelRequests(player, hand);
        player.getInventory().selected = 1;
        player.startUsingItem(hand);
        FoodContainerUse.acceptAppearance(player, new FoodContainerMealMessage(player.getId(), 14, hand, bread));
        helper.assertTrue(FoodContainerUse.particleFood(player).isEmpty(), "Returning to the slot restored a canceled request");
        player.stopUsingItem();
        player.startUsingItem(offHand);
        FoodContainerUse.acceptAppearance(player, new FoodContainerMealMessage(player.getId(), 15, offHand, bread));
        helper.assertTrue(FoodContainerUse.particleFood(player).is(Items.BREAD), "Changing hotbar slots canceled an off-hand request");
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void observersSeeOnlyTheCurrentMealAppearance(GameTestHelper helper) {
        var observer = helper.makeMockPlayer(GameType.SURVIVAL);
        var eater = helper.makeMockPlayer(GameType.SURVIVAL);
        var hand = InteractionHand.MAIN_HAND;
        eater.setItemInHand(hand, new ItemStack(SOLLimePieItems.LUNCHBAG.get()));
        helper.assertTrue(helper.getLevel().addFreshEntity(eater), "Could not add the observed player");
        try {
            eater.startUsingItem(hand);
            var message = new FoodContainerMealMessage(eater.getId(), 10, hand, new ItemStack(Items.APPLE));
            FoodContainerUse.acceptAppearance(observer, message);
            helper.assertTrue(FoodContainerUse.particleFood(eater).is(Items.APPLE), "A newly observing player did not get the current food");
            helper.assertTrue(FoodContainerUse.particleFood(observer).isEmpty(), "Remote food was applied to the observing player");

            eater.stopUsingItem();
            eater.startUsingItem(hand);
            helper.assertTrue(FoodContainerUse.particleFood(eater).isEmpty(), "A new animation restored the previous observed food");
        } finally {
            eater.discard();
        }
        helper.succeed();
    }
}
