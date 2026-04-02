package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptCommand implements CommandExecutor {

    private final GameOceanCore plugin;

    public AcceptCommand(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtils.format("&cCette commande ne peut etre utilisee que par un joueur."));
            return true;
        }

        // 1. Logique des Termes d'utilisation (sans arguments, en LOBBY)
        if (args.length == 0) {
            if ("LOBBY".equalsIgnoreCase(plugin.getServerType())) {
                if (plugin.getTermsManager() != null && plugin.getTermsManager().isPendingAcceptance(player.getUniqueId())) {
                    plugin.getTermsManager().handleAccept(player);
                    return true;
                }
            }
            player.sendMessage(MessageUtils.format("&cUsage pour un appartement : /accept <pseudo>"));
            return true;
        }

        // 2. Logique d'invitation d'Appartement
        String hostName = args[0];
        String targetName = player.getName();
        
        if (plugin.getRedisManager() == null || !plugin.getRedisManager().isConnected()) {
            player.sendMessage(MessageUtils.format("&cLe système d'invitation est temporairement indisponible."));
            return true;
        }

        // On vérifie si la clé existe dans Redis
        String redisKey = "apartment_invite:" + targetName + ":" + hostName;
        String serverName = plugin.getRedisManager().getSync(redisKey);

        if (serverName != null && !serverName.isEmpty()) {
            player.sendMessage(MessageUtils.format("&aTéléportation vers l'appartement de " + hostName + "..."));

            // Stocker la visite dans Redis (valide 5 minutes max)
            String visitKey = "apartment_visit:" + player.getUniqueId().toString();
            plugin.getRedisManager().set(visitKey, hostName, 300);

            // Supprimer l'invitation pour l'utiliser une seule fois
            plugin.getRedisManager().delete(redisKey);

            // Chercher si l'appartement du propriétaire est déjà chargé sur CE serveur via Redis
            // On lookup d'abord l'UUID du host pour avoir la clé apartment_instance
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Trouver l'UUID du host (peut être en ligne sur ce serveur)
                Player hostOnServer = Bukkit.getPlayer(hostName);
                String instanceKey = null;

                if (hostOnServer != null) {
                    instanceKey = "apartment_instance:" + hostOnServer.getUniqueId().toString();
                } else {
                    // Chercher l'UUID via la DB
                    String uuidFromDb = null;
                    if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                             java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM profiles WHERE username = ? LIMIT 1")) {
                            stmt.setString(1, hostName);
                            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) uuidFromDb = rs.getString("uuid");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("[AcceptCommand] Erreur lookup UUID du host: " + e.getMessage());
                        }
                    }
                    if (uuidFromDb != null) {
                        instanceKey = "apartment_instance:" + uuidFromDb;
                    }
                }

                final boolean isSameServer;
                if (instanceKey != null) {
                    String instanceServer = plugin.getRedisManager().getSync(instanceKey);
                    // Si l'instance de l'appartement est sur ce serveur (peut correspondre au serverId réel
                    // OU le serverId est "unknown-server" mais le host est présent physiquement en ligne ici)
                    String currentServerId = plugin.getServerId();
                    isSameServer = hostOnServer != null ||
                            (instanceServer != null && !instanceServer.isEmpty()
                                    && !instanceServer.equals("unknown-server")
                                    && !currentServerId.equals("unknown-server")
                                    && instanceServer.equals(currentServerId));
                } else {
                    // Aucune info : fallback sur la comparaison du serverName de l'invitation
                    String currentServerId = plugin.getServerId();
                    isSameServer = !currentServerId.equals("unknown-server") && serverName.equals(currentServerId);
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    if (isSameServer) {
                        // Le joueur est DEJA sur le bon serveur - TP direct
                        if (plugin.getApartmentManager() != null) {
                            plugin.getApartmentManager().joinApartment(player);
                        }
                    } else {
                        // Connecter via Bungee vers l'autre serveur
                        plugin.getBungeeMessaging().connectPlayerToServer(player, serverName);
                    }
                });
            });
        } else {
            player.sendMessage(MessageUtils.format("&cAucune invitation valide de ce joueur ou invitation expirée."));
        }

        return true;
    }
}
