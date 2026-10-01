package com.jia.sollimepie.tracking;

import com.jia.sollimepie.ConfigHandler;
import com.jia.sollimepie.SOLLimePieConfig;
import com.jia.sollimepie.api.FoodCapability;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.ToDoubleFunction;

@ParametersAreNonnullByDefault
public final class FoodList implements FoodCapability, INBTSerializable<CompoundTag> {
	private static final String NBT_KEY_FOOD_LIST = "foodList";
	private static final String NBT_KEY_UNIQUE_FOOD = "food";
	private static final String NBT_KEY_LAST_EATEN = "lastEaten";
	private static final String NBT_KEY_FOODS_EATEN = "foodsEaten";

	public static FoodList get(Player player) {
		return player.getData(CapabilityHandler.FOOD);
	}

	private static final int MAX_FOODS_EATEN = Integer.MAX_VALUE;
	private int foodsEaten = 0;
	// Keys - foods eaten, Values - lastEaten, i.e. # meals ago the food was last eaten
	private final Map<FoodInstance, Integer> uniqueFoods = new HashMap<>();

	public FoodList() {}

	@Nullable
	private CompoundTag serializeUniqueFood(Map.Entry<FoodInstance, Integer> foodPair) {
		FoodInstance food = foodPair.getKey();
		Integer lastEaten = foodPair.getValue();

		String encodedFood = food.encode();
		if (encodedFood == null) {
			return null;
		}

		CompoundTag tag = new CompoundTag();
		StringTag s = StringTag.valueOf(encodedFood);
		IntTag i = IntTag.valueOf(lastEaten);
		tag.put(NBT_KEY_UNIQUE_FOOD, s);
		tag.put(NBT_KEY_LAST_EATEN, i);

		return tag;
	}

	/** used for persistent storage */
	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();

		ListTag list = new ListTag();

		uniqueFoods.entrySet().stream()
			.map(this::serializeUniqueFood)
			.filter(Objects::nonNull)
			.forEach(list::add);
		tag.put(NBT_KEY_FOOD_LIST, list);
		tag.put(NBT_KEY_FOODS_EATEN, IntTag.valueOf(foodsEaten));

