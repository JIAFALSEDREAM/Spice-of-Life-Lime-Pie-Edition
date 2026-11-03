package com.jia.sollimepie.item.foodcontainer;

import com.jia.sollimepie.SOLLimePieConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;


public class FoodSlot extends SlotItemHandler {
    public FoodSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (getItemHandler() instanceof FoodContainerInventory inventory) {
            inventory.persist();
        }
    }

    @Override
    public boolean mayPlace(ItemStack stack)
    {
        if (!canHold(stack)) {
            return false;
        }
        return super.mayPlace(stack);
    }

    public static boolean canHold(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof FoodContainerItem)
            && (stack.has(DataComponents.FOOD)
                || SOLLimePieConfig.SERVER.lunchboxExtraItems.get().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }
}
