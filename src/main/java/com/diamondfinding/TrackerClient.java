package com.diamondfinding;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hardened tracker client (v2 — HMAC signed, fail-closed).
 * See SecurityUtils and SECURITY_GUIDE.md in MBBRPlugin for full design notes.
 */
public class TrackerClient {

    private static final String PLUGIN_NAME = "DiamondFinding";

    private final DiamondPlugin plugin;
    private final String machineUuid;
    private final HttpClient http;
    private final String jarHash;

    private volatile String accessStatus = "unknown"; // fail-closed default
    private BukkitTask heartbeatTask;

    public TrackerClient(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.machineUuid = loadOrCreateUuid();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.jarHash = SecurityUtils.selfJarSha256();
        plugin.getLogger().info("[Tracker] Machine UUID: " + machineUuid);
        plugin.getLogger().info("[Tracker] Integrity hash: " + jarHash);
    }

    public boolean isEnabled() { return true; }
    public boolean isApproved() { return "approved".equals(accessStatus); }
    public String getAccessStatus() { return accessStatus; }
    public String getMachineUuid() { return machineUuid; }

    private String loadOrCreateUuid() {
        File idFile = new File(plugin.getDataFolder(), "machine-id");
        if (idFile.exists()) {
            try {
                String stored = Files.readString(idFile.toPath()).trim();
                if (!stored.isEmpty()) return stored;
            } catch (IOException e) {
                plugin.getLogger().warning("[Tracker] Failed to read machine-id: " + e.getMessage());
            }
        }
        String newUuid = UUID.randomUUID().toString();
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(idFile.toPath(), newUuid);
        } catch (IOException e) {
            plugin.getLogger().warning("[Tracker] Failed to save machine-id: " + e.getMessage());
        }
        return newUuid;
    }

    // ─── Ping ────────────────────────────────────────────────────────────────
    public void pingAsync(String event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String hostname;
                try { hostname = java.net.InetAddress.getLocalHost().getHostName(); }
                catch (Exception e) { hostname = Bukkit.getServer().getName(); }

                Object[] online = Bukkit.getOnlinePlayers().toArray();
                StringBuilder playersList = new StringBuilder("[");
                for (int i = 0; i < online.length; i++) {
                    Player p = (Player) online[i];
                    playersList.append(String.format("{\"name\":\"%s\",\"uuid\":\"%s\"}",
                            escape(p.getName()), p.getUniqueId()));
                    if (i < online.length - 1) playersList.append(",");
                }
                playersList.append("]");

                String json = "{" +
                        "\"uuid\":\"" + machineUuid + "\"," +
                        "\"event\":\"" + event + "\"," +
                        "\"server\":\"" + escape(hostname) + "\"," +
                        "\"mc_version\":\"" + Bukkit.getMinecraftVersion() + "\"," +
                        "\"plugin_version\":\"" + plugin.getPluginMeta().getVersion() + "\"," +
                        "\"plugin_name\":\"" + PLUGIN_NAME + "\"," +
                        "\"jar_hash\":\"" + jarHash + "\"," +
                        "\"players\":" + online.length + "," +
                        "\"players_list\":" + playersList + "," +
                        "\"timestamp\":\"" + Instant.now() + "\"" +
                        "}";

                HttpResponse<String> res = SecurityUtils.signedPost(http, "/ping", json, 10);
                boolean sigOk = SecurityUtils.verifyResponse(res);
                String body = res.body();
                plugin.getLogger().info("[Tracker] Ping response (" + res.statusCode()
                        + ", sig=" + (sigOk ? "ok" : "BAD") + "): " + body);

                if (!sigOk || res.statusCode() != 200) {
                    // Untrusted response — revoke approval
                    if ("approved".equals(accessStatus)) {
                        plugin.getLogger().warning("[Tracker] Signature invalid — revoking approval");
                        accessStatus = "unknown";
                    }
                    return;
                }
                Matcher m = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                if (m.find()) {
                    String prev = accessStatus;
                    accessStatus = m.group(1);
                    if (!prev.equals(accessStatus)) {
                        plugin.getLogger().info("[Tracker] Access status changed: " + prev + " → " + accessStatus);
                    }
                }
                // React to banned / expired by kicking
                if ("banned".equals(accessStatus) || "expired".equals(accessStatus)) {
                    final String finalStatus = accessStatus;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player op : Bukkit.getOnlinePlayers()) {
                            op.kick(net.kyori.adventure.text.Component.text(
                                    "§c§lเซิร์ฟเวอร์ไม่สามารถใช้งานได้\n§eสถานะ: §c" + finalStatus + "\n§aกรุณาติดต่อ Admin"));
                        }
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Tracker] Ping failed: " + e.getMessage());
                // Network failure — don't auto-revoke, but also don't auto-approve
            }
        });
    }

    public void startHeartbeat() {
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, () -> pingAsync("heartbeat"), 6000L, 6000L);
        // Poll commands every 30s
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollCommands, 600L, 600L);
    }

    // ─── Player events ───────────────────────────────────────────────────────
    public void sendPlayerEvent(String eventType, Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String ip = "";
                try {
                    if (player.getAddress() != null)
                        ip = player.getAddress().getAddress().getHostAddress();
                } catch (Exception ignored) {}

                String json = "{" +
                        "\"uuid\":\"" + machineUuid + "\"," +
                        "\"event\":\"" + eventType + "\"," +
                        "\"player_name\":\"" + escape(player.getName()) + "\"," +
                        "\"player_uuid\":\"" + player.getUniqueId() + "\"," +
                        "\"player_ip\":\"" + ip + "\"," +
                        "\"timestamp\":\"" + Instant.now() + "\"" +
                        "}";
                SecurityUtils.signedPost(http, "/player-event", json, 10);
            } catch (Exception e) {
                plugin.getLogger().warning("[Tracker] Player event failed: " + e.getMessage());
            }
        });
    }

    // ─── Command polling ─────────────────────────────────────────────────────
    public void pollCommands() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpResponse<String> res = SecurityUtils.signedGet(http,
                        "/commands/pending?uuid=" + machineUuid, 10);
                if (res.statusCode() != 200) return;
                if (!SecurityUtils.verifyResponse(res)) {
                    plugin.getLogger().warning("[Tracker] Command poll signature invalid — dropping.");
                    return;
                }
                processCommands(res.body());
            } catch (Exception ignored) {}
        });
    }

    private void processCommands(String body) {
        try {
            List<Integer> ids = new ArrayList<>();
            List<String>  cmds = new ArrayList<>();
            Matcher m = Pattern.compile(
                    "\"id\"\\s*:\\s*(\\d+).*?\"command\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                    Pattern.DOTALL).matcher(body);
            while (m.find()) {
                ids.add(Integer.parseInt(m.group(1)));
                cmds.add(m.group(2));
            }
            if (ids.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String c : cmds) {
                    plugin.getLogger().info("[Tracker] Executing: " + c);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                }
                ackCommands(ids);
            });
        } catch (Exception e) {
            plugin.getLogger().warning("[Tracker] processCommands: " + e.getMessage());
        }
    }

    private void ackCommands(List<Integer> ids) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String idsJson = ids.toString().replace(" ", "");
                SecurityUtils.signedPost(http, "/commands/ack", "{\"ids\":" + idsJson + "}", 10);
            } catch (Exception e) {
                plugin.getLogger().warning("[Tracker] ack failed: " + e.getMessage());
            }
        });
    }

    public void stop() {
        if (heartbeatTask != null) heartbeatTask.cancel();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
