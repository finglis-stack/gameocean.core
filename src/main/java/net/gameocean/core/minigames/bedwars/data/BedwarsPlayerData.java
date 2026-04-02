package net.gameocean.core.minigames.bedwars.data;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Données d'un joueur pendant une partie de Bedwars
 */
public class BedwarsPlayerData {
    
    private final UUID uuid;
    private String teamColor;
    private boolean alive = true;
    private int kills = 0;
    private int finalKills = 0;
    private int bedsDestroyed = 0;
    private int ironCollected = 0;
    private int goldCollected = 0;
    private int diamondCollected = 0;
    private int emeraldCollected = 0;
    
    public BedwarsPlayerData(Player player) {
        this.uuid = player.getUniqueId();
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    public String getTeamColor() {
        return teamColor;
    }
    
    public void setTeamColor(String teamColor) {
        this.teamColor = teamColor;
    }
    
    public boolean isAlive() {
        return alive;
    }
    
    public void setAlive(boolean alive) {
        this.alive = alive;
    }
    
    public int getKills() {
        return kills;
    }
    
    public void addKill() {
        this.kills++;
    }
    
    public int getFinalKills() {
        return finalKills;
    }
    
    public void addFinalKill() {
        this.finalKills++;
    }
    
    public int getBedsDestroyed() {
        return bedsDestroyed;
    }
    
    public void addBedDestroyed() {
        this.bedsDestroyed++;
    }
    
    public int getIronCollected() {
        return ironCollected;
    }
    
    public void addIron(int amount) {
        this.ironCollected += amount;
    }
    
    public int getGoldCollected() {
        return goldCollected;
    }
    
    public void addGold(int amount) {
        this.goldCollected += amount;
    }
    
    public int getDiamondCollected() {
        return diamondCollected;
    }
    
    public void addDiamond(int amount) {
        this.diamondCollected += amount;
    }
    
    public int getEmeraldCollected() {
        return emeraldCollected;
    }
    
    public void addEmerald(int amount) {
        this.emeraldCollected += amount;
    }
}