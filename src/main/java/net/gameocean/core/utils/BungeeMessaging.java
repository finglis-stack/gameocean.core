package net.gameocean.core.utils;

import net.gameocean.core.GameOceanCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class BungeeMessaging implements PluginMessageListener {

    private final GameOceanCore plugin;
    // Callbacks for GetServer replies: PlayerUUID -> Callback
    private final Map<UUID, Consumer<String>> serverCallbacks = new java.util.concurrent.ConcurrentHashMap<>();

    public BungeeMessaging(GameOceanCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
    }

    /**
     * Request the BungeeCord/Velocity server name for the current Bukkit instance.
     * The callback is executed once the proxy replies.
     */
    public void requestServerName(Player player, Consumer<String> callback) {
        serverCallbacks.put(player.getUniqueId(), callback);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("GetServer");
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la requete BungeeCord GetServer: " + e.getMessage());
            serverCallbacks.remove(player.getUniqueId());
        }
    }

    /**
     * Connect a player to a specific BungeeCord/Velocity server.
     */
    public void connectPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la tentative de connexion au serveur " + serverName + ": " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subchannel = in.readUTF();

            if (subchannel.equals("GetServer")) {
                String serverName = in.readUTF();
                Consumer<String> callback = serverCallbacks.remove(player.getUniqueId());
                if (callback != null) {
                    // Run the callback symmetrically to bukkit thread
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(serverName));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de onPluginMessageReceived BungeeCord: " + e.getMessage());
        }
    }
}
