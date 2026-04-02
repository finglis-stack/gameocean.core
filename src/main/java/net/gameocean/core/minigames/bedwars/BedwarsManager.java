package net.gameocean.core.minigames.bedwars;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.commands.BedwarsCommand;
import net.gameocean.core.minigames.bedwars.listeners.BedwarsListener;
import net.gameocean.core.minigames.bedwars.managers.*;
import org.bukkit.plugin.PluginManager;

/**
 * Gestionnaire principal du mini-jeu Bedwars Solo
 */
public class BedwarsManager {
    
    private final GameOceanCore plugin;
    private final ArenaManager arenaManager;
    private final BedwarsScoreboardManager scoreboardManager;
    private final BuildModeManager buildModeManager;
    private final GeneratorManager generatorManager;
    private final NPCShopManager npcShopManager;
    private final BedwarsListener bedwarsListener;
    
    public BedwarsManager(GameOceanCore plugin) {
        this.plugin = plugin;
        
        plugin.getLogger().info("=== Initialisation du module BedwarsSolo ===");
        
        // Initialiser les managers
        this.arenaManager = new ArenaManager(plugin);
        plugin.getLogger().info(" ArenaManager initialisé");
        
        // Utiliser le scoreboardManager de l'ArenaManager
        this.scoreboardManager = arenaManager.getScoreboardManager();
        plugin.getLogger().info(" BedwarsScoreboardManager initialisé");
        
        this.buildModeManager = new BuildModeManager();
        plugin.getLogger().info(" BuildModeManager initialisé");
        
        this.generatorManager = new GeneratorManager(plugin);
        plugin.getLogger().info(" GeneratorManager initialisé");
        
        this.npcShopManager = new NPCShopManager(plugin);
        plugin.getLogger().info(" NPCShopManager initialisé");
        
        // Initialiser le listener
        this.bedwarsListener = new BedwarsListener(plugin, arenaManager, scoreboardManager, npcShopManager);
        
        // Enregistrer les listeners et commandes
        registerListeners();
        registerCommands();
        
        plugin.getLogger().info("=== Module BedwarsSolo chargé avec succès ===");
    }
    
    private void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(bedwarsListener, plugin);
        plugin.getLogger().info(" BedwarsListener enregistré");
    }
    
    private void registerCommands() {
        plugin.getCommand("bw").setExecutor(new BedwarsCommand(plugin, arenaManager));
        plugin.getLogger().info(" Commande /bw enregistrée");
    }
    
    // Getters
    
    public ArenaManager getArenaManager() {
        return arenaManager;
    }
    
    public BedwarsScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    public BuildModeManager getBuildModeManager() {
        return buildModeManager;
    }
    
    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }
    
    public NPCShopManager getNpcShopManager() {
        return npcShopManager;
    }
    
    public BedwarsListener getBedwarsListener() {
        return bedwarsListener;
    }
    
    /**
     * Arrête proprement le module Bedwars
     */
    public void shutdown() {
        plugin.getLogger().info("=== Arrêt du module BedwarsSolo ===");
        
        // Arrêter les générateurs
        generatorManager.stopAll();
        
        // Sauvegarder toutes les arènes
        // TODO: Sauvegarde si nécessaire
        
        plugin.getLogger().info("=== Module BedwarsSolo arrêté ===");
    }
}