		return tag;
	}

	@Nullable
	private Pair<FoodInstance, Integer> deserializeUniqueFood(Pair<String, Integer> encoded) {
		FoodInstance uniqueFood = FoodInstance.decode(encoded.getKey());
		Integer lastEaten = Math.max(0, encoded.getValue());

		if (uniqueFood == null) {
			return null;
		}

		return new ImmutablePair<>(uniqueFood, lastEaten);
	}

	/** used for persistent storage */
	public void deserializeNBT(CompoundTag tag) {
		ListTag list = tag.getList(NBT_KEY_FOOD_LIST, Tag.TAG_COMPOUND);

		uniqueFoods.clear();
		list.stream()
			.map(nbt-> (CompoundTag) nbt)
			// getInt also accepts legacy float tags, whose values were integer meal ages.
			.map(nbt -> new ImmutablePair<>(nbt.getString(NBT_KEY_UNIQUE_FOOD), nbt.getInt(NBT_KEY_LAST_EATEN)))
			.map(this::deserializeUniqueFood)
			.filter(Objects::nonNull)
			.forEach(pair -> uniqueFoods.put(pair.getKey(), pair.getValue()));
		foodsEaten = Math.max(0, tag.getInt(NBT_KEY_FOODS_EATEN));
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) { return serializeNBT(); }

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) { deserializeNBT(tag); }

	public void addFood(Item food, Map<FoodInstance, Integer> foodMap) {
		if (!SOLLimePieConfig.shouldCount(food) && !SOLLimePieConfig.shouldForbiddenCount()) {
			return;
		}

		if (foodsEaten < MAX_FOODS_EATEN) {
			foodsEaten++;
		}

		ArrayList<FoodInstance> toRemove = new ArrayList<>();

		for (Map.Entry<FoodInstance, Integer> entry : foodMap.entrySet()) {
			FoodInstance foodInstance = entry.getKey();
			Integer lastEaten = entry.getValue();

			lastEaten = nextAge(lastEaten);
			foodMap.put(foodInstance, lastEaten);

			if (SOLLimePieConfig.size() > 0 && lastEaten >= SOLLimePieConfig.size()) {
				toRemove.add(foodInstance);
			}
		}

		for (FoodInstance foodInstance : toRemove) {
			foodMap.remove(foodInstance);
		}

		if (SOLLimePieConfig.shouldCount(food)) {
			FoodInstance newlyEaten = new FoodInstance(food);
			foodMap.put(newlyEaten, 0);
		}
	}

	public void addFood(Item food) {
		addFood(food, uniqueFoods);
	}

	/**
	 * @return The change in food diversity from eating the food.
	 */
	public double simulateFoodAdd(Item food) {
		return simulateFoodAdd(food, FoodList::getComplexity);
	}

	/** A short-lived simulator for one selection; never retained across meals or config changes. */
	public ToDoubleFunction<Item> foodSimulator() {
		Map<FoodInstance, Double> complexities = new HashMap<>();
		Map<Item, Double> changes = new HashMap<>();
		return food -> changes.computeIfAbsent(food, item -> simulateFoodAdd(item,
			instance -> complexities.computeIfAbsent(instance, FoodList::getComplexity)));
	}

	private double simulateFoodAdd(Item food, ToDoubleFunction<FoodInstance> complexityOf) {
		if (!SOLLimePieConfig.shouldCount(food) && !SOLLimePieConfig.shouldForbiddenCount()) {
			return 0.0;
		}

		// Permanent records without decay have no history-wide change to simulate.
		if (SOLLimePieConfig.size() == 0 && !SOLLimePieConfig.decayEnabled()) {
			FoodInstance candidate = new FoodInstance(food);
			return SOLLimePieConfig.shouldCount(food) && !uniqueFoods.containsKey(candidate)
				? complexityOf.applyAsDouble(candidate) : 0.0;
		}
		double change = 0.0;

		for (Map.Entry<FoodInstance, Integer> entry : uniqueFoods.entrySet()) {
			FoodInstance foodInstance = entry.getKey();
			Integer lastEaten = entry.getValue();

			double complexity = complexityOf.applyAsDouble(foodInstance);
			double diversityContribution = calculateTimePenalty(lastEaten) * complexity;
			lastEaten = nextAge(lastEaten);

			if (foodInstance.getItem().equals(food)) {
				change -= diversityContribution;
			}
			else if (SOLLimePieConfig.size() > 0 && lastEaten >= SOLLimePieConfig.size()) {
				change -= diversityContribution;
			}
			else {
				double newDiversityContribution = calculateTimePenalty(lastEaten) * complexity;
				change += (newDiversityContribution - diversityContribution);
			}
		}

		if (SOLLimePieConfig.shouldCount(food)) {
			change += calculateTimePenalty(0) * complexityOf.applyAsDouble(new FoodInstance(food));
		}

		return change;
	}

	@Override
	public double foodDiversity() {
		return foodDiversity(uniqueFoods.entrySet());
	}

	public static double foodDiversity(Set<Map.Entry<FoodInstance, Integer>> foodData) {
		double diversity = 0;

		for (Map.Entry<FoodInstance, Integer> entry : foodData) {
			FoodInstance food = entry.getKey();
			Integer lastEaten = entry.getValue();

			diversity += calculateDiversityContribution(food, lastEaten);
		}

		return diversity;
	}

	public static double calculateDiversityContribution(FoodInstance food, int lastEaten) {
		return calculateTimePenalty(lastEaten) * getComplexity(food);
	}

	private static double calculateTimePenalty(int lastEaten) {
		if (!SOLLimePieConfig.decayEnabled()) {
			return 1.0;
		}
		int size = SOLLimePieConfig.size();
		int startDecay = SOLLimePieConfig.startDecay();
		int endDecay = SOLLimePieConfig.endDecay();
		double minContribution = SOLLimePieConfig.minContribution();

		if (startDecay > endDecay || startDecay < 0 || (size > 0 && endDecay > size) ||
				minContribution > 1 || minContribution < 0) {
			// invalid
			return 0.0;
		}

		if (lastEaten <= startDecay) {
			return 1.0;
		}
		else if (lastEaten > endDecay) {
			return minContribution;
		}

		double slope = (1.0 - minContribution) / (double) (startDecay - endDecay);
		return slope * (lastEaten - startDecay) + 1.0;
	}

	private static int nextAge(int age) {
		return age == Integer.MAX_VALUE ? age : age + 1;
	}

	public static double getComplexity(FoodInstance food) {
		if (ConfigHandler.complexityMap != null && ConfigHandler.complexityMap.containsKey(food)) {
			return ConfigHandler.complexityMap.get(food);
		}

		var foodProperties = new net.minecraft.world.item.ItemStack(food.item).getFoodProperties(null);
		if (foodProperties != null)
		{
			var nutrition = foodProperties.nutrition();
			var saturation = foodProperties.saturation() / 2.0f;
			var average = ((saturation + nutrition) / 2);

			if (average < 5) {
				return average * SOLLimePieConfig.defaultContribution() / 5f;
			}

			return SOLLimePieConfig.defaultContribution() * 4 * Math.log10(average - 4) + 1;
		}

		return SOLLimePieConfig.defaultContribution();
	}

	public Set<Map.Entry<FoodInstance, Integer>> getData() {
		return uniqueFoods.entrySet();
	}

	public int getLastEaten(Item food) {
		if (!hasEaten(food)) {
			return -1;
		}

		return uniqueFoods.get(new FoodInstance(food));
	}

	@Override
	public boolean hasEaten(Item food) {
		if (!new net.minecraft.world.item.ItemStack(food).has(net.minecraft.core.component.DataComponents.FOOD)) {
		    return false;
		}
		return uniqueFoods.containsKey(new FoodInstance(food));
	}

	public void clearFood() {
		uniqueFoods.clear();
	}

	public Set<FoodInstance> getEatenFoods() {
		return uniqueFoods.keySet();
	}

	public int getFoodsEaten() {
		return foodsEaten;
	}

	public void resetFoodsEaten() {
		foodsEaten = 0;
	}

	public static class FoodListNotFoundException extends RuntimeException {
		public FoodListNotFoundException() {
			super("Player must have food capability attached, but none was found.");
		}
	}
}
