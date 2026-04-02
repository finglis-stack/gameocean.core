package net.gameocean.core.utils;

import net.gameocean.core.GameOceanCore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

public class BungeeServerCheck implements PluginMessageListener {

    private final GameOceanCore plugin;
    private final Map<String, Integer> serverCounts = new HashMap<>();
    private String[] serverList = new String[0];
    private Player lastRequester;

    public BungeeServerCheck(GameOceanCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
    }

    public void startProcess(Player requester) {
        this.lastRequester = requester;
        serverCounts.clear();
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("GetServers");
            requester.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {}
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subchannel = in.readUTF();

            if (subchannel.equals("GetServers")) {
                serverList = in.readUTF().split(", ");
                for (String server : serverList) {
                    if (server.startsWith("main-lb-")) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(b);
                        out.writeUTF("PlayerCount");
                        out.writeUTF(server);
                        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
                    }
                }
                
                // Si on a pas trouvé de serveur main-lb, fallback
                plugin.getServer().getScheduler().runTaskLater(plugin, this::evaluateAndConnect, 20L);
            } else if (subchannel.equals("PlayerCount")) {
                String server = in.readUTF();
                int count = in.readInt();
                if (server.startsWith("main-lb-")) {
                    serverCounts.put(server, count);
                }
            }
        } catch (Exception e) {}
    }

    private void evaluateAndConnect() {
        if (lastRequester == null || !lastRequester.isOnline()) return;

        String bestServer = "main-lb"; // Fallback groupe
        int minPlayers = Integer.MAX_VALUE;

        for (Map.Entry<String, Integer> entry : serverCounts.entrySet()) {
            if (entry.getValue() < minPlayers) {
                minPlayers = entry.getValue();
                bestServer = entry.getKey();
            }
        }

        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(bestServer);
            lastRequester.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            lastRequester.kickPlayer("§cRetour au Hub.");
        }
        
        lastRequester = null;
    }
}
