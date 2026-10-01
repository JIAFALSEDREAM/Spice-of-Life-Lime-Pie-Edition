package com.jia.sollimepie;

import com.google.common.collect.Lists;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.regex.Pattern;


@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public final class SOLLimePieConfig
{
	private static String localizationPath(String path) {
		return "config." + SOLLimePie.MOD_ID + "." + path;
	}

	public static final Server SERVER;
	public static final ModConfigSpec SERVER_SPEC;

	static {
		Pair<Server, ModConfigSpec> specPair = new Builder().configure(Server::new);
		SERVER = specPair.getLeft();
		SERVER_SPEC = specPair.getRight();
	}

	public static final Client CLIENT;
	public static final ModConfigSpec CLIENT_SPEC;

	static {
		Pair<Client, ModConfigSpec> specPair = new Builder().configure(Client::new);
		CLIENT = specPair.getLeft();
		CLIENT_SPEC = specPair.getRight();
	}

	public static void setUp(ModContainer context) {
		context.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
		context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
	}

	@SubscribeEvent
	public static void onConfigReload(ModConfigEvent.Reloading event) {
		if (event.getConfig().getSpec() != SERVER_SPEC) {
			return;
		}
		MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
		if (currentServer == null) {
		    return;
		}

		currentServer.execute(() -> ConfigHandler.reloadServerConfig(currentServer.getPlayerList().getPlayers()));
	}

	public static List<String> getBlacklist() {
		return new ArrayList<>(SERVER.blacklist.get());
	}

	/** Rules used by client diversity calculations; NeoForge only syncs SERVER config during login. */
	public static CompoundTag serializeCalculationRules() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("size", size());
		tag.putInt("startDecay", startDecay());
		tag.putInt("endDecay", endDecay());
		tag.putBoolean("decayEnabled", decayEnabled());
		tag.putInt("minimumFoods", minFoodsToActivate());
		tag.putDouble("minContribution", minContribution());
		tag.putDouble("defaultContribution", defaultContribution());
		tag.putBoolean("forbiddenCount", shouldForbiddenCount());
		ListTag whitelist = new ListTag(), blacklist = new ListTag();
		getWhitelist().forEach(value -> whitelist.add(StringTag.valueOf(value)));
		getBlacklist().forEach(value -> blacklist.add(StringTag.valueOf(value)));
		tag.put("whitelist", whitelist);
		tag.put("blacklist", blacklist);
		return tag;
	}

	/** Apply only to the client loaded config, without saving or firing a config reload event. */
	public static void applyCalculationRules(CompoundTag tag) {
		SERVER.queueSize.set(tag.getInt("size"));
		SERVER.startDecay.set(tag.getInt("startDecay"));
		SERVER.endDecay.set(tag.getInt("endDecay"));
		SERVER.decayEnabled.set(!tag.contains("decayEnabled") || tag.getBoolean("decayEnabled"));
		SERVER.minFoodsToActivate.set(tag.getInt("minimumFoods"));
		SERVER.minContribution.set(tag.getDouble("minContribution"));
		SERVER.defaultContribution.set(tag.getDouble("defaultContribution"));
		SERVER.shouldForbiddenCount.set(tag.getBoolean("forbiddenCount"));
		SERVER.whitelist.set(tag.getList("whitelist", Tag.TAG_STRING).stream().map(Tag::getAsString).toList());
		SERVER.blacklist.set(tag.getList("blacklist", Tag.TAG_STRING).stream().map(Tag::getAsString).toList());
	}

	public static List<String> getWhitelist() {
		return new ArrayList<>(SERVER.whitelist.get());
	}

	public static List<String> getBenefitsUnparsed() { return new ArrayList<>(SERVER.benefitsUnparsed.get()); }

	public static List<String> getComplexityUnparsed() {return new ArrayList<>(SERVER.complexityUnparsed.get());}

	public static List<Double> getThresholds() { return new ArrayList<>(SERVER.thresholds.get()); }

	public static boolean shouldResetOnDeath() {
		return SERVER.shouldResetOnDeath.get();
	}

	public static boolean limitProgressionToSurvival() {
		return SERVER.limitProgressionToSurvival.get();
	}

	public static boolean shouldForbiddenCount() { return SERVER.shouldForbiddenCount.get(); }

	public static Integer size() {
		return SERVER.queueSize.get();
	}

	public static Integer endDecay() {
		return SERVER.endDecay.get();
	}

	public static Integer startDecay() {
		return SERVER.startDecay.get();
	}

	public static boolean decayEnabled() { return SERVER.decayEnabled.get(); }

	public static Double minContribution() {
		return SERVER.minContribution.get();
	}

	public static Double defaultContribution() {
		return SERVER.defaultContribution.get();
	}

	public static Integer minFoodsToActivate() {
		return SERVER.minFoodsToActivate.get();
	}

	public static class Server {
		public final ConfigValue<List<? extends String>> blacklist;
		public final ConfigValue<List<? extends String>> whitelist;

		public final IntValue queueSize;

		public final BooleanValue shouldResetOnDeath;
		public final BooleanValue limitProgressionToSurvival;

		public final ConfigValue<List<? extends Double>> thresholds;
		public final ConfigValue<List<? extends String>> benefitsUnparsed;
		public final IntValue minFoodsToActivate;

		public final DoubleValue minContribution;
		public final DoubleValue defaultContribution;
		public final IntValue endDecay;
		public final IntValue startDecay;
		public final BooleanValue decayEnabled;

		public final BooleanValue shouldForbiddenCount;

		public final ConfigValue<List<? extends String>> complexityUnparsed;

		Server(Builder builder) {
			builder.push("Benefits");

			thresholds = builder
					.translation(localizationPath("thresholds"))
					.comment(" A list of diversity value thresholds, in ascending order. When the player's food diversity reaches a threshold,\n"
							+" they will get the benefits associated with that threshold.\n"
							+" There is no fixed tier count or score cap: the 17 default tiers are only a starting preset.\n"
							+" Use decimal numbers, for example thresholds = [2.0, 10.0, 250.0].\n"
							+" Keep this list the same length as benefitsUnparsed; entries are paired by position.\n"
							+" Extra unmatched entries have no effect. All reached tiers apply together, and their bonuses\n"
							+" are removed if diversity drops below the corresponding threshold. The Food Book adds pages automatically.\n"
							+" Very long lists increase reward-update, config-sync and book-opening costs.\n"
							+"\n")
					.defineList("thresholds", Lists.newArrayList(2.0, 5.0, 7.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0), () -> 0.0, e -> e instanceof Double);

			benefitsUnparsed = builder
					.translation(localizationPath("benefits_unparsed"))
					.comment(" \n Define custom benefits here. Each entry in the list corresponds to a benefit that will be obtained\n"
							+" at the corresponding diversity threshold defined the list above. For example, the first entry in\n"
							+" this list will be applied when the player's food diversity reaches the number in the first entry in\n"
							+" the threshold list above.\n"
							+" A benefit can also be marked as a detriment. In that case, its activation is reversed.\n"
							+" A detriment is applied while the player has less diversity than the threshold,\n"
							+" and will be removed when the threshold is reached.\n"
							+" Each benefit is a string with the following form: [+/-][type],[registry name],[value] (without the brackets)\n"
							+" A leading plus (or no symbol) denotes a benefit, while a minus denotes a detriment.\n"
							+" The type can either be 'attribute' for attribute modifiers or 'effect' for potion effects\n"
							+" Registry names for common vanilla attributes are \n"
							+" generic.max_health, generic.knockback_resistance, generic.movement_speed, generic.luck, \n"
							+" generic.attack_damage, generic.attack_speed, generic.armor, generic.armor_toughness \n"
							+" The value of attributes is the numerical number that will be added to that attribute\n"
							+" Use a negative number for subtraction. Multiplicative modifiers are not supported.\n"
							+" For potion effects, the value is an integer and is the potion effect amplifier. Note\n"
							+" that the amplifier is 0 indexed, so minecraft:strength,1 corresponds to Strength II\n"
							+"\n"
							+" To add multiple benefits to the same threshold, separate them by a semicolon ';'\n"
							+" Make sure that you have NO SPACES!\n"
							+" As an example, 'attribute,generic.max_health,2;effect,strength,1' will give both +2 max hp\n"
							+" and Strength II at the corresponding threshold.\n"
							+" 'attribute,generic.attack_damage,1;-effect,slowness,0' will give +1 attack damage at the corresponding threshold\n"
							+" and Slowness I below the corresponding threshold.\n"
							+" Complete two-tier example: thresholds = [5.0, 20.0] and\n"
							+" benefitsUnparsed = [\"attribute,generic.max_health,2\", \"effect,strength,0;-effect,slowness,0\"].\n"
							+" Below 20 points, Slowness I applies; at 5 points, +2 health also applies; at 20 points,\n"
							+" Strength I replaces the slowness penalty while the earlier health bonus remains.\n"
							+" Attribute values add directly (2 health points = one heart) and still obey Minecraft's attribute limits.\n"
							+"\n")
					.defineList("benefitsUnparsed", Lists.newArrayList(
							"effect,speed,0",
							"attribute,generic.max_health,1",
							"attribute,generic.max_health,1;effect,haste,0",
							"attribute,generic.max_health,1;effect,strength,0",
							"attribute,generic.max_health,1;attribute,generic.armor_toughness,2",
							"attribute,generic.max_health,1;effect,regeneration,0",
							"attribute,generic.max_health,1;effect,speed,1",
							"attribute,generic.max_health,1;effect,strength,0",
							"attribute,generic.max_health,1;effect,luck,0",
							"attribute,generic.max_health,1;attribute,generic.knockback_resistance,1",
							"attribute,generic.max_health,1;attribute,generic.movement_speed,2",
							"attribute,generic.max_health,1;attribute,generic.armor_toughness,2",
							"attribute,generic.max_health,1;effect,haste,1",
							"attribute,generic.max_health,1;effect,strength,1",
							"attribute,generic.max_health,1;attribute,generic.armor_toughness,2",
							"attribute,generic.max_health,1;effect,strength,2",
							"attribute,generic.max_health,1;effect,luck,1"),
							() -> "", e -> e instanceof String);

			minFoodsToActivate = builder
					.translation(localizationPath("min_foods_to_activate"))
					.comment(" The minimum number of recorded meals before benefits or detriments can activate.\n"
							+" This counts meals, including repetitions, not distinct food types. 0 enables rewards immediately.\n"
							 +"\n")
					.defineInRange("minFoodsToActivate", 0, 0, Integer.MAX_VALUE);

			builder.pop();
			builder.push("Filtering");

			blacklist = builder
					.translation(localizationPath("blacklist"))
					.comment(" Foods in this list won't contribute to food diversity.\n"
							+" Use item IDs or '*' wildcards, e.g. [\"minecraft:rotten_flesh\", \"examplemod:*\"]. Tags are not supported.\n"
							+" Filtered meals still age other records when shouldForbiddenCount is true.\n"
							+"\n")
					.defineListAllowEmpty("blacklist", Lists.newArrayList(), () -> "", e -> e instanceof String);

			whitelist = builder
					.translation(localizationPath("whitelist"))
					.comment("\n When this list contains anything, the blacklist is ignored and instead only foods from here count.\n"
							+" Example: [\"minecraft:bread\", \"farmersdelight:*\"]. An empty list restores normal blacklist filtering.\n"
							+"\n")
					.defineListAllowEmpty("whitelist", Lists.newArrayList(), () -> "", e -> e instanceof String);

			builder.pop();
			builder.push("Miscellaneous");

			shouldResetOnDeath = builder
					.translation(localizationPath("reset_on_death"))
					.comment(" Whether or not to reset food diversity on death, effectively losing all benefits.\n"
							+"\n")
					.define("resetOnDeath", false);

			limitProgressionToSurvival = builder
					.translation(localizationPath("limit_progression_to_survival"))
					.comment("\n If true, eating foods outside of survival mode (e.g. creative/adventure) is not tracked.\n"
							+"\n")
					.define("limitProgressionToSurvival", false);

			queueSize = builder
					.translation(localizationPath("queue_size"))
					.comment("\n Number of recent recorded meals covered by the history. Default: 32. Set 0 for permanent records.\n"
							+" Each food type occupies one record; eating it again refreshes its age instead of adding duplicate points.\n"
							+" A positive size removes a food once that many later recorded meals have been eaten.\n"
							+" Recipes for common play styles (settings belong to the sections shown):\n"
							+" - Longer recent diet: Miscellaneous.queueSize = 256, Advanced.endDecay = 256, decayEnabled = true.\n"
							+" - Permanent collection: Miscellaneous.queueSize = 0, Advanced.decayEnabled = false.\n"
							+" - Recent diet without fading: Miscellaneous.queueSize = 64, Advanced.decayEnabled = false.\n"
							+" Permanent history alone does not disable decay: with decay enabled and minContribution = 0, old\n"
							+" foods remain listed but can contribute zero points. Increasing queueSize alone does not extend endDecay.\n"
							+" Retention has no preallocated array: costs grow with distinct foods actually recorded, not this number.\n"
							+" Large histories increase meal processing and save/sync costs; automatic food selection with decay\n"
							+" also scans history for candidate foods. Start small when tuning a large modpack.\n"
							+"\n")
					.defineInRange("queueSize", 32, 0, Integer.MAX_VALUE);

			builder.pop();
			builder.push("Advanced");

			decayEnabled = builder
					.translation(localizationPath("decay_enabled"))
					.comment(" Whether food contributions decay as more meals are eaten. If false, decay settings are ignored,\n"
							+" but records still expire when queueSize is greater than 0.\n"
							+" Disable this together with queueSize = 0 to preserve every food's full contribution permanently.\n")
					.define("decayEnabled", true);

			minContribution = builder
					.translation(localizationPath("min_contribution"))
					.comment(" Lowest contribution multiplier after decay, from 0.0 to 1.0. This is a proportion, not a point value.\n"
							+" A food's score is its food weight multiplied by an age multiplier. Its multiplier starts at 1.0,\n"
							+" stays there through startDecay meals, falls linearly to minContribution at endDecay, then stays there.\n"
							+" Example: weight 4, startDecay = 0, endDecay = 100, minContribution = 0.25 gives 4 points when fresh,\n"
							+" 2.5 points after 50 later meals and 1 point after 100. A finite queue can still remove the record.\n"
							+" Decay measures recorded meals, not elapsed seconds. These settings are ignored if decayEnabled is false.\n"
							+"\n")
					.defineInRange("minContribution", 0.0, 0.0, 1.0);

			defaultContribution = builder
					.translation(localizationPath("default_contribution"))
					.comment("\n Base parameter for foods without an explicit complexity override; range 0.0 to 1000000.0.\n"
							+" Their actual weight is calculated from nutrition and saturation, so 1.0 does not mean one point per food.\n"
							+" Increase this to raise general food scores, then adjust thresholds to match. Use complexityUnparsed\n"
							+" to set an exact weight for one food instead. Explicit overrides ignore this parameter.\n"
							+"\n")
					.defineInRange("defaultContribution", 1.0, 0.0, 1_000_000.0);

			endDecay = builder
					.translation(localizationPath("end_decay"))
					.comment("\n How many meals in the past should the diversity penalty stop from.\n"
							+" Must be at least startDecay, and at most queueSize unless queueSize is 0 (permanent records).\n"
							+" Note that if you update queueSize, to retain the default behavior, you need to also\n"
							+" set endDecay equal to the queueSize\n"
							+"\n")
					.defineInRange("endDecay", 32, 0, Integer.MAX_VALUE);

			startDecay = builder
					.translation(localizationPath("start_decay"))
					.comment("\n How many meals in the past should the diversity time penalty start to apply.\n"
							+" Must be less than or equal to endDecay. Ignored when decayEnabled is false.\n"
							+"\n")
					.defineInRange("startDecay", 0, 0, Integer.MAX_VALUE);

			shouldForbiddenCount = builder
					.translation(localizationPath("should_forbidden_count"))
					.comment("\n Whether blacklisted foods should still take a spot in the queue, even if they don't contribute any diversity.\n"
							+"\n")
					.define("shouldForbiddenCount", true);

			builder.pop();
			builder.push("Complexity");

			complexityUnparsed = builder
					.translation(localizationPath("complexity_unparsed"))
					.comment(" Define custom complexity values for individual foods here.\n"
							+" The complexity value of a food is how much diversity points it gives. \n"
							+" Foods not listed here use the nutrition/saturation formula controlled by defaultContribution.\n"
							+" Each entry in the list should be a string defining one food, and the format is [registry name],[value]\n"
							+" Note that tags are NOT currently supported.\n"
							+" Example: [\"minecraft:bread,2.5\", \"minecraft:golden_apple,8\"]. These are fresh, full weights;\n"
							+" decay still multiplies them when enabled. Use finite, nonnegative values, exact IDs and no spaces.\n"
							+" Unknown or non-food items are skipped with a log warning. Remove an entry to restore its calculated weight.\n"
							+"\n")
					.defineListAllowEmpty("complexityUnparsed", Lists.newArrayList(
									"minecraft:cooked_porkchop,2",
									"minecraft:cooked_beef,2",
									"minecraft:golden_carrot,2",
									"minecraft:golden_apple,4",
									"minecraft:enchanted_golden_apple,10",

										// Disabled: the 1.21.1 port uses the different namespace "large_meals".
										// "largemeals:sweet_berry_custard,5",
										// "largemeals:pufferfish_broth,5",
										// "largemeals:mushroom_pot_pie,5",
										// "largemeals:hearty_lunch,6",

									"farmersrespite:green_tea,3",
									"farmersrespite:yellow_tea,3",
									"farmersrespite:coffee,3",
									"farmersrespite:black_tea,3",
									"farmersrespite:rose_hip_tea,3",
									"farmersrespite:dandelion_tea,3",
									"farmersrespite:black_tea,3",

									"farmersrespite:rose_hip_pie_slice,4",
									"farmersrespite:coffee_cake_slice,4",
									"farmersrespite:blazing_chili,5",

									"farmersdelight:cake_slice,4",
									"farmersdelight:chocolate_pie_slice,4",
									"farmersdelight:apple_pie_slice,4",
									"farmersdelight:sweet_berry_cheesecake_slice,4"),
							() -> "", e -> e instanceof String);

			builder.pop();
		}
	}

	public static boolean isFoodTooltipEnabled() {
		return CLIENT.isFoodTooltipEnabled.get();
	}

	public static boolean shouldShowInactiveBenefits() { return CLIENT.shouldShowInactiveBenefits.get(); }

	public static class Client {
		public final BooleanValue isFoodTooltipEnabled;
		public final BooleanValue shouldShowInactiveBenefits;

		Client(Builder builder) {
			builder.push("miscellaneous");

			isFoodTooltipEnabled = builder
				.translation(localizationPath("is_food_tooltip_enabled"))
				.comment(" If true, foods indicate in their tooltips the last time they've been eaten, and their current diversity contribution."
						+"\n")
				.define("isFoodTooltipEnabled", true);

			shouldShowInactiveBenefits = builder
				.translation(localizationPath("should_show_inactive_benefits"))
				.comment("\n If true, the food book lists benefits that you haven't acquired yet, in addition to the ones you have.\n"
						+"\n")
				.define("shouldShowInactiveBenefits", true);

			builder.pop();
		}
	}

	// TODO: investigate performance of all these get() calls

	public static boolean hasWhitelist() {
		return !SERVER.whitelist.get().isEmpty();
	}

	public static boolean isAllowed(Item food) {
		String id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(food)).toString();
		if (hasWhitelist()) {
			return matchesAnyPattern(id, SERVER.whitelist.get());
		} else {
			return !matchesAnyPattern(id, SERVER.blacklist.get());
		}
	}

	public static boolean shouldCount(Item food) {
		return isAllowed(food);
	}

	private record CompiledPatterns(List<String> source, List<Pattern> patterns) {}
	private static volatile CompiledPatterns compiledPatterns = new CompiledPatterns(List.of(), List.of());

	private static boolean matchesAnyPattern(String query, Collection<? extends String> patterns) {
		CompiledPatterns cached = compiledPatterns;
		if (!cached.source().equals(patterns)) {
			List<String> source = List.copyOf(patterns);
			cached = new CompiledPatterns(source, source.stream().map(SOLLimePieConfig::compileGlob).toList());
			compiledPatterns = cached;
		}
		for (Pattern pattern : cached.patterns()) {
			if (pattern.matcher(query).matches()) {
				return true;
			}
		}
		return false;
	}

	private static Pattern compileGlob(String glob) {
		StringBuilder pattern = new StringBuilder(glob.length());
		for (String part : glob.split("\\*", -1)) {
			if (!part.isEmpty()) {
				pattern.append(Pattern.quote(part));
			}
			pattern.append(".*");
		}
		// delete extraneous trailing ".*" wildcard
		pattern.delete(pattern.length() - 2, pattern.length());
		return Pattern.compile(pattern.toString());
	}
}
