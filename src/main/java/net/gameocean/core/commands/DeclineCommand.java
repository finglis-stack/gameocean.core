package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeclineCommand implements CommandExecutor {

    private final GameOceanCore plugin;

    public DeclineCommand(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.format("&cCette commande ne peut etre utilisee que par un joueur."));
            return true;
        }

        Player player = (Player) sender;

        // Verifier que c'est en mode LOBBY
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) {
            return true;
        }

        // Verifier que le joueur est en attente d'acceptation
        if (plugin.getTermsManager() != null && plugin.getTermsManager().isPendingAcceptance(player.getUniqueId())) {
            plugin.getTermsManager().handleDecline(player);
        } else {
            player.sendMessage(MessageUtils.format("&cVous n'avez pas de conditions en attente d'acceptation."));
        }

        return true;
    }
}
