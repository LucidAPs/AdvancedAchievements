package com.hm.achievement.advancement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.advancement.AchievementAdvancement.AchievementAdvancementBuilder;
import com.hm.achievement.category.Category;
import com.hm.achievement.category.CommandAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.gui.GUIItems;
import com.hm.achievement.gui.OrderedCategory;
import com.hm.achievement.lifecycle.Reloadable;
import com.hm.achievement.utils.StringHelper;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("deprecation")
@Singleton
public class AdvancementManager implements Reloadable {

	public static final String ADVANCED_ACHIEVEMENTS_PARENT = "advanced_achievements_parent";
	private static final String MINECRAFT_BOOK_KEY = "minecraft:book";
	private static final Pattern REGEX_PATTERN_KEYS = Pattern.compile("[^A-Za-z0-9_]");

	private final YamlConfiguration mainConfig;
	private final GUIItems guiItems;
	private final AdvancedAchievements advancedAchievements;
	private final Logger logger;
	private final Set<Category> disabledCategories;
	private final AchievementMap achievementMap;

	private boolean configRegisterAdvancementDescriptions;
	private boolean configHideAdvancements;
	private String configRootAdvancementTitle;
	private String configBackgroundTexture;
	private int generatedAdvancements;
	private BukkitTask generationTask;
	private static final int DEFAULT_PER_TICK = 10;

	private static final class LoadRequest {
		final NamespacedKey key;
		final String json;
		LoadRequest(NamespacedKey key, String json) {
			this.key = key;
			this.json = json;
		}
	}

	@Inject
	public AdvancementManager(@Named("main") YamlConfiguration mainConfig,
							  GUIItems guiItems,
							  AchievementMap achievementMap,
							  AdvancedAchievements advancedAchievements,
							  Logger logger,
							  Set<Category> disabledCategories) {
		this.mainConfig = mainConfig;
		this.guiItems = guiItems;
		this.advancedAchievements = advancedAchievements;
		this.logger = logger;
		this.disabledCategories = disabledCategories;
		this.achievementMap = achievementMap;
	}

	@Override
	public void extractConfigurationParameters() {
		configRegisterAdvancementDescriptions = mainConfig.getBoolean("RegisterAdvancementDescriptions");
		configHideAdvancements = mainConfig.getBoolean("HideAdvancements");
		configRootAdvancementTitle = mainConfig.getString("RootAdvancementTitle");
		configBackgroundTexture = mainConfig.getString("AdvancementsBackground");
	}

	public static String getKey(String achName) {
		return REGEX_PATTERN_KEYS.matcher(achName).replaceAll("").toLowerCase();
	}

	public void registerAdvancements() {
		registerParentAdvancement();
		registerOtherAdvancements();
	}

	private List<NamespacedKey> findOldAchievementAdvancements() {
		List<NamespacedKey> toRemove = new ArrayList<>();
		Iterator<Advancement> advancements = Bukkit.advancementIterator();
		while (advancements.hasNext()) {
			NamespacedKey key = advancements.next().getKey();
			if ("advancedachievements".equals(key.getNamespace())) {
				toRemove.add(key);
			}
		}
		return toRemove;
	}

	public void ensureRootVisible(Player player) {
		NamespacedKey key = new NamespacedKey(advancedAchievements, ADVANCED_ACHIEVEMENTS_PARENT);
		Advancement adv = Bukkit.getAdvancement(key);
		if (adv == null) return;

		AdvancementProgress progress = player.getAdvancementProgress(adv);
		if (!progress.isDone()) {
			progress.awardCriteria(AchievementAdvancement.CRITERIA_NAME); // "aach_handled"
		}
	}

