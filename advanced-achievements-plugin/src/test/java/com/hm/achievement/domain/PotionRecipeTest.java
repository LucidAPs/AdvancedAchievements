package com.hm.achievement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class PotionRecipeTest {

	@Test
	void shouldParsePaperPotionTypeAndItemForm() {
		PotionRecipe recipe = PotionRecipe.fromConfig("lingering_potion", "turtle_master");

		assertEquals(Material.LINGERING_POTION, recipe.itemType());
		assertEquals(PotionType.TURTLE_MASTER, recipe.potionType());
		assertEquals("lingering_potion/turtle_master", recipe.key());
	}

	@Test
	void shouldRejectNonPotionItemType() {
		assertThrows(IllegalArgumentException.class, () -> PotionRecipe.fromConfig("diamond", "turtle_master"));
	}

	@Test
	void shouldExtractRecipeFromPotionItem() {
		ItemStack item = mock(ItemStack.class);
		PotionMeta meta = mock(PotionMeta.class);
		when(item.getType()).thenReturn(Material.POTION);
		when(item.getItemMeta()).thenReturn(meta);
		when(meta.getBasePotionType()).thenReturn(PotionType.SLOW_FALLING);

		assertEquals("potion/slow_falling", PotionRecipe.fromItem(item).orElseThrow().key());
	}

	@Test
	void shouldIgnoreCustomPotionWithoutBaseType() {
		ItemStack item = mock(ItemStack.class);
		PotionMeta meta = mock(PotionMeta.class);
		when(item.getType()).thenReturn(Material.POTION);
		when(item.getItemMeta()).thenReturn(meta);

		assertTrue(PotionRecipe.fromItem(item).isEmpty());
	}
}
