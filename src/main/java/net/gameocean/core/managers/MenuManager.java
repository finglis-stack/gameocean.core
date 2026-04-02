package net.gameocean.core.managers;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;

public class MenuManager {

    private final GameOceanCore plugin;

    public MenuManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        if (!isBedrockPlayer(player)) {
            player.sendMessage(ChatColor.RED + "Ce menu est optimisé pour les joueurs Bedrock pour l'instant.");
            return;
        }

        String rank = getPlayerRank(player);
        int level = 1;
        if (plugin.getProfileManager() != null) {
            net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
            if (profile != null) {
                level = profile.getLevel();
            }
        }
        String profileText = ChatColor.translateAlternateColorCodes('&', 
            "&l&bVotre Profil&r\n\n" +
            "&7Pseudo: &f" + player.getName() + "\n" +
            "&7Grade: &f" + rank + "\n" +
            "&7Niveau: &d" + level + "\n\n" +
            "&eQue souhaitez-vous faire aujourd'hui ?"
        );

        SimpleForm form = SimpleForm.builder()
                .title(ChatColor.translateAlternateColorCodes('&', "&fMenu Principal"))
                .content(profileText)
                .button("Trouver un mode de jeu", FormImage.Type.URL, "https://i.imgur.com/qxcIxOq.png")
                .validResultHandler(response -> {
                    if (response.clickedButtonId() == 0) {
                        // Ouvrir le selecteur de mini-jeux
                        Bukkit.getScheduler().runTask(plugin, () -> openGameSelector(player));
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    public void openGameSelector(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§l§8» §bSélecteur de Mini-Jeux §8«")
                .content("") // Laisser vide pour forcer l'affichage en grille (comme The Hive)
                .button("§l§cBedWars Solo\n§r§eJouer maintenant", FormImage.Type.URL, "https://i.imgur.com/wOLEWsb.png")
                .button("§l§eSkyWars\n§r§8Bientôt disponible", FormImage.Type.URL, "https://i.imgur.com/yU4l2sX.png")
                .button("§l§aMurder Mystery\n§r§8Bientôt disponible", FormImage.Type.URL, "https://i.imgur.com/yU4l2sX.png")
                .button("§l§6Duels\n§r§8Bientôt disponible", FormImage.Type.URL, "https://i.imgur.com/yU4l2sX.png")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (clicked == 0) {
                            player.sendMessage(ChatColor.GREEN + "Connexion au BedWars...");
                            // Implémentation future du transfert vers le serveur Bedwars
                            if (plugin.getBungeeServerCheck() != null) {
                                // Exemple d'utilisation si l'outil existait
                            }
                        } else if (clicked == 1) {
                            player.sendMessage(ChatColor.RED + "Le SkyWars est actuellement en développement.");
                        } else if (clicked == 2) {
                            player.sendMessage(ChatColor.RED + "Le Murder Mystery est actuellement en développement.");
                        }
                    });
                })
                .build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private boolean isBedrockPlayer(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }

    private String getPlayerRank(Player player) {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (rsp != null && rsp.getProvider() != null) {
                String group = rsp.getProvider().getPrimaryGroup(player);
                if (group != null && !group.isEmpty()) {
                    return group.substring(0, 1).toUpperCase() + group.substring(1).toLowerCase();
                }
            }
        }
        return "Joueur";
    }

    public void openSocialMenu(Player player) {
        if (!isBedrockPlayer(player)) {
            player.sendMessage(ChatColor.RED + "Ce menu est optimisé pour les joueurs Bedrock pour l'instant.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean hasDb = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected();
            if (!hasDb) {
                player.sendMessage(ChatColor.RED + "Base de données indisponible.");
                return;
            }

            java.util.List<String> validUuids = new java.util.ArrayList<>();
            java.util.List<String> validNames = new java.util.ArrayList<>();

            String sql = "SELECT uuid, username FROM profiles WHERE apartment_is_public = TRUE OR apartment_offline_access = TRUE LIMIT 20";
            try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                 while (rs.next()) {
                     validUuids.add(rs.getString("uuid"));
                     validNames.add(rs.getString("username"));
                 }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la recupération des apparts sociaux : " + e.getMessage());
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                SimpleForm.Builder formBuilder = SimpleForm.builder()
                        .title("§l§8» §bAmis & Social §8«")
                        .content("§7Appartements ouverts ou disponibles hors ligne:\n");
                
                if (validNames.isEmpty()) {
                    formBuilder.content("§cAucun appartement public ou ami disponible pour le moment.");
                    formBuilder.button("§cRetour", FormImage.Type.URL, "https://i.imgur.com/qxcIxOq.png");
                } else {
                    for (String name : validNames) {
                        formBuilder.button("Appartement de §b" + name + "\n§r§8Cliquer pour visiter");
                    }
                }

                SimpleForm form = formBuilder.validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    if (validNames.isEmpty()) return; // Clic sur retour
                    
                    String targetUuid = validUuids.get(clicked);
                    String targetName = validNames.get(clicked);
                    
                    player.sendMessage("§aRecherche de l'instance de " + targetName + "...");
                    
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        String bestServer = "app-1"; // Fallback server name
                        
                        if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
                            // 1. Chercher si l'instance est déjà allumée
                            String currentInstance = plugin.getRedisManager().getSync("apartment_instance:" + targetUuid);
                            
                            if (currentInstance != null && !currentInstance.isEmpty()) {
                                bestServer = currentInstance;
                            } else {
                                // 2. Trouver le serveur app-* avec le moins de joueurs
                                try (redis.clients.jedis.Jedis jedis = plugin.getRedisManager().getJedisPool().getResource()) {
                                    java.util.Map<String, String> servers = jedis.hgetAll("app_servers");
                                    int minPlayers = Integer.MAX_VALUE;
                                    for (java.util.Map.Entry<String, String> entry : servers.entrySet()) {
                                        try {
                                            int players = Integer.parseInt(entry.getValue());
                                            if (players < minPlayers) {
                                                minPlayers = players;
                                                bestServer = entry.getKey();
                                            }
                                        } catch (NumberFormatException ignored) {}
                                    }
                                } catch (Exception e) {
                                  // ignore
                                }
                            }
                            // Mémoriser la visite en définissant le nom de l'hôte
                            plugin.getRedisManager().set("apartment_visit:" + player.getUniqueId().toString(), targetName, 60);
                        }
                        
                        // Transfert BungeeCord sur le thread principal
                        final String finalServer = bestServer;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§aTéléportation vers " + finalServer + "...");
                            plugin.getBungeeMessaging().connectPlayerToServer(player, finalServer);
                        });
                    });
                }).build();

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
            });
        });
    }
}