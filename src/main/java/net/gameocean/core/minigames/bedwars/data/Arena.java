package net.gameocean.core.minigames.bedwars.data;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Représente une arène Bedwars Solo
 */
public class Arena {
    
    private final String name;
    private String displayName;
    private Location waitingSpawn;
    private String waitingSpawnWorld;
    private double waitingSpawnX, waitingSpawnY, waitingSpawnZ;
    private float waitingSpawnYaw, waitingSpawnPitch;
    private Location pos1;
    private Location pos2;
    private final Map<String, BedwarsTeam> teams = new HashMap<>();
    private final List<Generator> generators = new ArrayList<>();
    private final List<NPCShop> npcs = new ArrayList<>();
    private int minPlayers = 2;
    private int maxPlayers = 8;
    private ArenaState state = ArenaState.WAITING;
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, BedwarsPlayerData> playerData = new HashMap<>();
    
    public Arena(String name) {
        this.name = name;
        this.displayName = name;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public Location getWaitingSpawn() {
        // Si on a une location valide, la retourner
        if (waitingSpawn != null && waitingSpawn.getWorld() != null) {
            return waitingSpawn;
        }
        
        // Sinon, essayer de créer la location si on a les coordonnées et que le monde est maintenant chargé
        if (waitingSpawnWorld != null && org.bukkit.Bukkit.getWorld(waitingSpawnWorld) != null) {
            waitingSpawn = new Location(
                org.bukkit.Bukkit.getWorld(waitingSpawnWorld),
                waitingSpawnX, waitingSpawnY, waitingSpawnZ,
                waitingSpawnYaw, waitingSpawnPitch
            );
            return waitingSpawn;
        }
        
        return null;
    }
    
    public void setWaitingSpawn(Location waitingSpawn) {
        this.waitingSpawn = waitingSpawn;
        if (waitingSpawn != null) {
            this.waitingSpawnWorld = waitingSpawn.getWorld().getName();
            this.waitingSpawnX = waitingSpawn.getX();
            this.waitingSpawnY = waitingSpawn.getY();
            this.waitingSpawnZ = waitingSpawn.getZ();
            this.waitingSpawnYaw = waitingSpawn.getYaw();
            this.waitingSpawnPitch = waitingSpawn.getPitch();
        }
    }
    
    public String getWaitingSpawnWorld() {
        return waitingSpawnWorld;
    }
    
    public Location getPos1() {
        return pos1;
    }
    
    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }
    
