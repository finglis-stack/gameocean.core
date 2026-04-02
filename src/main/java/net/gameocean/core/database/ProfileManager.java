package net.gameocean.core.database;

import net.gameocean.core.GameOceanCore;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class ProfileManager {

    private final GameOceanCore plugin;
    private final DatabaseManager databaseManager;

    public ProfileManager(GameOceanCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Récupère ou crée un profil joueur. Doit être appelé depuis un thread ASYNC.
     */
    public PlayerProfile getOrCreateProfile(Player player) {
        if (!databaseManager.isConnected()) return null;

        UUID uuid = player.getUniqueId();
        String username = player.getName();

        PlayerProfile profile = getProfile(uuid);

        if (profile != null) {
            if (!profile.getUsername().equals(username)) {
                updateUsername(uuid, username);
                profile.setUsername(username);
            }
            updateLastLogin(uuid);
            return profile;
        }

        return createProfile(uuid, username);
    }

    /**
     * Récupère un profil par UUID. Doit être appelé depuis un thread ASYNC.
     */
    public PlayerProfile getProfile(UUID uuid) {
        if (!databaseManager.isConnected()) return null;

        String sql = "SELECT * FROM profiles WHERE uuid = ?";
        // ✅ HikariCP : la connexion ET le statement sont dans try-with-resources → rendus au pool automatiquement
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String apartment = null;
                    try { apartment = rs.getString("current_apartment"); } catch (SQLException ignored) {}
                    if (apartment == null) apartment = "default";

                    boolean introSeen = false;
                    try { introSeen = rs.getInt("apartment_intro_seen") == 1 || rs.getBoolean("apartment_intro_seen"); } catch (SQLException ignored) {}

                    boolean isPublic = false;
                    try { isPublic = rs.getInt("apartment_is_public") == 1 || rs.getBoolean("apartment_is_public"); } catch (SQLException ignored) {}

                    boolean offlineAccess = false;
                    try { offlineAccess = rs.getInt("apartment_offline_access") == 1 || rs.getBoolean("apartment_offline_access"); } catch (SQLException ignored) {}

                    return new PlayerProfile(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getInt("terms_accepted") == 1 || rs.getBoolean("terms_accepted"),
                            rs.getInt("level"),
                            apartment,
                            introSeen,
                            isPublic,
                            offlineAccess,
                            rs.getTimestamp("last_login"),
                            rs.getTimestamp("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la récupération du profil: " + e.getMessage(), e);
        }
        return null;
    }

    private PlayerProfile createProfile(UUID uuid, String username) {
        if (!databaseManager.isConnected()) return null;

        String sql = "INSERT INTO profiles (uuid, username, terms_accepted, level, current_apartment, apartment_intro_seen, apartment_is_public, apartment_offline_access) " +
                     "VALUES (?, ?, FALSE, 1, 'default', FALSE, FALSE, FALSE)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
            plugin.getLogger().info("Nouveau profil créé pour: " + username);
            return new PlayerProfile(uuid, username, false, 1, "default", false, false, false, null, null);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création du profil: " + e.getMessage(), e);
        }
        return null;
    }

    private void updateUsername(UUID uuid, String username) {
        if (!databaseManager.isConnected()) return;
        String sql = "UPDATE profiles SET username = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour du pseudo: " + e.getMessage(), e);
        }
    }

    private void updateLastLogin(UUID uuid) {
        if (!databaseManager.isConnected()) return;
        String sql = "UPDATE profiles SET last_login = CURRENT_TIMESTAMP WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour de la date: " + e.getMessage(), e);
        }
    }

    public boolean acceptTerms(UUID uuid) {
        if (!databaseManager.isConnected()) return false;
        String sql = "UPDATE profiles SET terms_accepted = TRUE WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'acceptation des termes: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean updateLevel(UUID uuid, int newLevel) {
        if (!databaseManager.isConnected()) return false;
        String sql = "UPDATE profiles SET level = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newLevel);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour du niveau: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean setApartmentIntroSeen(UUID uuid) {
        if (!databaseManager.isConnected()) return false;
        String sql = "UPDATE profiles SET apartment_intro_seen = TRUE WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour de l'intro appartement: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean setApartmentIsPublic(UUID uuid, boolean isPublic) {
        if (!databaseManager.isConnected()) return false;
        String sql = "UPDATE profiles SET apartment_is_public = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isPublic);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour de la visibilité: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean updateCurrentApartment(UUID uuid, String apartmentId) {
        if (!databaseManager.isConnected()) return false;
        String sql = "UPDATE profiles SET current_apartment = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, apartmentId);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour de l'appartement: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean hasAcceptedTerms(UUID uuid) {
        PlayerProfile profile = getProfile(uuid);
        return profile != null && profile.hasAcceptedTerms();
    }

    public boolean setApartmentOfflineAccess(UUID uuid, boolean offlineAccess) {
        if (!databaseManager.isConnected()) return false;
        String sql = "UPDATE profiles SET apartment_offline_access = ? WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, offlineAccess);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la mise à jour de l'accès hors-ligne", e);
        }
        return false;
    }
}
