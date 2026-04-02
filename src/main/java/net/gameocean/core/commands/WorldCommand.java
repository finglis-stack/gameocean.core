package net.gameocean.core.commands;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.managers.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Commande /world pour gérer les mondes
 */
public class WorldCommand implements CommandExecutor {
    
    private final GameOceanCore plugin;
    private final WorldManager worldManager;
    
    public WorldCommand(GameOceanCore plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux opérateurs.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "list" -> listWorlds(sender);
            case "loaded" -> listLoadedWorlds(sender);
            case "available" -> listAvailableWorlds(sender);
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world info <monde>");
                    return true;
                }
                showWorldInfo(sender, args[1]);
            }
            case "load" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world load <monde>");
                    return true;
                }
                loadWorld(sender, args[1]);
            }
            case "unload" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world unload <monde> [save|nosave]");
                    return true;
                }
                boolean save = args.length < 3 || !args[2].equalsIgnoreCase("nosave");
                unloadWorld(sender, args[1], save);
            }
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world create <nom>");
                    return true;
                }
                // Toujours créer en VOID
                createWorld(sender, args[1]);
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world delete <monde>");
                    return true;
                }
                deleteWorld(sender, args[1]);
            }
            case "tp", "teleport" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world tp <monde> [joueur]");
                    return true;
                }
                teleportToWorld(sender, args);
            }
            case "autoload" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world autoload <add|remove|list> <monde>");
                    return true;
                }
                handleAutoLoad(sender, args[1], args[2]);
            }
            case "alias" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /world alias <monde> <alias>");
                    return true;
                }
                setAlias(sender, args[1], args[2]);
            }
            default -> sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Commandes World Manager ===");
        sender.sendMessage(ChatColor.YELLOW + "/world list" + ChatColor.GRAY + " - Liste tous les mondes chargés");
        sender.sendMessage(ChatColor.YELLOW + "/world available" + ChatColor.GRAY + " - Liste les mondes disponibles");
        sender.sendMessage(ChatColor.YELLOW + "/world info <monde>" + ChatColor.GRAY + " - Infos sur un monde");
        sender.sendMessage(ChatColor.YELLOW + "/world load <monde>" + ChatColor.GRAY + " - Charge un monde");
        sender.sendMessage(ChatColor.YELLOW + "/world unload <monde> [save|nosave]" + ChatColor.GRAY + " - Décharge un monde");
        sender.sendMessage(ChatColor.YELLOW + "/world create <nom>" + ChatColor.GRAY + " - Crée un monde VOID (vide)");
        sender.sendMessage(ChatColor.YELLOW + "/world delete <monde>" + ChatColor.GRAY + " - Supprime un monde");
        sender.sendMessage(ChatColor.YELLOW + "/world tp <monde> [joueur]" + ChatColor.GRAY + " - Téléporte vers un monde");
        sender.sendMessage(ChatColor.YELLOW + "/world autoload <add|remove|list> <monde>" + ChatColor.GRAY + " - Gère l'auto-chargement");
        sender.sendMessage(ChatColor.YELLOW + "/world alias <monde> <alias>" + ChatColor.GRAY + " - Définit un alias");
    }
    
    private void listWorlds(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Mondes Chargés ===");
        List<World> worlds = worldManager.getLoadedWorlds();
        
        if (worlds.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Aucun monde chargé.");
            return;
        }
        
        for (World world : worlds) {
            String autoLoad = worldManager.isAutoLoadWorld(world.getName()) ? ChatColor.GREEN + " [Auto]" : "";
            String players = ChatColor.GRAY + " (" + world.getPlayerCount() + " joueurs)";
            sender.sendMessage(ChatColor.YELLOW + "• " + world.getName() + autoLoad + players);
        }
        
        sender.sendMessage(ChatColor.GRAY + "Total: " + worlds.size() + " monde(s)");
    }
    
    private void listLoadedWorlds(CommandSender sender) {
        listWorlds(sender);
    }
    
    private void listAvailableWorlds(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Mondes Disponibles ===");
        List<String> available = worldManager.getAvailableWorlds();
        List<World> loaded = worldManager.getLoadedWorlds();
        
        if (available.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Aucun monde disponible.");
            return;
        }
        
        for (String worldName : available) {
            boolean isLoaded = loaded.stream().anyMatch(w -> w.getName().equals(worldName));
            String status = isLoaded ? ChatColor.GREEN + " [Chargé]" : ChatColor.RED + " [Non chargé]";
            sender.sendMessage(ChatColor.YELLOW + "• " + worldName + status);
        }
        
        sender.sendMessage(ChatColor.GRAY + "Total: " + available.size() + " monde(s)");
    }
    
    private void showWorldInfo(CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Le monde '" + worldName + "' n'est pas chargé.");
            sender.sendMessage(ChatColor.GRAY + "Utilisez /world load " + worldName + " pour le charger.");
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Infos du Monde ===");
        sender.sendMessage(worldManager.getWorldInfo(world));
    }
    
    private void loadWorld(CommandSender sender, String worldName) {
        sender.sendMessage(ChatColor.YELLOW + "Chargement du monde '" + worldName + "'...");
        
        if (worldManager.loadWorld(worldName)) {
            sender.sendMessage(ChatColor.GREEN + "Monde '" + worldName + "' chargé avec succès!");
        } else {
            sender.sendMessage(ChatColor.RED + "Échec du chargement du monde '" + worldName + "'.");
            sender.sendMessage(ChatColor.GRAY + "Vérifiez que le dossier existe.");
        }
    }
    
    private void unloadWorld(CommandSender sender, String worldName, boolean save) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Le monde '" + worldName + "' n'est pas chargé.");
            return;
        }
        
        // Vérifier que ce n'est pas le monde principal
        if (world.equals(Bukkit.getWorlds().get(0))) {
            sender.sendMessage(ChatColor.RED + "Impossible de décharger le monde principal!");
            return;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Déchargement du monde '" + worldName + "'...");
        
        if (worldManager.unloadWorld(worldName, save)) {
            sender.sendMessage(ChatColor.GREEN + "Monde '" + worldName + "' déchargé avec succès!");
        } else {
            sender.sendMessage(ChatColor.RED + "Échec du déchargement du monde '" + worldName + "'.");
        }
    }
    
    private void createWorld(CommandSender sender, String worldName) {
        sender.sendMessage(ChatColor.YELLOW + "Création du monde VOID: '" + worldName + "'...");
        
        World world = worldManager.createWorld(worldName, "VOID");
        if (world != null) {
            sender.sendMessage(ChatColor.GREEN + "Monde void '" + worldName + "' créé avec succès!");
            sender.sendMessage(ChatColor.GRAY + "Spawn: (0, 64, 0) - Monde complètement vide");
        } else {
            sender.sendMessage(ChatColor.RED + "Échec de la création du monde. Le nom existe peut-être déjà.");
        }
    }
    
    private void deleteWorld(CommandSender sender, String worldName) {
        // Confirmation
        sender.sendMessage(ChatColor.RED + "§lATTENTION!");
        sender.sendMessage(ChatColor.RED + "Vous êtes sur le point de supprimer définitivement le monde '" + worldName + "'.");
        sender.sendMessage(ChatColor.RED + "Cette action est irréversible!");
        sender.sendMessage(ChatColor.YELLOW + "Utilisez /world unload " + worldName + " si vous voulez juste le décharger.");
        
        // TODO: Ajouter une confirmation (2ème commande ou GUI)
        // Pour l'instant, on demande juste au joueur de confirmer
        sender.sendMessage(ChatColor.GRAY + "Pour supprimer, utilisez: /world unload " + worldName + " puis supprimez le dossier manuellement.");
    }
    
    private void teleportToWorld(CommandSender sender, String[] args) {
        String worldName = args[1];
        Player target;
        
        if (args.length > 2) {
            // Téléporter un autre joueur
            if (!sender.hasPermission("gameocean.world.teleport.others")) {
                sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de téléporter d'autres joueurs.");
                return;
            }
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Joueur '" + args[2] + "' introuvable.");
                return;
            }
        } else {
            // Se téléporter soi-même
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console: utilisez /world tp <monde> <joueur>");
                return;
            }
            target = (Player) sender;
        }
        
        if (worldManager.teleportToWorld(target, worldName)) {
            target.sendMessage(ChatColor.GREEN + "Téléportation vers le monde '" + worldName + "'.");
            if (!sender.equals(target)) {
                sender.sendMessage(ChatColor.GREEN + target.getName() + " téléporté vers '" + worldName + "'.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Le monde '" + worldName + "' n'est pas chargé.");
            sender.sendMessage(ChatColor.GRAY + "Utilisez /world load " + worldName + " pour le charger.");
        }
    }
    
    private void handleAutoLoad(CommandSender sender, String action, String worldName) {
        switch (action.toLowerCase()) {
            case "add" -> {
                worldManager.addAutoLoadWorld(worldName);
                sender.sendMessage(ChatColor.GREEN + "Monde '" + worldName + "' ajouté au chargement automatique.");
                sender.sendMessage(ChatColor.GRAY + "Il sera chargé au prochain démarrage du serveur.");
            }
            case "remove" -> {
                worldManager.removeAutoLoadWorld(worldName);
                sender.sendMessage(ChatColor.GREEN + "Monde '" + worldName + "' retiré du chargement automatique.");
            }
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Mondes en Auto-Load ===");
                // Récupérer la liste depuis le WorldManager
                // Pour l'instant on affiche juste un message
                sender.sendMessage(ChatColor.GRAY + "Liste sauvegardée dans worlds.yml");
            }
            default -> sender.sendMessage(ChatColor.RED + "Action invalide. Utilisez: add, remove, list");
        }
    }
    
    private void setAlias(CommandSender sender, String worldName, String alias) {
        worldManager.setWorldAlias(worldName, alias);
        sender.sendMessage(ChatColor.GREEN + "Alias défini: '" + worldName + "' -> '" + alias + "'");
    }
}