    public Location getPos2() {
        return pos2;
    }
    
    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }
    
    public Map<String, BedwarsTeam> getTeams() {
        return teams;
    }
    
    public void addTeam(BedwarsTeam team) {
        teams.put(team.getColor().toLowerCase(), team);
    }
    
    public BedwarsTeam getTeam(String color) {
        return teams.get(color.toLowerCase());
    }
    
    public List<Generator> getGenerators() {
        return generators;
    }
    
    public void addGenerator(Generator generator) {
        generators.add(generator);
    }
    
    public List<NPCShop> getNpcs() {
        return npcs;
    }
    
    public void addNpc(NPCShop npc) {
        npcs.add(npc);
    }
    
    public int getMinPlayers() {
        return minPlayers;
    }
    
    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }
    
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
    
    public ArenaState getState() {
        return state;
    }
    
    public void setState(ArenaState state) {
        this.state = state;
    }
    
    public Set<UUID> getPlayers() {
        return players;
    }
    
    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }
    
    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }
    
    public int getPlayerCount() {
        return players.size();
    }
    
    public Map<UUID, BedwarsPlayerData> getPlayerData() {
        return playerData;
    }
    
    public BedwarsPlayerData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }
    
    public void setPlayerData(UUID uuid, BedwarsPlayerData data) {
        playerData.put(uuid, data);
    }
    
    /**
     * Vérifie si l'arène est complète
     */
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }
    
    /**
     * Vérifie si l'arène a assez de joueurs pour démarrer
     */
    public boolean hasEnoughPlayers() {
        return players.size() >= minPlayers;
    }
    
    /**
     * Vérifie si une location est dans les limites de l'arène
     */
    public boolean isInBounds(Location loc) {
        if (pos1 == null || pos2 == null) return true;
        
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        return loc.getX() >= minX && loc.getX() <= maxX &&
               loc.getY() >= minY && loc.getY() <= maxY &&
               loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }
    
    /**
     * Sauvegarde l'arène dans un fichier YAML
     */
    public void save(File file) {
        FileConfiguration config = new YamlConfiguration();
        
        config.set("name", name);
        config.set("displayName", displayName);
        config.set("minPlayers", minPlayers);
        config.set("maxPlayers", maxPlayers);
        
        if (waitingSpawn != null) {
            config.set("waitingSpawn.world", waitingSpawn.getWorld().getName());
            config.set("waitingSpawn.x", waitingSpawn.getX());
            config.set("waitingSpawn.y", waitingSpawn.getY());
            config.set("waitingSpawn.z", waitingSpawn.getZ());
            config.set("waitingSpawn.yaw", waitingSpawn.getYaw());
            config.set("waitingSpawn.pitch", waitingSpawn.getPitch());
        }
        
        if (pos1 != null) {
            config.set("bounds.pos1.world", pos1.getWorld().getName());
            config.set("bounds.pos1.x", pos1.getX());
            config.set("bounds.pos1.y", pos1.getY());
            config.set("bounds.pos1.z", pos1.getZ());
        }
        
        if (pos2 != null) {
            config.set("bounds.pos2.world", pos2.getWorld().getName());
            config.set("bounds.pos2.x", pos2.getX());
            config.set("bounds.pos2.y", pos2.getY());
            config.set("bounds.pos2.z", pos2.getZ());
        }
        
        // Sauvegarder les équipes
        for (Map.Entry<String, BedwarsTeam> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey() + ".";
            BedwarsTeam team = entry.getValue();
            
            config.set(path + "color", team.getColor());
            config.set(path + "name", team.getName());
            
            if (team.getSpawn() != null) {
                config.set(path + "spawn.world", team.getSpawn().getWorld().getName());
                config.set(path + "spawn.x", team.getSpawn().getX());
                config.set(path + "spawn.y", team.getSpawn().getY());
                config.set(path + "spawn.z", team.getSpawn().getZ());
                config.set(path + "spawn.yaw", team.getSpawn().getYaw());
                config.set(path + "spawn.pitch", team.getSpawn().getPitch());
            }
            
            if (team.getBedLocation() != null) {
                config.set(path + "bed.world", team.getBedLocation().getWorld().getName());
                config.set(path + "bed.x", team.getBedLocation().getX());
                config.set(path + "bed.y", team.getBedLocation().getY());
                config.set(path + "bed.z", team.getBedLocation().getZ());
            }
        }
        
        // Sauvegarder les générateurs
        for (int i = 0; i < generators.size(); i++) {
            Generator gen = generators.get(i);
            String path = "generators." + i + ".";
            
            config.set(path + "type", gen.getType().name());
            config.set(path + "location.world", gen.getLocation().getWorld().getName());
            config.set(path + "location.x", gen.getLocation().getX());
            config.set(path + "location.y", gen.getLocation().getY());
            config.set(path + "location.z", gen.getLocation().getZ());
            config.set(path + "tier", gen.getTier());
        }
        
        // Sauvegarder les NPCs
        for (int i = 0; i < npcs.size(); i++) {
            NPCShop npc = npcs.get(i);
            String path = "npcs." + i + ".";
            
            config.set(path + "type", npc.getType().name());
            config.set(path + "location.world", npc.getLocation().getWorld().getName());
            config.set(path + "location.x", npc.getLocation().getX());
            config.set(path + "location.y", npc.getLocation().getY());
            config.set(path + "location.z", npc.getLocation().getZ());
            config.set(path + "location.yaw", npc.getLocation().getYaw());
            config.set(path + "location.pitch", npc.getLocation().getPitch());
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Charge une arène depuis un fichier YAML
     */
    public static Arena load(File file, org.bukkit.plugin.java.JavaPlugin plugin) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        String name = config.getString("name", file.getName().replace(".yml", ""));
        net.gameocean.core.GameOceanCore core = (net.gameocean.core.GameOceanCore) plugin;
        Arena arena = new Arena(name);
        
        arena.setDisplayName(config.getString("displayName", name));
        arena.setMinPlayers(config.getInt("minPlayers", 2));
        arena.setMaxPlayers(config.getInt("maxPlayers", 8));
        
        if (config.contains("waitingSpawn")) {
            String world = config.getString("waitingSpawn.world");
            double x = config.getDouble("waitingSpawn.x");
            double y = config.getDouble("waitingSpawn.y");
            double z = config.getDouble("waitingSpawn.z");
            float yaw = (float) config.getDouble("waitingSpawn.yaw");
            float pitch = (float) config.getDouble("waitingSpawn.pitch");
            
            // Stocker les coordonnées même si le monde n'est pas chargé
            arena.waitingSpawnWorld = world;
            arena.waitingSpawnX = x;
            arena.waitingSpawnY = y;
            arena.waitingSpawnZ = z;
            arena.waitingSpawnYaw = yaw;
            arena.waitingSpawnPitch = pitch;
            
            // Créer la location si le monde est déjà chargé
            if (world != null && plugin.getServer().getWorld(world) != null) {
                arena.waitingSpawn = new Location(plugin.getServer().getWorld(world), x, y, z, yaw, pitch);
            }
        }
        
        if (config.contains("bounds.pos1")) {
            String world = config.getString("bounds.pos1.world");
            double x = config.getDouble("bounds.pos1.x");
            double y = config.getDouble("bounds.pos1.y");
            double z = config.getDouble("bounds.pos1.z");
            
            if (world != null) {
                if (plugin.getServer().getWorld(world) == null) core.getWorldManager().loadWorld(world);
                if (plugin.getServer().getWorld(world) != null) {
                    arena.setPos1(new Location(plugin.getServer().getWorld(world), x, y, z));
                }
            }
        }
        
        if (config.contains("bounds.pos2")) {
            String world = config.getString("bounds.pos2.world");
            double x = config.getDouble("bounds.pos2.x");
            double y = config.getDouble("bounds.pos2.y");
            double z = config.getDouble("bounds.pos2.z");
            
            if (world != null) {
                if (plugin.getServer().getWorld(world) == null) core.getWorldManager().loadWorld(world);
                if (plugin.getServer().getWorld(world) != null) {
                    arena.setPos2(new Location(plugin.getServer().getWorld(world), x, y, z));
                }
            }
        }
        
        // Charger les équipes
        if (config.contains("teams")) {
            for (String color : config.getConfigurationSection("teams").getKeys(false)) {
                String path = "teams." + color + ".";
                
                String teamName = config.getString(path + "name", color);
                BedwarsTeam team = new BedwarsTeam(color, teamName);
                
                if (config.contains(path + "spawn")) {
                    String world = config.getString(path + "spawn.world");
                    double x = config.getDouble(path + "spawn.x");
                    double y = config.getDouble(path + "spawn.y");
                    double z = config.getDouble(path + "spawn.z");
                    float yaw = (float) config.getDouble(path + "spawn.yaw");
                    float pitch = (float) config.getDouble(path + "spawn.pitch");
                    
                    if (world != null) {
                        if (plugin.getServer().getWorld(world) == null) core.getWorldManager().loadWorld(world);
                        if (plugin.getServer().getWorld(world) != null) {
                            team.setSpawn(new Location(plugin.getServer().getWorld(world), x, y, z, yaw, pitch));
                        }
                    }
                }
                
                if (config.contains(path + "bed")) {
                    String world = config.getString(path + "bed.world");
                    double x = config.getDouble(path + "bed.x");
                    double y = config.getDouble(path + "bed.y");
                    double z = config.getDouble(path + "bed.z");
                    
                    if (world != null) {
                        if (plugin.getServer().getWorld(world) == null) core.getWorldManager().loadWorld(world);
                        if (plugin.getServer().getWorld(world) != null) {
                            team.setBedLocation(new Location(plugin.getServer().getWorld(world), x, y, z));
                        }
                    }
                }
                
                arena.addTeam(team);
            }
        }
        
        // Charger les générateurs
        if (config.contains("generators")) {
            for (String key : config.getConfigurationSection("generators").getKeys(false)) {
                String path = "generators." + key + ".";
                
                GeneratorType type = GeneratorType.valueOf(config.getString(path + "type", "IRON"));
                String world = config.getString(path + "location.world");
                double x = config.getDouble(path + "location.x");
                double y = config.getDouble(path + "location.y");
                double z = config.getDouble(path + "location.z");
                int tier = config.getInt(path + "tier", 1);
                
                if (world != null) {
                    if (plugin.getServer().getWorld(world) == null) core.getWorldManager().loadWorld(world);
                    if (plugin.getServer().getWorld(world) != null) {
                        Location loc = new Location(plugin.getServer().getWorld(world), x, y, z);
                        arena.addGenerator(new Generator(type, loc, tier));
                    }
                }
            }
        }
        
        // Charger les NPCs
        if (config.contains("npcs")) {
            for (String key : config.getConfigurationSection("npcs").getKeys(false)) {
                String path = "npcs." + key + ".";
                
                NPCType type = NPCType.valueOf(config.getString(path + "type", "SHOP"));
                String world = config.getString(path + "location.world");
                double x = config.getDouble(path + "location.x");
                double y = config.getDouble(path + "location.y");
                double z = config.getDouble(path + "location.z");
                float yaw = (float) config.getDouble(path + "location.yaw");
                float pitch = (float) config.getDouble(path + "location.pitch");
                
                if (world != null) {
                    if (plugin.getServer().getWorld(world) == null) core.getWorldManager().loadWorld(world);
                    if (plugin.getServer().getWorld(world) != null) {
                        Location loc = new Location(plugin.getServer().getWorld(world), x, y, z, yaw, pitch);
                        arena.addNpc(new NPCShop(type, loc));
                    }
                }
            }
        }
        
        return arena;
    }
}