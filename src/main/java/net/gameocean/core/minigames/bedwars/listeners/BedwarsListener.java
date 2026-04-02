package net.gameocean.core.minigames.bedwars.listeners;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.data.*;
import net.gameocean.core.minigames.bedwars.managers.ArenaManager;
import net.gameocean.core.minigames.bedwars.managers.BedwarsScoreboardManager;
import net.gameocean.core.minigames.bedwars.managers.NPCShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Listener pour le mini-jeu Bedwars
 */
public class BedwarsListener implements Listener {
    
    private final GameOceanCore plugin;
    private final ArenaManager arenaManager;
    private final BedwarsScoreboardManager scoreboardManager;
    private final NPCShopManager npcShopManager;
    private final Set<Location> placedBlocks = new HashSet<>();
    
    public BedwarsListener(GameOceanCore plugin, ArenaManager arenaManager, 
                          BedwarsScoreboardManager scoreboardManager, NPCShopManager npcShopManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.scoreboardManager = scoreboardManager;
        this.npcShopManager = npcShopManager;
    }
    
    /**
     * Vérifie si un joueur est en mode construction
     */
    private boolean isInBuildMode(Player player) {
        return plugin.getBedwarsManager() != null && 
               plugin.getBedwarsManager().getBuildModeManager().isBuilder(player);
    }
    
    /**
     * Obtient l'arène d'un joueur
     */
    private Arena getPlayerArena(Player player) {
        return arenaManager.getPlayerArena(player);
    }
    
    @EventHandler
    public void onPlayerPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        if (!"MINIGAME".equalsIgnoreCase(plugin.getServerType()) ||
            !"BEDWARSSOLO".equalsIgnoreCase(plugin.getMinigameType())) {
            return;
        }
        
