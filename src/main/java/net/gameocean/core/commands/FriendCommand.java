package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FriendCommand implements CommandExecutor {

    private final GameOceanCore plugin;

    public FriendCommand(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Commande reservee aux joueurs.");
            return true;
        }

        Player player = (Player) sender;
        
        if (plugin.getFriendManager() == null) {
            player.sendMessage(ChatColor.RED + "Le systeme d'ami est en cours de demarrage ou inactif.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "add":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend add <joueur>");
                    break;
                }
                handleAdd(player, args[1]);
                break;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend accept <joueur>");
                    break;
                }
                handleAccept(player, args[1]);
                break;
            case "deny":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend deny <joueur>");
                    break;
                }
                handleDeny(player, args[1]);
                break;
            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend remove <joueur>");
                    break;
                }
                handleRemove(player, args[1]);
                break;
            case "list":
                handleList(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e--- §bSystème d'Amis §e---");
        player.sendMessage("§b/friend add <joueur> §7- Ajouter un ami");
        player.sendMessage("§b/friend accept <joueur> §7- Accepter une demande");
        player.sendMessage("§b/friend deny <joueur> §7- Refuser une demande");
        player.sendMessage("§b/friend remove <joueur> §7- Supprimer un ami");
        player.sendMessage("§b/friend list §7- Voir la liste de vos amis");
    }

    private void handleAdd(Player sender, String targetName) {
        if (sender.getName().equalsIgnoreCase(targetName)) {
            sender.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous ajouter vous-meme.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Recherche du joueur " + targetName + "...");

        plugin.getFriendManager().getUUIDByName(targetName).thenAccept(targetUUID -> {
            if (targetUUID == null) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED + "Ce joueur n'a jamais rejoint le serveur."));
                return;
            }

            plugin.getFriendManager().areFriends(sender.getUniqueId(), targetUUID).thenAccept(alreadyFriends -> {
                if (alreadyFriends) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED + targetName + " est deja dans votre liste d'amis."));
                    return;
                }

                plugin.getFriendManager().sendFriendRequest(sender.getUniqueId(), targetUUID).thenAccept(success -> {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "Demande d'ami envoyée à " + targetName + " !");
                            if (plugin.getRedisManager() != null) {
                                plugin.getRedisManager().publish("gameocean:friend_request", sender.getName() + ":" + targetName);
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "Une erreur est survenue lors de l'envoi de la demande.");
                        }
                    });
                });
            });
        });
    }

    private void handleAccept(Player receiver, String senderName) {
        receiver.sendMessage(ChatColor.YELLOW + "Traitement en cours...");

        plugin.getFriendManager().getUUIDByName(senderName).thenAccept(senderUUID -> {
            if (senderUUID == null) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> receiver.sendMessage(ChatColor.RED + "Ce joueur est introuvable."));
                return;
            }

            plugin.getFriendManager().acceptFriendRequest(senderUUID, receiver.getUniqueId()).thenAccept(success -> {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        receiver.sendMessage(ChatColor.GREEN + "Vous êtes maintenant ami avec " + senderName + " !");
                        if (plugin.getRedisManager() != null) {
                            plugin.getRedisManager().publish("gameocean:friend_accept", receiver.getName() + ":" + senderName);
                        }
                    } else {
                        receiver.sendMessage(ChatColor.RED + "Vous n'avez pas de demande en attente de la part de ce joueur.");
                    }
                });
            });
        });
    }

    private void handleDeny(Player receiver, String senderName) {
        plugin.getFriendManager().getUUIDByName(senderName).thenAccept(senderUUID -> {
            if (senderUUID != null) {
                plugin.getFriendManager().removeOrDenyFriend(senderUUID, receiver.getUniqueId()).thenAccept(success -> {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> receiver.sendMessage(ChatColor.YELLOW + "Demande refusée."));
                });
            }
        });
    }

    private void handleRemove(Player sender, String targetName) {
        plugin.getFriendManager().getUUIDByName(targetName).thenAccept(targetUUID -> {
            if (targetUUID == null) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.RED + "Ce joueur est introuvable."));
                return;
            }

            plugin.getFriendManager().removeOrDenyFriend(sender.getUniqueId(), targetUUID).thenAccept(success -> {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ChatColor.YELLOW + "Le joueur a été retiré de vos amis."));
            });
        });
    }

    private void handleList(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Chargement de la liste d'amis...");

        // Chaîner les deux futures pour garantir l'ordre d'affichage
        plugin.getFriendManager().getFriends(player.getUniqueId()).thenCompose(friends -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (friends.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Vous n'avez pas encore d'amis.");
                } else {
                    player.sendMessage("§e--- §bVos Amis §e---");
                    for (String name : friends.values()) {
                        player.sendMessage("§7- §b" + name);
                    }
                }
            });

            // Ensuite charger les demandes en attente
            return plugin.getFriendManager().getPendingRequests(player.getUniqueId());
        }).thenAccept(requests -> {
            if (!requests.isEmpty()) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(" ");
                    player.sendMessage("§e--- §bDemandes en attente §e---");
                    for (String reqName : requests) {
                        player.sendMessage("§7- §e" + reqName);
                        player.sendMessage("  §7/friend accept " + reqName + " §7| §7/friend deny " + reqName);
                    }
                });
            }
        });
    }
}
