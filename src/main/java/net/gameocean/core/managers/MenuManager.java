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

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int level = 1;
            if (plugin.getProfileManager() != null) {
                net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
                if (profile != null) {
                    level = profile.getLevel();
                }
            }

            if (!player.isOnline()) return;

            final int finalLevel = level;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                String profileText = ChatColor.translateAlternateColorCodes('&', 
                    "&l&bVotre Profil&r\n\n" +
                    "&7Pseudo: &f" + player.getName() + "\n" +
                    "&7Grade: &f" + rank + "\n" +
                    "&7Niveau: &d" + finalLevel + "\n\n" +
                    "&eQue souhaitez-vous faire aujourd'hui ?"
                );

                SimpleForm form = SimpleForm.builder()
                        .title(ChatColor.translateAlternateColorCodes('&', "&fMenu Principal"))
                        .content(profileText)
                        .button("Trouver un mode de jeu", FormImage.Type.URL, "https://i.imgur.com/qxcIxOq.png")
                        .validResultHandler(response -> {
                            if (response.clickedButtonId() == 0) {
                                Bukkit.getScheduler().runTask(plugin, () -> openGameSelector(player));
                            }
                        })
                        .build();

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
            });
        });
    }

    public void openGameSelector(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§8» §eSélecteur de Mini-Jeux §8«")
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

            java.util.List<String> finalUuids = new java.util.ArrayList<>();
            java.util.List<String> finalNames = new java.util.ArrayList<>();

            // Récupérer les amis d'abord
            if (plugin.getFriendManager() != null) {
                try {
                    java.util.Map<java.util.UUID, String> friends = plugin.getFriendManager().getFriends(player.getUniqueId()).get();
                    for (java.util.Map.Entry<java.util.UUID, String> entry : friends.entrySet()) {
                        finalUuids.add(entry.getKey().toString());
                        finalNames.add(entry.getValue());
                    }
                } catch (Exception e) {}
            }
            int friendsCount = finalNames.size();

            String sql = "SELECT uuid, username FROM profiles WHERE apartment_is_public = TRUE OR apartment_offline_access = TRUE LIMIT 20";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                 while (rs.next()) {
                     String u = rs.getString("uuid");
                     if (!finalUuids.contains(u) && !u.equals(player.getUniqueId().toString())) {
                         finalUuids.add(u);
                         finalNames.add(rs.getString("username"));
                     }
                 }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la recupération des apparts sociaux : " + e.getMessage());
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                SimpleForm.Builder formBuilder = SimpleForm.builder()
                        .title("§8» §eAmis & Social §8«")
                        .content("§7Gérez vos amis et visitez des appartements ouverts:\n");
                
                if (finalNames.isEmpty()) {
                    formBuilder.content("§cAucun ami ou appartement public pour le moment.");
                    formBuilder.button("§cRetour", FormImage.Type.URL, "https://i.imgur.com/qxcIxOq.png");
                } else {
                    for (int i = 0; i < finalNames.size(); i++) {
                        if (i < friendsCount) {
                            formBuilder.button("§a[Ami] §b" + finalNames.get(i) + "\n§r§8Cliquer pour visiter");
                        } else {
                            formBuilder.button("Appartement de §b" + finalNames.get(i) + "\n§r§8Cliquer pour visiter");
                        }
                    }
                }

                SimpleForm form = formBuilder.validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    if (finalNames.isEmpty() || clicked >= finalNames.size()) return; // Clic sur retour
                    
                    String targetUuid = finalUuids.get(clicked);
                    String targetName = finalNames.get(clicked);

                    if (clicked < friendsCount) {
                        openFriendDetailMenu(player, targetUuid, targetName);
                    } else {
                        joinApartment(player, targetUuid, targetName);
                    }
                }).build();

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
            });
        });
    }

    private void joinApartment(Player player, String targetUuid, String targetName) {
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
                    } catch (Exception e) {}
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
    }

    public void openFriendDetailMenu(Player player, String targetUuid, String targetName) {
        if (!isBedrockPlayer(player)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Vérifier le statut en ligne via Redis
            String serverId = null;
            if (plugin.getRedisManager() != null && plugin.getRedisManager().isConnected()) {
                serverId = plugin.getRedisManager().getSync("gameocean:online:" + targetUuid);
            }

            boolean isOnline = (serverId != null);
            String displayServer = isOnline ? serverId : "Hors-ligne";

            // Récupérer UNIQUEMENT les données d'appartement via SQL léger
            boolean hasPublicApt = false;
            boolean hasOfflineApt = false;
            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                String sql = "SELECT apartment_is_public, apartment_offline_access FROM profiles WHERE uuid = ?";
                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, targetUuid);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            hasPublicApt = rs.getBoolean("apartment_is_public");
                            hasOfflineApt = rs.getBoolean("apartment_offline_access");
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur SQL openFriendDetailMenu: " + e.getMessage());
                }
            }

            final boolean finalHasAptAccess = (isOnline && hasPublicApt) || (!isOnline && hasOfflineApt) || hasPublicApt;
            final String statusText = (isOnline ? "§aEn ligne" : "§cHors ligne");
            final String aptText = (hasPublicApt ? "§aPublic" : (hasOfflineApt ? "§eVisible (Hors-Ligne)" : "§cPrivé"));

            // Vérifier que le joueur est toujours en ligne avant d'envoyer le form
            if (!player.isOnline()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                SimpleForm.Builder formBuilder = SimpleForm.builder()
                        .title("§8» §e" + targetName + " §8«")
                        .content("§7Statut: " + statusText + "\n" +
                                 "§7Serveur: §e" + displayServer + "\n" +
                                 "§7Appartement: " + aptText + "\n\n" +
                                 "§7Que souhaitez-vous faire ?");

                if (finalHasAptAccess) {
                    formBuilder.button("§aRejoindre son appartement");
                }

                formBuilder.button("§cRetirer cet ami");
                formBuilder.button("§8Retour");

                SimpleForm form = formBuilder.validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    int actionIndex = clicked;

                    if (!finalHasAptAccess) {
                        actionIndex += 1;
                    }

                    if (actionIndex == 0) {
                        joinApartment(player, targetUuid, targetName);
                    } else if (actionIndex == 1) {
                        player.performCommand("friend remove " + targetName);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> openSocialMenu(player), 10L);
                    } else {
                        openSocialMenu(player);
                    }
                }).build();

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
            });
        });
    }

    public void openSettingsMenu(Player player) {
        if (!isBedrockPlayer(player)) {
            player.sendMessage(ChatColor.RED + "Ce menu est optimisé pour les joueurs Bedrock pour l'instant.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean hasAnnounce = true;
            boolean hasPopup = true;
            if (plugin.getProfileManager() != null) {
                net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
                if (profile != null) {
                    hasAnnounce = profile.hasFriendAnnouncements();
                    hasPopup = profile.hasFriendPopupRequests();
                }
            }

            final boolean finalAnnounce = hasAnnounce;
            final boolean finalPopup = hasPopup;

            Bukkit.getScheduler().runTask(plugin, () -> {
                org.geysermc.cumulus.form.CustomForm form = org.geysermc.cumulus.form.CustomForm.builder()
                        .title("§8» §eParamètres §8«")
                        .toggle("Annonces de connexion des amis", finalAnnounce)
                        .toggle("Recevoir les invitations en popup", finalPopup)
                        .validResultHandler(response -> {
                            boolean newAnnounce = response.asToggle(0);
                            boolean newPopup = response.asToggle(1);

                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                if (newAnnounce != finalAnnounce) {
                                    plugin.getProfileManager().updateFriendAnnouncements(player.getUniqueId(), newAnnounce);
                                }
                                if (newPopup != finalPopup) {
                                    plugin.getProfileManager().updateFriendPopupRequests(player.getUniqueId(), newPopup);
                                }
                                player.sendMessage(ChatColor.GREEN + "Paramètres mis à jour avec succès !");
                            });
                        })
                        .build();

                FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
            });
        });
    }

    public void openFriendRequestPopup(Player player, String senderName) {
        if (!isBedrockPlayer(player)) return;

        org.geysermc.cumulus.form.ModalForm form = org.geysermc.cumulus.form.ModalForm.builder()
                .title("§8» §eNouvel Ami ! §8«")
                .content("§e" + senderName + " §avoudrait être votre ami.\n\nSouhaitez-vous accepter ?")
                .button1("§aAccepter")
                .button2("§cRefuser")
                .validResultHandler(response -> {
                    if (response.clickedFirst()) {
                        player.performCommand("friend accept " + senderName);
                    } else {
                        player.performCommand("friend deny " + senderName);
                    }
                })
                .build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
}