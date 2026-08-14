package com.hm.achievement.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Filter matching an ItemStack against the properties described by a sub-category key, used by the ItemBreaks category.
 * <p>
 * A key is made of alternatives separated by {@code |}; each alternative is made of conditions separated by {@code ;}
 * that must all be satisfied. The supported conditions are:
 * <ul>
 * <li>{@code any}: matches any item;</li>
 * <li>{@code <material>} or {@code material:<material>}: e.g. {@code netherite_leggings};</li>
 * <li>{@code enchant:<name>[:<level>]}: e.g. {@code enchant:mending} or {@code enchant:unbreaking:3}; the level also
 * accepts the {@code >=}, {@code <=}, {@code >} and {@code <} operators, e.g. {@code enchant:unbreaking:>=2};</li>
 * <li>{@code name:<display name>}: case insensitive, formatting codes are ignored; {@code name:*} matches any item
 * with a custom name;</li>
 * <li>{@code color:<dye colour|#RRGGBB|r,g,b>}: colour of a dyed leather armour piece, e.g. {@code color:purple};</li>
 * <li>{@code unbreakable}: the item has the unbreakable flag.</li>
 * </ul>
 * For example {@code leather_leggings;color:purple;name:Purple Shorts} matches purple leather leggings named
 * "Purple Shorts", and {@code wooden_hoe;enchant:unbreaking:3|netherite_leggings} matches either a wooden hoe with
 * Unbreaking III or any pair of netherite leggings.
 */
public class ItemFilter {

	private static final Pattern LEVEL_PATTERN = Pattern.compile("(>=|<=|>|<|=)?\\d+");

	private final List<List<Predicate<ItemStack>>> alternatives;

	private ItemFilter(List<List<Predicate<ItemStack>>> alternatives) {
		this.alternatives = alternatives;
	}

	/**
	 * Parses a sub-category key into a filter. Invalid conditions are logged and never match any item.
	 *
	 * @param key the sub-category key, as written in the configuration file
	 * @param materialHelper used to match the material conditions
	 * @param logger used to report invalid conditions
	 * @return the corresponding filter
	 */
	public static ItemFilter parse(String key, MaterialHelper materialHelper, Logger logger) {
		List<List<Predicate<ItemStack>>> alternatives = new ArrayList<>();
		for (String alternative : StringUtils.split(key, '|')) {
			List<Predicate<ItemStack>> conditions = new ArrayList<>();
			for (String condition : StringUtils.split(alternative, ';')) {
				if (StringUtils.isNotBlank(condition)) {
					conditions.add(parseCondition(condition.trim(), key, materialHelper, logger));
				}
			}
			if (!conditions.isEmpty()) {
				alternatives.add(conditions);
			}
		}
		return new ItemFilter(alternatives);
	}

	/**
	 * Determines whether the item satisfies all the conditions of at least one of the alternatives of the key.
	 *
	 * @param item the item to match
	 * @return true if the item matches the key, false otherwise
	 */
	public boolean matches(ItemStack item) {
		for (List<Predicate<ItemStack>> conditions : alternatives) {
			boolean matchesAllConditions = true;
			for (Predicate<ItemStack> condition : conditions) {
				if (!condition.test(item)) {
					matchesAllConditions = false;
					break;
				}
			}
			if (matchesAllConditions) {
				return true;
			}
		}
		return false;
	}

	private static Predicate<ItemStack> parseCondition(String condition, String key, MaterialHelper materialHelper,
			Logger logger) {
		int separatorIndex = condition.indexOf(':');
		String type = (separatorIndex < 0 ? condition : condition.substring(0, separatorIndex)).trim()
				.toLowerCase(Locale.ROOT);
		String value = separatorIndex < 0 ? "" : condition.substring(separatorIndex + 1).trim();

		switch (type) {
			case "any":
			case "*":
				return item -> true;
			case "material":
			case "item":
				return parseMaterialCondition(value, key, materialHelper);
			case "enchant":
			case "enchantment":
				return parseEnchantmentCondition(value, key, logger);
			case "name":
				return parseNameCondition(value, key, logger);
			case "color":
			case "colour":
				return parseColorCondition(value, key, logger);
			case "unbreakable":
				return item -> {
					ItemMeta meta = item.getItemMeta();
					return meta != null && meta.isUnbreakable();
				};
			default:
				if (separatorIndex < 0) {
					// A condition without a value is a material name, e.g. 'netherite_leggings'.
					return parseMaterialCondition(type, key, materialHelper);
				}
				logger.warning("Condition \"" + condition + "\" used in the ItemBreaks sub-category \"" + key
						+ "\" is unknown. Supported conditions are: any, material, enchant, name, color and unbreakable.");
				return item -> false;
		}
	}

	private static Predicate<ItemStack> parseMaterialCondition(String value, String key,
			MaterialHelper materialHelper) {
		Material material = materialHelper.matchMaterial(value, null, "the ItemBreaks sub-category \"" + key + "\"");
		if (material == null) {
			return item -> false;
		}
		return item -> item.getType() == material;
	}

	private static Predicate<ItemStack> parseEnchantmentCondition(String value, String key, Logger logger) {
		if (value.isEmpty()) {
			logger.warning("The enchant condition used in the ItemBreaks sub-category \"" + key
					+ "\" is missing an enchantment name, for example enchant:mending.");
			return item -> false;
		}
		// The level, if any, follows the name of the enchantment. Namespaced names such as minecraft:mending are also
		// supported, hence the level is only extracted when the last part of the value looks like one.
		String enchantmentName = value;
		IntPredicate levelPredicate = level -> true;
		int levelIndex = value.lastIndexOf(':');
		if (levelIndex >= 0 && LEVEL_PATTERN.matcher(value.substring(levelIndex + 1).trim()).matches()) {
			enchantmentName = value.substring(0, levelIndex).trim();
			levelPredicate = parseLevelPredicate(value.substring(levelIndex + 1).trim(), key, logger);
			if (levelPredicate == null) {
				return item -> false;
			}
		}
		String expectedEnchantmentName = enchantmentName.toLowerCase(Locale.ROOT);
		IntPredicate expectedLevel = levelPredicate;
		return item -> {
			ItemMeta meta = item.getItemMeta();
			if (meta == null) {
				return false;
			}
			for (Map.Entry<Enchantment, Integer> enchantment : meta.getEnchants().entrySet()) {
				if (matchesEnchantmentName(enchantment.getKey().getKey(), expectedEnchantmentName)
						&& expectedLevel.test(enchantment.getValue())) {
					return true;
				}
			}
			return false;
		};
	}

	/**
	 * Determines whether the key of an enchantment matches the name used in the configuration, either in its short form
	 * (unbreaking) or in its namespaced one (minecraft:unbreaking).
	 *
	 * @param key the key of the enchantment carried by the item
	 * @param name the lower case name used in the configuration
	 * @return true if the enchantment matches the name, false otherwise
	 */
	static boolean matchesEnchantmentName(NamespacedKey key, String name) {
		return name.equals(key.getKey().toLowerCase(Locale.ROOT)) || name.equals(key.toString().toLowerCase(Locale.ROOT));
	}

	/**
	 * Parses the level part of an enchant condition, e.g. '3' or '>=2'.
	 *
	 * @param value the level, optionally prefixed by a comparison operator
	 * @param key used for logging
	 * @param logger used to report an invalid level
	 * @return the predicate on the enchantment level, or null if the level could not be parsed
	 */
	static IntPredicate parseLevelPredicate(String value, String key, Logger logger) {
		String operator = "";
		for (String candidate : Arrays.asList(">=", "<=", ">", "<", "=")) {
			if (value.startsWith(candidate)) {
				operator = candidate;
				break;
			}
		}
		String levelValue = value.substring(operator.length()).trim();
		int level;
		try {
			level = Integer.parseInt(levelValue);
		} catch (NumberFormatException e) {
			logger.warning("Enchantment level \"" + value + "\" used in the ItemBreaks sub-category \"" + key
					+ "\" is not a valid number, for example enchant:unbreaking:3 or enchant:unbreaking:>=3.");
			return null;
		}
		switch (operator) {
			case ">=":
				return itemLevel -> itemLevel >= level;
			case "<=":
				return itemLevel -> itemLevel <= level;
			case ">":
				return itemLevel -> itemLevel > level;
			case "<":
				return itemLevel -> itemLevel < level;
			default:
				return itemLevel -> itemLevel == level;
		}
	}

	private static Predicate<ItemStack> parseNameCondition(String value, String key, Logger logger) {
		if (value.isEmpty()) {
			logger.warning("The name condition used in the ItemBreaks sub-category \"" + key
					+ "\" is missing a value, for example name:Purple Shorts or name:* for any custom name.");
			return item -> false;
		}
		if ("*".equals(value)) {
			return item -> {
				ItemMeta meta = item.getItemMeta();
				return meta != null && meta.hasDisplayName();
			};
		}
		String expectedName = StringHelper.removeFormattingCodes(value).trim();
		return item -> {
			ItemMeta meta = item.getItemMeta();
			if (meta == null || !meta.hasDisplayName()) {
				return false;
			}
			return StringHelper.removeFormattingCodes(meta.getDisplayName()).trim().equalsIgnoreCase(expectedName);
		};
	}

	private static Predicate<ItemStack> parseColorCondition(String value, String key, Logger logger) {
		Color color = parseColor(value);
		if (color == null) {
			logger.warning("Colour \"" + value + "\" used in the ItemBreaks sub-category \"" + key
					+ "\" is invalid. Use a dye colour name (e.g. purple), a hexadecimal value (e.g. #8932b8) or "
					+ "comma-separated RGB components (e.g. 137,50,184).");
			return item -> false;
		}
		return item -> {
			ItemMeta meta = item.getItemMeta();
			return meta instanceof LeatherArmorMeta && color.equals(((LeatherArmorMeta) meta).getColor());
		};
	}

	private static Color parseColor(String value) {
		try {
			if (value.contains(",")) {
				String[] components = StringUtils.split(value, ',');
				if (components.length != 3) {
					return null;
				}
				return Color.fromRGB(Integer.parseInt(components[0].trim()), Integer.parseInt(components[1].trim()),
						Integer.parseInt(components[2].trim()));
			}
			if (value.startsWith("#")) {
				return Color.fromRGB(Integer.parseInt(value.substring(1).trim(), 16));
			}
			return DyeColor.valueOf(value.toUpperCase(Locale.ROOT).replace(' ', '_')).getColor();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