	private List<LoadRequest> buildLoadRequests(boolean forceRegenerate) {
		List<LoadRequest> loads = new ArrayList<>();

		// Parent
		NamespacedKey parentKey = new NamespacedKey(advancedAchievements, ADVANCED_ACHIEVEMENTS_PARENT);
		if (forceRegenerate || Bukkit.getAdvancement(parentKey) == null) {
			String parentJson;
			if (configHideAdvancements) {
				parentJson = AdvancementJsonHelper.toHiddenJson(configBackgroundTexture);
			} else {
				AchievementAdvancementBuilder builder = new AchievementAdvancementBuilder()
						.iconItem(MINECRAFT_BOOK_KEY)
						.title(configRootAdvancementTitle)
						.description("")
						.background(configBackgroundTexture)
						.type(AdvancementType.GOAL);
				parentJson = AdvancementJsonHelper.toJson(builder.build());
			}
			loads.add(new LoadRequest(parentKey, parentJson));
		}

		// Children
		for (Entry<OrderedCategory, ItemStack> categoryItemPair : guiItems.getOrderedAchievementItems().entrySet()) {
			Category category = categoryItemPair.getKey().getCategory();
			if (disabledCategories.contains(category)) continue;

			ItemStack item = categoryItemPair.getValue();
			String parent = ADVANCED_ACHIEVEMENTS_PARENT;

			List<Achievement> categoryAchievements = achievementMap.getForCategory(category);
			for (int i = 0; i < categoryAchievements.size(); ++i) {
				Achievement achievement = categoryAchievements.get(i);

				boolean last = achievement.getCategory() == CommandAchievements.COMMANDS
						|| i == categoryAchievements.size() - 1
						|| !achievement.getSubcategory().equals(categoryAchievements.get(i + 1).getSubcategory());

				String achKey = getKey(achievement.getName());
				NamespacedKey key = new NamespacedKey(advancedAchievements, achKey);

				// Always advance the chain, even if we skip loading because it already exists
				boolean shouldLoad = forceRegenerate || (Bukkit.getAdvancement(key) == null);

				if (shouldLoad) {
					String displayName = StringHelper.removeFormattingCodes(achievement.getDisplayName());

					String description = "";
					if (configRegisterAdvancementDescriptions) {
						description = StringHelper.removeFormattingCodes(
								StringUtils.replace(achievement.getGoal(), "\\n", " ")
						);
					}

					AchievementAdvancementBuilder builder = new AchievementAdvancementBuilder()
							.iconItem(item.getType().name().toLowerCase())
							.title(displayName)
							.description(description)
							.parent("advancedachievements:" + parent)
							.type(last ? AdvancementType.CHALLENGE : AdvancementType.TASK);

					loads.add(new LoadRequest(key, AdvancementJsonHelper.toJson(builder.build())));
				}

				parent = achKey;
			}
		}

		return loads;
	}

	public void generateAdvancementsIncremental(boolean forceRegenerate,
												CommandSender feedback,
												Runnable onComplete) {
		if (generationTask != null) {
			if (feedback != null) {
				feedback.sendMessage("§cAdvancement generation is already running.");
			}
			return;
		}

		// Build the work list on the main thread (safe for Bukkit calls)
		final List<Runnable> work = new ArrayList<>();

		// If forceRegenerate: remove old ones first (without reloadData)
		final List<NamespacedKey> toRemove = forceRegenerate ? findOldAchievementAdvancements() : List.of();
		for (NamespacedKey key : toRemove) {
			work.add(() -> Bukkit.getUnsafe().removeAdvancement(key));
		}

		final List<LoadRequest> loads = buildLoadRequests(forceRegenerate);
		for (LoadRequest req : loads) {
			work.add(() -> {
				Advancement adv = Bukkit.getUnsafe().loadAdvancement(req.key, req.json);
				if (adv == null) {
					logger.warning("Failed to load advancement: " + req.key);
				}
			});
		}

		if (feedback != null) {
			feedback.sendMessage("§7Generating advancements in small batches to reduce lag...");
		}

		final int perTick = Math.max(1, mainConfig.getInt("AdvancementGenerationPerTick", DEFAULT_PER_TICK));
		final int[] index = {0};

		generationTask = Bukkit.getScheduler().runTaskTimer(advancedAchievements, () -> {
			int processed = 0;
			while (processed < perTick && index[0] < work.size()) {
				work.get(index[0]++).run();
				processed++;
			}

			if (index[0] >= work.size()) {
				generationTask.cancel();
				generationTask = null;

				// Make tab appear immediately for online players
				Bukkit.getOnlinePlayers().forEach(this::ensureRootVisible);

				if (feedback != null) {
					feedback.sendMessage("§aAdvancements generation finished.");
				}
				if (onComplete != null) onComplete.run();
			}
		}, 1L, 1L);
	}

