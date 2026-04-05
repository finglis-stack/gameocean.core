package net.gameocean.core.managers;

import net.gameocean.core.GameOceanCore;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class ChatNameTagManager implements Listener {
    
    private final GameOceanCore plugin;
    
    public ChatNameTagManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        boolean isLobby = "LOBBY".equalsIgnoreCase(plugin.getServerType());
        boolean isApartment = "MINIGAME".equalsIgnoreCase(plugin.getServerType()) && "APARTMENT".equalsIgnoreCase(plugin.getMinigameType());
        
        if (!isLobby && !isApartment) return;
        
        Player player = event.getPlayer();
        String prefix = getPlayerPrefix(player);
        if (prefix != null && !prefix.isEmpty() && !prefix.endsWith(" ")) {
            prefix += " ";
        }
        String rankColor = extractFirstColor(prefix);
        
        event.setFormat(ChatColor.translateAlternateColorCodes('&', 
            prefix + "&r" + rankColor + "%s &f&l»&r %s"));
            
        // Si c'est un appartement, isoler le chat entre les joueurs du même appartement
        if (isApartment) {
            org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
            String myHost = player.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
            
            event.getRecipients().removeIf(recipient -> {
                String recHost = recipient.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                return myHost == null || !myHost.equals(recHost);
            });
        }
    }
    
    public void setNameTag(Player player) {
        // Enlever la restriction sur le type de serveur pour permettre l'utilisation globale
        
        Scoreboard playerScoreboard = player.getScoreboard();
        if (playerScoreboard == null) {
            playerScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(playerScoreboard);
        }

        // Pour chaque joueur en ligne (y compris le joueur lui-même)
        for (Player online : Bukkit.getOnlinePlayers()) {
            // Mettre à jour le tag de 'online' sur le scoreboard de 'player'
            updateTeam(playerScoreboard, online);
            
            // Mettre à jour le tag de 'player' sur le scoreboard de 'online'
            if (!online.equals(player)) {
                Scoreboard onlineScoreboard = online.getScoreboard();
                if (onlineScoreboard != null) {
                    updateTeam(onlineScoreboard, player);
                }
            }
        }
    }

    private void updateTeam(Scoreboard scoreboard, Player target) {
        String prefix = getPlayerPrefix(target);
        if (prefix != null && !prefix.isEmpty() && !prefix.endsWith(" ")) {
            prefix += " ";
        }
        String coloredPrefix = ChatColor.translateAlternateColorCodes('&', prefix);
        String rankColorCode = extractFirstColor(prefix);
        
        // Ordre de tri dans le tab (optionnel, mais utile)
        String weight = "99";
        String group = "";
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                org.bukkit.plugin.RegisteredServiceProvider<Permission> rsp = 
                    Bukkit.getServicesManager().getRegistration(Permission.class);
                if (rsp != null && rsp.getProvider() != null) {
                    group = rsp.getProvider().getPrimaryGroup(target).toLowerCase();
                    if (group.contains("admin") || group.contains("owner")) weight = "01";
                    else if (group.contains("mod")) weight = "02";
                    else if (group.contains("vip") || group.contains("premium")) weight = "05";
                    else weight = "10";
                }
            }
        } catch (Exception e) {}
        
        String teamName = weight + target.getName();
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);
        
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        
        team.setPrefix(coloredPrefix);
        try {
            if (rankColorCode.length() >= 2) {
                ChatColor color = ChatColor.getByChar(rankColorCode.charAt(1));
                if (color != null) {
                    team.setColor(color);
                } else {
                    team.setColor(ChatColor.WHITE);
                }
            } else {
                team.setColor(ChatColor.WHITE);
            }
        } catch (NoSuchMethodError e) {
            // Pour compatibilité avec les très vieilles versions si besoin
            team.setPrefix(coloredPrefix + ChatColor.translateAlternateColorCodes('&', rankColorCode));
        }
        
        team.setSuffix("");
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }
    }
    
    private String getPlayerPrefix(Player player) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                org.bukkit.plugin.RegisteredServiceProvider<Chat> rsp = 
                    Bukkit.getServicesManager().getRegistration(Chat.class);
                if (rsp != null) {
                    Chat chat = rsp.getProvider();
                    if (chat != null) {
                        String prefix = chat.getPlayerPrefix(player);
                        return prefix != null ? prefix : "";
                    }
                }
            }
        } catch (Exception e) {}
        return "";
    }
    
    private String extractFirstColor(String prefix) {
        if (prefix == null || prefix.isEmpty()) return "&f";
        
        String translated = ChatColor.translateAlternateColorCodes('&', prefix);
        if (translated.length() >= 2 && translated.charAt(0) == ChatColor.COLOR_CHAR) {
            return "&" + translated.charAt(1);
        }
        return "&f";
    }
}