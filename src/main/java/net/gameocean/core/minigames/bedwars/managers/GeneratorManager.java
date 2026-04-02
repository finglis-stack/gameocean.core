package net.gameocean.core.minigames.bedwars.managers;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.data.Arena;
import net.gameocean.core.minigames.bedwars.data.Generator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire des générateurs de ressources
 */
public class GeneratorManager {
    
    private final GameOceanCore plugin;
    private final Map<Arena, BukkitRunnable> arenaTasks = new HashMap<>();
    private final Map<Arena, Integer> arenaTicks = new HashMap<>();
    
    public GeneratorManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Démarre les générateurs pour une arène
     */
    public void startGenerators(Arena arena) {
        if (arenaTasks.containsKey(arena)) {
            return;
        }
        
        arenaTicks.put(arena, 0);
        
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState().name().equals("ENDING") || arena.getState().name().equals("RESTARTING")) {
                    stopGenerators(arena);
                    return;
                }
                
                int currentTick = arenaTicks.getOrDefault(arena, 0) + 1;
                arenaTicks.put(arena, currentTick);
                
                for (Generator generator : arena.getGenerators()) {
                    if (generator.shouldSpawn(currentTick)) {
                        spawnResource(generator);
                        generator.setLastSpawn(currentTick);
                    }
                }
            }
        };
        
        task.runTaskTimer(plugin, 20L, 20L); // Toutes les secondes
        arenaTasks.put(arena, task);
    }
    
    /**
     * Arrête les générateurs d'une arène
     */
    public void stopGenerators(Arena arena) {
        BukkitRunnable task = arenaTasks.remove(arena);
        if (task != null) {
            task.cancel();
        }
        arenaTicks.remove(arena);
    }
    
    /**
     * Fait apparaître une ressource à un générateur
     */
    private void spawnResource(Generator generator) {
        Location loc = generator.getLocation().clone().add(0.5, 1.0, 0.5);
        ItemStack item = new ItemStack(generator.getType().getMaterial());
        
        Item droppedItem = loc.getWorld().dropItem(loc, item);
        droppedItem.setVelocity(new Vector(0, 0.1, 0));
        droppedItem.setPickupDelay(10);
    }
    
    /**
     * Améliore tous les générateurs d'un certain type dans une arène
     */
    public void upgradeGenerators(Arena arena, net.gameocean.core.minigames.bedwars.data.GeneratorType type) {
        for (Generator generator : arena.getGenerators()) {
            if (generator.getType() == type) {
                generator.setTier(generator.getTier() + 1);
            }
        }
    }
    
    /**
     * Obtient le tier maximum d'un type de générateur dans une arène
     */
    public int getMaxTier(Arena arena, net.gameocean.core.minigames.bedwars.data.GeneratorType type) {
        int maxTier = 1;
        for (Generator generator : arena.getGenerators()) {
            if (generator.getType() == type && generator.getTier() > maxTier) {
                maxTier = generator.getTier();
            }
        }
        return maxTier;
    }
    
    /**
     * Arrête tous les générateurs
     */
    public void stopAll() {
        for (BukkitRunnable task : arenaTasks.values()) {
            task.cancel();
        }
        arenaTasks.clear();
        arenaTicks.clear();
    }
}