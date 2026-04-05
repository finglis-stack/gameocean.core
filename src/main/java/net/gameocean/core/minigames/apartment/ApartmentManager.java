package net.gameocean.core.minigames.apartment;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

public class ApartmentManager {

    private final GameOceanCore plugin;
    private final File configFile;
    private FileConfiguration config;
    
    // Joueurs actuellement en train de placer un meuble: UUID -> ID du meuble
    private final Map<UUID, String> placingFurniture = new ConcurrentHashMap<>();

    // Classe utilitaire pour les infos du meuble
    public static class FurnitureInfo {
        public final int dbId;
        public final String furnitureId;
        public final UUID ownerUuid;
        public final String materialName;
        public final float yaw;

        public FurnitureInfo(int dbId, String furnitureId, UUID ownerUuid, String materialName, float yaw) {
            this.dbId = dbId;
            this.furnitureId = furnitureId;
            this.ownerUuid = ownerUuid;
            this.materialName = materialName;
            this.yaw = yaw;
        }
    }

    // Map Globale de localisation des meubles : "x,y,z:owner_uuid" -> FurnitureInfo
    private final Map<String, FurnitureInfo> furnitureBlocks = new ConcurrentHashMap<>();
    
    private String getBlockKey(Location loc, UUID ownerUuid) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ":" + ownerUuid.toString();
    }
    
    private void sendFakeBlockToApartment(UUID hostUuid, Location loc, org.bukkit.block.data.BlockData data) {
        org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
        String targetHost = hostUuid.toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String thisHost = p.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (thisHost != null && thisHost.equals(targetHost)) {
                p.sendBlockChange(loc, data);
            }
        }
    }
    
    // Suivi du joueur qui veut récupérer un meuble : Joueur_UUID -> (Location -> Timestamp dernière frappe)
    // Inner ConcurrentHashMap pour éviter les accès concurrents si deux events arrivent simultanément
    private final Map<UUID, Map<Location, Long>> pendingBlockRemoval = new ConcurrentHashMap<>();

    public ApartmentManager(GameOceanCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "apartment.yml");
        loadConfig();
        
        plugin.getLogger().info("ApartmentManager (Mutualisé) initialisé.");
        
        // Démarrer le heartbeat vers Redis pour la liste des serveurs app-*
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
                String serverId = plugin.getServerId();
                if (serverId != null && !serverId.equals("unknown-server") && plugin.getServerType().equalsIgnoreCase("MINIGAME") && plugin.getMinigameType().equalsIgnoreCase("APARTMENT")) {
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    try (redis.clients.jedis.Jedis jedis = plugin.getRedisManager().getJedisPool().getResource()) {
                        jedis.hset("app_servers", serverId, String.valueOf(playerCount));
                    } catch (Exception e) {
                        // ignore heartbeat err
                    }
                }
            }
        }, 20L, 20L); // Chaque seconde
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de créer apartment.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        
        if (!config.contains("apartment-world")) {
            config.set("apartment-world", "apartment_shared");
            saveConfig();
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder apartment.yml");
        }
    }

    public String getApartmentWorldName() {
        return config.getString("apartment-world", "apartment_shared");
    }

    public void setApartmentWorldName(String worldName) {
        config.set("apartment-world", worldName);
        saveConfig();
    }

    public void setTemplateSpawn(String templateId, Location location) {
        String path = "templates." + templateId + ".spawn";
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
        saveConfig();
    }

    public boolean hasTemplateSpawn(String templateId) {
        return config.contains("templates." + templateId + ".spawn");
    }

    public Location getTemplateSpawn(String templateId, World world) {
        String path = "templates." + templateId + ".spawn";
        if (config.contains(path)) {
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");
            float pitch = (float) config.getDouble(path + ".pitch");
            return new Location(world, x, y, z, yaw, pitch);
        }
        return null;
    }

    public void joinApartment(Player player) {
        // Obtenir l'appartement actuel du joueur (ou de l'hôte si en visite)
        String currentTemplate = "default";
        UUID ownerUuid = player.getUniqueId();
        boolean isVisitor = false;
        String hostName = null;

        if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
            String visitKey = "apartment_visit:" + player.getUniqueId().toString();
            hostName = plugin.getRedisManager().getSync(visitKey);
            
            if (hostName != null && !hostName.isEmpty()) {
                isVisitor = true;
                plugin.getRedisManager().delete(visitKey); // Consommer la clé
                Player hostPlayer = Bukkit.getPlayer(hostName);
                if (hostPlayer != null) {
                    ownerUuid = hostPlayer.getUniqueId();
                } else {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(hostName);
                    if (op != null && op.hasPlayedBefore()) {
                        ownerUuid = op.getUniqueId();
                        // Verification de l'acces hors l'igne
                        net.gameocean.core.database.PlayerProfile hostProfile = plugin.getProfileManager().getProfile(ownerUuid);
                        if (hostProfile == null || !hostProfile.hasOfflineAccess()) {
                            player.sendMessage("§cCet appartement n'est pas accessible quand le propriétaire est hors-ligne.");
                            return; // Annuler la téléportation
                        }
                    } else {
                        player.sendMessage("§cImpossible de localiser le propriétaire, retour à votre propre appartement.");
                        isVisitor = false;
                    }
                }
            }
        }

        // Verification du ban
        if (isVisitor && isPlayerBanned(ownerUuid, player.getUniqueId())) {
            player.sendMessage("§cVous êtes banni de cet appartement.");
            return;
        }

        // Vérifier si l'appartement est DEJA CHARGE sur un autre serveur app-* via Redis
        String serverId = plugin.getServerId();
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
            final String instanceKey = "apartment_instance:" + ownerUuid.toString();
            String currentInstance = plugin.getRedisManager().getSync(instanceKey);

            if (serverId == null || serverId.equals("unknown-server")) {
                // Si on ne connaît pas encore le nom de notre serveur, on doit attendre BungeeCord
                if (isVisitor) {
                     // Restaurer le ticket consommé plus haut
                     plugin.getRedisManager().set("apartment_visit:" + player.getUniqueId().toString(), hostName != null ? hostName : ownerUuid.toString(), 60);
                }
                if (plugin.getBungeeMessaging() != null) {
                    plugin.getBungeeMessaging().requestServerName(player, (name) -> {
                        plugin.setServerId(name);
                        // Une fois le nom obtenu, on relance la logique proprement
                        Bukkit.getScheduler().runTask(plugin, () -> joinApartment(player));
                    });
                }
                return; // Arrêter ici.
            }
            
            if (currentInstance != null && !currentInstance.isEmpty() && !currentInstance.equals(serverId) && !currentInstance.equals("unknown-server")) {
                // Il est chargé ailleurs sur un AUTRE serveur valide ! On l'envoie là-bas.
                player.sendMessage("§eL'appartement est déjà ouvert sur " + currentInstance + ". Transfert en cours...");
                if (isVisitor) {
                     // Recréer le ticket de visite pour le prochain serveur
                     plugin.getRedisManager().set("apartment_visit:" + player.getUniqueId().toString(), hostName != null ? hostName : ownerUuid.toString(), 60);
                }
                plugin.getBungeeMessaging().connectPlayerToServer(player, currentInstance);
                return; // Annuler le chargement local !
            } else {
                // Il n'est nulle part (ou il est déjà chez nous). On réactualise notre clé avec nous-même.
                plugin.getRedisManager().set(instanceKey, serverId, 7200);
            }
        }

        if (plugin.getProfileManager() != null) {
            net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(ownerUuid);
            if (profile != null && profile.getCurrentApartment() != null) {
                currentTemplate = profile.getCurrentApartment();
            }
        }
        
        final String templateId = currentTemplate;
        final UUID finalOwnerUuid = ownerUuid;
        final boolean finalIsVisitor = isVisitor;
        final String finalHostName = hostName;

        String worldName = getApartmentWorldName();
        World world = Bukkit.getWorld(worldName);

        // Charger ou créer le monde mutualisé unique
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new net.gameocean.core.utils.VoidWorldGenerator());
            world = Bukkit.createWorld(creator);
            
            if (world != null) {
                world.setSpawnLocation(0, 64, 0);
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                world.setTime(6000);
            }
        }

        if (world != null) {
            if (!hasTemplateSpawn(templateId)) {
                // Si le template n'a pas été configuré par l'admin, on ne fait rien
                if (player.isOp()) {
                    player.sendMessage("§e[Apartment] Le point d'apparition pour '" + templateId + "' n'est pas défini.");
                    player.sendMessage("§eUtilisez /app admin setspawn " + templateId + " pour le configurer.");
                }
                return;
            }

            // Téléporter au spawn spécifique du template
            Location spawn = getTemplateSpawn(templateId, world);
            if (spawn != null) {
                // Si on change localement d'appartement, on nettoie d'abord l'ancien affichage
                org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
                String oldHostString = player.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                if (oldHostString != null && !oldHostString.isEmpty()) {
                    try {
                        UUID oldHostUuid = UUID.fromString(oldHostString);
                        if (!oldHostUuid.equals(finalOwnerUuid)) {
                            // Effacer l'ancien décor local
                            for (java.util.Map.Entry<String, FurnitureInfo> entry : furnitureBlocks.entrySet()) {
                                if (entry.getValue().ownerUuid.equals(oldHostUuid)) {
                                    String[] parts = entry.getKey().split(":");
                                    if (parts.length > 0) {
                                        String[] coords = parts[0].split(",");
                                        if (coords.length == 3) {
                                            int x = Integer.parseInt(coords[0]);
                                            int y = Integer.parseInt(coords[1]);
                                            int z = Integer.parseInt(coords[2]);
                                            Location loc = new Location(world, x, y, z);
                                            player.sendBlockChange(loc, org.bukkit.Material.AIR.createBlockData());
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                
                // Utilisation de teleportAsync pour charger le chunk avant d'afficher, évitant le "moved too quickly" de Geyser
                player.teleportAsync(spawn).thenAccept(success -> {
                    if (!success || !player.isOnline()) return;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.setFallDistance(0); // Réinitialiser l'historique de chute pour l'anti-cheat
                        player.setGameMode(org.bukkit.GameMode.ADVENTURE); // Adventure pour éviter de casser sans faire exprès
                        player.getInventory().clear();
                        
                        if (!finalIsVisitor) {
                            // Donner l'item de gestion de l'appartement (Totem d'immortalité)
                            org.bukkit.inventory.ItemStack managementItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.TOTEM_OF_UNDYING);
                            org.bukkit.inventory.meta.ItemMeta managementMeta = managementItem.getItemMeta();
                            if (managementMeta != null) {
                                managementMeta.setDisplayName("§e§lGestion de l'Appartement §7(Clic droit)");
                                managementItem.setItemMeta(managementMeta);
                            }
                            player.getInventory().setItem(8, managementItem);
                            player.sendMessage("§aBienvenue dans votre appartement (" + templateId + ") !");
                        } else {
                            player.sendMessage("§aBienvenue dans l'appartement de " + finalHostName + " !");
                        }

                        // Charger les meubles depuis la base de données et envoyer les paquets (FakeBlocks)
                        // On attend 1 seconde pour que les joueurs soient bien sync
                        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                            loadDecorationsForPlayer(player, templateId, finalOwnerUuid);
                        }, 20L);

                        // Vérifier si le joueur a déjà vu l'introduction de l'appartement
                        if (plugin.getProfileManager() != null) {
                            net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
                            plugin.getLogger().info("Verification intro appartement pour " + player.getName() + " - hasSeen: " + (profile != null ? profile.hasSeenApartmentIntro() : "null"));
                            if (profile != null && !profile.hasSeenApartmentIntro()) {
                                showApartmentIntro(player);
                            }
                        }

                        // ✅ FIX race condition: on définit apartment_host EN PREMIER,
                        //    PUIS on met à jour la visibilité. L'ordre est désormais garanti.
                        NamespacedKey updateHostKey = new NamespacedKey(plugin, "apartment_host");
                        player.getPersistentDataContainer().set(updateHostKey, PersistentDataType.STRING, finalOwnerUuid.toString());

                        // Mise à jour de la visibilité mutuelle avec tous les joueurs en ligne.
                        // Les joueurs du même appartement se voient, les autres se cachent.
                        String myHostStr = finalOwnerUuid.toString();
                        for (Player other : Bukkit.getOnlinePlayers()) {
                            if (other.equals(player)) continue;
                            String otherHost = other.getPersistentDataContainer().get(updateHostKey, PersistentDataType.STRING);
                            if (myHostStr.equals(otherHost)) {
                                // Même appartement: on se voit mutuellement
                                player.showPlayer(plugin, other);
                                other.showPlayer(plugin, player);
                            } else {
                                // Appartements différents: on se cache mutuellement
                                player.hidePlayer(plugin, other);
                                other.hidePlayer(plugin, player);
                            }
                        }

                        // Mettre à jour le nametag si disponible
                        if (plugin.getNameTagManager() != null) {
                            plugin.getNameTagManager().setNameTag(player);
                        }
                    });
                });
            }

        } else {
            player.sendMessage("§cImpossible de charger le monde de l'appartement. Veuillez contacter un administrateur.");
        }
    }

    /**
     * Charge les décorations de l'appartement depuis la base de données
     * et les envoie au joueur sous forme de faux blocs / fausses entités (packets).
     */
    private void clearOldBlocks(UUID ownerUuid) {
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<String, FurnitureInfo> entry : furnitureBlocks.entrySet()) {
            if (entry.getValue().ownerUuid.equals(ownerUuid)) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            furnitureBlocks.remove(key);
        }
    }

    private void loadDecorationsForPlayer(Player player, String apartmentId, UUID ownerUuid) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;

        Bukkit.getScheduler().runTask(plugin, () -> clearOldBlocks(ownerUuid));

        String sql = "SELECT id, dec_type, material, x, y, z, yaw FROM apartment_decorations WHERE uuid = ? AND apartment_id = ?";
        // ✅ HikariCP: Connection + Statement + ResultSet tous dans try-with-resources
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, apartmentId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int dbId = rs.getInt("id");
                    String furnitureId = rs.getString("dec_type");
                    String materialName = rs.getString("material");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    float yaw = rs.getFloat("yaw");
                    
                    try {
                        final org.bukkit.Material mat = org.bukkit.Material.valueOf(materialName);
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            World world = player.getWorld();
                            if (!world.getName().equals(getApartmentWorldName())) return;

                            Location loc = new Location(world, x, y, z);
                            Block block = loc.getBlock();
                            
                            BlockData blockData = Bukkit.createBlockData(mat);
                            if (blockData instanceof org.bukkit.block.data.Directional dir) {
                                dir.setFacing(yawToFace(yaw));
                                blockData = dir;
                            }
                            
                            if (blockData instanceof Bed bed) {
                                bed.setPart(Bed.Part.FOOT);
                                player.sendBlockChange(loc, bed);
                                
                                Location headLoc = loc.clone().add(yawToFace(yaw).getDirection());
                                Bed headBed = (Bed) bed.clone();
                                headBed.setPart(Bed.Part.HEAD);
                                player.sendBlockChange(headLoc, headBed);
                                
                                furnitureBlocks.put(getBlockKey(headLoc, ownerUuid), new FurnitureInfo(dbId, furnitureId, ownerUuid, materialName, yaw));
                            } else {
                                player.sendBlockChange(loc, blockData);
                            }

                            furnitureBlocks.put(getBlockKey(loc, ownerUuid), new FurnitureInfo(dbId, furnitureId, ownerUuid, materialName, yaw));
                        });
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Materiau inconnu dans la BDD : " + materialName);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement des decorations", e);
        }
    }

    private BlockFace yawToFace(float yaw) {
        yaw = Math.round(yaw / 90f) * 90f;
        while (yaw <= -180) yaw += 360;
        while (yaw > 180) yaw -= 360;
        
        if (yaw == 0) return BlockFace.SOUTH;
        if (yaw == 90) return BlockFace.WEST;
        if (yaw == 180 || yaw == -180) return BlockFace.NORTH;
        if (yaw == -90) return BlockFace.EAST;
        return BlockFace.NORTH;
    }

    /**
     * Ouvre le menu de gestion de l'appartement pour le joueur.
     */
    public void openManagementMenu(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null && FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            showFloodgateManagementMenu(player);
        } else {
            // Version Java si besoin plus tard (ex: Chest GUI)
            player.sendMessage(MessageUtils.format("&cLe menu de gestion est actuellement optimise pour les joueurs Bedrock."));
            player.sendMessage(MessageUtils.format("&eBientôt disponible pour Java."));
        }
    }

    private void showFloodgateManagementMenu(Player player) {
        net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        boolean isPublic = profile != null && profile.isApartmentPublic();
        boolean offlineAccess = profile != null && profile.hasOfflineAccess();

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§eGestion de l'Appartement")
                .content("Gérez les paramètres de votre appartement et sa décoration.\nStatut actuel: " + (isPublic ? "§aPublic" : "§cPrivé") + "\nAccès Hors Ligne: " + (offlineAccess ? "§aAutorisé" : "§cRefusé"))
                .button("Visibilité: " + (isPublic ? "Public" : "Privé"))
                .button("Accès Hors-Ligne: " + (offlineAccess ? "Oui" : "Non"))
                .button("Décorer mon appartement", FormImage.Type.URL, "https://i.imgur.com/XBfOG2W.png")
                .button("Gérer les joueurs", FormImage.Type.URL, "https://i.imgur.com/GdbXf0S.png")
                .button("Fermer");

        formBuilder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clickedId = response.clickedButtonId();
                if (clickedId == 0) {
                    // Toggle visibilité
                    boolean newStatus = !isPublic;
                    plugin.getProfileManager().setApartmentIsPublic(player.getUniqueId(), newStatus);
                    player.sendMessage("§eVotre appartement est maintenant " + (newStatus ? "§aPublic" : "§cPrivé") + "§e.");
                    openManagementMenu(player); // Rouvrir pour voir le changement
                } else if (clickedId == 1) {
                    // Toggle acces hors-ligne
                    boolean newOffline = !offlineAccess;
                    plugin.getProfileManager().setApartmentOfflineAccess(player.getUniqueId(), newOffline);
                    player.sendMessage("§eL'accès hors-ligne est maintenant " + (newOffline ? "§aAutorisé" : "§cRefusé") + "§e.");
                    openManagementMenu(player);
                } else if (clickedId == 2) {
                    // Ouvrir le menu de décoration
                    showDecorationMenu(player);
                } else if (clickedId == 3) {
                    showFloodgatePlayerListMenu(player);
                }
            });
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), formBuilder.build());
    }

    private void showFloodgatePlayerListMenu(Player player) {
        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§cGestion des Joueurs")
                .content("Cliquez sur un joueur pour le gérer.");

        // On récupère les joueurs présents dans l'appartement
        org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
        String myHostUuid = player.getUniqueId().toString();
        if (player.getPersistentDataContainer().has(hostKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            myHostUuid = player.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
        }
        
        // Si le joueur gère le menu mais n'est pas le proprio, on peut bloquer ou laisser (normalement seul le proprio a l'item)
        if (!myHostUuid.equals(player.getUniqueId().toString())) {
            player.sendMessage("§cVous ne pouvez gérer que votre propre appartement.");
            return;
        }

        java.util.List<Player> playersInApartment = new java.util.ArrayList<>();
        for (Player p : player.getWorld().getPlayers()) {
            if (p.getPersistentDataContainer().has(hostKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                String host = p.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                if (myHostUuid.equals(host)) {
                    playersInApartment.add(p);
                }
            }
        }

        formBuilder.button("§4» §cVoir les joueurs bannis", FormImage.Type.URL, "https://i.imgur.com/GdbXf0S.png");

        for (Player p : playersInApartment) {
            String role = p.getUniqueId().toString().equals(myHostUuid) ? "§e[Moi]§r " : "§7[Invité]§r ";
            String imageUrl = "https://mcprofile.io/api/v1/bedrock/fuid/" + p.getUniqueId().toString();
            formBuilder.button(role + p.getName(), FormImage.Type.URL, imageUrl);
        }

        formBuilder.button("§8Retour");

        formBuilder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clickedId = response.clickedButtonId();
                if (clickedId == 0) {
                    showFloodgateBannedPlayerMenu(player);
                } else if (clickedId >= 1 && clickedId <= playersInApartment.size()) {
                    Player target = playersInApartment.get(clickedId - 1);
                    if (target.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage("§cVous ne pouvez pas vous expulser vous-même !");
                        showFloodgatePlayerListMenu(player);
                        return;
                    }
                    showFloodgatePlayerActionMenu(player, target);
                } else {
                    openManagementMenu(player);
                }
            });
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), formBuilder.build());
    }

    private void showFloodgatePlayerActionMenu(Player owner, Player target) {
        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§cActions sur " + target.getName())
                .content("Choisissez une action pour ce joueur :")
                .button("Expulser", FormImage.Type.URL, "https://i.imgur.com/vHqZb0p.png")
                .button("Bannir de l'appartement", FormImage.Type.URL, "https://i.imgur.com/vHqZb0p.png")
                .button("§8Retour");

        formBuilder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clickedId = response.clickedButtonId();
                if (clickedId == 0) {
                    // Kick : expulser le joueur vers le lobby
                    target.sendMessage("§cVous avez été expulsé de l'appartement.");
                    owner.sendMessage("§aVous avez expulsé " + target.getName() + ".");
                    sendPlayerToLobby(target);
                    showFloodgatePlayerListMenu(owner);
                } else if (clickedId == 1) {
                    // Ban : bannir ET expulser le joueur vers le lobby
                    banPlayerFromApartment(owner.getUniqueId(), target.getUniqueId(), target.getName());
                    target.sendMessage("§4Vous avez été banni de cet appartement !");
                    owner.sendMessage("§aVous avez banni " + target.getName() + " de votre appartement.");
                    sendPlayerToLobby(target);
                    showFloodgatePlayerListMenu(owner);
                } else {
                    showFloodgatePlayerListMenu(owner);
                }
            });
        });

        FloodgateApi.getInstance().sendForm(owner.getUniqueId(), formBuilder.build());
    }

    /**
     * Expulse un joueur de l'appartement et le renvoie vers un serveur lobby.
     * Nettoie aussi sa clé apartment_host pour qu'il ne puisse pas revenir.
     */
    private void sendPlayerToLobby(Player player) {
        // 1. Nettoyer la clé apartment_host pour qu'il ne puisse pas rejoindre l'appartement automatiquement
        org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
        player.getPersistentDataContainer().remove(hostKey);

        // 2. Supprimer la clé de visite Redis s'il y en a une
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
            plugin.getRedisManager().delete("apartment_visit:" + player.getUniqueId().toString());
        }

        // 3. Mettre à jour la visibilité : cacher ce joueur à tout le monde dans l'appartement
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                String otherHost = other.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                if (otherHost != null) {
                    other.hidePlayer(plugin, player);
                    player.showPlayer(plugin, other);
                }
            }
        }

        // 4. Envoyer vers le lobby via BungeeCord ou via Redis pub/sub
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
            // Publier un message Redis pour que le lobby permette la connexion normale
            plugin.getRedisManager().publish("gameocean:send_to_lobby", player.getName());
        }

        // 5. Connecter au premier serveur lobby disponible
        String lobbyServer = plugin.getConfig().getString("lobby-server", "lobby");
        plugin.getBungeeMessaging().connectPlayerToServer(player, lobbyServer);
    }

    private void showFloodgateBannedPlayerMenu(Player player) {
        Map<UUID, String> bannedPlayers = getBannedPlayers(player.getUniqueId());
        
        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§4Joueurs Bannis")
                .content(bannedPlayers.isEmpty() ? "Aucun joueur n'est banni de votre appartement." : "Cliquez sur un joueur pour le débannir.");

        java.util.List<UUID> bannedUuids = new java.util.ArrayList<>(bannedPlayers.keySet());
        
        for (UUID uuid : bannedUuids) {
            formBuilder.button("§c" + bannedPlayers.get(uuid) + "\n§8Cliquez pour débannir");
        }
        formBuilder.button("§8Retour");

        formBuilder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clickedId = response.clickedButtonId();
                if (clickedId < bannedUuids.size()) {
                    UUID targetUuid = bannedUuids.get(clickedId);
                    unbanPlayerFromApartment(player.getUniqueId(), targetUuid);
                    player.sendMessage("§aVous avez débanni " + bannedPlayers.get(targetUuid) + ".");
                    showFloodgateBannedPlayerMenu(player); // Rouvrir pour actualiser
                } else {
                    showFloodgatePlayerListMenu(player);
                }
            });
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), formBuilder.build());
    }

    // --- Ban System SQL Logic ---
    public boolean isPlayerBanned(UUID owner, UUID target) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return false;
        String sql = "SELECT 1 FROM apartment_bans WHERE apartment_owner = ? AND banned_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, target.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur verification ban", e);
        }
        return false;
    }

    public void banPlayerFromApartment(UUID owner, UUID target, String targetName) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;
        String sql = "INSERT INTO apartment_bans (apartment_owner, banned_uuid, banned_name) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE banned_name = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, target.toString());
            stmt.setString(3, targetName);
            stmt.setString(4, targetName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur bannissement", e);
        }
    }

    public void unbanPlayerFromApartment(UUID owner, UUID target) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;
        String sql = "DELETE FROM apartment_bans WHERE apartment_owner = ? AND banned_uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, target.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur debannissement", e);
        }
    }

    public Map<UUID, String> getBannedPlayers(UUID owner) {
        Map<UUID, String> banned = new HashMap<>();
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return banned;
        String sql = "SELECT banned_uuid, banned_name FROM apartment_bans WHERE apartment_owner = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    banned.put(UUID.fromString(rs.getString("banned_uuid")), rs.getString("banned_name"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur listing bans", e);
        }
        return banned;
    }

    private void showDecorationMenu(Player player) {
        // Récupérer l'inventaire des meubles depuis la BDD
        Map<String, Integer> inventory = getFurnitureInventory(player.getUniqueId());

        if (inventory.isEmpty()) {
            SimpleForm emptyForm = SimpleForm.builder()
                    .title("§eDécoration")
                    .content("Vous n'avez aucun meuble dans votre inventaire.\n\nDemandez à un administrateur ou débloquez-en !")
                    .button("Retour")
                    .validResultHandler(r -> Bukkit.getScheduler().runTask(plugin, () -> openManagementMenu(player)))
                    .build();
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), emptyForm);
            return;
        }

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§eMes Meubles")
                .content("Choisissez un meuble à placer dans votre appartement.");

        // On associe un ID de bouton à un item_id pour le handler
        final java.util.List<String> orderedItems = new java.util.ArrayList<>();

        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            String itemId = entry.getKey();
            int amount = entry.getValue();
            orderedItems.add(itemId);
            
            // On pourrait avoir un mapping des noms et images (ex: light_blue_bed -> Lit bleu pâle)
            String displayName = formatFurnitureName(itemId);
            String imageUrl = getFurnitureImage(itemId);
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                formBuilder.button(displayName + " (x" + amount + ")", FormImage.Type.URL, imageUrl);
            } else {
                formBuilder.button(displayName + " (x" + amount + ")");
            }
        }
        
        formBuilder.button("Retour");

        formBuilder.validResultHandler(response -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int clickedId = response.clickedButtonId();
                if (clickedId < orderedItems.size()) {
                    String selectedFurniture = orderedItems.get(clickedId);
                    // Commencer la logique de placement de ce meuble
                    startFurniturePlacement(player, selectedFurniture);
                } else {
                    openManagementMenu(player);
                }
            });
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), formBuilder.build());
    }

    private String formatFurnitureName(String itemId) {
        if ("light_blue_bed".equals(itemId)) return "Lit bleu pâle";
        return itemId.replace("_", " ");
    }

    private String getFurnitureImage(String itemId) {
        if ("light_blue_bed".equals(itemId)) return "https://i.imgur.com/HUTIZGt.png";
        return "";
    }

    private void startFurniturePlacement(Player player, String furnitureId) {
        player.sendMessage("§aVous avez sélectionné le meuble : §e" + formatFurnitureName(furnitureId));
        player.sendMessage("§7Faites un clic droit sur un bloc pour le poser.");
        
        // On donne un bloc représentatif dans la main du joueur
        org.bukkit.Material mat = getMaterialForFurniture(furnitureId);
        org.bukkit.inventory.ItemStack placeItem = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = placeItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§ePoser: " + formatFurnitureName(furnitureId));
            placeItem.setItemMeta(meta);
        }
        
        player.getInventory().setItem(4, placeItem);
        player.getInventory().setHeldItemSlot(4);
        
        placingFurniture.put(player.getUniqueId(), furnitureId);
    }
    
    public String getPlacingFurniture(UUID uuid) {
        return placingFurniture.get(uuid);
    }
    
    public void cancelPlacement(Player player) {
        placingFurniture.remove(player.getUniqueId());
        player.getInventory().setItem(4, new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        player.sendMessage("§cPlacement annulé.");
    }

    public void confirmPlacement(Player player, Block clickedBlock, org.bukkit.block.BlockFace face) {
        String furnitureId = placingFurniture.remove(player.getUniqueId());
        if (furnitureId == null) return;
        
        // Retirer l'item de placement
        player.getInventory().setItem(4, new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        
        // Calculer la position cible (bloc contre lequel il a cliqué + la face)
        Block targetBlock = clickedBlock.getRelative(face);
        
        // Determiner le materiel final
        org.bukkit.Material mat = getMaterialForFurniture(furnitureId);
        
        BlockFace playerFacing = yawToFace(player.getLocation().getYaw());
        
        // Pas besoin d'air strict maintenant que ce sont des entités, mais on vérifie au moins que le bloc cible d'origine n'est pas "dans" un mur
        if (mat.name().endsWith("_BED")) {
            Block headBlock = targetBlock.getRelative(playerFacing);
            if (!targetBlock.getType().isAir() || !headBlock.getType().isAir()) {
                player.sendMessage("§cPas assez de place pour poser ce meuble ici.");
                return;
            }
        } else {
            if (!targetBlock.getType().isAir()) {
                player.sendMessage("§cVous ne pouvez pas poser de meuble ici.");
                return;
            }
        }
        
        // Récupérer l'appartement actuel du joueur (fait sur le main thread avant l'async)
        String apartmentId = "default";
        if (plugin.getProfileManager() != null) {
            net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
            if (profile != null && profile.getCurrentApartment() != null) {
                apartmentId = profile.getCurrentApartment();
            }
        }
        float yaw = player.getLocation().getYaw();
        yaw = Math.round(yaw / 45f) * 45f;
        
        // Capturer pour le lambda (doit être effectively final)
        final String finalApartmentId = apartmentId;
        final float finalYaw = yaw;
        
        // ✅ FIX: saveFurnitureToDB et consumeFurniture sont bloquants (SQL) → async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int insertedId = saveFurnitureToDB(player.getUniqueId(), finalApartmentId, furnitureId, mat.name(), targetBlock.getLocation(), finalYaw);
            
            if (insertedId != -1) {
                // Retirer de l'inventaire 1 item
                consumeFurniture(player.getUniqueId(), furnitureId);
                
                // Retour sur le main thread pour les interactions Bukkit
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage("§aMeuble posé avec succès !");
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_WOOD_PLACE, 1f, 1f);

                    // Recharger les meubles pour l'hôte et ses visiteurs
                    org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
                    String hostStr = player.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                    if (hostStr != null) {
                        UUID hostUuid = java.util.UUID.fromString(hostStr);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            String thisHost = p.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                            if (thisHost != null && thisHost.equals(hostStr)) {
                                loadDecorationsForPlayer(p, finalApartmentId, hostUuid);
                            }
                        }
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage("§cErreur lors de la sauvegarde du meuble.");
                });
            }
        });
    } // fin confirmPlacement()
    
    private int saveFurnitureToDB(UUID uuid, String apartmentId, String furnitureId, String materialName, Location loc, float yaw) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return -1;
        
        String sql = "INSERT INTO apartment_decorations (uuid, apartment_id, dec_type, material, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, apartmentId);
            stmt.setString(3, furnitureId);
            stmt.setString(4, materialName);
            stmt.setDouble(5, loc.getBlockX());
            stmt.setDouble(6, loc.getBlockY());
            stmt.setDouble(7, loc.getBlockZ());
            stmt.setFloat(8, yaw);
            stmt.executeUpdate();
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde du meuble en BDD", e);
            return -1;
        }
    }

    /**
     * Traite les interactions avec les meubles (qui sont des FakeBlocks).
     * @return true si une interacion avec un fake block a été gérée, false sinon.
     */
    public boolean handleFurnitureInteraction(Player player, boolean isLeftClick) {
        org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
        String hostStr = player.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (hostStr == null) return false;
        UUID hostUuid = java.util.UUID.fromString(hostStr);

        // Raytrace manuel au travers de l'air pour trouver un FakeBlock
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize().multiply(0.2); // Pas de 0.2 blocs
        Location current = eye.clone();

        FurnitureInfo hitFurniture = null;
        Location hitLocation = null;

        for (int i = 0; i < 25; i++) { // Max 5 blocs de distance (25 * 0.2)
            current.add(dir);
            String blockKey = getBlockKey(current.getBlock().getLocation(), hostUuid);
            if (furnitureBlocks.containsKey(blockKey)) {
                hitFurniture = furnitureBlocks.get(blockKey);
                hitLocation = current.getBlock().getLocation();
                break;
            }
        }

        if (hitFurniture != null && hitLocation != null) {
            // Forcer le rafraîchissement visuel du bloc car le client Bedrock (ou Java) l'a sûrement fait disparaître (prédiction de minage)
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(hitFurniture.materialName);
            if (mat != null) {
                org.bukkit.block.data.BlockData data = mat.createBlockData();
                if (data instanceof org.bukkit.block.data.Directional) {
                    ((org.bukkit.block.data.Directional) data).setFacing(yawToFace(hitFurniture.yaw));
                }
                player.sendBlockChange(hitLocation, data);
            }

            // Si c'est un clic gauche, on gère la récupération
            if (isLeftClick) {
                if (!hitFurniture.ownerUuid.equals(player.getUniqueId())) {
                    return true; // Pas à lui, mais on a réparé le visuel
                }

                Map<Location, Long> playerPending = pendingBlockRemoval.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
                Long lastHit = playerPending.get(hitLocation);
                
                long now = System.currentTimeMillis();
                if (lastHit == null || (now - lastHit) > 3000) { // Si > 3 sec ou premier coup
                    playerPending.put(hitLocation, now);
                    player.sendActionBar("§eTapez encore une fois pour récupérer: §f" + formatFurnitureName(hitFurniture.furnitureId));
                } else {
                    // Confirmé ! On supprime le meuble
                    playerPending.remove(hitLocation);
                    removeFurnitureBlock(player, hitFurniture, hitLocation.getBlock());
                }
            }
            return true;
        }
        return false;
    }

    private void removeFurnitureBlock(Player player, FurnitureInfo info, Block block) {
        // ✅ FIX: DELETE SQL en asynchrone
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                // ✅ FIX: Récupérer le vrai apartmentId du joueur au lieu de hardcoder "default"
                String apartmentId = "default";
                if (plugin.getProfileManager() != null) {
                    net.gameocean.core.database.PlayerProfile p = plugin.getProfileManager().getProfile(info.ownerUuid);
                    if (p != null && p.getCurrentApartment() != null) {
                        apartmentId = p.getCurrentApartment();
                    }
                }
                final String finalApartmentId = apartmentId;
                
                String sql = "DELETE FROM apartment_decorations WHERE id = ?";
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, info.dbId);
                    int result = stmt.executeUpdate();
                    if (result > 0) {
                        // Remettre dans l'inventaire (+1)
                        giveFurniture(player.getUniqueId(), info.furnitureId, 1);
                        
                        // Retirer via Faux Blocs (AIR) — main thread requis pour les packets
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            org.bukkit.block.data.BlockData airData = org.bukkit.Material.AIR.createBlockData();
                            
                            // On efface visuellement les blocs
                            sendFakeBlockToApartment(info.ownerUuid, block.getLocation(), airData);
                            furnitureBlocks.remove(getBlockKey(block.getLocation(), info.ownerUuid));
                            
                            // Recharger tout pour effacer complètement (lits, etc)
                            org.bukkit.NamespacedKey hostKey = new org.bukkit.NamespacedKey(plugin, "apartment_host");
                            String targetHost = info.ownerUuid.toString();
                            for (Player p2 : Bukkit.getOnlinePlayers()) {
                                String thisHost = p2.getPersistentDataContainer().get(hostKey, org.bukkit.persistence.PersistentDataType.STRING);
                                if (thisHost != null && thisHost.equals(targetHost)) {
                                    loadDecorationsForPlayer(p2, finalApartmentId, info.ownerUuid);
                                }
                            }

                            if (player.isOnline()) {
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                                player.sendActionBar("§aMeuble récupéré !");
                            }
                        });
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors de la suppression de la decoration", e);
                }
            }
        });
    }
    
    private void consumeFurniture(UUID uuid, String itemId) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;
        String sql = "UPDATE apartment_inventory SET amount = amount - 1 WHERE uuid = ? AND item_id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, itemId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la consommation du meuble", e);
        }
    }

    private org.bukkit.Material getMaterialForFurniture(String furnitureId) {
        if ("light_blue_bed".equals(furnitureId)) return org.bukkit.Material.LIGHT_BLUE_BED;
        
        try {
            return org.bukkit.Material.valueOf(furnitureId.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[DEBUG] Impossible de trouver le bloc pour le meuble : '" + furnitureId + "'");
            // Par defaut un bloc de bois si inconnu
            return org.bukkit.Material.OAK_PLANKS;
        }
    }

    public Map<String, Integer> getFurnitureInventory(UUID uuid) {
        Map<String, Integer> inv = new HashMap<>();
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return inv;
        String sql = "SELECT item_id, amount FROM apartment_inventory WHERE uuid = ? AND amount > 0";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    inv.put(rs.getString("item_id"), rs.getInt("amount"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la recup de l'inventaire des meubles", e);
        }
        return inv;
    }

    public void giveFurniture(UUID uuid, String itemId, int amount) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;
        String sql = "INSERT INTO apartment_inventory (uuid, item_id, amount) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE amount = amount + ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, itemId);
            stmt.setInt(3, amount);
            stmt.setInt(4, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du don d'un meuble", e);
        }
    }

    /**
     * Affiche le formulaire d'introduction de l'appartement via Floodgate
     */
    private void showApartmentIntro(Player player) {
        // Attendre 2 secondes que le joueur soit bien apparu (comme pour le ToS)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Vérifier si Floodgate est disponible
            if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
                plugin.getLogger().info("Floodgate non trouve, envoi de l'intro Java a " + player.getName());
                // Version chat pour les joueurs Java (si pas de Floodgate)
                sendJavaIntro(player);
                plugin.getProfileManager().setApartmentIntroSeen(player.getUniqueId());
                return;
            }

            try {
                boolean isFloodgate = FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
                plugin.getLogger().info("Floodgate API dispo, joueur " + player.getName() + " isFloodgate: " + isFloodgate);
                
                if (!isFloodgate) {
                    // Version chat pour les joueurs Java
                    sendJavaIntro(player);
                    plugin.getProfileManager().setApartmentIntroSeen(player.getUniqueId());
                    return;
                }

                String title = ChatColor.translateAlternateColorCodes('&', "&6&lVotre Appartement");
                String content = ChatColor.translateAlternateColorCodes('&', 
                    "&aBienvenue dans votre propre appartement sur &r" + MessageUtils.SERVER_NAME_NO_RESET + "&r&a!\n\n" +
                    "&7Ici, vous pouvez :\n" +
                    "&8- &eDécorer l'espace selon vos goûts.\n" +
                    "&8- &eLancer des missions exclusives.\n" +
                    "&8- &eInviter jusqu'à 6 amis pour vous amuser.\n\n" +
                    "&aVous pouvez toujours configurer votre appartement depuis le &eMenu Principal&a (le dernier item de votre barre d'inventaire)."
                );
                
                String buttonOk = "J'ai compris !";

                SimpleForm form = SimpleForm.builder()
                        .title(title)
                        .content(content)
                        .button(buttonOk, FormImage.Type.URL, "https://i.imgur.com/er32GdR.png") // Bouton OK
                        .validResultHandler(response -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getProfileManager().setApartmentIntroSeen(player.getUniqueId());
                                player.sendMessage(MessageUtils.format("&aBon jeu dans votre appartement !"));
                            });
                        })
                        .closedOrInvalidResultHandler(() -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getProfileManager().setApartmentIntroSeen(player.getUniqueId());
                            });
                        })
                        .build();

                boolean sent = FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
                plugin.getLogger().info("Formulaire Floodgate Appartement envoye: " + sent + " a " + player.getName());

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'envoi du formulaire Bedrock pour l'appartement: " + e.getMessage(), e);
                sendJavaIntro(player);
                plugin.getProfileManager().setApartmentIntroSeen(player.getUniqueId());
            }
        }, 40L); // Attendre 2 secondes (40 ticks)
    }

    private void sendJavaIntro(Player player) {
        player.sendMessage(" ");
        player.sendMessage(MessageUtils.format("&6&lVotre Appartement"));
        player.sendMessage(MessageUtils.format("&aBienvenue dans votre propre appartement sur &r" + MessageUtils.SERVER_NAME_NO_RESET + "&r&a!"));
        player.sendMessage(MessageUtils.format("&7Ici, vous pouvez :"));
        player.sendMessage(MessageUtils.format("&8- &eDécorer l'espace selon vos goûts."));
        player.sendMessage(MessageUtils.format("&8- &eLancer des missions exclusives."));
        player.sendMessage(MessageUtils.format("&8- &eInviter jusqu'à 6 amis pour vous amuser."));
        player.sendMessage(MessageUtils.format("&aVous pouvez toujours configurer votre appartement depuis le &eMenu Principal&a."));
        player.sendMessage(" ");
    }
}
