package net.gameocean.core.minigames.bedwars.data;

/**
 * Type de NPC
 */
public enum NPCType {
    SHOP("§6§lSHOP", "Shop"),
    UPGRADE("§b§lUPGRADE", "Améliorations");
    
    private final String displayName;
    private final String simpleName;
    
    NPCType(String displayName, String simpleName) {
        this.displayName = displayName;
        this.simpleName = simpleName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getSimpleName() {
        return simpleName;
    }
}