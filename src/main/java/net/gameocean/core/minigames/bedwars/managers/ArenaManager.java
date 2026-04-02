package net.gameocean.core.minigames.bedwars.managers;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.data.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * Gestionnaire des arènes Bedwars
 */
public class ArenaManager {
    
    private final GameOceanCore plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private Arena activeArena = null;
    private final Map<UUID, Arena> playerArena = new HashMap<>();
    private final Map<Arena, Integer> arenaCountdowns = new HashMap<>();
    private final File arenasFolder;
    private final BedwarsScoreboardManager scoreboardManager;
    
    public ArenaManager(GameOceanCore plugin) {
        this.plugin = plugin;
        this.arenasFolder = new File(plugin.getDataFolder(), "MG/BedwarsSolo/arenas");
        this.scoreboardManager = new BedwarsScoreboardManager(plugin);
        
        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
        }
        
        loadArenas();
        startUpdateTask();
    }
    
    /**
     * Démarre la tâche de mise à jour des scoreboards
     */
    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Arena arena : arenas.values()) {
                // Mettre à jour les scoreboards pour tous les joueurs de l'arène
                for (UUID uuid : arena.getPlayers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        scoreboardManager.updateScoreboard(player, arena);
                    }
                }
                
                // Gérer le compte à rebours
                if (arena.getState() == ArenaState.STARTING) {
                    int countdown = arenaCountdowns.getOrDefault(arena, 30);
                    countdown--;
                    arenaCountdowns.put(arena, countdown);
                    
                    // Annoncer les secondes restantes
                    if (countdown > 0 && countdown <= 10) {
                        broadcastToArena(arena, "§eLa partie commence dans §c" + countdown + " §eseconde(s)!");
                    }
                    
                    if (countdown <= 0) {
                        arenaCountdowns.remove(arena);
                        startGame(arena);
                    }
                }
            }
        }, 20L, 20L); // Toutes les secondes
    }
    
    public BedwarsScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public Map<Arena, Integer> getArenaCountdowns() {
        return arenaCountdowns;
    }
    
    /**
     * Met à jour le statut du serveur (MOTD et slots max) pour remonter les infos à SimpleCloud.
     */
    public void updateServerStatus(Arena arena) {
        if (arena == null) return;
        try {
            // Mise à jour du MOTD et de l'état SimpleCloud par réflexion
            if (arena.getState() == ArenaState.WAITING && !arena.isFull()) {
                Bukkit.getServer().setMotd("WAITING");
                setSimpleCloudState("WAITING");
            } else {
                Bukkit.getServer().setMotd("IN-GAME");
                setSimpleCloudState("INGAME");
            }
            
            // Appliquer le MaxPlayers réel sur le serveur Bukkit
            Bukkit.getServer().setMaxPlayers(arena.getMaxPlayers());
        } catch (Exception e) {
            // Certaines versions de Bukkit n'ont pas setMaxPlayers(), mais Paper 1.21 l'a
        }
    }

    /**
     * Essaie de définir l'état SimpleCloud via son API par réflexion pour éviter une dépendance en dur
     */
    private void setSimpleCloudState(String state) {
        try {
            // app.simplecloud.droplet.api.DropletAPI.getInstance().getService().setState(state)
            Class<?> dropletAPIClass = Class.forName("app.simplecloud.droplet.api.DropletAPI");
            Object apiInstance = dropletAPIClass.getMethod("getInstance").invoke(null);
            Object service = dropletAPIClass.getMethod("getService").invoke(apiInstance);
            if (service != null) {
                service.getClass().getMethod("setState", String.class).invoke(service, state);
            }
        } catch (Exception ignored) {
            // Ignoré si SimpleCloud n'est pas présent
        }
    }

    /**
     * Retourne l'arène active pour ce serveur
     */
    public Arena getActiveArena() {
        return activeArena;
    }

    /**
     * Charge les arènes et sélectionne aléatoirement celle qui sera jouée
     */
    public void loadArenas() {
        arenas.clear();
        activeArena = null;
        
        // Vérifier si le dossier existe
        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
            plugin.getLogger().info("Dossier des arènes créé: " + arenasFolder.getPath());
            return;
        }

        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("Aucune arène trouvée dans: " + arenasFolder.getPath());
            return;
        }

        List<Arena> loadedArenas = new ArrayList<>();
        for (File file : files) {
            try {
                Arena arena = Arena.load(file, plugin);
                if (arena != null) {
                    arenas.put(arena.getName().toLowerCase(), arena);
                    loadedArenas.add(arena);
                    plugin.getLogger().info("Arène Bedwars chargée: " + arena.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement de l'arène: " + file.getName());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Total des arènes chargées: " + arenas.size());
        
        if (!loadedArenas.isEmpty()) {
            // Sélectionner une arène au hasard pour cette instance de serveur
            int randomIndex = new Random().nextInt(loadedArenas.size());
            activeArena = loadedArenas.get(randomIndex);
            plugin.getLogger().info("=== ARÈNE ACTIVE SÉLECTIONNÉE: " + activeArena.getName() + " ===");
            
            // Appliquer la configuration au serveur Bukkit/SimpleCloud
            updateServerStatus(activeArena);
        }
    }
    
    /**
     * Crée une nouvelle arène
     */
    public Arena createArena(String name) {
        if (arenas.containsKey(name.toLowerCase())) {
            return null;
        }
        
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(), arena);
        return arena;
    }
    
    /**
     * Sauvegarde une arène
     */
    public boolean saveArena(String name) {
        Arena arena = getArena(name);
        if (arena == null) return false;
        
        File file = new File(arenasFolder, name + ".yml");
        arena.save(file);
        return true;
    }
    
    /**
     * Obtient une arène par son nom
     */
    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }
    
    /**
     * Obtient toutes les arènes
     */
    public Collection<Arena> getArenas() {
        return arenas.values();
    }
    
    /**
     * Trouve l'arène disponible pour rejoindre (l'arène active du serveur)
     */
    public Arena findAvailableArena() {
        if (activeArena != null && activeArena.getState() == ArenaState.WAITING && !activeArena.isFull()) {
            return activeArena;
        }
        return null;
    }
    
    /**
     * Ajoute un joueur à une arène
     */
    public boolean joinArena(Player player, String arenaName) {
        Arena arena = getArena(arenaName);
        if (arena == null) {
            player.sendMessage("§cL'arène '" + arenaName + "' n'existe pas.");
            return false;
        }
        
        return joinArena(player, arena);
    }
    
    /**
     * Ajoute un joueur à une arène
     */
    public boolean joinArena(Player player, Arena arena) {
        if (arena.isFull()) {
            player.sendMessage("§cCette arène est pleine.");
            return false;
        }
        
        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
            player.sendMessage("§cCette partie a déjà commencé.");
            return false;
        }
        
        // Vérifier que le monde du spawn est défini
        String worldName = arena.getWaitingSpawnWorld();
        if (worldName == null) {
            player.sendMessage("§cCette arène n'a pas de spawn d'attente défini!");
            player.sendMessage("§7L'admin doit faire: /bw setwaiting " + arena.getName());
            return false;
        }

        // Vérifier que le monde du spawn est chargé
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            // Essayer de charger le monde
            player.sendMessage("§7Chargement du monde de l'arène...");
            boolean loaded = plugin.getWorldManager().loadWorld(worldName);
            if (!loaded) {
                player.sendMessage("§cImpossible de charger le monde de l'arène: " + worldName);
                return false;
            }
            world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                player.sendMessage("§cLe monde n'a pas pu être chargé correctement.");
                return false;
            }
        }
        
        // Vérifier que le spawn d'attente est défini
        if (arena.getWaitingSpawn() == null) {
            player.sendMessage("§cCette arène n'a pas de spawn d'attente défini (coordonnées manquantes)!");
            return false;
        }
        
        // Quitter l'ancienne arène si nécessaire
        leaveArena(player);
        
        // Ajouter le joueur
        arena.addPlayer(player.getUniqueId());
        playerArena.put(player.getUniqueId(), arena);
        updateServerStatus(arena); // Met à jour l'état si full
        
        // Téléporter au spawn d'attente
        player.teleport(arena.getWaitingSpawn());
        
        // Initialiser les données du joueur
        BedwarsPlayerData data = new BedwarsPlayerData(player);
        arena.setPlayerData(player.getUniqueId(), data);
        
        // Messages
        player.sendMessage("§aVous avez rejoint l'arène §6" + arena.getDisplayName() + "§a!");
        broadcastToArena(arena, "§6" + player.getName() + " §aa rejoint la partie! §7(" + arena.getPlayerCount() + "/" + arena.getMaxPlayers() + ")");
        
        // Vérifier si assez de joueurs pour démarrer
        checkStart(arena);
        
        return true;
    }
    
    /**
     * Fait quitter une arène à un joueur
     */
    public void leaveArena(Player player) {
        Arena arena = playerArena.get(player.getUniqueId());
        if (arena == null) return;
        
        arena.removePlayer(player.getUniqueId());
        arena.getPlayerData().remove(player.getUniqueId());
        playerArena.remove(player.getUniqueId());
        
        broadcastToArena(arena, "§6" + player.getName() + " §ca quitté la partie.");
        
        // Vérifier si la partie doit continuer
        if (arena.getState() == ArenaState.IN_GAME) {
            checkEnd(arena);
        } else if (arena.getState() == ArenaState.STARTING && !arena.hasEnoughPlayers()) {
            // Annuler le démarrage si pas assez de joueurs
            arena.setState(ArenaState.WAITING);
            arenaCountdowns.remove(arena);
            broadcastToArena(arena, "§cDémarrage annulé : il n'y a plus assez de joueurs.");
        }
        updateServerStatus(arena);
    }
    
    /**
     * Obtient l'arène d'un joueur
     */
    public Arena getPlayerArena(Player player) {
        return playerArena.get(player.getUniqueId());
    }
    
    /**
     * Envoie un message à tous les joueurs d'une arène
     */
    public void broadcastToArena(Arena arena, String message) {
        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
    
    /**
     * Vérifie si la partie peut démarrer
     */
    private void checkStart(Arena arena) {
        if (arena.getState() != ArenaState.WAITING) return;
        
        if (arena.hasEnoughPlayers()) {
            startCountdown(arena);
        }
    }
    
    /**
     * Démarre le compte à rebours
     */
    private void startCountdown(Arena arena) {
        arena.setState(ArenaState.STARTING);
        updateServerStatus(arena);
        arenaCountdowns.put(arena, 30);
        broadcastToArena(arena, "§aLa partie commence dans §e30 §asecondes!");
        broadcastToArena(arena, "§7En attente de plus de joueurs...");
    }
    
    /**
     * Démarre la partie
     */
    public void startGame(Arena arena) {
        if (arena.getState() != ArenaState.STARTING) return;

        arena.setState(ArenaState.IN_GAME);
        updateServerStatus(arena);
        
        // Supprimer tous les items au sol avant le début
        if (arena.getWaitingSpawn() != null && arena.getWaitingSpawn().getWorld() != null) {
            for (org.bukkit.entity.Entity entity : arena.getWaitingSpawn().getWorld().getEntities()) {
                if (entity instanceof org.bukkit.entity.Item) {
                    entity.remove();
                }
            }
        }
        
        // Démarrer les générateurs
        plugin.getBedwarsManager().getGeneratorManager().startGenerators(arena);

        // Mettre à jour les nametags pour appliquer les couleurs des équipes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : arena.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && plugin.getNameTagManager() != null) {
                    plugin.getNameTagManager().setNameTag(p);
                }
            }
        }, 10L);

        // Assigner les équipes
        assignTeams(arena);
        
        if (arena.getTeams().isEmpty()) {
            broadcastToArena(arena, "§cErreur fatale: Aucune équipe n'est configurée pour cette arène! (/bw setteam)");
            restartArena(arena);
            return;
        }

        // Téléporter les joueurs à leur spawn d'équipe
        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            
            BedwarsPlayerData data = arena.getPlayerData(uuid);
            if (data == null || data.getTeamColor() == null) {
                player.sendMessage("§cErreur: Impossible de vous assigner une équipe.");
                continue;
            }
            
            BedwarsTeam team = arena.getTeam(data.getTeamColor());
            if (team != null) {
                if (team.getSpawn() != null) {
                    player.teleport(team.getSpawn());
                    player.getInventory().clear();
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WOODEN_SWORD));
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WOODEN_PICKAXE));
                    
                    player.sendMessage("§aLa partie commence! Défendez votre lit et détruisez ceux des autres!");
                } else {
                    player.sendMessage("§cErreur: Le spawn de votre équipe n'est pas défini! (/bw setspawn " + team.getColor() + ")");
                }
            }
        }
        
        // Spawn les NPCs
        spawnNPCs(arena);
        
        broadcastToArena(arena, "§6§lLa partie de Bedwars commence!");
    }
    
    /**
     * Assigne les équipes aux joueurs
     */
    private void assignTeams(Arena arena) {
        List<String> availableTeams = new ArrayList<>(arena.getTeams().keySet());
        if (availableTeams.isEmpty()) return;
        
        int teamIndex = 0;
        for (UUID uuid : arena.getPlayers()) {
            String teamColor = availableTeams.get(teamIndex % availableTeams.size());
            BedwarsPlayerData data = arena.getPlayerData(uuid);
            if (data != null) {
                data.setTeamColor(teamColor);
                arena.getTeam(teamColor).addPlayer(uuid);
            }
            teamIndex++;
        }
    }
    
    /**
     * Spawn les NPCs de l'arène
     */
    private void spawnNPCs(Arena arena) {
        for (NPCShop npcData : arena.getNpcs()) {
            Location loc = npcData.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            
            org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.VILLAGER);
            villager.setCustomName("§e§l" + npcData.getType().name());
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            
            // Keep track of the entity if needed, or clear it on restart
            // SimpleCloud will delete the server so it's fine.
        }
    }
    
    /**
     * Vérifie si la partie doit se terminer
     */
    private void checkEnd(Arena arena) {
        // TODO: Vérifier s'il reste des équipes en vie
    }
    
    /**
     * Termine la partie
     */
    public void endGame(Arena arena, BedwarsTeam winner) {
        if (arena.getState() == ArenaState.ENDING) return;

        arena.setState(ArenaState.ENDING);
        updateServerStatus(arena);
        
        if (winner != null) {
            broadcastToArena(arena, winner.getColorCode() + winner.getName() + " §6§lont gagné la partie!");
        } else {
            broadcastToArena(arena, "§6§lMatch nul!");
        }
        
        // TODO: Récompenses, retour au lobby, etc.
        
        // Redémarrer après 10 secondes
        Bukkit.getScheduler().runTaskLater(plugin, () -> restartArena(arena), 200L);
    }
    
    /**
     * Redémarre une arène
     */
    public void restartArena(Arena arena) {
        arena.setState(ArenaState.RESTARTING);
        
        // Rediriger tous les joueurs hors du jeu
        for (UUID uuid : new HashSet<>(arena.getPlayers())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage("§aLa partie est terminée. Retour au lobby...");
                leaveArena(player);
                
                if (plugin.getBungeeServerCheck() != null) {
                    plugin.getBungeeServerCheck().startProcess(player);
                } else {
                    // Fallback simple
                    try {
                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                        java.io.DataOutputStream out = new java.io.DataOutputStream(b);
                        out.writeUTF("Connect");
                        out.writeUTF("main-lb");
                        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
                    } catch (Exception e) {
                        player.kickPlayer("§cRetour au Hub.");
                    }
                }
            }
        }
        
        // Puisque SimpleCloud démarre un nouveau serveur par partie, on demande simplement la fermeture du serveur
        // pour que SimpleCloud le supprime. Le monde original est ainsi "préservé" car on a travaillé sur une instance jetable.
        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 60L);
    }
}  
