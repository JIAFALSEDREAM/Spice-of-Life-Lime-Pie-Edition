package com.jia.sollimepie.client;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.SOLLimePieConfig;
import com.jia.sollimepie.item.foodcontainer.FoodContainerItem;
import com.jia.sollimepie.tracking.FoodInstance;
import com.jia.sollimepie.tracking.FoodList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

import static com.jia.sollimepie.lib.Localization.localized;
import static com.jia.sollimepie.lib.Localization.localizedComponent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, modid = SOLLimePie.MOD_ID)
public final class TooltipHandler {
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onItemTooltip(ItemTooltipEvent event) {
		if (!SOLLimePieConfig.isFoodTooltipEnabled()) {
		    return;
		}

		Player player = event.getEntity();
		if (player == null) {
		    return;
		}

		Item food = event.getItemStack().getItem();
		if (food instanceof FoodContainerItem) {
			return;
		}
		if (!new net.minecraft.world.item.ItemStack(food).has(net.minecraft.core.component.DataComponents.FOOD)) {
		    return;
		}

		FoodList foodList = FoodList.get(player);
		boolean hasBeenEaten = foodList.hasEaten(food);
		boolean isAllowed = SOLLimePieConfig.isAllowed(food);

		List<Component> tooltip = event.getToolTip();
		if (!isAllowed) {
			tooltip.add(localizedTooltip("disabled", ChatFormatting.DARK_GRAY));
		} else {
			if (hasBeenEaten) {
				int lastEaten = foodList.getLastEaten(food);
				double contribution = FoodList.calculateDiversityContribution(new FoodInstance(food), lastEaten);

				addDiversityInfoTooltips(tooltip, contribution, lastEaten);
			}
			else {
				addDiversityInfoTooltips(tooltip, FoodList.getComplexity(new FoodInstance(food)), -1);
			}
		}
	}

	private static Component localizedTooltip(String path, ChatFormatting color) {
		return localizedComponent("tooltip", path).withStyle(style -> style.applyFormat(color));
	}

	public static void addDiversityInfoTooltips(List<Component> tooltip, double contribution, int lastEaten) {
		String contribution_path = lastEaten == -1 ? "food_book.queue.tooltip.uneaten_contribution_label" : "food_book.queue.tooltip.contribution_label";
		tooltip.add(Component.literal(localized("gui", contribution_path) + ": " + String.format("%.2f", contribution)).withStyle(lastEaten == -1 ? ChatFormatting.GRAY : ChatFormatting.GOLD));

		if (lastEaten != -1)
		{
			String last_eaten_path = "food_book.queue.tooltip.last_eaten_label";
			if (lastEaten == 1)
			{
				last_eaten_path = "food_book.queue.tooltip.last_eaten_label_singular";
			}
			tooltip.add(Component.literal(localized("gui", last_eaten_path, lastEaten)).withStyle(ChatFormatting.GRAY));
		}

	}

	private TooltipHandler() {}
}
