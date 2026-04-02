package net.gameocean.core.minigames.bedwars.managers;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.minigames.bedwars.data.Arena;
import net.gameocean.core.minigames.bedwars.data.NPCShop;
import net.gameocean.core.minigames.bedwars.data.NPCType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.component.ButtonComponent;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire des NPCs (boutiques) avec Forms Cumulus
 */
public class NPCShopManager {
    
    private final GameOceanCore plugin;
    private final Map<Player, Long> lastClick = new HashMap<>();
    
    public NPCShopManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Spawn tous les NPCs d'une arène
     */
    public void spawnNPCs(Arena arena) {
        for (NPCShop npc : arena.getNpcs()) {
            spawnNPC(npc);
        }
    }
    
    /**
     * Spawn un NPC
     */
    public void spawnNPC(NPCShop npc) {
        Location loc = npc.getLocation();
        
        // Supprimer l'ancien NPC s'il existe
        npc.remove();
        
        // Créer un villageois
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerLevel(5);
        villager.setCustomNameVisible(true);
        villager.setCustomName(npc.getType().getDisplayName());
        
        npc.setEntity(villager);
    }
    
    /**
     * Supprime tous les NPCs d'une arène
     */
    public void removeNPCs(Arena arena) {
        for (NPCShop npc : arena.getNpcs()) {
            npc.remove();
        }
    }
    
