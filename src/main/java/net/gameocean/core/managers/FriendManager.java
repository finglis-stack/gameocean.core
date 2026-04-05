package net.gameocean.core.managers;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class FriendManager {

    private final GameOceanCore plugin;

    public FriendManager(GameOceanCore plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> sendFriendRequest(UUID sender, UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            if (!plugin.getDatabaseManager().isConnected()) return false;
            String sql = "INSERT INTO friends (uuid_1, uuid_2, status) VALUES (?, ?, 'PENDING') ON DUPLICATE KEY UPDATE status = 'PENDING'";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sender.toString());
                stmt.setString(2, receiver.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur sendFriendRequest", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> acceptFriendRequest(UUID sender, UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            if (!plugin.getDatabaseManager().isConnected()) return false;
            String sql = "UPDATE friends SET status = 'ACCEPTED' WHERE uuid_1 = ? AND uuid_2 = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sender.toString());
                stmt.setString(2, receiver.toString());
                if (stmt.executeUpdate() > 0) {
                    // Créer la relation inverse
                    String sqlReverse = "INSERT INTO friends (uuid_1, uuid_2, status) VALUES (?, ?, 'ACCEPTED') ON DUPLICATE KEY UPDATE status = 'ACCEPTED'";
                    try (PreparedStatement stmtRev = conn.prepareStatement(sqlReverse)) {
                        stmtRev.setString(1, receiver.toString());
                        stmtRev.setString(2, sender.toString());
                        stmtRev.executeUpdate();
                    }
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur acceptFriendRequest", e);
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> removeOrDenyFriend(UUID u1, UUID u2) {
        return CompletableFuture.supplyAsync(() -> {
            if (!plugin.getDatabaseManager().isConnected()) return false;
            String sql = "DELETE FROM friends WHERE (uuid_1 = ? AND uuid_2 = ?) OR (uuid_1 = ? AND uuid_2 = ?)";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, u1.toString());
                stmt.setString(2, u2.toString());
                stmt.setString(3, u2.toString());
                stmt.setString(4, u1.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur removeOrDenyFriend", e);
                return false;
            }
        });
    }

    public CompletableFuture<Map<UUID, String>> getFriends(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, String> friends = new HashMap<>();
            if (!plugin.getDatabaseManager().isConnected()) return friends;

            String sql = "SELECT f.uuid_2, p.username FROM friends f JOIN profiles p ON f.uuid_2 = p.uuid WHERE f.uuid_1 = ? AND f.status = 'ACCEPTED'";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        friends.put(UUID.fromString(rs.getString("uuid_2")), rs.getString("username"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur getFriends", e);
            }
            return friends;
        });
    }

    public CompletableFuture<List<String>> getPendingRequests(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> requests = new ArrayList<>();
            if (!plugin.getDatabaseManager().isConnected()) return requests;

            // PENDING sont stockés avec uuid_1 = sender, uuid_2 = receiver.
            String sql = "SELECT p.username FROM friends f JOIN profiles p ON f.uuid_1 = p.uuid WHERE f.uuid_2 = ? AND f.status = 'PENDING'";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        requests.add(rs.getString("username"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur getPendingRequests", e);
            }
            return requests;
        });
    }

    public CompletableFuture<Boolean> areFriends(UUID u1, UUID u2) {
        return CompletableFuture.supplyAsync(() -> {
            if (!plugin.getDatabaseManager().isConnected()) return false;
            String sql = "SELECT 1 FROM friends WHERE uuid_1 = ? AND uuid_2 = ? AND status = 'ACCEPTED'";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, u1.toString());
                stmt.setString(2, u2.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur areFriends", e);
            }
            return false;
        });
    }

    // Récupérer le UUID d'un joueur par son nom depuis la DB (car il peut être hors ligne)
    public CompletableFuture<UUID> getUUIDByName(String username) {
        return CompletableFuture.supplyAsync(() -> {
            if (!plugin.getDatabaseManager().isConnected()) return null;
            String sql = "SELECT uuid FROM profiles WHERE username = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return java.util.UUID.fromString(rs.getString("uuid"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sur getUUIDByName", e);
            }
            return null;
        });
    }
}
