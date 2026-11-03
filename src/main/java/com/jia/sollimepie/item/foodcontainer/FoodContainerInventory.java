package com.jia.sollimepie.item.foodcontainer;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Inventory view backed by the stack's vanilla container data component. */
public final class FoodContainerInventory extends ItemStackHandler {
    private final ItemStack container;

    public FoodContainerInventory(ItemStack container, int slots) {
        super(read(container, slots));
        this.container = container;
    }

    private static NonNullList<ItemStack> read(ItemStack container, int slots) {
        NonNullList<ItemStack> items = NonNullList.withSize(slots, ItemStack.EMPTY);
        container.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return FoodSlot.canHold(stack) && super.isItemValid(slot, stack);
    }

    public void persist() {
        onContentsChanged(0);
    }

    @Override
    protected void onContentsChanged(int slot) {
        NonNullList<ItemStack> contents = NonNullList.withSize(getSlots(), ItemStack.EMPTY);
        for (int i = 0; i < getSlots(); i++) {
            contents.set(i, getStackInSlot(i).copy());
        }
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }
}
