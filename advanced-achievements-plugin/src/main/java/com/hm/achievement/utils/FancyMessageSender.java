package com.hm.achievement.utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * Class used to send fancy messages to the player; can be titles, hoverable chat messages or action bar messages. All
 * methods are static and this class cannot be instanciated.
 *
 * @author Pyves
 *
 */
@Singleton
public final class FancyMessageSender {

	@Inject
	public FancyMessageSender() {
	}

	/**
	 * Sends a hoverable message to the player.
	 *
	 * @param player Online player to send the message to.
	 * @param message The text to display in the chat.
	 * @param hover The text to display in the hover.
	 * @param color The color of the hover text.
	 */
	@SuppressWarnings("deprecation")
	public void sendHoverableMessage(Player player, String message, String hover, String color) {
		BaseComponent[] components = TextComponent.fromLegacyText(ColorHelper.translateColorCodes(message),
				ColorHelper.bungeeColor(color));
		HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new Text(TextComponent.fromLegacyText(ColorHelper.translateColorCodes(hover))));
		for (BaseComponent component : components) {
			component.setHoverEvent(hoverEvent);
		}
		player.spigot().sendMessage(components);
	}

	/**
	 * Sends a clickable and hoverable message to the player.
	 *
	 * @param player Online player to send the message to.
	 * @param message The text to display in the chat.
	 * @param command The command that is entered when clicking on the message.
	 * @param hover The text to display in the hover.
	 * @param color The color of the hover text.
	 */
	@SuppressWarnings("deprecation")
	public void sendHoverableCommandMessage(Player player, String message, String command, String hover,
			String color) {
		BaseComponent[] components = TextComponent.fromLegacyText(ColorHelper.translateColorCodes(message),
				ColorHelper.bungeeColor(color));
		ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
		HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new Text(TextComponent.fromLegacyText(ColorHelper.translateColorCodes(hover))));
		for (BaseComponent component : components) {
			component.setClickEvent(clickEvent);
			component.setHoverEvent(hoverEvent);
		}
		player.spigot().sendMessage(components);
	}
}
