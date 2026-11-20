package com.jia.sollimepie.utils;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.SOLLimePieConfig;
import com.jia.sollimepie.tracking.FoodInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ResourceLocationException;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ComplexityParser {
    public static Map<FoodInstance, Double> parse(List<String> unparsed) {
        return parse(unparsed, warning -> SOLLimePie.LOGGER.warn(warning));
    }

    public static Map<FoodInstance, Double> parse(List<String> unparsed, Consumer<String> warnings) {
        Map<FoodInstance, Double> complexityMap = new HashMap<>();

        for (String complexityString : unparsed) {
            String[] s = complexityString.split(",", 0);
            if (s.length != 2) {
                warnings.accept("Invalid complexity specification: " + complexityString);
                continue;
            }

            String foodString = s[0];
            double complexity;
            try {
                complexity = Double.parseDouble(s[1]);
            }
            catch (NumberFormatException e) {
                warnings.accept("Second argument in complexity specification needs to be a number: " + complexityString);
                continue;
            }
            if (!Double.isFinite(complexity) || complexity < 0) {
                warnings.accept("Complexity must be finite and nonnegative: " + complexityString);
                continue;
            }

            ResourceLocation itemId;
            try {
                itemId = ResourceLocation.parse(foodString);
            }
            catch (ResourceLocationException e) {
                warnings.accept("Invalid item name: " + foodString);
                continue;
            }
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                if (!SOLLimePieConfig.Server.OPTIONAL_COMPLEXITY_PRESETS.contains(complexityString)) {
                    warnings.accept("Unknown registered item: " + foodString);
                }
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);

            if (!new net.minecraft.world.item.ItemStack(item).has(net.minecraft.core.component.DataComponents.FOOD)) {
                warnings.accept("Item is not food: " + foodString);
                continue;
            }

            complexityMap.put(new FoodInstance(item), complexity);
        }
        return complexityMap;
    }
}
