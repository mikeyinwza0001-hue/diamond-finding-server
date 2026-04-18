package com.diamondfinding;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class DiamondPlugin extends JavaPlugin {

    private static DiamondPlugin instance;
    private DiamondManager diamondManager;
    private OverlayServer overlayServer;
    private DiamondCommands diamondCommands;
    private TrackerClient trackerClient;

    // Runtime toggles
    private boolean hit3Enabled = true;
    private boolean autoPickupDiamond = true;
    private int deathPenalty = 0;

    // Countdown state
    private int countdownTask = -1;
    private int countdownRemaining = -1;
    private int countdownTotal = 60;
    private boolean dramaticPhase = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveResource("KOMIKAX_.ttf", false);

        // Initialize diamond manager
        diamondManager = new DiamondManager(this);
        diamondManager.load();

        // Initialize overlay server
        int port = getConfig().getInt("overlay-port", 6970);
        overlayServer = new OverlayServer(this, port);
        overlayServer.start();

        // Register commands
        diamondCommands = new DiamondCommands(this);
        MbdmCommand mbdmCmd = new MbdmCommand(diamondCommands);
        getCommand("mbdm").setExecutor(mbdmCmd);
        getCommand("mbdm").setTabCompleter(mbdmCmd);

        // Register listener
        Bukkit.getPluginManager().registerEvents(new DiamondListener(this), this);

        // Periodic diamond inventory sync + countdown check (every 0.5s)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                diamondManager.syncInventory(p);
            }
            maybeStartCountdown();
        }, 20L, 10L);

        // mabel-tracker integration
        trackerClient = new TrackerClient(this);
        if (trackerClient.isEnabled()) {
            trackerClient.pingAsync("startup");
            trackerClient.startHeartbeat();
            Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                trackerClient::pollCommands, 20 * 30L, 20 * 30L);
            getLogger().info("[Tracker] Connected to mabel-tracker: " + getConfig().getString("tracker-url"));
        }

        getLogger().info("DiamondFinding v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (trackerClient != null && trackerClient.isEnabled()) {
            trackerClient.pingAsync("shutdown");
            trackerClient.stop();
        }
        if (diamondManager != null) diamondManager.save();
        if (overlayServer != null) overlayServer.stop();
        getLogger().info("DiamondFinding disabled.");
    }

    public static DiamondPlugin getInstance() { return instance; }
    public DiamondManager getDiamondManager() { return diamondManager; }
    public OverlayServer getOverlayServer() { return overlayServer; }
    public DiamondCommands getDiamondCommands() { return diamondCommands; }
    public TrackerClient getTrackerClient() { return trackerClient; }

    public boolean isHit3Enabled() { return hit3Enabled; }
    public void setHit3Enabled(boolean v) { hit3Enabled = v; }
    public boolean isAutoPickupDiamond() { return autoPickupDiamond; }
    public void setAutoPickupDiamond(boolean v) { autoPickupDiamond = v; }
    public int getDeathPenalty() { return deathPenalty; }
    public void setDeathPenalty(int v) { deathPenalty = v; }
    public int getCountdownRemaining() { return countdownRemaining; }

    public void maybeStartCountdown() {
        int total = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            total += diamondManager.getDiamonds(p.getUniqueId());
        }

        if (total >= diamondManager.getGoal()) {
            if (countdownTask < 0 && !dramaticPhase) {
                countdownTotal = getConfig().getInt("countdown-seconds", 60);
                countdownRemaining = countdownTotal;
                countdownTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
                    if (countdownRemaining < 0) return;
                    if (countdownRemaining == 0) {
                        onCountdownEnd();
                        return;
                    }
                    if (countdownRemaining <= 3) {
                        int rem = countdownRemaining;
                        Bukkit.getScheduler().cancelTask(countdownTask);
                        countdownTask = -1;
                        startDramaticCountdown(rem);
                        return;
                    }
                    String color = countdownRemaining > countdownTotal * 2 / 3 ? "§a"
                            : countdownRemaining > countdownTotal / 3 ? "§e" : "§c";
                    Sound tickSound = countdownRemaining <= countdownTotal / 3
                            ? Sound.BLOCK_NOTE_BLOCK_BASS
                            : Sound.BLOCK_NOTE_BLOCK_HAT;
                    float tickPitch = countdownRemaining <= countdownTotal / 3 ? 1.5f : 1.0f;
                    String title = color + countdownRemaining;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(title, "§fจะชนะในอีก", 0, 25, 5);
                        p.playSound(p.getLocation(), tickSound, 1f, tickPitch);
                    }
                    countdownRemaining--;
                }, 0L, 20L);
            }
        } else {
            stopCountdown();
        }
    }

    private void onCountdownEnd() {
        if (countdownTask >= 0) {
            Bukkit.getScheduler().cancelTask(countdownTask);
            countdownTask = -1;
        }
        countdownRemaining = -1;
        dramaticPhase = false;

        // Reset diamonds IMMEDIATELY so maybeStartCountdown won't restart
        for (Player p : Bukkit.getOnlinePlayers()) {
            diamondManager.setDiamonds(p.getUniqueId(), 0);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§6§lชนะแล้ว!", "§fกำลังส่งไปเกิดใหม่...", 10, 80, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            World world = Bukkit.getWorlds().get(0);
            Location spawn = world.getSpawnLocation();
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int spread = 200;

            for (Player p : Bukkit.getOnlinePlayers()) {
                int rx = spawn.getBlockX() + rng.nextInt(-spread, spread);
                int rz = spawn.getBlockZ() + rng.nextInt(-spread, spread);
                int ry = world.getHighestBlockYAt(rx, rz) + 1;
                p.teleport(new Location(world, rx + 0.5, ry, rz + 0.5));
                p.clearTitle();
            }
        }, 100L);
    }

    private void startDramaticCountdown(int from) {
        dramaticPhase = true;
        long delay = 0;
        for (int n = from; n >= 1; n--) {
            final int num = n;
            final long d = delay;
            Bukkit.getScheduler().runTaskLater(this, () -> showDramatic(num), d);
            delay += (n == 1) ? 60L : 40L;
        }
        Bukkit.getScheduler().runTaskLater(this, this::onCountdownEnd, delay);
    }

    private void showDramatic(int num) {
        float pitch = num == 1 ? 0.5f : num == 2 ? 0.8f : 1.1f;
        int stay = num == 1 ? 65 : 45;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§c§l" + num, "§fจะชนะในอีก", 5, stay, 5);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2f, pitch);
        }
    }

    public void stopCountdown() {
        if (countdownTask >= 0) {
            Bukkit.getScheduler().cancelTask(countdownTask);
            countdownTask = -1;
        }
        if (countdownRemaining >= 0 || dramaticPhase) {
            countdownRemaining = -1;
            dramaticPhase = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.clearTitle();
            }
        }
    }
}
