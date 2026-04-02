package net.gameocean.core.utils;

import org.bukkit.ChatColor;

public class MessageUtils {

    public static final String PREFIX = ChatColor.translateAlternateColorCodes('&', "&6&lG&bO&r ");
    public static final String SERVER_NAME = ChatColor.translateAlternateColorCodes('&', "&6&lGame&bOcean&r");
    public static final String SERVER_NAME_NO_RESET = ChatColor.translateAlternateColorCodes('&', "&6&lGame&bOcean");

    /**
     * Ajoute le prefix GO au message et translate les couleurs
     */
    public static String format(String message) {
        return PREFIX + ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Remplace "GameOcean" par le nom coloré du serveur
     */
    public static String replaceServerName(String message) {
        return message.replace("GameOcean", SERVER_NAME);
    }

    /**
     * Format complet: prefix + couleurs + remplacement nom serveur
     */
    public static String formatWithServerName(String message) {
        return format(replaceServerName(message));
    }
}
