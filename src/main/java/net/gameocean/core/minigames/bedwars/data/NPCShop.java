package net.gameocean.core.minigames.bedwars.data;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Représente un NPC (vendeur)
 */
public class NPCShop {
    
    private final NPCType type;
    private final Location location;
    private Entity entity;
    
    public NPCShop(NPCType type, Location location) {
        this.type = type;
        this.location = location;
    }
    
    public NPCType getType() {
        return type;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public Entity getEntity() {
        return entity;
    }
    
    public void setEntity(Entity entity) {
        this.entity = entity;
    }
    
    /**
     * Supprime l'entité NPC du monde
     */
    public void remove() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
            entity = null;
        }
    }
}