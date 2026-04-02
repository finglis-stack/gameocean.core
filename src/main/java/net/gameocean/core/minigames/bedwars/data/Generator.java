package net.gameocean.core.minigames.bedwars.data;

import org.bukkit.Location;

/**
 * Représente un générateur de ressources
 */
public class Generator {
    
    private final GeneratorType type;
    private final Location location;
    private int tier;
    private int interval;
    private int lastSpawn = 0;
    
    public Generator(GeneratorType type, Location location, int tier) {
        this.type = type;
        this.location = location;
        this.tier = tier;
        this.interval = calculateInterval();
    }
    
    public GeneratorType getType() {
        return type;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public int getTier() {
        return tier;
    }
    
    public void setTier(int tier) {
        this.tier = tier;
        this.interval = calculateInterval();
    }
    
    public int getInterval() {
        return interval;
    }
    
    public int getLastSpawn() {
        return lastSpawn;
    }
    
    public void setLastSpawn(int lastSpawn) {
        this.lastSpawn = lastSpawn;
    }
    
    /**
     * Calcule l'intervalle de spawn selon le tier
     */
    private int calculateInterval() {
        int baseInterval = type.getDefaultInterval();
        // Réduire l'intervalle de 20% par tier
        return Math.max(1, (int) (baseInterval * (1 - (tier - 1) * 0.2)));
    }
    
    /**
     * Vérifie si c'est le moment de faire apparaître une ressource
     */
    public boolean shouldSpawn(int currentTick) {
        return currentTick - lastSpawn >= interval;
    }
}