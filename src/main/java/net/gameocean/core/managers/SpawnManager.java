package net.gameocean.core.managers;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class SpawnManager {

    private final GameOceanCore plugin;
    private Location spawnLocation;

    public SpawnManager(GameOceanCore plugin) {
        this.plugin = plugin;
        loadSpawn();
    }

    public void loadSpawn() {
        if (!plugin.getConfig().contains("spawn.world")) {
            spawnLocation = null;
            return;
        }

        String worldName = plugin.getConfig().getString("spawn.world");
        World world = plugin.getServer().getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Monde du spawn non trouvé: " + worldName);
            spawnLocation = null;
            return;
        }

        double x = plugin.getConfig().getDouble("spawn.x");
        double y = plugin.getConfig().getDouble("spawn.y");
        double z = plugin.getConfig().getDouble("spawn.z");
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw");
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch");

        spawnLocation = new Location(world, x, y, z, yaw, pitch);
        plugin.getLogger().info("Spawn du lobby chargé: " + worldName + " " + x + " " + y + " " + z);
    }

    public Location getSpawn() {
        return spawnLocation;
    }

    public boolean hasSpawn() {
        return spawnLocation != null;
    }

    public void teleportToSpawn(Player player) {
        if (spawnLocation == null) {
            plugin.getLogger().info("Pas de spawn défini pour " + player.getName());
            return;
        }

        // Ajouter 0.5 à Y pour éviter de spawner dans le sol
        Location safeLoc = spawnLocation.clone();
        safeLoc.setY(safeLoc.getY() + 0.5);
        
        player.teleport(safeLoc);
        player.sendMessage(net.gameocean.core.utils.MessageUtils.format("&eVous avez été téléporté au spawn !"));
        plugin.getLogger().info(player.getName() + " téléporté au spawn du lobby.");
    }

    public void setupLobbyWorld() {
        if (spawnLocation == null) return;

        World world = spawnLocation.getWorld();
        if (world == null) return;

        // Toujours jour
        world.setTime(6000);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        
        // Tickrate à 0 (empêche la pousse, la fonte, etc.)
        world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);

        // Pas de pluie
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);

        // Pas de dégâts de chute
        world.setGameRule(org.bukkit.GameRule.FALL_DAMAGE, false);

        // Pas de dégâts de faim
        world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, true);

        plugin.getLogger().info("Configuration du monde lobby appliquée sur: " + world.getName());
    }

    public void refreshLobbySettings() {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        setupLobbyWorld();
    }
}
