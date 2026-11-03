package com.jia.sollimepie;

import com.jia.sollimepie.item.SOLLimePieItems;
import com.jia.sollimepie.item.foodcontainer.FoodContainer;
import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.item.foodcontainer.FoodSlot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
            player.setItemInHand(InteractionHand.MAIN_HAND, bag);
            player.getInventory().setItem(9, new ItemStack(Items.BOWL, 3));
            var menu = new FoodContainer(1, player.getInventory(), player);
            helper.assertTrue(menu.quickMoveStack(player, menu.nslots).is(Items.BOWL), "Shift insertion failed");
            helper.assertTrue(Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0).getCount() == 3,
                "Shift insertion did not persist");
            SOLLimePieConfig.SERVER.lunchboxExtraItems.set(List.of());
            helper.assertTrue(menu.quickMoveStack(player, 0).is(Items.BOWL), "Removed extra could not be shift-extracted");
            helper.assertTrue(menu.quickMoveStack(player, menu.nslots).isEmpty(), "Removed extra could be shift-inserted");
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
            bag.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(Objects.requireNonNull(FoodContainerItem.getInventory(bag)).getStackInSlot(0).isEmpty(),
                "Disallowed remainder stayed inside");
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.BOWL)), "Disallowed remainder was lost");
        }
        helper.succeed();
    }
}
