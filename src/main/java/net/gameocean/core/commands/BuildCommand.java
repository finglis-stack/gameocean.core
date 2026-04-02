package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BuildCommand implements CommandExecutor {

    private final GameOceanCore plugin;

    public BuildCommand(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seul un joueur peut utiliser cette commande.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Cette commande est réservée aux opérateurs.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (plugin.getLobbyBuilders().contains(uuid)) {
            plugin.getLobbyBuilders().remove(uuid);
            player.sendMessage(ChatColor.RED + "Mode construction désactivé.");
        } else {
            plugin.getLobbyBuilders().add(uuid);
            player.sendMessage(ChatColor.GREEN + "Mode construction activé. Vous pouvez modifier la map.");
        }

        return true;
    }
}
