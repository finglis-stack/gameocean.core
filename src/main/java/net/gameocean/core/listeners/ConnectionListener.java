package net.gameocean.core.listeners;

import net.gameocean.core.GameOceanCore;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

public class ConnectionListener implements Listener {

    private final GameOceanCore plugin;

    public ConnectionListener(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    private Permission getPerms() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return null;
        org.bukkit.plugin.RegisteredServiceProvider<Permission> rsp = plugin.getServer().getServicesManager().getRegistration(Permission.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    private Chat getChat() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return null;
        org.bukkit.plugin.RegisteredServiceProvider<Chat> rsp = plugin.getServer().getServicesManager().getRegistration(Chat.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().clear();
        
        ItemStack menu = new ItemStack(Material.COMPASS);
        ItemMeta menuMeta = menu.getItemMeta();
        menuMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bMenu principal &7(Utiliser)"));
        menu.setItemMeta(menuMeta);
        player.getInventory().setItem(0, menu);
        
        ItemStack social = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta socialMeta = social.getItemMeta();
        socialMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bAmis & social &7(Utiliser)"));
        social.setItemMeta(socialMeta);
        player.getInventory().setItem(1, social);
        
        ItemStack params = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta paramsMeta = params.getItemMeta();
        paramsMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bParamètres &7(Utiliser)"));
        params.setItemMeta(paramsMeta);
        player.getInventory().setItem(7, params);
        
        ItemStack hub = new ItemStack(Material.CLOCK);
        ItemMeta hubMeta = hub.getItemMeta();
        hubMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bSélecteur de hub &7(Utiliser)"));
        hub.setItemMeta(hubMeta);
        player.getInventory().setItem(8, hub);
        
        player.updateInventory();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Toujours retirer le message de connexion par defaut
        event.setJoinMessage(null);

        Player player = event.getPlayer();
        String serverType = plugin.getServerType();
        
        // En mode MINIGAME, les listeners dédiés gèrent la logique
        if ("MINIGAME".equalsIgnoreCase(serverType)) {
            return;
        }

        // Logique pour le serveur LOBBY
        if ("LOBBY".equalsIgnoreCase(serverType)) {
            // ✅ FIX: Le chargement du profil SQL est ASYNCHRONE pour ne pas bloquer le main thread
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean needsTerms = false;
                
                if (plugin.getTermsManager() != null && plugin.getProfileManager() != null) {
                    // Creer/recuperer le profil du joueur (SQL async — OK)
                    plugin.getProfileManager().getOrCreateProfile(player);
                    needsTerms = plugin.getTermsManager().needsToAcceptTerms(player);
                }
                
                final boolean finalNeedsTerms = needsTerms;
                
                // Retour sur le main thread pour toutes les interactions Bukkit API
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    
                    // Téléporter au spawn et donner les items
                    if (plugin.getSpawnManager() != null && plugin.getSpawnManager().hasSpawn()) {
                        plugin.getSpawnManager().teleportToSpawn(player);
                    }
                    giveLobbyItems(player);
                    
                    if (finalNeedsTerms) {
                        // Afficher le formulaire des termes après un court délai
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) {
                                plugin.getTermsManager().showTerms(player);
                            }
                        }, 40L); // 2 secondes
                        return;
                    }
                    
                    // Suite du login normal : scoreboard, nametag, message de bienvenue VIP
                    finishLobbyJoin(player);
                });
            });
        }
    }

    /**
     * Finalise la connexion lobby (scoreboard, nametag, message VIP).
     * Doit être appelé depuis le main thread.
     */
    private void finishLobbyJoin(Player player) {
        Permission perms = getPerms();
        Chat chat = getChat();
        
        // Délai de 4 secondes (80 ticks) pour laisser Vault charger les données de permission
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            boolean isMember = false;
            String primaryGroup = "unknown";
            
            if (perms != null) {
                try {
                    primaryGroup = perms.getPrimaryGroup(player);
                    plugin.getLogger().info("[DEBUG] " + player.getName() + " group detected as: '" + primaryGroup + "'");
                    
                    if (primaryGroup == null || primaryGroup.isEmpty()) {
                        isMember = true;
                        plugin.getLogger().info("[DEBUG] Group is null/empty -> treating as member");
                    } else {
                        String groupLower = primaryGroup.toLowerCase();
                        if (groupLower.equals("member") || groupLower.equals("default") || groupLower.equals("joueur")) {
                            isMember = true;
                            plugin.getLogger().info("[DEBUG] Group '" + primaryGroup + "' is a member group");
                        } else {
                            plugin.getLogger().info("[DEBUG] Group '" + primaryGroup + "' is NOT a member group -> will show join message");
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().info("[DEBUG] Exception checking group for " + player.getName() + " : " + e.getMessage());
                    isMember = true; // Fallback
                }
            } else {
                plugin.getLogger().info("[DEBUG] Vault Permission provider is NULL!");
                isMember = true;
            }

            plugin.getLogger().info("[DEBUG] FINAL RESULT for " + player.getName() + " -> isMember=" + isMember + ", group=" + primaryGroup);

            // Si le joueur n'est pas un membre régulier, on annonce sa connexion
            if (!isMember) {
                String prefix = "";
                if (chat != null) {
                    try {
                        prefix = chat.getPlayerPrefix(player);
                        plugin.getLogger().info("[DEBUG] Chat prefix for " + player.getName() + ": '" + prefix + "'");
                        if (prefix != null && !prefix.isEmpty()) {
                            prefix = ChatColor.translateAlternateColorCodes('&', prefix) + " ";
                        } else {
                            prefix = "";
                        }
                    } catch (UnsupportedOperationException e) {
                        prefix = "";
                    }
                }

                String joinMessage = prefix + ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " a rejoint le lobby !";
                plugin.getLogger().info("[DEBUG] Broadcasting join message for " + player.getName() + ": " + joinMessage);
                
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.sendMessage(joinMessage);
                }
            } else {
                plugin.getLogger().info("[DEBUG] No join message shown for " + player.getName() + " (is member or default group)");
            }
            
            // Appliquer le scoreboard en mode LOBBY
            if (plugin.getScoreboardManager() != null) {
                plugin.getLogger().info("[DEBUG] Scheduling scoreboard for " + player.getName() + " in 5 ticks");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getLogger().info("[DEBUG] Creating scoreboard for " + player.getName());
                        plugin.getScoreboardManager().createScoreboard(player);
                        plugin.getLogger().info("[DEBUG] Scoreboard created for " + player.getName());
                        
                        // Appliquer le NameTag juste apres le scoreboard
                        if (plugin.getNameTagManager() != null) {
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                if (player.isOnline()) {
                                    plugin.getNameTagManager().setNameTag(player);
                                    plugin.getLogger().info("[DEBUG] NameTag set for " + player.getName());
                                }
                            }, 5L);
                        }
                    } else {
                        plugin.getLogger().info("[DEBUG] Player " + player.getName() + " is offline, skipping scoreboard");
                    }
                }, 5L); // 5 ticks apres Vault
            } else {
                plugin.getLogger().warning("[DEBUG] ScoreboardManager is NULL!");
            }
        }, 80L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Le message de déconnexion est annulé dans tous les cas
        event.setQuitMessage(null);
    }
}
