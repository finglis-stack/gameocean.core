package net.gameocean.core.terms;

import net.gameocean.core.GameOceanCore;
import net.gameocean.core.database.PlayerProfile;
import net.gameocean.core.database.ProfileManager;
import net.gameocean.core.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class TermsManager {

    private final GameOceanCore plugin;
    private final ProfileManager profileManager;
    private final Set<UUID> pendingAcceptance = new HashSet<>();

    // Configuration des termes
    private String termsTitle;
    private String termsContent;
    private String buttonAccept;
    private String buttonDecline;

    private boolean floodgateAvailable = false;

    public TermsManager(GameOceanCore plugin, ProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        loadConfig();
        initializeFloodgate();
    }

    private void loadConfig() {
        termsTitle = ChatColor.translateAlternateColorCodes('&', "&fConditions d'utilisation");
        // Texte hardcode directement dans le code (pas de YAML)
        String rawContent = "&aBienvenue sur GameOcean&r&a!\n\n&aEn jouant sur ce serveur, vous acceptez nos regles et conditions d'utilisation.\n\n&aPour plus de details, consultez &bwww.gameocean.net/tos&r&a.\n\n&aVoulez-vous accepter et continuer?";
        termsContent = ChatColor.translateAlternateColorCodes('&', rawContent.replace("GameOcean", MessageUtils.SERVER_NAME_NO_RESET + "&r&a"));
        buttonAccept = plugin.getConfig().getString("terms.button-accept", "J'accepte");
        buttonDecline = plugin.getConfig().getString("terms.button-decline", "Je refuse");
    }

    private void initializeFloodgate() {
        try {
            Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
            if (floodgate == null) {
                plugin.getLogger().warning("Floodgate n'est pas installe sur ce serveur Spigot.");
                return;
            }

            // Test if the API is available
            FloodgateApi.getInstance();
            plugin.getLogger().info("Floodgate detecte ! Formulaires Bedrock actives.");
            floodgateAvailable = true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossible d'initialiser Floodgate API: " + e.getMessage());
            floodgateAvailable = false;
        }
    }

    /**
     * Verifie si le joueur doit accepter les termes
     */
    public boolean needsToAcceptTerms(Player player) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) {
            return false;
        }

        PlayerProfile profile = profileManager.getProfile(player.getUniqueId());
        if (profile == null) {
            return true; // Nouveau joueur, doit accepter
        }
        return !profile.hasAcceptedTerms();
    }

    /**
     * Affiche le formulaire des termes au joueur
     */
    public void showTerms(Player player) {
        if (!"LOBBY".equalsIgnoreCase(plugin.getServerType())) {
            return;
        }

        pendingAcceptance.add(player.getUniqueId());

        // Bloquer le joueur immédiatement
        freezePlayer(player);

        // Attendre 2 secondes que Floodgate enregistre le joueur
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showBedrockForm(player);
            }
        }, 40L); // 40 ticks = 2 secondes
    }

    /**
     * Affiche le formulaire Bedrock natif avec Cumulus
     */
    private void showBedrockForm(Player player) {
        if (!floodgateAvailable) {
            plugin.getLogger().warning("Floodgate n'est pas disponible pour " + player.getName());
            player.sendMessage(MessageUtils.format("&cErreur: Floodgate non disponible."));
            pendingAcceptance.remove(player.getUniqueId());
            unfreezePlayer(player);
            return;
        }

        try {
            String uuidStr = player.getUniqueId().toString();
            plugin.getLogger().info("Tentative d'envoi du formulaire a: " + player.getName() + " (UUID: " + uuidStr + ")");

            boolean isFloodgate = FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
            boolean isBedrockUUID = uuidStr.startsWith("00000000-0000-0000-0009-");

            plugin.getLogger().info(player.getName() + " isFloodgatePlayer: " + isFloodgate + ", isBedrockUUID: " + isBedrockUUID);

            if (!isFloodgate && isBedrockUUID) {
                plugin.getLogger().warning(player.getName() + " a un UUID Bedrock mais Floodgate(Spigot) ne le reconnait pas ! Verifiez 'send-floodgate-data' sur Velocity.");
            }

            if (!isFloodgate) {
                plugin.getLogger().warning("Le joueur n'est pas connecte via Floodgate sur ce serveur. Le formulaire ne peut pas etre envoye.");
                player.sendMessage(MessageUtils.format("&cErreur: Vous n'etes pas connecte via Floodgate."));
                unfreezePlayer(player);
                return;
            }

            // Creer le formulaire avec Cumulus
            SimpleForm form = SimpleForm.builder()
                    .title(termsTitle)
                    .content(termsContent)
                    .button(buttonAccept, FormImage.Type.URL, "https://i.imgur.com/er32GdR.png")  // Bouton 0 = Accepter
                    .button(buttonDecline, FormImage.Type.URL, "https://i.imgur.com/sQR0Djx.png") // Bouton 1 = Refuser
                    .validResultHandler(response -> {
                        int clickedButton = response.clickedButtonId();
                        if (clickedButton == 0) {
                            Bukkit.getScheduler().runTask(plugin, () -> handleAccept(player));
                        } else {
                            Bukkit.getScheduler().runTask(plugin, () -> handleDecline(player));
                        }
                    })
                    .closedOrInvalidResultHandler(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            pendingAcceptance.remove(player.getUniqueId());
                            if (player.isOnline()) {
                                player.kickPlayer(ChatColor.RED + "Vous devez accepter les conditions pour jouer.");
                            }
                        });
                    })
                    .build();

            boolean sent = FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);

            if (sent) {
                plugin.getLogger().info("Formulaire envoye avec succes a " + player.getName());
            } else {
                plugin.getLogger().severe("FloodgateApi.sendForm a retourne false pour " + player.getName() + " (FloodgatePlayer non trouve).");
                player.sendMessage(MessageUtils.format("&cErreur: Impossible d'afficher le formulaire."));
                unfreezePlayer(player);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'envoi du formulaire Bedrock: " + e.getMessage(), e);
            player.sendMessage(MessageUtils.format("&cErreur lors de l'affichage des conditions."));
            unfreezePlayer(player);
        }
    }

    /**
     * Verifie si le joueur est sur Bedrock
     */
    public boolean isBedrockPlayer(Player player) {
        if (!floodgateAvailable) {
            return player.getUniqueId().toString().startsWith("00000000-0000-0000-0009-");
        }
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }

    /**
     * Gere l'acceptation des termes
     */
    public void handleAccept(Player player) {
        pendingAcceptance.remove(player.getUniqueId());
        profileManager.acceptTerms(player.getUniqueId());
        player.sendMessage(MessageUtils.format("&aVous avez accepte les conditions d'utilisation. Bon jeu!"));
        unfreezePlayer(player);
        plugin.getLogger().info(player.getName() + " a accepte les conditions d'utilisation.");
        
        // Appliquer le scoreboard et le nametag après acceptation
        if ("LOBBY".equalsIgnoreCase(plugin.getServerType())) {
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().createScoreboard(player);
            }
            if (plugin.getNameTagManager() != null) {
                plugin.getNameTagManager().setNameTag(player);
            }
        }
    }

    /**
     * Gere le refus des termes
     */
    public void handleDecline(Player player) {
        pendingAcceptance.remove(player.getUniqueId());
        player.kickPlayer(MessageUtils.format("&cVous devez accepter les conditions d'utilisation pour jouer."));
        plugin.getLogger().info(player.getName() + " a refuse les conditions et a ete expulse.");
    }

    /**
     * Verifie si un joueur est en attente d'acceptation
     */
    public boolean isPendingAcceptance(UUID uuid) {
        return pendingAcceptance.contains(uuid);
    }

    public Set<UUID> getPendingAcceptance() {
        return new HashSet<>(pendingAcceptance);
    }

    /**
     * Bloque le joueur (empeche de bouger)
     */
    private void freezePlayer(Player player) {
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        player.setAllowFlight(false);
    }

    /**
     * Debloque le joueur
     */
    private void unfreezePlayer(Player player) {
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
    }
}
