package com.jia.sollimepie.client;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.item.foodcontainer.FoodContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ContainerScreenRegistry {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, SOLLimePie.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<FoodContainer>> FOOD_CONTAINER =
        MENU_TYPES.register("food_container", () -> IMenuTypeExtension.create(FoodContainer::createClientMenu));
}
