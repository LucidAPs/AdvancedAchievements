package com.hm.achievement.advancement;

import static com.hm.achievement.advancement.AchievementAdvancement.CRITERIA_NAME;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.Bukkit;

public class AdvancementJsonHelper {

	// 1.20.5+ uses ItemStack "id"/"count"/"components" format (also applies to advancement icon).
	private static final boolean NEW_ITEMSTACK_FORMAT = isAtLeast(1, 20, 5);

	public static String toJson(AchievementAdvancement aa) {
		return "{\n" +
				"  \"criteria\":{\n" +
				"    \"" + CRITERIA_NAME + "\":{\n" +
				"      \"trigger\":\"minecraft:impossible\"\n" +
				"    }\n" +
				"  },\n" +
				"  \"requirements\":[\n" +
				"    [\n" +
				"      \"" + CRITERIA_NAME + "\"\n" +
				"    ]\n" +
				"  ],\n" +
				"  \"display\":{\n" +
				"    \"icon\":{\n" +
				iconLine(aa) +
				"    },\n" +
				"    \"title\":\"" + StringEscapeUtils.escapeJson(aa.getTitle()) + "\",\n" +
				"    \"description\":\"" + StringEscapeUtils.escapeJson(aa.getDescription()) + "\",\n" +
				"    \"frame\":\"" + aa.getFrame() + "\",\n" +
				"    \"announce_to_chat\":false" +
				getStringFieldOrLineBreak("background", aa.getBackground(), 4) +
				"  }" +
				getStringFieldOrLineBreak("parent", aa.getParent(), 2) +
				"}\n";
	}

	public static String toHiddenJson(String background) {
		return "{\n" +
				"  \"criteria\":{\n" +
				"    \"" + CRITERIA_NAME + "\":{\n" +
				"      \"trigger\":\"minecraft:impossible\"\n" +
				"    }\n" +
				"  },\n" +
				"  \"requirements\":[\n" +
				"    [\n" +
				"      \"" + CRITERIA_NAME + "\"\n" +
				"    ]\n" +
				"  ],\n" +
				"  \"background\":\"" + background + "\"\n" +
				"}\n";
	}

	private static String iconLine(AchievementAdvancement aa) {
		String id = normalizeItemId(aa.getIconItem());

		if (NEW_ITEMSTACK_FORMAT) {
			// New format: id / count / components (count optional, defaults to 1)
			return "      \"id\":\"" + id + "\"\n";
		}

		// Old format: item + optional legacy data
		return "      \"item\":\"" + id + "\"" + getIntegerFieldOrEmpty("data", aa.getIconData()) + "\n";
	}

	private static String normalizeItemId(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "minecraft:stone";
		}
		return raw.contains(":") ? raw : ("minecraft:" + raw);
	}

	private static boolean isAtLeast(int major, int minor, int patch) {
		int[] v = parseMcVersion(Bukkit.getMinecraftVersion()); // e.g. "1.21.11"
		if (v[0] != major) return v[0] > major;
		if (v[1] != minor) return v[1] > minor;
		return v[2] >= patch;
	}

	private static int[] parseMcVersion(String s) {
		// returns [major, minor, patch], missing parts default to 0
		int major = 0, minor = 0, patch = 0;
		try {
			String[] p = s.split("\\.");
			major = p.length > 0 ? Integer.parseInt(p[0]) : 0;
			minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
			patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
		} catch (NumberFormatException ignored) {
		}
		return new int[] { major, minor, patch };
	}

	private static String getIntegerFieldOrEmpty(String key, String value) {
		return value == null ? "" : ",\"" + key + "\":" + value;
	}

	private static String getStringFieldOrLineBreak(String key, String value, int spacing) {
		return value == null ? "\n"
				: ",\n" + StringUtils.repeat(' ', spacing) + "\"" + key + "\":\"" + value + "\"\n";
	}

	private AdvancementJsonHelper() {
		// Not called.
	}
}
