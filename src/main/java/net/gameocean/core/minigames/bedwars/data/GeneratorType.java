package net.gameocean.core.minigames.bedwars.data;

import org.bukkit.Material;

/**
 * Type de générateur de ressources
 */
public enum GeneratorType {
    IRON(Material.IRON_INGOT, "§7Fer", 2),
    GOLD(Material.GOLD_INGOT, "§6Or", 4),
    DIAMOND(Material.DIAMOND, "§bDiamant", 30),
    EMERALD(Material.EMERALD, "§aÉmeraude", 60);
    
    private final Material material;
    private final String displayName;
    private final int defaultInterval;
    
    GeneratorType(Material material, String displayName, int defaultInterval) {
        this.material = material;
        this.displayName = displayName;
        this.defaultInterval = defaultInterval;
    }
    
    public Material getMaterial() {
        return material;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getDefaultInterval() {
        return defaultInterval;
    }
}