        // Bloquer la connexion si aucune arène n'est disponible (pleine ou déjà commencée)
        Arena arena = arenaManager.findAvailableArena();
        if (arena == null) {
            event.disallow(org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_FULL, "§cLe serveur est plein ou la partie a déjà commencé.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Vérifier si c'est un serveur MINIGAME Bedwars
        if (!"MINIGAME".equalsIgnoreCase(plugin.getServerType()) ||
            !"BEDWARSSOLO".equalsIgnoreCase(plugin.getMinigameType())) {
            return;
        }
        
        // Téléporter le joueur à l'arène disponible ou attendre
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            Arena arena = arenaManager.findAvailableArena();
            if (arena != null) {
                arenaManager.joinArena(player, arena);
                if (arena.getWaitingSpawn() != null) {
                    scoreboardManager.createWaitingScoreboard(player, arena);
                }
                
                // Set game mode and clear inventory
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.getInventory().clear();
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
                
                // Redstone pour retourner au hub
                org.bukkit.inventory.ItemStack hubItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.REDSTONE);
                org.bukkit.inventory.meta.ItemMeta hubMeta = hubItem.getItemMeta();
                if (hubMeta != null) {
                    hubMeta.setDisplayName("§c§lRetourner au Hub §7(Clic droit)");
                    hubItem.setItemMeta(hubMeta);
                }
                player.getInventory().setItem(8, hubItem);
                
                // Mettre à jour les tags Vault au dessus de la tête
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.getNameTagManager() != null) {
                        plugin.getNameTagManager().setNameTag(player);
                    }
                }, 5L);
            } else {
                player.sendMessage("§cAucune arène disponible. Demandez à un admin d'en créer une avec /bw create");
                player.sendMessage("§7Utilisez /bw create <nom> puis /bw setwaiting <nom>");
            }
        }, 40L); // Délai augmenté à 2 secondes pour laisser le temps au serveur de charger
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        if (arena != null) {
            arenaManager.leaveArena(player);
        }
        
        scoreboardManager.removeScoreboard(player);
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        if (arena == null) return;
        
        // Mode construction bypass
        if (isInBuildMode(player)) {
            return;
        }

        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            event.setCancelled(true);
            return;
        }
        
        // Vérifier les limites de l'arène
        if (!arena.isInBounds(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas construire en dehors de l'arène!");
            return;
        }
        
        // Enregistrer le bloc placé (pour pouvoir le casser plus tard)
        placedBlocks.add(event.getBlock().getLocation());
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        if (arena == null) return;
        
        // Mode construction bypass
        if (isInBuildMode(player)) {
            return;
        }

        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            event.setCancelled(true);
            return;
        }
        
        Block block = event.getBlock();
        Location loc = block.getLocation();
        
        // Vérifier si c'est un lit (destruction de lit)
        if (block.getType().name().contains("BED")) {
            handleBedBreak(event, player, arena, block);
            return;
        }
        
        // Vérifier si le bloc a été placé par un joueur
        if (!placedBlocks.contains(loc)) {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez casser que les blocs placés par les joueurs!");
            return;
        }
        
        // Retirer de la liste des blocs placés
        placedBlocks.remove(loc);
    }
    
    private void handleBedBreak(BlockBreakEvent event, Player player, Arena arena, Block bedBlock) {
        // Trouver à quelle équipe appartient ce lit
        for (BedwarsTeam team : arena.getTeams().values()) {
            if (team.getBedLocation() == null) continue;
            
            Location bedLoc = team.getBedLocation();
            // Vérifier si c'est le lit de cette équipe (distance 2 max pour inclure la 2eme partie du lit)
            if (bedLoc.getWorld().equals(bedBlock.getWorld()) && bedLoc.distanceSquared(bedBlock.getLocation()) <= 3.0) {
                BedwarsPlayerData playerData = arena.getPlayerData(player.getUniqueId());
                
                // Vérifier si c'est son propre lit
                if (playerData != null && team.getColor().equalsIgnoreCase(playerData.getTeamColor())) {
                    event.setCancelled(true);
                    player.sendMessage("§cVous ne pouvez pas détruire votre propre lit!");
                    return;
                }
                
                // Détruire le lit
                team.setBedDestroyed(true);
                event.setDropItems(false);
                
                // Annoncer
                String teamColor = team.getColorCode();
                arenaManager.broadcastToArena(arena, "§f§lDESTRUCTION DE LIT > " + teamColor + team.getName() + " §7a perdu son lit!");
                arenaManager.broadcastToArena(arena, "§7Détruit par §f" + player.getName());
                
                if (playerData != null) {
                    playerData.addBedDestroyed();
                }
                
                // Notifier l'équipe
                for (UUID uuid : team.getPlayers()) {
                    Player teamPlayer = Bukkit.getPlayer(uuid);
                    if (teamPlayer != null) {
                        teamPlayer.sendTitle("§c§lLIT DÉTRUIT!", "§7Vous ne réapparaîtrez plus!", 10, 70, 20);
                    }
                }
                
                return;
            }
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        Arena arena = getPlayerArena(player);
        if (arena == null) return;
        
        // Pas de dégâts pendant l'attente
        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            event.setCancelled(true);
            return;
        }
        
        // Mode construction = invincible
        if (isInBuildMode(player)) {
            event.setCancelled(true);
            return;
        }
        
        // Simuler la mort si dégâts fatals
        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            killPlayer(player, arena);
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        if (arena == null) return;
        
        BedwarsPlayerData data = arena.getPlayerData(player.getUniqueId());
        
        // Si le joueur sort de l'arène
        if (!arena.isInBounds(player.getLocation()) || player.getLocation().getY() < 0) {
            if (data != null && (!data.isAlive() || player.getGameMode() != org.bukkit.GameMode.SURVIVAL)) {
                // S'il est spectateur ou en attente de respawn, on le ramène au lit ou au spawn
                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                player.setFallDistance(0);
                BedwarsTeam team = arena.getTeam(data.getTeamColor());
                if (team != null && team.getBedLocation() != null) {
                    player.teleport(team.getBedLocation().clone().add(0.5, 5, 0.5));
                } else if (team != null && team.getSpawn() != null) {
                    player.teleport(team.getSpawn().clone().add(0, 5, 0));
                } else if (arena.getWaitingSpawn() != null) {
                    player.teleport(arena.getWaitingSpawn());
                }
            } else if (arena.getState() == ArenaState.IN_GAME) {
                // Sinon, il meurt et passe par la logique de mort
                killPlayer(player, arena);
            }
        }
    }
    
    private void killPlayer(Player player, Arena arena) {
        BedwarsPlayerData playerData = arena.getPlayerData(player.getUniqueId());
        if (playerData == null) return;
        BedwarsTeam team = arena.getTeam(playerData.getTeamColor());
        
        // Traitement du kill (attribution)
        if (player.getLastDamageCause() != null && player.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager() instanceof Player killer) {
                BedwarsPlayerData killerData = arena.getPlayerData(killer.getUniqueId());
                if (killerData != null) {
                    if (team != null && team.isBedDestroyed()) {
                        killerData.addFinalKill();
                    } else {
                        killerData.addKill();
                    }
                }
            }
        }
        
        // Messages de mort globaux
        String playerName = (team != null ? team.getColorCode() : "§7") + player.getName();
        arenaManager.broadcastToArena(arena, playerName + " §7est mort.");

        // Nettoyage complet du joueur à la mort
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Configuration Spectateur Bedwars
        // On le met en SPECTATOR vanilla qui garantit aucun effet, aucune interaction, invisible et volant.
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);

        if (team != null && team.isBedDestroyed()) {
            // Mort définitive (reste en mode spectateur)
            playerData.setAlive(false);
            player.sendTitle("§c§lÉLIMINÉ!", "§7Votre lit a été détruit", 10, 70, 20);
            
            if (arena.getWaitingSpawn() != null) {
                player.teleport(arena.getWaitingSpawn());
            }
            
            checkGameEnd(arena);
        } else {
            // Mort temporaire (attente de 5 secondes)
            player.sendTitle("§c§lMORT!", "§eRéapparition dans 5 secondes", 0, 100, 0);
            
            // On le téléporte à son spawn pendant les 5 secondes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (team != null && team.getSpawn() != null) {
                    player.teleportAsync(team.getSpawn().clone().add(0, 5, 0), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                } else if (arena.getWaitingSpawn() != null) {
                    player.teleportAsync(arena.getWaitingSpawn().clone().add(0, 5, 0), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }, 1L);
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && arena.getState() == ArenaState.IN_GAME && playerData.isAlive()) {
                    
                    // STOP NET DE TOUT MOUVEMENT
                    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    player.setFallDistance(0);
                    
                    // REVIVRE
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    
                    // NE PAS MODIFIER CE BLOC DE TELEPORTATION ! (Demande expresse pour corriger le bug de chute dans le vide)
                    // L'ordre d'exécution et teleportAsync sont nécessaires pour 1.21.
                    // RETOUR AU SPAWN INITIAL EXACT
                    if (team != null && team.getSpawn() != null) {
                        player.teleportAsync(team.getSpawn().clone().add(0, 0.5, 0), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    } else if (arena.getWaitingSpawn() != null) {
                        player.teleportAsync(arena.getWaitingSpawn().clone().add(0, 0.5, 0), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                    
                    giveSpawnItems(player);
                    
                    // REAFFICHER HOLOGRAM
                    if (plugin.getNameTagManager() != null) {
                        plugin.getNameTagManager().setNameTag(player);
                    }
                    
                    player.sendTitle("§a§lRÉAPPARITION", "§7Vous êtes de retour !", 10, 40, 10);
                }
            }, 100L); // 5 secondes
        }
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        
        Arena arena = getPlayerArena(victim);
        if (arena == null) return;
        
        // Pas de PvP pendant l'attente
        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            event.setCancelled(true);
            return;
        }
        
        // Vérifier si l'attaquant est dans la même équipe
        if (event.getDamager() instanceof Player attacker) {
            BedwarsPlayerData victimData = arena.getPlayerData(victim.getUniqueId());
            BedwarsPlayerData attackerData = arena.getPlayerData(attacker.getUniqueId());
            
            if (victimData != null && attackerData != null && 
                victimData.getTeamColor() != null && attackerData.getTeamColor() != null &&
                victimData.getTeamColor().equals(attackerData.getTeamColor())) {
                event.setCancelled(true);
                attacker.sendMessage("§cVous ne pouvez pas attaquer vos coéquipiers!");
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Arena arena = getPlayerArena(player);
        
        if (arena == null) return;
        
        // Annuler le message par défaut si le joueur est dans une arène
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        
        // Auto-respawn si le joueur meurt "vanilla" (ex: /kill)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
            // Puis on simule la mort Bedwars si pas déjà fait
            if (arena.getState() == ArenaState.IN_GAME) {
                killPlayer(player, arena);
            }
        }, 2L);
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        if (arena == null) return;
        
        BedwarsPlayerData playerData = arena.getPlayerData(player.getUniqueId());
        if (playerData != null) {
            BedwarsTeam team = arena.getTeam(playerData.getTeamColor());
            if (team != null && team.getSpawn() != null) {
                event.setRespawnLocation(team.getSpawn().clone().add(0, 0.5, 0));
            } else if (arena.getWaitingSpawn() != null) {
                event.setRespawnLocation(arena.getWaitingSpawn().clone().add(0, 0.5, 0));
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        if (arena == null) return;
        
        BedwarsPlayerData data = arena.getPlayerData(player.getUniqueId());
        if (data != null && !data.isAlive()) {
            event.setCancelled(true);
            return;
        }
        
        // Item Hub
        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            org.bukkit.inventory.ItemStack item = event.getItem();
            if (item != null && item.getType() == org.bukkit.Material.REDSTONE &&
                event.getAction().name().contains("RIGHT")) {
                event.setCancelled(true);
                openLeaveForm(player);
            }
        }
    }
    
    private void openLeaveForm(Player player) {
        if (plugin.getServer().getPluginManager().getPlugin("floodgate") != null &&
            org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            
            org.geysermc.cumulus.form.ModalForm form = org.geysermc.cumulus.form.ModalForm.builder()
                .title("§c§lQuitter la partie ?")
                .content("§7Êtes-vous sûr de vouloir quitter la partie et retourner au hub principal ?\n\n§cCette action est irréversible.")
                .button1("§cQuitter vers le Hub")
                .button2("§aRester ici")
                .validResultHandler((f, response) -> {
                    if (response.clickedFirst()) {
                        transferToLobby(player);
                    }
                })
                .build();
                
            org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } else {
            transferToLobby(player);
        }
    }
    
    private void transferToLobby(Player player) {
        player.sendMessage("§aVérification des serveurs lobby...");
        arenaManager.leaveArena(player);
        
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
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        Arena arena = getPlayerArena(player);
        if (arena == null) return;
        
        BedwarsPlayerData data = arena.getPlayerData(player.getUniqueId());
        if (data != null && !data.isAlive()) {
            event.setCancelled(true);
            return;
        }
        
        // Vérifier si c'est un NPC
        if (entity instanceof Villager villager) {
            event.setCancelled(true);
            
            String customName = villager.getCustomName();
            if (customName == null) return;
            
            if (customName.contains("SHOP")) {
                npcShopManager.openShop(player);
            } else if (customName.contains("UPGRADE")) {
                npcShopManager.openUpgrades(player);
            }
        }
    }
    
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        Arena arena = getPlayerArena(player);
        if (arena == null) return;
        
        // Pas de faim du tout dans l'arène Bedwars (attente ou en jeu)
        event.setCancelled(true);
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        if (arena == null) return;
        
        // Pas de drop pendant l'attente
        if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.STARTING) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerPickupItem(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        if (arena == null) return;
        
        // Compter les ressources ramassées
        ItemStack item = event.getItem().getItemStack();
        BedwarsPlayerData data = arena.getPlayerData(player.getUniqueId());
        
        if (data != null) {
            switch (item.getType()) {
                case IRON_INGOT -> data.addIron(item.getAmount());
                case GOLD_INGOT -> data.addGold(item.getAmount());
                case DIAMOND -> data.addDiamond(item.getAmount());
                case EMERALD -> data.addEmerald(item.getAmount());
            }
        }
    }
    
    private void giveSpawnItems(Player player) {
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
        
        // Donner une pioche si c'est le début de partie
        Arena arena = getPlayerArena(player);
        if (arena != null && arena.getState() == ArenaState.IN_GAME) {
            player.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE));
        }
    }
    
    private void checkGameEnd(Arena arena) {
        int teamsAlive = 0;
        BedwarsTeam lastTeam = null;
        
        for (BedwarsTeam team : arena.getTeams().values()) {
            if (!team.isEliminated()) {
                teamsAlive++;
                lastTeam = team;
            }
        }
        
        if (teamsAlive <= 1) {
            arenaManager.endGame(arena, lastTeam);
        }
    }
    
    private boolean isSameLocation(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
               loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }
    
    /**
     * Obtient les blocs placés (pour le reset de l'arène)
     */
    public Set<Location> getPlacedBlocks() {
        return placedBlocks;
    }
    
    /**
     * Réinitialise les blocs placés
     */
    public void clearPlacedBlocks() {
        placedBlocks.clear();
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (!"MINIGAME".equalsIgnoreCase(plugin.getServerType()) ||
            !"BEDWARSSOLO".equalsIgnoreCase(plugin.getMinigameType())) {
            return;
        }
        
        Player player = event.getPlayer();
        Arena arena = getPlayerArena(player);
        
        String prefix = "";
        try {
            net.milkbowl.vault.chat.Chat chat = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class).getProvider();
            if (chat != null) {
                prefix = chat.getPlayerPrefix(player);
                if (prefix != null && !prefix.isEmpty() && !prefix.endsWith(" ")) {
                    prefix += " ";
                }
            }
        } catch (Exception e) {}
        
        if (prefix == null) prefix = "";
        
        String nameColor = "&f"; // Blanc par défaut pour WAITING
        if (arena != null && (arena.getState() == ArenaState.IN_GAME || arena.getState() == ArenaState.ENDING)) {
            BedwarsPlayerData data = arena.getPlayerData(player.getUniqueId());
            if (data != null && data.getTeamColor() != null) {
                BedwarsTeam team = arena.getTeam(data.getTeamColor());
                if (team != null) {
                    nameColor = team.getColorCode();
                }
            }
            
            // Si le joueur est mort (spectateur)
            if (data != null && !data.isAlive()) {
                prefix = "&7[MORT] " + prefix;
                nameColor = "&7";
            }
        }
        
        String rankColor = "&f";
        if (prefix != null && prefix.contains("&")) {
            int lastAmpersand = prefix.lastIndexOf("&");
            if (lastAmpersand + 1 < prefix.length()) {
                rankColor = "&" + prefix.charAt(lastAmpersand + 1);
            }
        } else if (prefix != null && prefix.contains("§")) {
            int lastSection = prefix.lastIndexOf("§");
            if (lastSection + 1 < prefix.length()) {
                rankColor = "&" + prefix.charAt(lastSection + 1);
            }
        }
        
        if (arena == null || (arena.getState() != ArenaState.IN_GAME && arena.getState() != ArenaState.ENDING)) {
            nameColor = rankColor;
        }

        event.setFormat(ChatColor.translateAlternateColorCodes('&',
            prefix + "&r" + nameColor + "%1$s &f&l»&r %2$s"));
    }
    
    @EventHandler
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if ("MINIGAME".equalsIgnoreCase(plugin.getServerType()) && "BEDWARSSOLO".equalsIgnoreCase(plugin.getMinigameType())) {
            if (event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL) {
                event.setCancelled(true);
            }
        }
    }
}