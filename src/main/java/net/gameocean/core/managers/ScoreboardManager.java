package net.gameocean.core.managers;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {
    
    private final GameOceanCore plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    
    public ScoreboardManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }
    
    public void createScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // 1. Titre exact: "&6&lGame&b&lOcean &r&aHub"
        Objective objective = scoreboard.registerNewObjective("gameocean", "dummy", 
                ChatColor.translateAlternateColorCodes('&', "&6&lGame&b&lOcean &r&aHub"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // 2. Obtenir le rang avec couleur
        String rank = "Joueur";
        String rankColorCode = "&b"; // Bleu par défaut
        
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> rsp = 
                    plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
                if (rsp != null) {
                    net.milkbowl.vault.permission.Permission perms = rsp.getProvider();
                    if (perms != null) {
                        String primaryGroup = perms.getPrimaryGroup(player);
                        if (primaryGroup != null && !primaryGroup.isEmpty()) {
                            rank = primaryGroup.substring(0, 1).toUpperCase() + primaryGroup.substring(1);
                            
                            // Couleurs selon le rang
                            String groupLower = primaryGroup.toLowerCase();
                            if (groupLower.contains("admin") || groupLower.contains("owner")) {
                                rankColorCode = "&c"; // Rouge
                            } else if (groupLower.contains("mod") || groupLower.contains("moderateur")) {
                                rankColorCode = "&a"; // Vert
                            } else if (groupLower.contains("vip") || groupLower.contains("premium")) {
                                rankColorCode = "&6"; // Or
                            } else if (groupLower.contains("builder") || groupLower.contains("constructeur")) {
                                rankColorCode = "&d"; // Magenta
                            } else {
                                rankColorCode = "&b"; // Bleu aqua
                            }
                        }
                    }
                }
            } catch (Exception e) {
                rankColorCode = "&b";
            }
        }
        
        // Créer les teams pour chaque ligne
        Team line1 = scoreboard.registerNewTeam("line1");
        Team line2 = scoreboard.registerNewTeam("line2");
        Team line3 = scoreboard.registerNewTeam("line3");
        Team line4 = scoreboard.registerNewTeam("line4");
        Team line5 = scoreboard.registerNewTeam("line5");
        Team line6 = scoreboard.registerNewTeam("line6");
        Team line7 = scoreboard.registerNewTeam("line7");
        
        // Ligne - Vide (espace après le titre)
        line1.addEntry(ChatColor.BLACK.toString());
        line1.setPrefix(" ");
        objective.getScore(ChatColor.BLACK.toString()).setScore(7);
        
        // Ligne 6 - Rang: "&e&l» &r&fRang: &r[COULEUR]Rang"
        line2.addEntry(ChatColor.DARK_BLUE.toString());
        line2.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fRang: &r" + rankColorCode + rank));
        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(6);
        
        // Ligne 5 - Niveau: "&e&l» &r&fNiveau: &r&d1"
        int level = 1;
        if (plugin.getProfileManager() != null) {
            net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
            if (profile != null) {
                level = profile.getLevel();
            }
        }
        line3.addEntry(ChatColor.DARK_GREEN.toString());
        line3.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fNiveau: &r&d" + level));
        objective.getScore(ChatColor.DARK_GREEN.toString()).setScore(5);
        
        // Ligne - Vide (espace)
        line4.addEntry(ChatColor.DARK_AQUA.toString());
        line4.setPrefix(" ");
        objective.getScore(ChatColor.DARK_AQUA.toString()).setScore(4);
        
        // Ligne 3 - Hub avec PlaceholderAPI: "&e&l» &r&fVous êtes dans le Hub &b%placeholder%&f!"
        line5.addEntry(ChatColor.BLUE.toString());
        // Le placeholder sera remplacé si PlaceholderAPI est disponible
        String hubText = "&e&l» &r&fVous êtes dans le Hub &b%simplecloud_server_numerical_id%&f!";
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                // Essayer de parser avec PlaceholderAPI
                Class<?> placeholderAPI = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = placeholderAPI.getMethod("setPlaceholders", Player.class, String.class);
                hubText = (String) setPlaceholders.invoke(null, player, hubText);
            } catch (Exception e) {
                // Si PlaceholderAPI n'est pas disponible, garder le texte brut
            }
        }
        line5.setPrefix(ChatColor.translateAlternateColorCodes('&', hubText));
        objective.getScore(ChatColor.BLUE.toString()).setScore(3);
        
        // Ligne - Vide (espace)
        line6.addEntry(ChatColor.DARK_GRAY.toString());
        line6.setPrefix(" ");
        objective.getScore(ChatColor.DARK_GRAY.toString()).setScore(2);
        
        // Ligne 1 - Site web: "&ewww.gameocean.net"
        line7.addEntry(ChatColor.GRAY.toString());
        line7.setPrefix(ChatColor.translateAlternateColorCodes('&', "&ewww.gameocean.net"));
        objective.getScore(ChatColor.GRAY.toString()).setScore(1);
        
        // Appliquer le scoreboard
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerScoreboards.remove(player.getUniqueId());
    }
    
    public void updateAllScoreboards() {
        for (UUID uuid : playerScoreboards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                createScoreboard(player);
                if (plugin.getNameTagManager() != null) {
                    plugin.getNameTagManager().setNameTag(player);
                }
            }
        }
    }
}