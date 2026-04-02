package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InviteCommand implements CommandExecutor {

    private final GameOceanCore plugin;

    public InviteCommand(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.format("&cCette commande est reservee aux joueurs."));
            return true;
        }

        if (!"MINIGAME".equalsIgnoreCase(plugin.getServerType()) || !"APARTMENT".equalsIgnoreCase(plugin.getMinigameType())) {
            player.sendMessage(MessageUtils.format("&cVous ne pouvez inviter des joueurs que depuis votre appartement."));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(MessageUtils.format("&cUsage: /invite <pseudo>"));
            return true;
        }

        String targetName = args[0];

        if (player.getName().equalsIgnoreCase(targetName)) {
            player.sendMessage(MessageUtils.format("&cVous ne pouvez pas vous inviter vous-même."));
            return true;
        }

        // Check if Redis is enabled
        if (plugin.getRedisManager() == null || !plugin.getRedisManager().isConnected()) {
            player.sendMessage(MessageUtils.format("&cErreur : Le système d'invitation (Redis) n'est pas actif."));
            return true;
        }

        player.sendMessage(MessageUtils.format("&eEnvoi de l'invitation a " + targetName + "..."));

        // Request current ServerName via BungeeMessaging, and then send the Redis publish
        plugin.getBungeeMessaging().requestServerName(player, serverName -> {
            if (serverName != null && !serverName.isEmpty()) {
                // Publish on gameocean:apartment_invite
                // Format: hostName:targetName:serverName
                String message = player.getName() + ":" + targetName + ":" + serverName;
                plugin.getRedisManager().publish("gameocean:apartment_invite", message);
                
                player.sendMessage(MessageUtils.format("&aInvitation envoyée à " + targetName + " !"));
            } else {
                player.sendMessage(MessageUtils.format("&cErreur lors de la récupération du nom du serveur."));
            }
        });

        return true;
    }
}
