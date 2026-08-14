package com.hm.achievement.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.function.IntPredicate;
import java.util.logging.Logger;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemFilterTest {

	private static final Logger LOGGER = Logger.getLogger("ItemFilterTestLogger");
	private static final MaterialHelper MATERIAL_HELPER = new MaterialHelper(LOGGER);

	@Mock
	private ItemStack itemStack;

	@Mock
	private ItemMeta itemMeta;

	@Mock
	private LeatherArmorMeta leatherArmorMeta;

	@Test
	void shouldMatchAnyItem() {
		assertTrue(parse("any").matches(itemStack));
	}

	@Test
	void shouldMatchMaterial() {
		when(itemStack.getType()).thenReturn(Material.NETHERITE_LEGGINGS);

		assertTrue(parse("netherite_leggings").matches(itemStack));
	}

	@Test
	void shouldNotMatchOtherMaterial() {
		when(itemStack.getType()).thenReturn(Material.WOODEN_HOE);

		assertFalse(parse("material:netherite_leggings").matches(itemStack));
	}

	@Test
	void shouldMatchOneOfTheGroupedMaterials() {
		when(itemStack.getType()).thenReturn(Material.STONE_HOE);

		assertTrue(parse("wooden_hoe|stone_hoe").matches(itemStack));
	}

	@Test
	void shouldNotMatchUnknownCondition() {
		assertFalse(parse("nonsense:value").matches(itemStack));
	}

	@Test
	void shouldMatchEnchantmentName() {
		assertTrue(ItemFilter.matchesEnchantmentName(NamespacedKey.minecraft("unbreaking"), "unbreaking"));
		assertTrue(ItemFilter.matchesEnchantmentName(NamespacedKey.minecraft("mending"), "minecraft:mending"));
		assertFalse(ItemFilter.matchesEnchantmentName(NamespacedKey.minecraft("mending"), "unbreaking"));
	}

	@Test
	void shouldMatchExactEnchantmentLevel() {
		IntPredicate level = ItemFilter.parseLevelPredicate("3", "wooden_hoe;enchant:unbreaking:3", LOGGER);

		assertTrue(level.test(3));
		assertFalse(level.test(2));
		assertFalse(level.test(4));
	}

	@Test
	void shouldMatchEnchantmentLevelWithOperator() {
		assertTrue(ItemFilter.parseLevelPredicate(">=3", "enchant:unbreaking:>=3", LOGGER).test(4));
		assertFalse(ItemFilter.parseLevelPredicate(">=3", "enchant:unbreaking:>=3", LOGGER).test(2));
		assertTrue(ItemFilter.parseLevelPredicate("<2", "enchant:unbreaking:<2", LOGGER).test(1));
		assertFalse(ItemFilter.parseLevelPredicate("<2", "enchant:unbreaking:<2", LOGGER).test(2));
	}

	@Test
	void shouldNotMatchEnchantmentOfItemWithoutMeta() {
		assertFalse(parse("enchant:unbreaking:3").matches(itemStack));
	}

	@Test
	void shouldNotMatchEmptyEnchantmentCondition() {
		assertFalse(parse("enchant:").matches(itemStack));
	}

	@Test
	void shouldMatchCustomName() {
		when(itemStack.getItemMeta()).thenReturn(itemMeta);
		when(itemMeta.hasDisplayName()).thenReturn(true);
		when(itemMeta.getDisplayName()).thenReturn("§5Purple Shorts");

		assertTrue(parse("name:purple shorts").matches(itemStack));
	}

	@Test
	void shouldMatchAnyCustomName() {
		when(itemStack.getItemMeta()).thenReturn(itemMeta);
		when(itemMeta.hasDisplayName()).thenReturn(true);

		assertTrue(parse("name:*").matches(itemStack));
	}

	@Test
	void shouldNotMatchItemWithoutCustomName() {
		when(itemStack.getItemMeta()).thenReturn(itemMeta);
		when(itemMeta.hasDisplayName()).thenReturn(false);

		assertFalse(parse("name:Purple Shorts").matches(itemStack));
	}

	@Test
	void shouldMatchDyedLeatherArmourWithName() {
		when(itemStack.getType()).thenReturn(Material.LEATHER_LEGGINGS);
		when(itemStack.getItemMeta()).thenReturn(leatherArmorMeta);
		when(leatherArmorMeta.getColor()).thenReturn(DyeColor.PURPLE.getColor());
		when(leatherArmorMeta.hasDisplayName()).thenReturn(true);
		when(leatherArmorMeta.getDisplayName()).thenReturn("Purple Shorts");

		assertTrue(
				parse("leather_leggings;color:purple;name:Purple Shorts").matches(itemStack));
	}

	@Test
	void shouldMatchHexadecimalColour() {
		when(itemStack.getItemMeta()).thenReturn(leatherArmorMeta);
		when(leatherArmorMeta.getColor()).thenReturn(DyeColor.PURPLE.getColor());

		assertTrue(parse("color:#8932b8").matches(itemStack));
	}

	@Test
	void shouldNotMatchOtherColour() {
		when(itemStack.getItemMeta()).thenReturn(leatherArmorMeta);
		when(leatherArmorMeta.getColor()).thenReturn(DyeColor.RED.getColor());

		assertFalse(parse("color:purple").matches(itemStack));
	}

	@Test
	void shouldNotMatchColourOfNonDyeableItem() {
		when(itemStack.getItemMeta()).thenReturn(itemMeta);

		assertFalse(parse("color:purple").matches(itemStack));
	}

	@Test
	void shouldMatchUnbreakableItem() {
		when(itemStack.getItemMeta()).thenReturn(itemMeta);
		when(itemMeta.isUnbreakable()).thenReturn(true);

		assertTrue(parse("unbreakable").matches(itemStack));
	}

	private static ItemFilter parse(String key) {
		return ItemFilter.parse(key, MATERIAL_HELPER, LOGGER);
	}

}
