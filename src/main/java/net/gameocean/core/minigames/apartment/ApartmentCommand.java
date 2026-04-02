package net.gameocean.core.minigames.apartment;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ApartmentCommand implements CommandExecutor {

    private final GameOceanCore plugin;
    private final ApartmentManager apartmentManager;

    public ApartmentCommand(GameOceanCore plugin, ApartmentManager apartmentManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"MINIGAME".equalsIgnoreCase(plugin.getServerType()) || 
            !"APARTMENT".equalsIgnoreCase(plugin.getMinigameType())) {
            sender.sendMessage(ChatColor.RED + "Le mode APARTMENT n'est pas activé sur ce serveur.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eCommandes Appartement :");
            if (sender.hasPermission("gameocean.apartment.admin")) {
                sender.sendMessage("§e/app admin setworld <nom_monde> §7- Définit le monde global des appartements.");
                sender.sendMessage("§e/app admin setspawn <id_template> §7- Définit le point de spawn d'un template.");
                sender.sendMessage("§e/app admin tp <id_template> §7- Te téléporte au spawn de ce template.");
                sender.sendMessage("§e/app admin set <joueur> <id_template> §7- Change l'appartement par défaut d'un joueur.");
                sender.sendMessage("§e/app admin give <joueur> <meuble_id> [quantite] §7- Donne un meuble a un joueur.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("gameocean.apartment.admin")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /app admin <setworld|setspawn|tp|set|give> ...");
                return true;
            }

            String subCommand = args[1].toLowerCase();
            if (subCommand.equals("setworld")) {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /app admin setworld <nom_monde>");
                    return true;
                }
                String worldName = args[2];
                apartmentManager.setApartmentWorldName(worldName);
                sender.sendMessage("§aLe monde des appartements a été défini sur : " + worldName);
                return true;
            }

            if (subCommand.equals("setspawn")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSeul un joueur peut définir le spawn.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /app admin setspawn <id_template>");
                    return true;
                }
                String templateId = args[2];
                apartmentManager.setTemplateSpawn(templateId, player.getLocation());
                player.sendMessage("§aLe point d'apparition pour l'appartement '" + templateId + "' a été défini ici !");
                return true;
            }

            if (subCommand.equals("tp")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSeul un joueur peut se téléporter.");
                    return true;
                }
                String templateId = (args.length >= 3) ? args[2] : "default";
                String worldName = apartmentManager.getApartmentWorldName();
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    world = Bukkit.createWorld(new WorldCreator(worldName));
                }
                if (world != null) {
                    Location spawn = apartmentManager.getTemplateSpawn(templateId, world);
                    player.teleport(spawn);
                    player.sendMessage("§aTéléporté au modèle : " + templateId);
                } else {
                    player.sendMessage("§cImpossible de charger le monde global des appartements.");
                }
                return true;
            }

            if (subCommand.equals("set")) {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /app admin set <joueur> <id_template>");
                    return true;
                }
                String targetName = args[2];
                String templateId = args[3];
                Player target = Bukkit.getPlayer(targetName);
                
                if (target != null && target.isOnline()) {
                    if (plugin.getProfileManager() != null) {
                        plugin.getProfileManager().updateCurrentApartment(target.getUniqueId(), templateId);
                        
                        net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(target.getUniqueId());
                        if (profile != null) profile.setCurrentApartment(templateId);
                        
                        sender.sendMessage("§aL'appartement par défaut de " + target.getName() + " est maintenant : " + templateId);
                        target.sendMessage("§aVotre appartement par défaut a été modifié pour : " + templateId);
                        
                        // Si le joueur est déjà dans le minigame, on le retéléporte
                        if ("MINIGAME".equalsIgnoreCase(plugin.getServerType()) && "APARTMENT".equalsIgnoreCase(plugin.getMinigameType())) {
                            apartmentManager.joinApartment(target);
                        }
                    } else {
                        sender.sendMessage("§cLe système de profils n'est pas activé.");
                    }
                } else {
                    sender.sendMessage("§cJoueur introuvable.");
                }
                return true;
            }

            if (subCommand.equals("give")) {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /app admin give <joueur> <meuble_id> [quantite]");
                    return true;
                }
                String targetName = args[2];
                String itemId = args[3];
                int amount = 1;
                if (args.length >= 5) {
                    try {
                        amount = Integer.parseInt(args[4]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cQuantité invalide.");
                        return true;
                    }
                }

                // On peut donner même si offline si on a son UUID, mais pour simplifier on demande qu'il soit online
                Player target = Bukkit.getPlayer(targetName);
                if (target != null && target.isOnline()) {
                    apartmentManager.giveFurniture(target.getUniqueId(), itemId, amount);
                    sender.sendMessage("§aDonné " + amount + "x " + itemId + " à " + target.getName());
                    target.sendMessage("§aVous avez reçu " + amount + "x meuble: " + itemId);
                } else {
                    sender.sendMessage("§cJoueur introuvable ou hors-ligne.");
                }
                return true;
            }
        }

        return true;
    }
}
