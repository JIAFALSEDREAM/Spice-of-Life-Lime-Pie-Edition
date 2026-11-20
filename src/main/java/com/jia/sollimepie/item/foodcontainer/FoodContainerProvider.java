package com.jia.sollimepie.item.foodcontainer;

import com.jia.sollimepie.lib.Localization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class FoodContainerProvider implements MenuProvider {
    private final String displayName;
    private final ItemStack container;

    public FoodContainerProvider(String displayName, ItemStack container) {
        this.displayName = displayName;
        this.container = container;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(Localization.keyString("item", "container." + displayName));
    }

    @Nullable
    @Override
    public FoodContainer createMenu(int id, Inventory playerInventory, Player player) {
        FoodContainerInventory storage = FoodContainerItem.getInventory(container);
        return storage == null ? null : new FoodContainer(id, playerInventory, storage);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(((FoodContainer) menu).getStorageSlotCount());
    }
}
