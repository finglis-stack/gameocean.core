package net.gameocean.core.minigames.bedwars.commands;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.data.*;
import net.gameocean.core.minigames.bedwars.managers.ArenaManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Commande d'administration /bw
 */
public class BedwarsCommand implements CommandExecutor {
    
    private final GameOceanCore plugin;
    private final ArenaManager arenaManager;
    private final Map<UUID, Location[]> boundSelections = new HashMap<>();
    private final Map<UUID, String> selectedArena = new HashMap<>();
    
    public BedwarsCommand(GameOceanCore plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande doit être utilisée par un joueur.");
            return true;
        }
        
        if (!player.hasPermission("gameocean.bedwars.admin")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "build" -> toggleBuildMode(player);
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw create <nom>");
                    return true;
                }
                createArena(player, args[1]);
            }
            case "setwaiting" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw setwaiting <nom>");
                    return true;
                }
                setWaitingSpawn(player, args[1]);
            }
            case "setbounds" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw setbounds <nom> <pos1|pos2>");
                    return true;
                }
                setBounds(player, args[1], args[2]);
            }
            case "team" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw team <arène> <couleur>");
                    return true;
                }
                addTeam(player, args[1], args[2]);
            }
            case "setspawn" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw setspawn <arène> <couleur>");
                    return true;
                }
                setTeamSpawn(player, args[1], args[2]);
            }
            case "setbed" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw setbed <arène> <couleur>");
                    return true;
                }
                setTeamBed(player, args[1], args[2]);
            }
            case "addnpc" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw addnpc <arène> <shop|upgrade>");
                    return true;
                }
                addNpc(player, args[1], args[2]);
            }
            case "addgen" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw addgen <arène> <iron|gold|diamond|emerald>");
                    return true;
                }
                addGenerator(player, args[1], args[2]);
            }
            case "setmaxplayers" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bw setmaxplayers <nom> <montant>");
                    return true;
                }
                setMaxPlayers(player, args[1], args[2]);
            }
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw save <nom>");
                    return true;
                }
                saveArena(player, args[1]);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw info <nom>");
                    return true;
                }
                showArenaInfo(player, args[1]);
            }
            case "list" -> listArenas(player);
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw join <nom>");
                    return true;
                }
                joinArena(player, args[1]);
            }
            case "leave" -> leaveArena(player);
            case "start" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw start <nom>");
                    return true;
                }
                startArena(player, args[1]);
            }
            case "stop" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bw stop <nom>");
                    return true;
                }
                stopArena(player, args[1]);
            }
            default -> sendHelp(player);
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== Commandes Bedwars Admin ===");
        player.sendMessage("§e/bw build §7- Active/désactive le mode construction");
        player.sendMessage("§e/bw create <nom> §7- Crée une nouvelle arène");
        player.sendMessage("§e/bw setwaiting <nom> §7- Définit le spawn d'attente");
        player.sendMessage("§e/bw setmaxplayers <nom> <montant> §7- Définit le nombre de places max");
        player.sendMessage("§e/bw setbounds <nom> <pos1|pos2> §7- Définit les limites");
        player.sendMessage("§e/bw team <arène> <couleur> §7- Ajoute une équipe");
        player.sendMessage("§e/bw setspawn <arène> <couleur> §7- Définit le spawn d'équipe");
        player.sendMessage("§e/bw setbed <arène> <couleur> §7- Définit le lit d'équipe");
        player.sendMessage("§e/bw addnpc <arène> <shop|upgrade> §7- Ajoute un NPC");
        player.sendMessage("§e/bw addgen <arène> <type> §7- Ajoute un générateur");
        player.sendMessage("§e/bw save <nom> §7- Sauvegarde l'arène");
        player.sendMessage("§e/bw info <nom> §7- Affiche les infos d'une arène");
        player.sendMessage("§e/bw list §7- Liste les arènes");
        player.sendMessage("§e/bw join <nom> §7- Rejoint une arène (test)");
        player.sendMessage("§e/bw leave §7- Quitte l'arène");
        player.sendMessage("§e/bw start <nom> §7- Démarre une partie");
        player.sendMessage("§e/bw stop <nom> §7- Arrête une partie");
    }
    
    private void toggleBuildMode(Player player) {
        if (plugin.getBedwarsManager() != null) {
            plugin.getBedwarsManager().getBuildModeManager().toggleBuildMode(player);
        }
    }
    
    private void createArena(Player player, String name) {
        Arena arena = arenaManager.createArena(name);
        if (arena == null) {
            player.sendMessage("§cUne arène avec ce nom existe déjà.");
            return;
        }
        player.sendMessage("§aArène '§6" + name + "§a' créée avec succès!");
        player.sendMessage("§7Utilisez §e/bw setwaiting " + name + " §7pour définir le spawn d'attente.");
    }
    
    private void setWaitingSpawn(Player player, String name) {
        Arena arena = arenaManager.getArena(name);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + name + "§c' n'existe pas.");
            return;
        }
        
        arena.setWaitingSpawn(player.getLocation());
        player.sendMessage("§aSpawn d'attente défini pour l'arène '§6" + name + "§a'.");
    }
    
    private void setBounds(Player player, String name, String pos) {
        Arena arena = arenaManager.getArena(name);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + name + "§c' n'existe pas.");
            return;
        }
        
        Location loc = player.getLocation();
        if (pos.equalsIgnoreCase("pos1")) {
            arena.setPos1(loc);
            player.sendMessage("§aPosition 1 définie pour l'arène '§6" + name + "§a'.");
        } else if (pos.equalsIgnoreCase("pos2")) {
            arena.setPos2(loc);
            player.sendMessage("§aPosition 2 définie pour l'arène '§6" + name + "§a'.");
        } else {
            player.sendMessage("§cUtilisez 'pos1' ou 'pos2'.");
        }
    }
    
    private void addTeam(Player player, String arenaName, String color) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + arenaName + "§c' n'existe pas.");
            return;
        }
        
        String teamName = color.substring(0, 1).toUpperCase() + color.substring(1).toLowerCase();
        BedwarsTeam team = new BedwarsTeam(color.toLowerCase(), teamName);
        arena.addTeam(team);
        
        player.sendMessage("§aÉquipe '§" + BedwarsTeam.getChatColorFromString(color).getChar() + teamName + "§a' ajoutée à l'arène '§6" + arenaName + "§a'.");
        player.sendMessage("§7Utilisez §e/bw setspawn " + arenaName + " " + color + " §7pour définir le spawn.");
    }
    
    private void setTeamSpawn(Player player, String arenaName, String color) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + arenaName + "§c' n'existe pas.");
            return;
        }
        
        BedwarsTeam team = arena.getTeam(color);
        if (team == null) {
            player.sendMessage("§cL'équipe '§6" + color + "§c' n'existe pas. Créez-la avec /bw team.");
            return;
        }
        
        team.setSpawn(player.getLocation());
        player.sendMessage("§aSpawn défini pour l'équipe '§" + team.getChatColor().getChar() + team.getName() + "§a'.");
    }
    
    private void setTeamBed(Player player, String arenaName, String color) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + arenaName + "§c' n'existe pas.");
            return;
        }
        
        BedwarsTeam team = arena.getTeam(color);
        if (team == null) {
            player.sendMessage("§cL'équipe '§6" + color + "§c' n'existe pas.");
            return;
        }
        
        Block target = player.getTargetBlockExact(5);
        if (target == null || !target.getType().name().contains("BED")) {
            player.sendMessage("§cVisez un lit à moins de 5 blocs.");
            return;
        }
        
        team.setBedLocation(target.getLocation());
        player.sendMessage("§aLit défini pour l'équipe '§" + team.getChatColor().getChar() + team.getName() + "§a'.");
    }
    
    private void addNpc(Player player, String arenaName, String type) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + arenaName + "§c' n'existe pas.");
            return;
        }
        
        NPCType npcType;
        try {
            npcType = NPCType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cType invalide. Utilisez: shop, upgrade");
            player.sendMessage("§7Exemple: /bw addnpc " + arenaName + " upgrade");
            return;
        }
        
        NPCShop npc = new NPCShop(npcType, player.getLocation());
        arena.addNpc(npc);
        
        player.sendMessage("§aNPC '§6" + npcType.getSimpleName() + "§a' ajouté à l'arène '§6" + arenaName + "§a'.");
    }
    
    private void addGenerator(Player player, String arenaName, String type) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + arenaName + "§c' n'existe pas.");
            return;
        }

        GeneratorType genType;
        try {
            genType = GeneratorType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cType invalide. Utilisez: iron, gold, diamond, emerald");
            return;
        }

        Generator gen = new Generator(genType, player.getLocation(), 1);
        arena.addGenerator(gen);

        player.sendMessage("§aGénérateur de '§6" + genType.getDisplayName() + "§a' ajouté à l'arène '§6" + arenaName + "§a'.");
    }

    private void setMaxPlayers(Player player, String arenaName, String amountStr) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + arenaName + "§c' n'existe pas.");
            return;
        }

        try {
            int max = Integer.parseInt(amountStr);
            if (max <= 0) {
                player.sendMessage("§cLe montant doit être supérieur à 0.");
                return;
            }
            arena.setMaxPlayers(max);
            player.sendMessage("§aLe nombre de joueurs maximum pour '§6" + arenaName + "§a' est maintenant de §e" + max + "§a.");
            // Update le serveur si l'arène est actuellement disponible
            if (plugin.getBedwarsManager() != null && plugin.getBedwarsManager().getArenaManager() != null) {
                plugin.getBedwarsManager().getArenaManager().updateServerStatus(arena);
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cMontant invalide.");
        }
    }

    private void saveArena(Player player, String name) {
        if (arenaManager.saveArena(name)) {
            player.sendMessage("§aArène '§6" + name + "§a' sauvegardée avec succès!");
        } else {
            player.sendMessage("§cL'arène '§6" + name + "§c' n'existe pas.");
        }
    }
    
    private void showArenaInfo(Player player, String name) {
        Arena arena = arenaManager.getArena(name);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + name + "§c' n'existe pas.");
            return;
        }
        
        player.sendMessage("§6§l=== Infos Arène: " + arena.getDisplayName() + " ===");
        player.sendMessage("§7Nom: §f" + arena.getName());
        player.sendMessage("§7État: " + arena.getState().getDisplayName());
        player.sendMessage("§7Joueurs: §f" + arena.getPlayerCount() + "/" + arena.getMaxPlayers());
        player.sendMessage("§7Spawn d'attente: §f" + (arena.getWaitingSpawn() != null ? "§aDéfini" : "§cNon défini"));
        player.sendMessage("§7Limites: §f" + (arena.getPos1() != null && arena.getPos2() != null ? "§aDéfinies" : "§cNon définies"));
        player.sendMessage("§7Équipes: §f" + arena.getTeams().size());
        for (BedwarsTeam team : arena.getTeams().values()) {
            player.sendMessage("  §" + team.getChatColor().getChar() + "• " + team.getName() + " §7- Spawn: " + 
                    (team.getSpawn() != null ? "§a✔" : "§c✘") + " §7Lit: " + 
                    (team.getBedLocation() != null ? "§a✔" : "§c✘"));
        }
        player.sendMessage("§7Générateurs: §f" + arena.getGenerators().size());
        player.sendMessage("§7NPCs: §f" + arena.getNpcs().size());
    }
    
    private void listArenas(Player player) {
        player.sendMessage("§6§l=== Arènes Bedwars ===");
        if (arenaManager.getArenas().isEmpty()) {
            player.sendMessage("§7Aucune arène créée.");
            return;
        }
        
        for (Arena arena : arenaManager.getArenas()) {
            player.sendMessage("§7• §f" + arena.getName() + " " + arena.getState().getDisplayName() + 
                    " §7(" + arena.getPlayerCount() + "/" + arena.getMaxPlayers() + ")");
        }
    }
    
    private void joinArena(Player player, String name) {
        arenaManager.joinArena(player, name);
    }
    
    private void leaveArena(Player player) {
        arenaManager.leaveArena(player);
    }
    
    private void startArena(Player player, String name) {
        Arena arena = arenaManager.getArena(name);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + name + "§c' n'existe pas.");
            return;
        }
        
        if (arena.getState() == ArenaState.WAITING) {
            arena.setState(ArenaState.STARTING);
        }
        arenaManager.startGame(arena);
        player.sendMessage("§aPartie démarrée sur l'arène '§6" + name + "§a'.");
    }
    
    private void stopArena(Player player, String name) {
        Arena arena = arenaManager.getArena(name);
        if (arena == null) {
            player.sendMessage("§cL'arène '§6" + name + "§c' n'existe pas.");
            return;
        }
        
        arenaManager.restartArena(arena);
        player.sendMessage("§aPartie arrêtée sur l'arène '§6" + name + "§a'.");
    }
}