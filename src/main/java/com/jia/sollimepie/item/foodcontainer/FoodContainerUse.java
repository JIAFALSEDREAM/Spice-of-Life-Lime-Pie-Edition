package com.jia.sollimepie.item.foodcontainer;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.communication.FoodContainerMealMessage;
import com.jia.sollimepie.tracking.CapabilityHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Transient state for one food container use on each side. */
@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public final class FoodContainerUse {
    private static final int NO_SEQUENCE = -1;

    private int requestSequence = NO_SEQUENCE;
    private @Nullable Meal meal;
    private final Map<Integer, Attempt> pending = new HashMap<>();
    private @Nullable Appearance appearance;
    private @Nullable FoodContainerMealMessage observedAppearance;

    public static int swapRequestSequence(Player player, int sequence) {
        var use = player.getData(CapabilityHandler.FOOD_CONTAINER_USE);
        int previous = use.requestSequence;
        use.requestSequence = sequence;
        return previous;
    }

    static void startServerMeal(Player player, InteractionHand hand, ItemStack container, int slot, ItemStack food) {
        var use = player.getData(CapabilityHandler.FOOD_CONTAINER_USE);
        var meal = new Meal(container, hand, player.getInventory().selected, slot, food.copyWithCount(1), use.requestSequence);
        use.meal = meal;
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, meal.appearance(player));
        }
    }

    static void predict(Player player, InteractionHand hand, ItemStack container, ItemStack food) {
        var use = player.getData(CapabilityHandler.FOOD_CONTAINER_USE);
        use.appearance = new Appearance(Attempt.of(player, hand, container), food.copyWithCount(1));
    }

    public static void rememberRequest(Player player, InteractionHand hand, ItemStack container, int sequence) {
        player.getData(CapabilityHandler.FOOD_CONTAINER_USE).pending.put(sequence, Attempt.of(player, hand, container));
    }

    public static void acknowledgeRequests(Player player, int sequence) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use != null) {
            // A meal confirmation is sent during use handling, before the connection flushes its vanilla acknowledgement.
            use.pending.keySet().removeIf(request -> request <= sequence);
        }
    }

    public static void cancelRequests(Player player, @Nullable InteractionHand hand) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use != null) {
            use.pending.values().removeIf(attempt -> hand == null || attempt.hand() == hand);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof Player eater) {
            var use = eater.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
            if (use != null && use.meal != null && use.meal.isActive(eater)) {
                PacketDistributor.sendToPlayer(observer, use.meal.appearance(eater));
            }
        }
    }

    @Nullable
    static Meal takeMeal(Player player) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use == null) {
            return null;
        }
        Meal meal = use.meal;
        use.meal = null;
        return meal;
    }

    public static void stop(Player player) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use != null) {
            // Vanilla may stop an older animation while a newer right-click is still awaiting confirmation.
            use.meal = null;
            use.appearance = null;
            use.observedAppearance = null;
        }
    }

    static boolean changedContainer(Player player) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use == null) {
            return false;
        }
        return (use.meal != null && !use.meal.isActive(player))
            || (use.appearance != null && !use.appearance.attempt().isHeld(player));
    }

    public static void acceptAppearance(Player recipient, FoodContainerMealMessage message) {
        if (recipient.getId() == message.playerId()) {
            var use = recipient.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
            Attempt attempt = use == null ? null : use.pending.remove(message.sequence());
            if (attempt != null && attempt.hand() == message.hand() && attempt.isHeld(recipient)) {
                use.appearance = new Appearance(attempt, message.food());
            }
        } else if (recipient.level().getEntity(message.playerId()) instanceof Player eater) {
            eater.getData(CapabilityHandler.FOOD_CONTAINER_USE).observedAppearance = message;
            if (eater.isUsingItem() && eater.getUsedItemHand() == message.hand() && eater.getUseItem().isEmpty()
                && eater.getItemInHand(message.hand()).getItem() instanceof FoodContainerItem) {
                // An initial tracking bundle can deliver the use flag before the held item.
                ((ObservedUse) eater).sollimepie$refreshObservedUse();
            }
        }
    }

    public static ItemStack particleFood(Player player) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use == null || !player.isUsingItem()) {
            return ItemStack.EMPTY;
        }
        InteractionHand hand = player.getUsedItemHand();
        if (use.appearance != null) {
            Attempt attempt = use.appearance.attempt();
            return attempt.hand() == hand && attempt.isHeld(player) ? use.appearance.food() : ItemStack.EMPTY;
        }
        return use.observedAppearance != null && use.observedAppearance.hand() == hand
            ? use.observedAppearance.food() : ItemStack.EMPTY;
    }

    public static void clearObservedAppearance(Player player) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use != null) {
            use.observedAppearance = null;
        }
    }

    public interface ObservedUse {
        void sollimepie$refreshObservedUse();
    }

    private record Attempt(InteractionHand hand, Item container, int selectedSlot) {
        static Attempt of(Player player, InteractionHand hand, ItemStack container) {
            return new Attempt(hand, container.getItem(), player.getInventory().selected);
        }

        boolean isHeld(Player player) {
            return player.getItemInHand(hand).is(container)
                && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == selectedSlot);
        }
    }

    private record Appearance(Attempt attempt, ItemStack food) {}

    record Meal(ItemStack container, InteractionHand hand, int selectedSlot, int slot, ItemStack food, int sequence) {
        FoodContainerMealMessage appearance(Player player) {
            return new FoodContainerMealMessage(player.getId(), sequence, hand, food);
        }

        boolean isActive(Player player) {
            return player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItem() == container
                && player.getItemInHand(hand) == container
                && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == selectedSlot);
        }
    }
}
