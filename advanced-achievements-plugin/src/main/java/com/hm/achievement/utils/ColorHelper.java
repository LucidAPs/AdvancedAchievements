package com.hm.achievement.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.boss.BarColor;

public class ColorHelper {

	private static final Pattern HEX_CODE = Pattern.compile("(?i)&?#([0-9a-f]{6})");
	private static final Pattern STANDALONE_HEX = Pattern.compile("(?i)&?#[0-9a-f]{6}");

	public static String translateHexCodes(String text) {
		Matcher matcher = HEX_CODE.matcher(text);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(result, Matcher.quoteReplacement(
					net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString()));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	public static String translateColorCodes(String text) {
		return ChatColor.translateAlternateColorCodes('&', translateHexCodes(text));
	}

	public static boolean isColorCode(String code) {
		if (code == null) {
			return false;
		}
		if (STANDALONE_HEX.matcher(code).matches()) {
			return true;
		}
		ChatColor legacy = code.length() == 1 ? ChatColor.getByChar(code) : null;
		return legacy != null && legacy.isColor();
	}

	public static String colorCode(String code) {
		if (!isColorCode(code)) {
			throw new IllegalArgumentException("Invalid color code: " + code);
		}
		return STANDALONE_HEX.matcher(code).matches() ? translateHexCodes(code) : ChatColor.getByChar(code).toString();
	}

	public static net.md_5.bungee.api.ChatColor bungeeColor(String code) {
		return STANDALONE_HEX.matcher(code).matches()
				? net.md_5.bungee.api.ChatColor.of(code.startsWith("&") ? code.substring(1) : code)
				: (code.length() == 1 ? ChatColor.getByChar(code) : ChatColor.valueOf(code.toUpperCase())).asBungee();
	}

	public static Color fireworkColor(String code) {
		return STANDALONE_HEX.matcher(code).matches()
				? Color.fromRGB(Integer.parseInt(code.substring(code.indexOf('#') + 1), 16))
				: convertChatColorToColor(ChatColor.getByChar(code));
	}

	public static BarColor barColor(String code) {
		if (!STANDALONE_HEX.matcher(code).matches()) {
			return convertChatColorToBarColor(ChatColor.getByChar(code));
		}
		Color requested = fireworkColor(code);
		BarColor nearest = BarColor.WHITE;
		long bestDistance = Long.MAX_VALUE;
		for (BarColor candidate : BarColor.values()) {
			ChatColor representative = switch (candidate) {
				case PINK -> ChatColor.LIGHT_PURPLE;
				case BLUE -> ChatColor.BLUE;
				case RED -> ChatColor.RED;
				case GREEN -> ChatColor.GREEN;
				case YELLOW -> ChatColor.YELLOW;
				case PURPLE -> ChatColor.DARK_PURPLE;
				case WHITE -> ChatColor.WHITE;
			};
			Color available = convertChatColorToColor(representative);
			long red = requested.getRed() - available.getRed();
			long green = requested.getGreen() - available.getGreen();
			long blue = requested.getBlue() - available.getBlue();
			long distance = red * red + green * green + blue * blue;
			if (distance < bestDistance) {
				bestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	public static Color convertChatColorToColor(ChatColor chatColor) {
		switch (chatColor) {
			case AQUA:
				return Color.fromRGB(0x55, 0xFF, 0xFF);
			case BLACK:
				return Color.BLACK;
			case BLUE:
				return Color.fromRGB(0x55, 0x55, 0xFF);
			case GRAY:
				return Color.fromRGB(0xAA, 0xAA, 0xAA);
			case DARK_AQUA:
				return Color.fromRGB(0x00, 0xAA, 0xAA);
			case DARK_BLUE:
				return Color.fromRGB(0x00, 0x00, 0xAA);
			case DARK_GRAY:
				return Color.fromRGB(0x55, 0x55, 0x55);
			case DARK_GREEN:
				return Color.fromRGB(0x00, 0xAA, 0x00);
			case DARK_PURPLE:
				return Color.fromRGB(0xAA, 0x00, 0xAA);
			case DARK_RED:
				return Color.fromRGB(0xAA, 0x00, 0x00);
			case GOLD:
				return Color.fromRGB(0xFF, 0xAA, 0x00);
			case GREEN:
				return Color.fromRGB(0x55, 0xFF, 0x55);
			case LIGHT_PURPLE:
				return Color.fromRGB(0xFF, 0x55, 0xFF);
			case RED:
				return Color.fromRGB(0xFF, 0x55, 0x55);
			case WHITE:
				return Color.WHITE;
			case YELLOW:
				return Color.fromRGB(0xFF, 0xFF, 0x55);
			default:
				return Color.WHITE;
		}
	}

	public static BarColor convertChatColorToBarColor(ChatColor chatColor) {
		switch (chatColor) {
			case AQUA:
				return BarColor.GREEN;
			case BLACK:
				return BarColor.PURPLE;
			case BLUE:
				return BarColor.BLUE;
			case GRAY:
				return BarColor.WHITE;
			case DARK_AQUA:
				return BarColor.BLUE;
			case DARK_BLUE:
				return BarColor.BLUE;
			case DARK_GRAY:
				return BarColor.PURPLE;
			case DARK_GREEN:
				return BarColor.GREEN;
			case DARK_PURPLE:
				return BarColor.PURPLE;
			case DARK_RED:
				return BarColor.RED;
			case GOLD:
				return BarColor.YELLOW;
			case GREEN:
				return BarColor.GREEN;
			case LIGHT_PURPLE:
				return BarColor.PURPLE;
			case RED:
				return BarColor.RED;
			case WHITE:
				return BarColor.WHITE;
			case YELLOW:
				return BarColor.YELLOW;
			default:
				return BarColor.WHITE;
		}
	}

	private ColorHelper() {
		// Not called.
	}

}
