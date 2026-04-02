package net.gameocean.core;

import net.gameocean.core.commands.AcceptCommand;
import net.gameocean.core.commands.DeclineCommand;
import net.gameocean.core.commands.SetSpawnCommand;
import net.gameocean.core.commands.WorldCommand;
import net.gameocean.core.database.DatabaseManager;
import net.gameocean.core.database.ProfileManager;
import net.gameocean.core.database.RedisManager;
import net.gameocean.core.listeners.ConnectionListener;
import net.gameocean.core.listeners.DamageListener;
import net.gameocean.core.managers.ScoreboardManager;
import net.gameocean.core.managers.SpawnManager;
import net.gameocean.core.terms.TermsManager;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class GameOceanCore extends JavaPlugin {

    private Logger log;
    private String serverType;
    private String minigameType;
    private String serverId;

    private Chat chat = null;
    private Permission perms = null;
    private SpawnManager spawnManager;
    private DatabaseManager databaseManager;
    private ProfileManager profileManager;
    private RedisManager redisManager;
    private TermsManager termsManager;
    private net.gameocean.core.managers.ScoreboardManager scoreboardManager;
    private net.gameocean.core.managers.ChatNameTagManager chatNameTagManager;
    private net.gameocean.core.managers.MenuManager menuManager;
    private net.gameocean.core.managers.WorldManager worldManager;
    private net.gameocean.core.minigames.bedwars.BedwarsManager bedwarsManager;
    private net.gameocean.core.minigames.apartment.ApartmentManager apartmentManager;
    private net.gameocean.core.utils.BungeeServerCheck bungeeServerCheck;
    private net.gameocean.core.utils.BungeeMessaging bungeeMessaging;
    
    // ConcurrentHashMap.newKeySet() est thread-safe (accédé depuis plusieurs event threads)
    private final java.util.Set<java.util.UUID> lobbyBuilders = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        this.log = getLogger();
        
        // Sauvegarde de la configuration par defaut (config.yml) si elle n'existe pas
        saveDefaultConfig();
        
        // Chargement des parametres depuis la configuration
        loadConfigVariables();

        log.info("=========================================");
        log.info(" GameOcean Core - Activation en cours...");
        log.info(" Type d'instance : " + serverType);
        
        if ("MINIGAME".equalsIgnoreCase(serverType)) {
            log.info(" Type de mini-jeu : " + minigameType);
        }

        // Configuration de Vault
        if (!setupPermissions()) {
            log.warning("Vault (Permissions) n'a pas ete trouve. Les prefixes par defaut seront utilises.");
        }
        if (!setupChat()) {
            log.warning("Vault (Chat) n'a pas ete trouve. Impossible de recuperer les prefixes.");
        }

        // Initialiser la base de donnees (toujours, pour les stats/profils)
        databaseManager = new DatabaseManager(this);
        if (databaseManager.connect()) {
            profileManager = new ProfileManager(this, databaseManager);
            
            // Initialisation de Redis
            redisManager = new RedisManager(this);
            if (redisManager.connect()) {
                log.info(" [Redis] ===== CONNECTE ET OPERATIONNEL =====");
            } else {
                log.warning(" [Redis] ===== ECHEC DE LA CONNEXION =====");
            }
            
            // Initialiser le TermsManager uniquement en mode LOBBY
            if ("LOBBY".equalsIgnoreCase(serverType)) {
                termsManager = new TermsManager(this, profileManager);
            }
        } else {
            log.warning("Impossible de se connecter a la base de donnees. Les profils ne seront pas sauvegardes.");
        }

        // Initialiser le WorldManager (toujours, pour tous les modes)
        worldManager = new net.gameocean.core.managers.WorldManager(this);
        log.info(" WorldManager initialise.");

        // Initialiser le SpawnManager (si en mode LOBBY)
        if ("LOBBY".equalsIgnoreCase(serverType)) {
            spawnManager = new SpawnManager(this);
            spawnManager.setupLobbyWorld();
            
            // Initialiser le ScoreboardManager (uniquement LOBBY)
            scoreboardManager = new ScoreboardManager(this);
            log.info(" ScoreboardManager initialise.");

            menuManager = new net.gameocean.core.managers.MenuManager(this);
            log.info(" MenuManager initialise.");
        }
        
        // Initialiser ChatNameTagManager (GLOBAL)
        chatNameTagManager = new net.gameocean.core.managers.ChatNameTagManager(this);
        getServer().getPluginManager().registerEvents(chatNameTagManager, this);
        log.info(" ChatNameTagManager global initialise.");
        
        if ("MINIGAME".equalsIgnoreCase(serverType)) {
            if ("BEDWARSSOLO".equalsIgnoreCase(minigameType)) {
                bedwarsManager = new net.gameocean.core.minigames.bedwars.BedwarsManager(this);
            } else if ("APARTMENT".equalsIgnoreCase(minigameType)) {
                apartmentManager = new net.gameocean.core.minigames.apartment.ApartmentManager(this);
                getServer().getPluginManager().registerEvents(new net.gameocean.core.minigames.apartment.ApartmentListener(this, apartmentManager), this);
                org.bukkit.command.PluginCommand appCmd = getCommand("app");
                if (appCmd != null) {
                    appCmd.setExecutor(new net.gameocean.core.minigames.apartment.ApartmentCommand(this, apartmentManager));
                }
            }
        }

        // Enregistrer les commandes globales (null-check pour éviter NPE si absents du plugin.yml)
        if (getCommand("world") != null) getCommand("world").setExecutor(new WorldCommand(this, worldManager));
        if (getCommand("build") != null) getCommand("build").setExecutor(new net.gameocean.core.commands.BuildCommand(this));
        if (getCommand("accept") != null) getCommand("accept").setExecutor(new net.gameocean.core.commands.AcceptCommand(this));
        if (getCommand("invite") != null) getCommand("invite").setExecutor(new net.gameocean.core.commands.InviteCommand(this));
        
        // Initialiser Bungee messaging
        bungeeMessaging = new net.gameocean.core.utils.BungeeMessaging(this);
        
        log.info(" Commandes globales (/world, /build, /accept, /invite) enregistrees.");

        // Enregistrer les listeners
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        
        // Enregistrer les listeners et commandes spécifiques au mode LOBBY
        if ("LOBBY".equalsIgnoreCase(serverType)) {
            DamageListener damageListener = new DamageListener(this);
            getServer().getPluginManager().registerEvents(damageListener, this);
            damageListener.startVoidCheck(); // Démarrer la vérification du vide
            getCommand("setspawn").setExecutor(new SetSpawnCommand(this));
            getCommand("decline").setExecutor(new DeclineCommand(this));
        }
        
        log.info("=========================================");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        if (redisManager != null) {
            redisManager.disconnect();
        }
        if (log != null) {
            log.info("GameOcean Core desactive.");
        }
    }

    private void loadConfigVariables() {
        serverType = getConfig().getString("server-type", "LOBBY");
        minigameType = getConfig().getString("minigame-type", "NONE");
        serverId = getConfig().getString("server-id", "unknown-server");
    }

    private boolean setupPermissions() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            return false;
        }
        perms = rsp.getProvider();
        return perms != null;
    }

    private boolean setupChat() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp == null) {
            return false;
        }
        chat = rsp.getProvider();
        return chat != null;
    }

    public String getServerType() {
        return serverType;
    }

    public String getMinigameType() {
        return minigameType;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String newId) {
        if (!newId.equals(this.serverId)) {
            this.serverId = newId;
            getConfig().set("server-id", newId);
            saveConfig();
        }
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public TermsManager getTermsManager() {
        return termsManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public net.gameocean.core.managers.ChatNameTagManager getNameTagManager() {
        return chatNameTagManager;
    }

    public net.gameocean.core.managers.MenuManager getMenuManager() {
        return menuManager;
    }
    
    public net.gameocean.core.utils.BungeeServerCheck getBungeeServerCheck() {
        return bungeeServerCheck;
    }

    public net.gameocean.core.utils.BungeeMessaging getBungeeMessaging() {
        return bungeeMessaging;
    }

    public net.gameocean.core.minigames.bedwars.BedwarsManager getBedwarsManager() {
        return bedwarsManager;
    }

    public net.gameocean.core.minigames.apartment.ApartmentManager getApartmentManager() {
        return apartmentManager;
    }

    public net.gameocean.core.managers.WorldManager getWorldManager() {
        return worldManager;
    }

    public java.util.Set<java.util.UUID> getLobbyBuilders() {
        return lobbyBuilders;
    }
}