    /**
     * Ouvre le menu du shop
     */
    public void openShop(Player player) {
        // Anti-spam
        long now = System.currentTimeMillis();
        if (lastClick.getOrDefault(player, 0L) + 500 > now) {
            return;
        }
        lastClick.put(player, now);
        
        if (!isBedrockPlayer(player)) {
            // Pour Java, on pourrait ouvrir un menu classique plus tard
            openJavaShop(player);
            return;
        }
        
        SimpleForm form = SimpleForm.builder()
                .title(ChatColor.translateAlternateColorCodes('&', "&6&lShop Bedwars"))
                .content("§7Votre argent:\n" +
                        "§7Fer: §f" + countItems(player, Material.IRON_INGOT) + " §7| " +
                        "§6Or: §f" + countItems(player, Material.GOLD_INGOT) + " §7| " +
                        "§bDiamants: §f" + countItems(player, Material.DIAMOND) + " §7| " +
                        "§aÉmeraudes: §f" + countItems(player, Material.EMERALD))
                .button("§f§lBlocs\n§7Cliquez pour voir", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/3h07a0U.png")
                .button("§c§lArmures\n§7Cliquez pour voir", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/J8d0G7a.png")
                .button("§b§lÉpées\n§7Cliquez pour voir", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/4J8JQYy.png")
                .button("§e§lOutils\n§7Cliquez pour voir", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/YV2xQ2T.png")
                .button("§a§lArcherie\n§7Cliquez pour voir", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/k3yM5cW.png")
                .button("§d§lUtilitaires\n§7Cliquez pour voir", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/3X6hM3D.png")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> openBlocksShop(player);
                            case 1 -> openArmorsShop(player);
                            case 2 -> openSwordsShop(player);
                            case 3 -> openToolsShop(player);
                            case 4 -> openBowShop(player);
                            case 5 -> openUtilityShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Ouvre le menu des améliorations
     */
    public void openUpgrades(Player player) {
        if (!isBedrockPlayer(player)) {
            player.sendMessage("§cLes améliorations sont disponibles via les NPCs Bedrock pour l'instant.");
            return;
        }
        
        SimpleForm form = SimpleForm.builder()
                .title(ChatColor.translateAlternateColorCodes('&', "&b&lAméliorations"))
                .content("§7Améliorez votre île et vos générateurs!")
                .button("§eAméliorer Générateur de Fer\n§7Coût: §b2 Diamants")
                .button("§6Améliorer Générateur d'Or\n§7Coût: §b4 Diamants")
                .button("§aForge (Protection)\n§7Coût: §b2 Diamants")
                .validResultHandler(response -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§aAmélioration achetée!");
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop des blocs
     */
    private void openBlocksShop(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§f§lBlocs")
                .content("§7Achetez des blocs de construction")
                .button("§7Wool x16\n§7Coût: §f4 Fer", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/3h07a0U.png")
                .button("§fTerracotta x16\n§7Coût: §f12 Fer", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/4J8JQYy.png")
                .button("§8Obsidian x4\n§7Coût: §64 Or", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/J8d0G7a.png")
                .button("§a← Retour", org.geysermc.cumulus.util.FormImage.Type.URL, "https://i.imgur.com/YV2xQ2T.png")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> buyItem(player, Material.WHITE_WOOL, 16, Material.IRON_INGOT, 4);
                            case 1 -> buyItem(player, Material.TERRACOTTA, 16, Material.IRON_INGOT, 12);
                            case 2 -> buyItem(player, Material.OBSIDIAN, 4, Material.GOLD_INGOT, 4);
                            case 3 -> openShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop des armures
     */
    private void openArmorsShop(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§c§lArmures")
                .content("§7Achetez des armures")
                .button("§7Armure en Mailles\n§7Coût: §f24 Fer")
                .button("§fArmure en Fer\n§7Coût: §612 Or")
                .button("§bArmure en Diamant\n§7Coût: §66 Émeraudes")
                .button("§a← Retour")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> {
                                buyItem(player, Material.CHAINMAIL_BOOTS, 1, Material.IRON_INGOT, 24);
                                buyItem(player, Material.CHAINMAIL_LEGGINGS, 1, Material.IRON_INGOT, 0);
                                buyItem(player, Material.CHAINMAIL_CHESTPLATE, 1, Material.IRON_INGOT, 0);
                                buyItem(player, Material.CHAINMAIL_HELMET, 1, Material.IRON_INGOT, 0);
                            }
                            case 1 -> {
                                buyItem(player, Material.IRON_BOOTS, 1, Material.GOLD_INGOT, 12);
                                buyItem(player, Material.IRON_LEGGINGS, 1, Material.GOLD_INGOT, 0);
                                buyItem(player, Material.IRON_CHESTPLATE, 1, Material.GOLD_INGOT, 0);
                                buyItem(player, Material.IRON_HELMET, 1, Material.GOLD_INGOT, 0);
                            }
                            case 3 -> openShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop des épées
     */
    private void openSwordsShop(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§b§lÉpées")
                .content("§7Achetez des armes")
                .button("§7Épée en Pierre\n§7Coût: §f10 Fer")
                .button("§fÉpée en Fer\n§7Coût: §67 Or")
                .button("§bÉpée en Diamant\n§7Coût: §64 Or")
                .button("§cBâton de Knockback\n§7Coût: §65 Or")
                .button("§a← Retour")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> buyItem(player, Material.STONE_SWORD, 1, Material.IRON_INGOT, 10);
                            case 1 -> buyItem(player, Material.IRON_SWORD, 1, Material.GOLD_INGOT, 7);
                            case 2 -> buyItem(player, Material.DIAMOND_SWORD, 1, Material.GOLD_INGOT, 4);
                            case 3 -> buyItem(player, Material.STICK, 1, Material.GOLD_INGOT, 5); // Knockback stick
                            case 4 -> openShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop des outils
     */
    private void openToolsShop(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§e§lOutils")
                .content("§7Achetez des outils")
                .button("§7Pioche en Fer\n§7Coût: §f10 Fer")
                .button("§7Hache en Fer\n§7Coût: §f10 Fer")
                .button("§fCiseaux\n§7Coût: §f5 Fer")
                .button("§a← Retour")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> buyItem(player, Material.IRON_PICKAXE, 1, Material.IRON_INGOT, 10);
                            case 1 -> buyItem(player, Material.IRON_AXE, 1, Material.IRON_INGOT, 10);
                            case 2 -> buyItem(player, Material.SHEARS, 1, Material.IRON_INGOT, 5);
                            case 3 -> openShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop des arcs
     */
    private void openBowShop(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§a§lArcherie")
                .content("§7Achetez des arcs et flèches")
                .button("§7Arc\n§7Coût: §612 Or")
                .button("§7Arc Power I\n§7Coût: §624 Or")
                .button("§7Arc Punch I\n§7Coût: §63 Émeraudes")
                .button("§7Flèches x8\n§7Coût: §62 Or")
                .button("§a← Retour")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> buyItem(player, Material.BOW, 1, Material.GOLD_INGOT, 12);
                            case 3 -> buyItem(player, Material.ARROW, 8, Material.GOLD_INGOT, 2);
                            case 4 -> openShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop utilitaires
     */
    private void openUtilityShop(Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§d§lUtilitaires")
                .content("§7Achetez des items utilitaires")
                .button("§7Échelles x16\n§7Coût: §f2 Fer")
                .button("§fBlocs de TNT x4\n§7Coût: §68 Or")
                .button("§7Fireball\n§7Coût: §640 Or")
                .button("§aPomme en Or\n§7Coût: §63 Or")
                .button("§bEnder Pearl\n§7Coût: §64 Émeraudes")
                .button("§a← Retour")
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (clicked) {
                            case 0 -> buyItem(player, Material.LADDER, 16, Material.IRON_INGOT, 2);
                            case 1 -> buyItem(player, Material.TNT, 4, Material.GOLD_INGOT, 8);
                            case 2 -> buyItem(player, Material.FIRE_CHARGE, 1, Material.GOLD_INGOT, 40);
                            case 3 -> buyItem(player, Material.GOLDEN_APPLE, 1, Material.GOLD_INGOT, 3);
                            case 4 -> buyItem(player, Material.ENDER_PEARL, 1, Material.EMERALD, 4);
                            case 5 -> openShop(player);
                        }
                    });
                })
                .build();
        
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
    
    /**
     * Shop Java temporaire
     */
    private void openJavaShop(Player player) {
        player.sendMessage("§6§l=== Shop Bedwars ===");
        player.sendMessage("§7Utilisez le menu Bedrock ou contactez un admin.");
        player.sendMessage("§7Vous avez: §f" + countItems(player, Material.IRON_INGOT) + " Fer, " +
                countItems(player, Material.GOLD_INGOT) + " Or, " +
                countItems(player, Material.DIAMOND) + " Diamants, " +
                countItems(player, Material.EMERALD) + " Émeraudes");
    }
    
    /**
     * Achète un item
     */
    private boolean buyItem(Player player, Material item, int amount, Material currency, int cost) {
        if (cost == 0) return true; // Items gratuits (pour les sets complets)
        
        int has = countItems(player, currency);
        if (has < cost) {
            player.sendMessage("§cVous n'avez pas assez de " + getCurrencyName(currency) + "!");
            player.sendMessage("§7Requis: §c" + cost + " §7| Vous avez: §c" + has);
            return false;
        }
        
        // Retirer la monnaie
        removeItems(player, currency, cost);
        
        // Donner l'item
        ItemStack giveItem = new ItemStack(item, amount);
        player.getInventory().addItem(giveItem);
        
        player.sendMessage("§aVous avez acheté §f" + amount + "x " + item.name().toLowerCase().replace("_", " ") + " §apour §f" + cost + " " + getCurrencyName(currency));
        return true;
    }
    
    /**
     * Compte le nombre d'items d'un type dans l'inventaire
     */
    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    /**
     * Retire des items de l'inventaire
     */
    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().remove(item);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
                if (remaining <= 0) break;
            }
        }
    }
    
    /**
     * Obtient le nom d'affichage d'une monnaie
     */
    private String getCurrencyName(Material material) {
        return switch (material) {
            case IRON_INGOT -> "Fer";
            case GOLD_INGOT -> "Or";
            case DIAMOND -> "Diamants";
            case EMERALD -> "Émeraudes";
            default -> material.name();
        };
    }
    
    /**
     * Vérifie si le joueur est sur Bedrock
     */
    private boolean isBedrockPlayer(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }
}