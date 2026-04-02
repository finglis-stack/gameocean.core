package net.gameocean.core.minigames.bedwars;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BuildModeManager {
    
    private final Set<UUID> builders = new HashSet<>();
    
    public void toggleBuildMode(Player player) {
        UUID uuid = player.getUniqueId();
        if (builders.contains(uuid)) {
            builders.remove(uuid);
            player.sendMessage("§cMode construction désactivé.");
        } else {
            builders.add(uuid);
            player.sendMessage("§aMode construction activé.");
        }
    }
    
    public boolean isBuilder(Player player) {
        return builders.contains(player.getUniqueId());
    }
}
