package net.gameocean.core.managers;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Gestionnaire des mondes - charge/décharge et configuration
 */
public class WorldManager {
    
    private final GameOceanCore plugin;
    private final File worldsConfigFile;
    private FileConfiguration worldsConfig;
    private final Set<String> autoLoadWorlds = new HashSet<>();
    private final Map<String, String> worldAliases = new HashMap<>();
    
    public WorldManager(GameOceanCore plugin) {
        this.plugin = plugin;
        this.worldsConfigFile = new File(plugin.getDataFolder(), "worlds.yml");
        
        loadConfig();
        loadAutoLoadWorlds();
    }
    
    /**
     * Charge la configuration des mondes
     */
    private void loadConfig() {
        if (!worldsConfigFile.exists()) {
            try {
                worldsConfigFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de créer worlds.yml");
            }
        }
        
        worldsConfig = YamlConfiguration.loadConfiguration(worldsConfigFile);
    }
    
    /**
     * Sauvegarde la configuration
     */
    public void saveConfig() {
        try {
            worldsConfig.save(worldsConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder worlds.yml");
        }
    }
    
    /**
     * Charge les mondes configurés pour le démarrage automatique
     */
    private void loadAutoLoadWorlds() {
        if (worldsConfig.contains("autoload")) {
            autoLoadWorlds.addAll(worldsConfig.getStringList("autoload"));
        }
        
        // Charger les alias
        if (worldsConfig.contains("aliases")) {
            for (String key : worldsConfig.getConfigurationSection("aliases").getKeys(false)) {
                worldAliases.put(key.toLowerCase(), worldsConfig.getString("aliases." + key));
            }
        }
        
        // Charger les mondes auto-load UNIQUEMENT s'ils existent
        for (String worldName : autoLoadWorlds) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                File levelDat = new File(worldFolder, "level.dat");
                if (levelDat.exists()) {
                    loadWorld(worldName);
                } else {
                    plugin.getLogger().warning("Monde '" + worldName + "' en auto-load mais level.dat manquant.");
                }
            } else {
                plugin.getLogger().warning("Monde '" + worldName + "' en auto-load mais dossier introuvable.");
            }
        }
    }
    
    /**
     * Charge un monde
     */
    public boolean loadWorld(String worldName) {
        // Vérifier si le monde existe déjà
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            return true; // Déjà chargé
        }
        
        // Vérifier si le dossier du monde existe
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists() || !worldFolder.isDirectory()) {
            plugin.getLogger().warning("Le monde '" + worldName + "' n'existe pas (dossier introuvable)");
            return false;
        }
        
        // Charger le monde
        plugin.getLogger().info("Chargement du monde: " + worldName);
        WorldCreator creator = new WorldCreator(worldName);
        World world = Bukkit.createWorld(creator);
        
        if (world != null) {
            plugin.getLogger().info("Monde '" + worldName + "' chargé avec succès!");
            return true;
        } else {
            plugin.getLogger().warning("Échec du chargement du monde '" + worldName + "'");
            return false;
        }
    }
    
    /**
     * Décharge un monde
     */
    public boolean unloadWorld(String worldName, boolean save) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Le monde '" + worldName + "' n'est pas chargé");
            return false;
        }
        
        // Vérifier que ce n'est pas le monde principal
        if (world.equals(Bukkit.getWorlds().get(0))) {
            plugin.getLogger().warning("Impossible de décharger le monde principal!");
            return false;
        }
        
        // Téléporter les joueurs vers le monde principal
        World defaultWorld = Bukkit.getWorlds().get(0);
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            player.teleport(defaultWorld.getSpawnLocation());
            player.sendMessage("§cLe monde où vous étiez a été déchargé.");
        }
        
        // Décharger
        plugin.getLogger().info("Déchargement du monde: " + worldName);
        boolean success = Bukkit.unloadWorld(world, save);
        
        if (success) {
            plugin.getLogger().info("Monde '" + worldName + "' déchargé avec succès!");
        } else {
            plugin.getLogger().warning("Échec du déchargement du monde '" + worldName + "'");
        }
        
        return success;
    }
    
    /**
     * Crée un nouveau monde en VOID (complètement vide)
     */
    public World createWorld(String worldName, String type) {
        // Vérifier si le monde existe déjà
        if (Bukkit.getWorld(worldName) != null || new File(Bukkit.getWorldContainer(), worldName).exists()) {
            return null;
        }
        
        plugin.getLogger().info("Création du monde VOID: " + worldName);
        
        WorldCreator creator = new WorldCreator(worldName);
        // Ne PAS utiliser creator.type() - on veut juste du void pur
        // Utiliser uniquement le générateur void
        creator.generator(new net.gameocean.core.utils.VoidWorldGenerator());
        // Générer un monde vide de type NORMAL mais avec notre générateur
        creator.environment(org.bukkit.World.Environment.NORMAL);
        World world = creator.createWorld();
        
        if (world != null) {
            // Paramètres pour un monde void optimal
            world.setSpawnLocation(0, 64, 0);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            world.setTime(6000); // Midi permanent
            
            plugin.getLogger().info("Monde void '" + worldName + "' créé avec succès!");
        }
        
        return world;
    }
    
    /**
     * Supprime un monde
     */
    public boolean deleteWorld(String worldName) {
        // D'abord décharger
        unloadWorld(worldName, false);
        
        // Supprimer le dossier
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists()) {
            return false;
        }
        
        return deleteDirectory(worldFolder);
    }
    
    /**
     * Supprime récursivement un dossier
     */
    private boolean deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return directory.delete();
    }
    
    /**
     * Ajoute un monde au chargement automatique
     */
    public void addAutoLoadWorld(String worldName) {
        autoLoadWorlds.add(worldName);
        worldsConfig.set("autoload", new ArrayList<>(autoLoadWorlds));
        saveConfig();
    }
    
    /**
     * Retire un monde du chargement automatique
     */
    public void removeAutoLoadWorld(String worldName) {
        autoLoadWorlds.remove(worldName);
        worldsConfig.set("autoload", new ArrayList<>(autoLoadWorlds));
        saveConfig();
    }
    
    /**
     * Vérifie si un monde est en auto-load
     */
    public boolean isAutoLoadWorld(String worldName) {
        return autoLoadWorlds.contains(worldName);
    }
    
    /**
     * Définit un alias pour un monde
     */
    public void setWorldAlias(String worldName, String alias) {
        worldAliases.put(worldName.toLowerCase(), alias);
        worldsConfig.set("aliases." + worldName.toLowerCase(), alias);
        saveConfig();
    }
    
    /**
     * Obtient l'alias d'un monde
     */
    public String getWorldAlias(String worldName) {
        return worldAliases.getOrDefault(worldName.toLowerCase(), worldName);
    }
    
    /**
     * Obtient la liste des mondes chargés
     */
    public List<World> getLoadedWorlds() {
        return Bukkit.getWorlds();
    }
    
    /**
     * Obtient la liste des mondes disponibles (dossiers)
     */
    public List<String> getAvailableWorlds() {
        List<String> worlds = new ArrayList<>();
        File container = Bukkit.getWorldContainer();
        
        File[] files = container.listFiles();
        if (files == null) return worlds;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Vérifier si c'est un monde valide (contient level.dat)
                File levelDat = new File(file, "level.dat");
                if (levelDat.exists()) {
                    worlds.add(file.getName());
                }
            }
        }
        
        return worlds;
    }
    
    /**
     * Téléporte un joueur vers un monde
     */
    public boolean teleportToWorld(org.bukkit.entity.Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }
        
        player.teleport(world.getSpawnLocation());
        return true;
    }
    
    /**
     * Obtient des informations sur un monde
     */
    public String getWorldInfo(World world) {
        StringBuilder info = new StringBuilder();
        info.append("§6Monde: §f").append(world.getName()).append("\n");
        info.append("§7Type: §f").append(world.getWorldType().name()).append("\n");
        info.append("§7Environnement: §f").append(world.getEnvironment().name()).append("\n");
        info.append("§7Joueurs: §f").append(world.getPlayerCount()).append("\n");
        info.append("§7Chunks chargés: §f").append(world.getLoadedChunks().length).append("\n");
        info.append("§7Entités: §f").append(world.getEntities().size()).append("\n");
        info.append("§7Seed: §f").append(world.getSeed()).append("\n");
        info.append("§7Auto-load: §f").append(isAutoLoadWorld(world.getName()) ? "§aOui" : "§cNon");
        
        return info.toString();
    }
}