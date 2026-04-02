package net.gameocean.core.minigames.bedwars.managers;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.data.Arena;
import net.gameocean.core.minigames.bedwars.data.ArenaState;
import net.gameocean.core.minigames.bedwars.data.BedwarsPlayerData;
import net.gameocean.core.minigames.bedwars.data.BedwarsTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire des scoreboards pour Bedwars
 * Utilise le même design visuel que le lobby
 */
public class BedwarsScoreboardManager {
    
    private final GameOceanCore plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    
    public BedwarsScoreboardManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Crée le scoreboard pour l'état d'attente
     */
    public void createWaitingScoreboard(Player player, Arena arena) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Titre identique au lobby: "&6&lGame&b&lOcean &r&aHub"
        Objective objective = scoreboard.registerNewObjective("bedwars", "dummy", 
                ChatColor.translateAlternateColorCodes('&', "&6&lGame&b&lOcean &r&aBedwars"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Créer les teams pour chaque ligne
        Team line1 = scoreboard.registerNewTeam("line1");
        Team line2 = scoreboard.registerNewTeam("line2");
        Team line3 = scoreboard.registerNewTeam("line3");
        Team line4 = scoreboard.registerNewTeam("line4");
        Team line5 = scoreboard.registerNewTeam("line5");
        Team line6 = scoreboard.registerNewTeam("line6");
        Team line7 = scoreboard.registerNewTeam("line7");
        Team line8 = scoreboard.registerNewTeam("line8");
        
        // Ligne vide
        line1.addEntry(ChatColor.BLACK.toString());
        line1.setPrefix(" ");
        objective.getScore(ChatColor.BLACK.toString()).setScore(8);
        
        // Map
        line2.addEntry(ChatColor.DARK_BLUE.toString());
        line2.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fMap: &r&a" + arena.getDisplayName()));
        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(7);
        
        // Joueurs
        line3.addEntry(ChatColor.DARK_GREEN.toString());
        line3.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fJoueurs: &r&b" + arena.getPlayerCount() + "/" + arena.getMaxPlayers()));
        objective.getScore(ChatColor.DARK_GREEN.toString()).setScore(6);
        
        // Ligne vide
        line4.addEntry(ChatColor.DARK_AQUA.toString());
        line4.setPrefix(" ");
        objective.getScore(ChatColor.DARK_AQUA.toString()).setScore(5);
        
        // État
        line5.addEntry(ChatColor.BLUE.toString());
        line5.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fStatut: &r" + arena.getState().getDisplayName()));
        objective.getScore(ChatColor.BLUE.toString()).setScore(4);
        
        // Temps d'attente ou démarrage
        line6.addEntry(ChatColor.DARK_GRAY.toString());
        if (arena.getState() == ArenaState.STARTING) {
            int countdown = plugin.getBedwarsManager().getArenaManager().getArenaCountdowns().getOrDefault(arena, 30);
            line6.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fDémarrage: &r&e" + countdown + "s"));
        } else {
            line6.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fEn attente..."));
        }
        objective.getScore(ChatColor.DARK_GRAY.toString()).setScore(3);
        
        // Ligne vide
        line7.addEntry(ChatColor.GRAY.toString());
        line7.setPrefix(" ");
        objective.getScore(ChatColor.GRAY.toString()).setScore(2);
        
        // Site web
        line8.addEntry(ChatColor.DARK_PURPLE.toString());
        line8.setPrefix(ChatColor.translateAlternateColorCodes('&', "&ewww.gameocean.net"));
        objective.getScore(ChatColor.DARK_PURPLE.toString()).setScore(1);
        
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    /**
     * Crée le scoreboard pour le jeu en cours
     */
    public void createGameScoreboard(Player player, Arena arena) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        
        // Titre
        Objective objective = scoreboard.registerNewObjective("bedwars_game", "dummy", 
                ChatColor.translateAlternateColorCodes('&', "&6&lGame&b&lOcean &r&cBedwars"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Créer les teams
        Team line1 = scoreboard.registerNewTeam("line1");
        Team line2 = scoreboard.registerNewTeam("line2");
        Team line3 = scoreboard.registerNewTeam("line3");
        Team line4 = scoreboard.registerNewTeam("line4");
        Team line5 = scoreboard.registerNewTeam("line5");
        Team line6 = scoreboard.registerNewTeam("line6");
        Team line7 = scoreboard.registerNewTeam("line7");
        Team line8 = scoreboard.registerNewTeam("line8");
        Team line9 = scoreboard.registerNewTeam("line9");
        
        BedwarsPlayerData playerData = arena.getPlayerData(player.getUniqueId());
        BedwarsTeam playerTeam = playerData != null ? arena.getTeam(playerData.getTeamColor()) : null;
        
        // Ligne vide
        line1.addEntry(ChatColor.BLACK.toString());
        line1.setPrefix(" ");
        objective.getScore(ChatColor.BLACK.toString()).setScore(9);
        
        // Map
        line2.addEntry(ChatColor.DARK_BLUE.toString());
        line2.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fMap: &r&a" + arena.getDisplayName()));
        objective.getScore(ChatColor.DARK_BLUE.toString()).setScore(8);
        
        // Équipe du joueur
        line3.addEntry(ChatColor.DARK_GREEN.toString());
        if (playerTeam != null) {
            line3.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fÉquipe: " + playerTeam.getColorCode() + playerTeam.getName()));
        } else {
            line3.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fÉquipe: &7-"));
        }
        objective.getScore(ChatColor.DARK_GREEN.toString()).setScore(7);
        
        // Ligne vide
        line4.addEntry(ChatColor.DARK_AQUA.toString());
        line4.setPrefix(" ");
        objective.getScore(ChatColor.DARK_AQUA.toString()).setScore(6);
        
        // Équipes restantes
        int teamsAlive = 0;
        for (BedwarsTeam team : arena.getTeams().values()) {
            if (!team.isEliminated() && !team.getPlayers().isEmpty()) teamsAlive++;
        }
        line5.addEntry(ChatColor.BLUE.toString());
        line5.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fÉquipes: &r&b" + teamsAlive));
        objective.getScore(ChatColor.BLUE.toString()).setScore(5);
        
        // Kills
        line6.addEntry(ChatColor.DARK_GRAY.toString());
        int kills = playerData != null ? playerData.getKills() : 0;
        line6.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fKills: &r&a" + kills));
        objective.getScore(ChatColor.DARK_GRAY.toString()).setScore(4);
        
        // Lits détruits
        line7.addEntry(ChatColor.GRAY.toString());
        int beds = playerData != null ? playerData.getBedsDestroyed() : 0;
        line7.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fLits: &r&c" + beds));
        objective.getScore(ChatColor.GRAY.toString()).setScore(3);
        
        // Ligne vide
        line8.addEntry(ChatColor.DARK_PURPLE.toString());
        line8.setPrefix(" ");
        objective.getScore(ChatColor.DARK_PURPLE.toString()).setScore(2);
        
        // Site web
        line9.addEntry(ChatColor.LIGHT_PURPLE.toString());
        line9.setPrefix(ChatColor.translateAlternateColorCodes('&', "&ewww.gameocean.net"));
        objective.getScore(ChatColor.LIGHT_PURPLE.toString()).setScore(1);
        
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    /**
     * Met à jour le scoreboard d'un joueur
     */
    public void updateScoreboard(Player player, Arena arena) {
        Scoreboard sb = playerScoreboards.get(player.getUniqueId());
        
        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            if (sb == null || sb.getObjective("bedwars") == null) {
                createWaitingScoreboard(player, arena);
            } else {
                // Update dynamically without recreation
                Team line6 = sb.getTeam("line6");
                if (line6 != null) {
                    if (arena.getState() == ArenaState.STARTING) {
                        int countdown = plugin.getBedwarsManager().getArenaManager().getArenaCountdowns().getOrDefault(arena, 30);
                        line6.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fDémarrage: &r&e" + countdown + "s"));
                    } else {
                        line6.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fEn attente..."));
                    }
                }
                Team line3 = sb.getTeam("line3");
                if (line3 != null) {
                    line3.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fJoueurs: &r&b" + arena.getPlayerCount() + "/" + arena.getMaxPlayers()));
                }
                Team line5 = sb.getTeam("line5");
                if (line5 != null) {
                    line5.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fStatut: &r" + arena.getState().getDisplayName()));
                }
            }
        } else if (arena.getState() == ArenaState.IN_GAME) {
            if (sb == null || sb.getObjective("bedwars_game") == null) {
                createGameScoreboard(player, arena);
            } else {
                // Update dynamically
                Team line5 = sb.getTeam("line5");
                if (line5 != null) {
                    int teamsAlive = 0;
                    for (BedwarsTeam team : arena.getTeams().values()) {
                        if (!team.isEliminated()) teamsAlive++;
                    }
                    line5.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fÉquipes: &r&b" + teamsAlive));
                }
                
                BedwarsPlayerData data = arena.getPlayerData(player.getUniqueId());
                if (data != null) {
                    Team line6 = sb.getTeam("line6");
                    if (line6 != null) {
                        line6.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fKills: &r&a" + data.getKills()));
                    }
                    Team line7 = sb.getTeam("line7");
                    if (line7 != null) {
                        line7.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fLits: &r&c" + data.getBedsDestroyed()));
                    }
                }
                
                BedwarsTeam playerTeam = data != null ? arena.getTeam(data.getTeamColor()) : null;
                Team line3 = sb.getTeam("line3");
                if (line3 != null && playerTeam != null) {
                    line3.setPrefix(ChatColor.translateAlternateColorCodes('&', "&e&l» &r&fÉquipe: " + playerTeam.getColorCode() + playerTeam.getName()));
                }
            }
        }
    }
    
    /**
     * Met à jour tous les scoreboards d'une arène
     */
    public void updateArenaScoreboards(Arena arena) {
        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                updateScoreboard(player, arena);
            }
        }
    }
    
    /**
     * Supprime le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerScoreboards.remove(player.getUniqueId());
    }
    
    /**
     * Vérifie si un joueur a un scoreboard actif
     */
    public boolean hasScoreboard(Player player) {
        return playerScoreboards.containsKey(player.getUniqueId());
    }
}