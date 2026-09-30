package com.jia.sollimepie;

import com.jia.sollimepie.client.ContainerScreenRegistry;
import com.jia.sollimepie.communication.ConfigMessage;
import com.jia.sollimepie.communication.FoodListMessage;
import com.jia.sollimepie.item.SOLLimePieItems;
import com.jia.sollimepie.item.SOLLimePieCreativeTabs;
import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.tracking.CapabilityHandler;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SOLLimePie.MOD_ID)
@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public final class SOLLimePie {
    public static final String MOD_ID = "sollimepie";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static boolean HasFarmersDelight() { return ModList.get().isLoaded("farmersdelight"); }
    public static boolean HasPamsHarvestcraft() { return ModList.get().isLoaded("pamhc2foodcore"); }

    public static ResourceLocation resourceLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public SOLLimePie(IEventBus modBus, ModContainer container) {
        SOLLimePieConfig.setUp(container);
        SOLLimePieItems.ITEMS.register(modBus);
        SOLLimePieCreativeTabs.TABS.register(modBus);
        ContainerScreenRegistry.MENU_TYPES.register(modBus);
        CapabilityHandler.ATTACHMENTS.register(modBus);
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(FoodListMessage.TYPE, FoodListMessage.STREAM_CODEC, FoodListMessage::handle);
        registrar.playToClient(ConfigMessage.TYPE, ConfigMessage.STREAM_CODEC, ConfigMessage::handle);
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(MigrationGameTests.class);
        event.register(OptimizationGameTests.class);
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.ItemHandler.ITEM, (stack, context) -> FoodContainerItem.getInventory(stack),
            SOLLimePieItems.LUNCHBOX.get(), SOLLimePieItems.LUNCHBAG.get(), SOLLimePieItems.GOLDEN_LUNCHBOX.get());
    }

}
