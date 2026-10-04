package com.hm.achievement.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;

class FancyMessageSenderTest {

	@Test
	void shouldPreserveHexColorsAndInteractionsInRichMessages() {
		Player player = mock(Player.class);
		Player.Spigot spigot = mock(Player.Spigot.class);
		when(player.spigot()).thenReturn(spigot);

		new FancyMessageSender().sendHoverableCommandMessage(player, "Default &#12AB34green #FF0000red",
				"/aach list", "&#ABCDEFDetails", "#102030");

		ArgumentCaptor<BaseComponent[]> sent = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(spigot).sendMessage(sent.capture());
		BaseComponent[] components = sent.getValue();
		assertEquals("Default green red", BaseComponent.toPlainText(components));
		assertTrue(Arrays.stream(components).anyMatch(c -> ChatColor.of("#12AB34").equals(c.getColor())));
		assertTrue(Arrays.stream(components).anyMatch(c -> ChatColor.of("#FF0000").equals(c.getColor())));
		for (BaseComponent component : components) {
			assertEquals("/aach list", component.getClickEvent().getValue());
			assertNotNull(component.getHoverEvent());
		}
	}
}
