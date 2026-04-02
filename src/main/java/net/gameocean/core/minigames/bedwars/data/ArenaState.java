package net.gameocean.core.minigames.bedwars.data;

/**
 * État d'une arène Bedwars
 */
public enum ArenaState {
    WAITING("§aEn attente"),
    STARTING("§eDémarrage..."),
    IN_GAME("§cEn jeu"),
    ENDING("§6Fin de partie"),
    RESTARTING("§7Redémarrage");
    
    private final String displayName;
    
    ArenaState(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}