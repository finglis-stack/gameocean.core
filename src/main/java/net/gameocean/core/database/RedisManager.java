package net.gameocean.core.database;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.logging.Level;

public class RedisManager {

    private final GameOceanCore plugin;
    private JedisPool jedisPool;
    private JedisPubSub pubSub;

    private boolean enabled;
    private String host;
    private int port;
    private String password;

    public RedisManager(GameOceanCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("redis.enabled", true);
        host = plugin.getConfig().getString("redis.host", "127.0.0.1");
        port = plugin.getConfig().getInt("redis.port", 6379);
        password = plugin.getConfig().getString("redis.password", "");
    }

    public boolean connect() {
        if (!enabled) {
            plugin.getLogger().info("Redis desactive dans la configuration.");
            return false;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            
            if (password != null && !password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, 2000);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            plugin.getLogger().info("=========================================");
            plugin.getLogger().info(" [Redis] CONNEXION ETABLIE AVEC SUCCES !");
            plugin.getLogger().info("=========================================");
            startSubscriber();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur de connexion a Redis: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        if (pubSub != null) {
            pubSub.unsubscribe();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            plugin.getLogger().info("Deconnexion de Redis.");
        }
    }

    public void publish(String channel, String message) {
        if (jedisPool == null || jedisPool.isClosed()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du publish Redis: " + e.getMessage());
            }
        });
    }

    public void set(String key, String value, int expireSeconds) {
        if (jedisPool == null || jedisPool.isClosed()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(key, expireSeconds, value);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du setex Redis: " + e.getMessage());
            }
        });
    }

    public void delete(String key) {
        if (jedisPool == null || jedisPool.isClosed()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du del Redis: " + e.getMessage());
            }
        });
    }

    public String getSync(String key) {
        if (jedisPool == null || jedisPool.isClosed()) return null;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void startSubscriber() {
        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if ("gameocean:apartment_invite".equals(channel)) {
                    // Format: hostName:targetName:serverName
                    String[] parts = message.split(":");
                    if (parts.length == 3) {
                        String hostName = parts[0];
                        String targetName = parts[1];
                        String serverName = parts[2];

                        // ⚠ onMessage() s'exécute sur le thread Redis (subscriber) — pas le main thread !
                        // Toute interaction avec l'API Bukkit DOIT être faite via runTask().
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player target = Bukkit.getPlayerExact(targetName);
                            if (target != null && target.isOnline()) {
                                // Stocker l'invitation dans Redis (60 sec)
                                set("apartment_invite:" + targetName + ":" + hostName, serverName, 60);

                                target.sendMessage(" ");
                                target.sendMessage("§a[Appartement] §e" + hostName + " §avous invite dans son appartement !");
                                target.sendMessage("§a[Appartement] §7Faites §e/accept " + hostName + " §7pour le rejoindre (Expire dans 60s).");
                                target.sendMessage(" ");
                            }
                        });
                    }
                } else if ("gameocean:friend_request".equals(channel)) {
                    String[] parts = message.split(":");
                    if (parts.length == 2) {
                        String senderName = parts[0];
                        String receiverName = parts[1];
                        
                        Player target = Bukkit.getPlayerExact(receiverName);
                        if (target != null && target.isOnline()) {
                            // Envoyer le message immédiatement sur le main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                target.sendMessage("§a[Amis] §e" + senderName + " §avoudrait être votre ami !");
                                target.sendMessage("§7Faites §e/friend accept " + senderName + " §7pour accepter la demande.");
                            });
                            
                            // Vérifier le profil en async (pas sur le thread subscriber !)
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(target.getUniqueId());
                                boolean canPopup = profile != null && profile.hasFriendPopupRequests();
                                if (canPopup && ("LOBBY".equalsIgnoreCase(plugin.getServerType()) || "APARTMENT".equalsIgnoreCase(plugin.getMinigameType()))) {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (target.isOnline() && plugin.getMenuManager() != null) {
                                            plugin.getMenuManager().openFriendRequestPopup(target, senderName);
                                        }
                                    });
                                }
                            });
                        }
                    }
                } else if ("gameocean:friend_accept".equals(channel)) {
                    String[] parts = message.split(":");
                    if (parts.length == 2) {
                        String receiverName = parts[0];
                        String senderName = parts[1];
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player target = Bukkit.getPlayerExact(senderName);
                            if (target != null && target.isOnline()) {
                                target.sendMessage("§a[Amis] §e" + receiverName + " §aa accepté votre demande d'ami !");
                            }
                        });
                    }
                } else if ("gameocean:friend_joined_direct".equals(channel)) {
                    String[] parts = message.split(":");
                    if (parts.length == 2) {
                        String friendName = parts[0];
                        String senderName = parts[1];
                        Player target = Bukkit.getPlayerExact(friendName);
                        if (target != null && target.isOnline()) {
                            // Vérifier le profil en async, pas sur le thread subscriber !
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                net.gameocean.core.database.PlayerProfile profile = plugin.getProfileManager().getProfile(target.getUniqueId());
                                if (profile != null && profile.hasFriendAnnouncements()) {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (target.isOnline()) {
                                            target.sendMessage("§e[Amis] §b" + senderName + " §avient de se connecter au réseau !");
                                        }
                                    });
                                }
                            });
                        }
                    }
                }
            }
        };

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(pubSub, "gameocean:apartment_invite", "gameocean:friend_request", "gameocean:friend_accept", "gameocean:friend_joined_direct");
            } catch (Exception e) {
                plugin.getLogger().warning("Le subscriber Redis s'est deconnecte: " + e.getMessage());
            }
        });
    }

    public boolean isConnected() {
        return jedisPool != null && !jedisPool.isClosed();
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }
}