	private void registerParentAdvancement() {
		NamespacedKey namespacedKey = new NamespacedKey(advancedAchievements, ADVANCED_ACHIEVEMENTS_PARENT);
		if (Bukkit.getAdvancement(namespacedKey) != null) {
			return;
		}

		String json;
		if (configHideAdvancements) {
			json = AdvancementJsonHelper.toHiddenJson(configBackgroundTexture);
		} else {
			AchievementAdvancementBuilder builder = new AchievementAdvancementBuilder()
					.iconItem(MINECRAFT_BOOK_KEY)
					.title(configRootAdvancementTitle)
					.description("")
					.background(configBackgroundTexture)
					.type(AdvancementType.GOAL);
			json = AdvancementJsonHelper.toJson(builder.build());
		}

		Advancement adv = Bukkit.getUnsafe().loadAdvancement(namespacedKey, json);
		if (adv == null) {
			logger.warning("Failed to load parent advancement: " + namespacedKey);
		}
	}

	private void registerOtherAdvancements() {
		generatedAdvancements = 1;
		for (Entry<OrderedCategory, ItemStack> categoryItemPair : guiItems.getOrderedAchievementItems().entrySet()) {
			Category category = categoryItemPair.getKey().getCategory();
			if (disabledCategories.contains(category)) {
				continue;
			}

			ItemStack item = categoryItemPair.getValue();
			String parentKey = ADVANCED_ACHIEVEMENTS_PARENT;
			List<Achievement> categoryAchievements = achievementMap.getForCategory(category);

			for (int i = 0; i < categoryAchievements.size(); ++i) {
				Achievement achievement = categoryAchievements.get(i);
				boolean last = achievement.getCategory() == CommandAchievements.COMMANDS
						|| i == categoryAchievements.size() - 1
						|| !achievement.getSubcategory().equals(categoryAchievements.get(i + 1).getSubcategory());

				parentKey = registerAdvancement(item, achievement, parentKey, last);
			}
		}

		logger.info("Generated " + generatedAdvancements + " new advancements.");
	}

	private String registerAdvancement(ItemStack item, Achievement achievement, String parentKey, boolean lastAchievement) {
		String displayName = StringHelper.removeFormattingCodes(achievement.getDisplayName());

		String achKey = getKey(achievement.getName());
		NamespacedKey namespacedKey = new NamespacedKey(advancedAchievements, achKey);

		String description = "";
		if (configRegisterAdvancementDescriptions) {
			description = StringHelper.removeFormattingCodes(StringUtils.replace(achievement.getGoal(), "\\n", " "));
		}

		AchievementAdvancementBuilder builder = new AchievementAdvancementBuilder()
				.iconItem(item.getType().name().toLowerCase())
				.title(displayName)
				.description(description)
				.parent("advancedachievements:" + parentKey)
				.type(lastAchievement ? AdvancementType.CHALLENGE : AdvancementType.TASK);

		String json = AdvancementJsonHelper.toJson(builder.build());
		Advancement adv = Bukkit.getUnsafe().loadAdvancement(namespacedKey, json);

		if (adv == null) {
			logger.warning("Failed to load advancement: " + namespacedKey);
			return parentKey;
		}

		++generatedAdvancements;
		return achKey;
	}
}
