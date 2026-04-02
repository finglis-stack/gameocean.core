package net.gameocean.core.minigames.bedwars.data;

import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Représente une équipe dans Bedwars
 */
public class BedwarsTeam {
    
    private final String color;
    private final String name;
    private Location spawn;
    private Location bedLocation;
    private boolean bedDestroyed = false;
    private final Set<UUID> players = new HashSet<>();
    private boolean eliminated = false;
    
    public BedwarsTeam(String color, String name) {
        this.color = color;
        this.name = name;
    }
    
    public String getColor() {
        return color;
    }
    
    public String getName() {
        return name;
    }
    
    public Location getSpawn() {
        return spawn;
    }
    
    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }
    
    public Location getBedLocation() {
        return bedLocation;
    }
    
    public void setBedLocation(Location bedLocation) {
        this.bedLocation = bedLocation;
    }
    
    public boolean isBedDestroyed() {
        return bedDestroyed;
    }
    
    public void setBedDestroyed(boolean bedDestroyed) {
        this.bedDestroyed = bedDestroyed;
    }
    
    public Set<UUID> getPlayers() {
        return players;
    }
    
    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }
    
    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }
    
    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }
    
    public int getPlayerCount() {
        return players.size();
    }
    
    public boolean isEliminated() {
        return eliminated;
    }
    
    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }
    
    /**
     * Obtient la couleur ChatColor correspondante
     */
    public ChatColor getChatColor() {
        return getChatColorFromString(color);
    }
    
    /**
     * Obtient le code couleur pour le scoreboard
     */
    public String getColorCode() {
        ChatColor chatColor = getChatColor();
        return chatColor.toString();
    }
    
    public static ChatColor getChatColorFromString(String color) {
        switch (color.toLowerCase()) {
            case "red":
            case "rouge":
                return ChatColor.RED;
            case "blue":
            case "bleu":
                return ChatColor.BLUE;
            case "green":
            case "vert":
                return ChatColor.GREEN;
            case "yellow":
            case "jaune":
                return ChatColor.YELLOW;
            case "aqua":
            case "cyan":
                return ChatColor.AQUA;
            case "white":
            case "blanc":
                return ChatColor.WHITE;
            case "pink":
            case "rose":
                return ChatColor.LIGHT_PURPLE;
            case "magenta":
            case "purple":
                return ChatColor.DARK_PURPLE;
            case "gray":
            case "gris":
                return ChatColor.GRAY;
            case "black":
            case "noir":
                return ChatColor.BLACK;
            case "orange":
                return ChatColor.GOLD;
            case "dark_gray":
            case "dark_gris":
                return ChatColor.DARK_GRAY;
            case "dark_blue":
            case "dark_bleu":
                return ChatColor.DARK_BLUE;
            case "dark_green":
            case "dark_vert":
                return ChatColor.DARK_GREEN;
            case "dark_aqua":
            case "dark_cyan":
                return ChatColor.DARK_AQUA;
            case "dark_red":
            case "dark_rouge":
                return ChatColor.DARK_RED;
            default:
                return ChatColor.WHITE;
        }
    }
}