package com.hm.achievement.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.boss.BarColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;

class ColorHelperTest {

	@Test
	void shouldTranslateBothHexFormsAlongsideLegacyCodes() {
		String first = net.md_5.bungee.api.ChatColor.of("#12AB34").toString();
		String second = net.md_5.bungee.api.ChatColor.of("#aBcDeF").toString();

		assertEquals("§aLegacy " + first + "first §lbold " + second + "second",
				ColorHelper.translateColorCodes("&aLegacy &#12AB34first &lbold #aBcDeFsecond"));
		assertEquals("invalid #12FG34 and &#12345",
				ColorHelper.translateColorCodes("invalid #12FG34 and &#12345"));
	}

	@Test
	void shouldValidateStandaloneColorsAndPreserveLegacyEffects() {
		assertTrue(ColorHelper.isColorCode("5"));
		assertTrue(ColorHelper.isColorCode("#12AB34"));
		assertTrue(ColorHelper.isColorCode("&#12AB34"));
		assertFalse(ColorHelper.isColorCode("#12345"));
		assertFalse(ColorHelper.isColorCode("#1234567"));
		assertFalse(ColorHelper.isColorCode("l"));
		assertEquals(ChatColor.DARK_PURPLE.toString(), ColorHelper.colorCode("5"));
		assertEquals(Color.fromRGB(0x12, 0xAB, 0x34), ColorHelper.fireworkColor("#12AB34"));
		assertEquals(BarColor.RED, ColorHelper.barColor("#FF5555"));
		assertEquals(BarColor.PURPLE, ColorHelper.barColor("5"));
	}

	@ParameterizedTest
	@EnumSource(value = ChatColor.class, mode = Mode.EXCLUDE, names = { "MAGIC", "BOLD", "STRIKETHROUGH", "UNDERLINE",
			"ITALIC", "RESET" })
	void shouldConvertChatColorsToColor(ChatColor chatColor) {
		Color color = ColorHelper.convertChatColorToColor(chatColor);

		java.awt.Color javaColor = chatColor.asBungee().getColor();
		assertEquals(Color.fromRGB(javaColor.getRed(), javaColor.getGreen(), javaColor.getBlue()), color);
	}

	@ParameterizedTest
	@EnumSource(value = ChatColor.class, mode = Mode.INCLUDE, names = { "MAGIC", "BOLD", "STRIKETHROUGH", "UNDERLINE",
			"ITALIC", "RESET" })
	void shouldConvertChatFormatsToWhite(ChatColor chatColor) {
		Color color = ColorHelper.convertChatColorToColor(chatColor);

		assertEquals(Color.WHITE, color);
	}

	@ParameterizedTest
	@MethodSource("chatAndBarColors")
	void shouldConvertChatColorToBarColor(ChatColor chatColor, BarColor barColor) {
		BarColor color = ColorHelper.convertChatColorToBarColor(chatColor);

		assertEquals(barColor, color);
	}

	static Stream<Arguments> chatAndBarColors() {
		return Stream.of(
				Arguments.of(ChatColor.BLACK, BarColor.PURPLE),
				Arguments.of(ChatColor.DARK_BLUE, BarColor.BLUE),
				Arguments.of(ChatColor.DARK_GREEN, BarColor.GREEN),
				Arguments.of(ChatColor.DARK_AQUA, BarColor.BLUE),
				Arguments.of(ChatColor.DARK_RED, BarColor.RED),
				Arguments.of(ChatColor.DARK_PURPLE, BarColor.PURPLE),
				Arguments.of(ChatColor.GOLD, BarColor.YELLOW),
				Arguments.of(ChatColor.GRAY, BarColor.WHITE),
				Arguments.of(ChatColor.DARK_GRAY, BarColor.PURPLE),
				Arguments.of(ChatColor.BLUE, BarColor.BLUE),
				Arguments.of(ChatColor.GREEN, BarColor.GREEN),
				Arguments.of(ChatColor.AQUA, BarColor.GREEN),
				Arguments.of(ChatColor.RED, BarColor.RED),
				Arguments.of(ChatColor.LIGHT_PURPLE, BarColor.PURPLE),
				Arguments.of(ChatColor.YELLOW, BarColor.YELLOW),
				Arguments.of(ChatColor.WHITE, BarColor.WHITE),
				Arguments.of(ChatColor.MAGIC, BarColor.WHITE),
				Arguments.of(ChatColor.BOLD, BarColor.WHITE),
				Arguments.of(ChatColor.STRIKETHROUGH, BarColor.WHITE),
				Arguments.of(ChatColor.UNDERLINE, BarColor.WHITE),
				Arguments.of(ChatColor.ITALIC, BarColor.WHITE),
				Arguments.of(ChatColor.RESET, BarColor.WHITE));
	}

}
