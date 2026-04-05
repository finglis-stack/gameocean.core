package net.gameocean.core.listeners;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DamageListener implements Listener {

    private final GameOceanCore plugin;
    private final Map<UUID, Integer> voidTeleportTasks = new HashMap<>();
    private final Map<UUID, Long> menuCooldowns = new HashMap<>();

    public DamageListener(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        // Vérifier que c'est un joueur
        if (!(event.getEntity() instanceof Player)) return;

        // Vérifier que c'est en mode LOBBY
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;

        Player player = (Player) event.getEntity();

        // Annuler les dégâts de chute
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        // Annuler les dégâts de faim
        if (event.getCause() == EntityDamageEvent.DamageCause.STARVATION) {
            event.setCancelled(true);
            return;
        }

        // Annuler les dégâts de suffocation
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            return;
        }

        // Annuler les dégâts de noyade
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
            return;
        }

        // Annuler les dégâts de feu/lave
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE || 
            event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
            event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            event.setCancelled(true);
            player.setFireTicks(0);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Vérifier que c'est un joueur qui reçoit des dégâts
        if (!(event.getEntity() instanceof Player)) return;

        // Vérifier que c'est en mode LOBBY
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;

        // Si l'attaquant est aussi un joueur, annuler (pas de PVP)
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }

        // Annuler tous les dégâts par entité dans le lobby (mobs, etc.)
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        if (plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        if (plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        if (plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) return;
        
        // Anti-piétinement
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
            event.setCancelled(true);
            return;
        }

        // Bloquer l'utilisation des items du lobby
        if (event.getItem() != null && event.getAction().name().contains("RIGHT_CLICK")) {
            // Empecher toute interaction/consommation
            event.setCancelled(true);

            // Ignorer l'off-hand pour eviter le double-fire
            if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

            // Cooldown anti-spam (500ms)
            UUID uid = event.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            if (menuCooldowns.containsKey(uid) && (now - menuCooldowns.get(uid)) < 500) return;
            menuCooldowns.put(uid, now);
            
            // Ouvrir les menus
            if (event.getItem().getType() == Material.COMPASS) {
                if (plugin.getMenuManager() != null) {
                    plugin.getMenuManager().openMainMenu(event.getPlayer());
                }
            } else if (event.getItem().getType() == Material.TOTEM_OF_UNDYING) {
                if (plugin.getMenuManager() != null) {
                    plugin.getMenuManager().openSocialMenu(event.getPlayer());
                }
            } else if (event.getItem().getType() == Material.MAGMA_CREAM) {
                if (plugin.getMenuManager() != null) {
                    plugin.getMenuManager().openSettingsMenu(event.getPlayer());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getLobbyBuilders().contains(player.getUniqueId())) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        if (plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;
        if (plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Vérifier que c'est en mode LOBBY
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;

        // Annuler seulement le spawn naturel (pas les spawns artificiels pour cosmétiques)
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL ||
            reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN ||
            reason == CreatureSpawnEvent.SpawnReason.SPAWNER ||
            reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
            reason == CreatureSpawnEvent.SpawnReason.BREEDING ||
            reason == CreatureSpawnEvent.SpawnReason.EGG ||
            reason == CreatureSpawnEvent.SpawnReason.JOCKEY ||
            reason == CreatureSpawnEvent.SpawnReason.MOUNT ||
            reason == CreatureSpawnEvent.SpawnReason.PATROL ||
            reason == CreatureSpawnEvent.SpawnReason.RAID ||
            reason == CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK ||
            reason == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT ||
            reason == CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE ||
            reason == CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION) {
            event.setCancelled(true);
        }
    }

    public void startVoidCheck() {
        // Vérifier toutes les secondes si des joueurs sont tombés dans le vide
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) return;

            // Ne rien faire si le spawn n'est pas défini
            if (plugin.getSpawnManager() == null || !plugin.getSpawnManager().hasSpawn()) {
                return;
            }

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                // Attendre 5 secondes après la connexion avant d'activer la protection
                if (player.getTicksLived() < 100) continue; // 100 ticks = 5 secondes

                // Récupérer la hauteur minimum du monde (là où commence le vide qui tue)
                int minHeight = player.getWorld().getMinHeight();
                double y = player.getLocation().getY();
                
                // Si le joueur est sous la hauteur minimum du monde (dans le vide qui tue)
                if (y < minHeight) {
                    UUID playerId = player.getUniqueId();

                    // Si pas déjà en attente de téléportation
                    if (!voidTeleportTasks.containsKey(playerId)) {
                        plugin.getLogger().info("[VOID] " + player.getName() + " détecté dans le vide à Y=" + y + " (minHeight=" + minHeight + ")");

                        // Téléportation immédiate
                        plugin.getSpawnManager().teleportToSpawn(player);
                        voidTeleportTasks.put(playerId, -1);
                    }
                } else {
                    // Joueur hors du vide, reset
                    voidTeleportTasks.remove(player.getUniqueId());
                }
            }
        }, 20L, 20L); // Vérifier toutes les secondes (20 ticks)
    }
}
