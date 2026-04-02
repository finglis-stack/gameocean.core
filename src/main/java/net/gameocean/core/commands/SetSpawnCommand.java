package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final GameOceanCore plugin;

    public SetSpawnCommand(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Seuls les OP peuvent utiliser cette commande
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Seuls les opérateurs peuvent utiliser cette commande.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        // Vérifier que le serveur est en mode LOBBY
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) {
            sender.sendMessage(ChatColor.RED + "Cette commande n'est disponible qu'en mode LOBBY.");
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation();

        // Sauvegarder le spawn dans la config
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", loc.getYaw());
        plugin.getConfig().set("spawn.pitch", loc.getPitch());
        plugin.saveConfig();

        player.sendMessage(MessageUtils.format("&aSpawn du lobby défini à votre position actuelle !"));
        player.sendMessage(MessageUtils.format("&7Location: &f" + loc.getWorld().getName() + " " +
                String.format("%.2f", loc.getX()) + " " +
                String.format("%.2f", loc.getY()) + " " +
                String.format("%.2f", loc.getZ())));

        return true;
    }
}
