package com.hm.achievement.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Identifies a brewed potion by both its item form and its Paper potion type.
 */
public final class PotionRecipe {

	private static final Set<Material> POTION_ITEMS = Set.of(Material.POTION, Material.SPLASH_POTION,
			Material.LINGERING_POTION);

	private final Material itemType;
	private final PotionType potionType;

	public PotionRecipe(Material itemType, PotionType potionType) {
		Objects.requireNonNull(itemType, "itemType");
		Objects.requireNonNull(potionType, "potionType");
		if (!POTION_ITEMS.contains(itemType)) {
			throw new IllegalArgumentException(itemType + " is not a potion item type.");
		}
		this.itemType = itemType;
		this.potionType = potionType;
	}

	/**
	 * Parses the two keys used below {@code Brewing.Recipes} in config.yml.
	 *
	 * @param itemTypeKey potion item key, for example {@code lingering_potion}
	 * @param potionTypeKey Paper potion type key, for example {@code turtle_master}
	 * @return the parsed recipe
	 * @throws IllegalArgumentException if either key is invalid
	 */
	public static PotionRecipe fromConfig(String itemTypeKey, String potionTypeKey) {
		Material itemType = Material.matchMaterial(itemTypeKey);
		if (itemType == null || !POTION_ITEMS.contains(itemType)) {
			throw new IllegalArgumentException("Potion item type must be potion, splash_potion or lingering_potion.");
		}

		NamespacedKey key = NamespacedKey.fromString(potionTypeKey.toLowerCase(Locale.ROOT));
		PotionType potionType = key == null
				? null
				: Arrays.stream(PotionType.values()).filter(type -> type.getKey().equals(key)).findFirst().orElse(null);
		if (potionType == null) {
			throw new IllegalArgumentException("Unknown Paper PotionType: " + potionTypeKey + ".");
		}
		return new PotionRecipe(itemType, potionType);
	}

	/**
	 * Extracts an exact recipe identifier from an item produced by a brewing stand.
	 *
	 * @param item potion item
	 * @return the recipe, or empty for non-potions and custom potions without a base type
	 */
	public static Optional<PotionRecipe> fromItem(ItemStack item) {
		if (item == null || !POTION_ITEMS.contains(item.getType())) {
			return Optional.empty();
		}
		ItemMeta itemMeta = item.getItemMeta();
		if (!(itemMeta instanceof PotionMeta)) {
			return Optional.empty();
		}
		PotionMeta meta = (PotionMeta) itemMeta;
		PotionType type = meta.getBasePotionType();
		return type == null ? Optional.empty() : Optional.of(new PotionRecipe(item.getType(), type));
	}

	public Material itemType() {
		return itemType;
	}

	public PotionType potionType() {
		return potionType;
	}

	/**
	 * @return stable subcategory key used by achievements and database statistics
	 */
	public String key() {
		return itemType.name().toLowerCase(Locale.ROOT) + "/" + potionType.getKey().getKey();
	}
}
