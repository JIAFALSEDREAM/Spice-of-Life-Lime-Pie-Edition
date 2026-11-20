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
    private int useDuration = -1;

    public static int swapRequestSequence(Player player, int sequence) {
        var use = player.getData(CapabilityHandler.FOOD_CONTAINER_USE);
        int previous = use.requestSequence;
        use.requestSequence = sequence;
        return previous;
    }

    static void startMeal(Player player, InteractionHand hand, ItemStack container, int slot, ItemStack food) {
        var use = player.getData(CapabilityHandler.FOOD_CONTAINER_USE);
        ItemStack selected = food.copy();
        // Vanilla queries the held item's duration inside startUsingItem, before it sets the use flag.
        use.useDuration = selected.getUseDuration(player);
        if (player.level().isClientSide) {
            use.appearance = new Appearance(Attempt.of(player, hand, container), selected, -1);
        } else {
            use.meal = new Meal(container, hand, player.getInventory().selected, slot, selected, use.requestSequence);
        }
        player.startUsingItem(hand);
        if (!player.isUsingItem() || player.getUseItem() != container) {
            stop(player);
            return;
        }
        use.useDuration = player.getUseItemRemainingTicks();
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null
            && use.meal != null && use.meal.isActive(player)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, use.meal.appearance(player, use.useDuration));
        }
    }

    static int useDuration(Player player, ItemStack container) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use == null) {
            return -1;
        }
        if (use.meal != null && use.meal.container() == container && use.meal.isHeld(player)) {
            return use.useDuration;
        }
        if (use.appearance != null && use.appearance.attempt().matches(player, container)) {
            if (use.appearance.confirmedDuration() >= 0) {
                return player.isUsingItem() ? use.appearance.confirmedDuration() : use.appearance.food().getUseDuration(player);
            }
            return use.useDuration;
        }
        return use.observedAppearance != null && matchesHand(player, use.observedAppearance.hand(), container)
            ? use.observedAppearance.duration() : -1;
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
                PacketDistributor.sendToPlayer(observer, use.meal.appearance(eater, use.useDuration));
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
            use.useDuration = -1;
        }
    }

    public static boolean changedContainer(Player player) {
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
                int previousDuration = activeDuration(recipient, message.hand());
                use.appearance = new Appearance(attempt, message.food(), message.duration());
                if (previousDuration >= 0) {
                    ((ObservedUse) recipient).sollimepie$adjustUseDuration(message.duration() - previousDuration);
                }
            }
        } else if (recipient.level().getEntity(message.playerId()) instanceof Player eater) {
            eater.getData(CapabilityHandler.FOOD_CONTAINER_USE).observedAppearance = message;
            if (eater.isUsingItem() && eater.getUsedItemHand() == message.hand()) {
                ObservedUse observed = (ObservedUse) eater;
                if (eater.getUseItem().getItem() instanceof FoodContainerItem) {
                    observed.sollimepie$setMealRemainingTicks(message.remainingTicks());
                } else if (eater.getUseItem().isEmpty() && eater.getItemInHand(message.hand()).getItem() instanceof FoodContainerItem) {
                    // An initial tracking bundle can deliver the use flag before the held item.
                    observed.sollimepie$refreshObservedUse();
                }
            }
        }
    }

    private static int activeDuration(Player player, InteractionHand hand) {
        ItemStack active = player.getUseItem();
        return player.isUsingItem() && player.getUsedItemHand() == hand
            && active.getItem() instanceof FoodContainerItem ? active.getUseDuration(player) : -1;
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

    public static boolean hasSelectedMeal(Player player, ItemStack container) {
        return container.getItem() instanceof FoodContainerItem && useDuration(player, container) >= 0;
    }

    public static int confirmedDuration(Player player, InteractionHand hand, ItemStack container) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use == null || !(container.getItem() instanceof FoodContainerItem)) {
            return -1;
        }
        if (use.appearance != null && use.appearance.attempt().hand() == hand && use.appearance.attempt().matches(player, container)) {
            return use.appearance.confirmedDuration();
        }
        return use.observedAppearance != null && use.observedAppearance.hand() == hand && matchesHand(player, hand, container)
            ? use.observedAppearance.duration() : -1;
    }

    public static int observedRemainingTicks(Player player, InteractionHand hand, ItemStack container) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use != null && use.observedAppearance != null && use.observedAppearance.hand() == hand
            && container.getItem() instanceof FoodContainerItem && matchesHand(player, hand, container)) {
            return use.observedAppearance.remainingTicks();
        }
        return -1;
    }

    private static boolean matchesHand(Player player, InteractionHand hand, ItemStack container) {
        return player.getItemInHand(hand) == container
            || (player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItem() == container);
    }

    public static void clearObservedAppearance(Player player) {
        var use = player.getExistingDataOrNull(CapabilityHandler.FOOD_CONTAINER_USE);
        if (use != null) {
            use.observedAppearance = null;
        }
    }

    public interface ObservedUse {
        void sollimepie$refreshObservedUse();
        void sollimepie$adjustUseDuration(int difference);
        void sollimepie$setMealRemainingTicks(int remainingTicks);
    }

    private record Attempt(InteractionHand hand, Item container, int selectedSlot) {
        static Attempt of(Player player, InteractionHand hand, ItemStack container) {
            return new Attempt(hand, container.getItem(), player.getInventory().selected);
        }

        boolean isHeld(Player player) {
            return player.getItemInHand(hand).is(container)
                && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == selectedSlot);
        }

        boolean matches(Player player, ItemStack stack) {
            return isHeld(player) && matchesHand(player, hand, stack);
        }
    }

    private record Appearance(Attempt attempt, ItemStack food, int confirmedDuration) {}

    record Meal(ItemStack container, InteractionHand hand, int selectedSlot, int slot, ItemStack food, int sequence) {
        FoodContainerMealMessage appearance(Player player, int duration) {
            return new FoodContainerMealMessage(player.getId(), sequence, hand, food, duration, player.getUseItemRemainingTicks());
        }

        boolean isActive(Player player) {
            return player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItem() == container
                && isHeld(player);
        }

        boolean isHeld(Player player) {
            return player.getItemInHand(hand) == container
                && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == selectedSlot);
        }
    }
}
