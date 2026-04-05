package net.gameocean.core.minigames.apartment;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ApartmentListener implements Listener {

    private final GameOceanCore plugin;
    private final ApartmentManager apartmentManager;

    public ApartmentListener(GameOceanCore plugin, ApartmentManager apartmentManager) {
        this.plugin = plugin;
        this.apartmentManager = apartmentManager;
    }

    private boolean isApartmentMode() {
        return "MINIGAME".equalsIgnoreCase(plugin.getServerType()) && 
               "APARTMENT".equalsIgnoreCase(plugin.getMinigameType());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isApartmentMode()) return;

        Player player = event.getPlayer();

        // ✅ FIX: Anti-flash 0.5s - masquer instantanément et envoyer au point neutre
        // Cela empêche un joueur de voir brièvement un autre joueur qui se trouve
        // physiquement au même endroit dans le monde partagé.
        for (Player other : Bukkit.getOnlinePlayers()) {
            player.hidePlayer(plugin, other);
            other.hidePlayer(plugin, player);
        }
        
        org.bukkit.World w = Bukkit.getWorld(apartmentManager.getApartmentWorldName());
        if (w != null) {
            player.teleportAsync(new org.bukkit.Location(w, 0.5, 64.0, 0.5));
        }

        // ✅ FIX: getProfile() est SQL → async, puis retour main thread pour joinApartment()
        // ✅ FIX: la logique de visibilité est maintenant dans joinApartment() → plus de race condition
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String templateId = "default";
            if (plugin.getProfileManager() != null) {
                net.gameocean.core.database.PlayerProfile profile =
                        plugin.getProfileManager().getOrCreateProfile(player);
                if (profile != null && profile.getCurrentApartment() != null) {
                    templateId = profile.getCurrentApartment();
                }
            }
            final String finalTemplateId = templateId;

            // Retour main thread pour les appels Bukkit
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // Si l'appartement n'est pas configuré, on laisse Minecraft de base
                if (!apartmentManager.hasTemplateSpawn(finalTemplateId)) {
                    if (player.isOp()) {
                        player.sendMessage("§e[Apartment] Aucun spawn défini pour le modèle '" + finalTemplateId + "'.");
                        player.sendMessage("§eComportement Minecraft Vanilla actif. Utilisez /app admin setspawn " + finalTemplateId);
                    }
                    return;
                }

                // joinApartment() gère maintenant :
                //  - La téléportation
                //  - La définition de apartment_host
                //  - La mise à jour de la visibilité mutuelle (showPlayer/hidePlayer)
                //  - La mise à jour du nametag
                apartmentManager.joinApartment(player);

            }, 20L); // 1 seconde pour que le joueur soit bien chargé
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isApartmentMode()) return;
        // Rien de spécial à faire quand on quitte un monde partagé
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isApartmentMode()) return;
        
        // Si l'admin a le mode construction, on le laisse casser
        if (plugin.getLobbyBuilders() != null && plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isApartmentMode()) return;
        
        if (plugin.getLobbyBuilders() != null && plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isApartmentMode()) return;
        
        if (plugin.getLobbyBuilders() != null && plugin.getLobbyBuilders().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        
        // Gérer le placement de meuble
        String placingFurniture = apartmentManager.getPlacingFurniture(event.getPlayer().getUniqueId());
        if (placingFurniture != null) {
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                apartmentManager.confirmPlacement(event.getPlayer(), event.getClickedBlock(), event.getBlockFace());
                event.setCancelled(true);
                return;
            } else if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK || event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR) {
                apartmentManager.cancelPlacement(event.getPlayer());
                event.setCancelled(true);
                return;
            }
        }

        // On empêche d'interagir avec les objets comme les portes, coffres pour éviter de modifier la map partagée
        // Sauf si c'est le Totem d'immortalité pour la gestion de l'appartement
        if (event.getItem() != null && event.getItem().getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
            if (event.getAction().name().contains("RIGHT_CLICK") || event.getAction().name().contains("LEFT_CLICK")) {
                apartmentManager.openManagementMenu(event.getPlayer());
            }
            event.setCancelled(true);
            return;
        }
        
        // Gérer les interactions avec les FakeBlocks (rafraichir le rendu, ou les récupérer via clic gauche)
        if (event.getAction().name().contains("LEFT_CLICK") || event.getAction().name().contains("RIGHT_CLICK")) {
            boolean isLeft = event.getAction().name().contains("LEFT_CLICK");
            if (apartmentManager.handleFurnitureInteraction(event.getPlayer(), isLeft)) {
                event.setCancelled(true);
                return;
            }
        }
        
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isApartmentMode()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (!isApartmentMode()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!isApartmentMode()) return;
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getLobbyBuilders() != null && plugin.getLobbyBuilders().contains(player.getUniqueId())) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isApartmentMode()) return;
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!isApartmentMode()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!isApartmentMode()) return;
        
        Player player = event.getPlayer();
        org.bukkit.World w = Bukkit.getWorld(apartmentManager.getApartmentWorldName());
        if (w != null) {
            event.setRespawnLocation(new org.bukkit.Location(w, 0.5, 64.0, 0.5));
            // Cacher le joueur pendant qu'il charge
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.hidePlayer(plugin, other);
                other.hidePlayer(plugin, player);
            }
        }
        
        // Le replacer proprement dans son appartement
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                apartmentManager.joinApartment(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!isApartmentMode()) return;
        if (event.getEntity().getShooter() instanceof Player player) {
            if (plugin.getLobbyBuilders() != null && plugin.getLobbyBuilders().contains(player.getUniqueId())) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!isApartmentMode()) return;
        if (event.getEntity() instanceof Player player) {
            if (plugin.getLobbyBuilders() != null && plugin.getLobbyBuilders().contains(player.getUniqueId())) return;
            event.setCancelled(true);
        }
    }
}
