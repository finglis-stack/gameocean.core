package net.gameocean.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.gameocean.core.GameOceanCore;

import java.sql.*;
import java.util.logging.Level;

/**
 * Gestionnaire de base de données MySQL utilisant HikariCP (connection pool).
 *
 * Avantages par rapport à une connexion unique :
 *  - Thread-safe : chaque requête prend une connexion du pool, l'utilise, et la rend.
 *  - Reconnexion automatique : HikariCP détecte les connexions mortes et en crée de nouvelles.
 *  - Performance : pas besoin de recréer une connexion à chaque requête.
 *  - Concurrence : plusieurs threads async peuvent interroger la DB simultanément sans conflit.
 */
public class DatabaseManager {

    private final GameOceanCore plugin;
    private HikariDataSource dataSource;

    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean useSSL;
    private boolean enabled;

    public DatabaseManager(GameOceanCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        enabled  = plugin.getConfig().getBoolean("database.enabled", true);
        host     = plugin.getConfig().getString("database.host", "localhost");
        port     = plugin.getConfig().getInt("database.port", 3306);
        database = plugin.getConfig().getString("database.database", "gameocean");
        username = plugin.getConfig().getString("database.username", "root");
        password = plugin.getConfig().getString("database.password", "password");
        useSSL   = plugin.getConfig().getBoolean("database.useSSL", false);
    }

    public boolean connect() {
        if (!enabled) {
            plugin.getLogger().info("Base de données désactivée dans la configuration.");
            return false;
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSSL
                    + "&characterEncoding=utf8"
                    + "&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Pool : taille recommandée pour un plugin Minecraft
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10_000);   // 10s max pour obtenir une connexion du pool
            config.setIdleTimeout(600_000);        // 10min avant de fermer une connexion inactive
            config.setMaxLifetime(1_800_000);      // 30min max de vie d'une connexion
            config.setKeepaliveTime(60_000);       // Ping toutes les 1min pour garder les connexions vivantes
            config.setConnectionTestQuery("SELECT 1"); // Query de test de connexion

            // Nom du pool pour les logs
            config.setPoolName("GameOcean-Pool");

            dataSource = new HikariDataSource(config);

            // Test: prendre une connexion pour vérifier que ça marche
            try (Connection testConn = dataSource.getConnection()) {
                plugin.getLogger().info("Connexion au pool MySQL (HikariCP) réussie !");
            }

            createTables();
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur de connexion à la base de données: " + e.getMessage(), e);
            return false;
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Pool de connexions MySQL fermé.");
        }
    }

    /**
     * Retourne une connexion du pool.
     * IMPORTANT : toujours utiliser dans un bloc try-with-resources pour la rendre au pool !
     *
     * Exemple :
     *   try (Connection conn = getDatabaseManager().getConnection();
     *        PreparedStatement stmt = conn.prepareStatement(sql)) { ... }
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Le pool HikariCP n'est pas initialisé ou est fermé.");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void createTables() {
        String createProfilesTable =
                "CREATE TABLE IF NOT EXISTS profiles (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "username VARCHAR(16) NOT NULL," +
                "terms_accepted BOOLEAN DEFAULT FALSE," +
                "level INT DEFAULT 1," +
                "current_apartment VARCHAR(64) DEFAULT 'default'," +
                "apartment_intro_seen BOOLEAN DEFAULT FALSE," +
                "apartment_is_public BOOLEAN DEFAULT FALSE," +
                "apartment_offline_access BOOLEAN DEFAULT FALSE," +
                "friend_announcements BOOLEAN DEFAULT TRUE," +
                "friend_popup_requests BOOLEAN DEFAULT TRUE," +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createDecorationsTable =
                "CREATE TABLE IF NOT EXISTS apartment_decorations (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL," +
                "apartment_id VARCHAR(64) NOT NULL," +
                "dec_type VARCHAR(64) NOT NULL," +
                "material VARCHAR(64) NOT NULL," +
                "x DOUBLE NOT NULL," +
                "y DOUBLE NOT NULL," +
                "z DOUBLE NOT NULL," +
                "yaw FLOAT DEFAULT 0," +
                "pitch FLOAT DEFAULT 0," +
                "extra_data TEXT," +
                "INDEX(uuid, apartment_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createInventoryTable =
                "CREATE TABLE IF NOT EXISTS apartment_inventory (" +
                "uuid VARCHAR(36) NOT NULL," +
                "item_id VARCHAR(64) NOT NULL," +
                "amount INT DEFAULT 1," +
                "PRIMARY KEY (uuid, item_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createBansTable =
                "CREATE TABLE IF NOT EXISTS apartment_bans (" +
                "apartment_owner VARCHAR(36) NOT NULL," +
                "banned_uuid VARCHAR(36) NOT NULL," +
                "banned_name VARCHAR(16) NOT NULL," +
                "PRIMARY KEY (apartment_owner, banned_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createFriendsTable =
                "CREATE TABLE IF NOT EXISTS friends (" +
                "uuid_1 VARCHAR(36) NOT NULL," +
                "uuid_2 VARCHAR(36) NOT NULL," +
                "status ENUM('PENDING', 'ACCEPTED') DEFAULT 'PENDING'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid_1, uuid_2)," +
                "INDEX(uuid_1)," +
                "INDEX(uuid_2)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // Listes des ALTER TABLE pour ajouter les colonnes manquantes si la table existe déjà
        String[] alterQueries = {
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS level INT DEFAULT 1",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS current_apartment VARCHAR(64) DEFAULT 'default'",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS apartment_intro_seen BOOLEAN DEFAULT FALSE",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS apartment_is_public BOOLEAN DEFAULT FALSE",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS apartment_offline_access BOOLEAN DEFAULT FALSE",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS friend_announcements BOOLEAN DEFAULT TRUE",
            "ALTER TABLE profiles ADD COLUMN IF NOT EXISTS friend_popup_requests BOOLEAN DEFAULT TRUE",
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createProfilesTable);
            stmt.execute(createDecorationsTable);
            stmt.execute(createInventoryTable);
            stmt.execute(createBansTable);
            stmt.execute(createFriendsTable);

            // Migrations gracieuses : ignorer si la colonne existe déjà
            for (String alter : alterQueries) {
                try {
                    stmt.execute(alter);
                } catch (SQLException ignored) {
                    // MySQL < 8 ne supporte pas IF NOT EXISTS sur ALTER — on ignore
                }
            }

            plugin.getLogger().info("Tables MySQL vérifiées/créées avec succès.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création des tables: " + e.getMessage(), e);
        }
    }
}
