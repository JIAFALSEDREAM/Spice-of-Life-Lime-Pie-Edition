/**
 * Much of the following code was adapted from Cyclic's storage bag code.
 * Copyright for portions of the code are held by Samson Basset (Lothrazar)
 * as part of Cyclic, under the MIT license.
 */
package com.jia.sollimepie.item.foodcontainer;

import com.jia.sollimepie.client.ContainerScreenRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;


public class FoodContainer extends AbstractContainerMenu {
    public static final int PLAYERSIZE = 4 * 9;

    private final int nslots;
    private final Inventory playerInventory;

    public static FoodContainer createClientMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        if (data == null) {
            throw new IllegalArgumentException("Missing food container menu data");
        }
        int capacity = data.readVarInt();
        // Match the maximum size of vanilla ItemContainerContents.
        if (capacity < 1 || capacity > 256) {
            throw new IllegalArgumentException("Invalid food container capacity: " + capacity);
        }
        return new FoodContainer(id, playerInventory, new ItemStackHandler(capacity));
    }

    FoodContainer(int id, Inventory playerInventory, ItemStackHandler storage) {
        super(ContainerScreenRegistry.FOOD_CONTAINER.get(), id);
        this.playerInventory = playerInventory;
        nslots = storage.getSlots();
        int slotsPerRow = nslots > 9 ? nslots / 2 : nslots;
        int xStart = (2 * 8 + 9 * 18 - slotsPerRow * 18) / 2;
        int yStart = nslots > 9 ? 17 + (84 - 36 - 23) / 2 : 17 + 18;
        for (int slot = 0; slot < nslots; slot++) {
            int xPos = xStart + slot % slotsPerRow * 18;
            int yPos = yStart + slot / slotsPerRow * 18;
            this.addSlot(new FoodSlot(storage, slot, xPos, yPos));
        }

        layoutPlayerInventorySlots(8, 84);
    }

    public int getStorageSlotCount() {
        return nslots;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickTypeIn, Player player) {
        if (!(slotId < 0 || slotId >= this.slots.size())) {
            ItemStack clickedStack = this.slots.get(slotId).getItem();
            if (clickedStack.getItem() instanceof FoodContainerItem) {
                //lock the bag in place by quitting early
                return;
            }
        }

        super.clicked(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId < 0 || slotId >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot clickedSlot = slots.get(slotId);
        if (!clickedSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack clickedStack = clickedSlot.getItem();
        if (slotId >= nslots && !FoodSlot.canHold(clickedStack)) {
            return ItemStack.EMPTY;
        }

        final ItemStack unchangedCopy = clickedStack.copy();
        if (slotId < nslots) {
            // Item is in the FoodContainer, move it to inventory
            if (!moveItemStackTo(clickedStack, nslots, nslots + PLAYERSIZE, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Item is in the inventory, move it to the FoodContainer
            if (!moveItemStackTo(clickedStack, 0, nslots, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (clickedStack.isEmpty()) {
            clickedSlot.set(ItemStack.EMPTY);
        } else {
            clickedSlot.setChanged();
        }

        if (clickedStack.getCount() == unchangedCopy.getCount()) {
            return ItemStack.EMPTY;
        }

        clickedSlot.onTake(player, clickedStack);
        return unchangedCopy;
     }

    private int addSlotRange(Inventory handler, int index, int x, int y, int amount, int dx) {
        for (int i = 0; i < amount; i++) {
            addSlot(new Slot(handler, index, x, y));
            x += dx;
            index++;
        }
        return index;
    }

    private void addSlotBox(Inventory handler, int index, int x, int y, int horAmount, int dx, int verAmount, int dy) {
        for (int j = 0; j < verAmount; j++) {
            index = addSlotRange(handler, index, x, y, horAmount, dx);
            y += dy;
        }
    }

    protected void layoutPlayerInventorySlots(int leftCol, int topRow) {
        // Player inventory
        addSlotBox(playerInventory, 9, leftCol, topRow, 9, 18, 3, 18);
        // Hotbar
        topRow += 58;
        addSlotRange(playerInventory, 0, leftCol, topRow, 9, 18);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
