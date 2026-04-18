package com.diamondfinding;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DiamondCommands {

    private final DiamondPlugin plugin;
    private final DiamondManager dm;

    public DiamondCommands(DiamondPlugin plugin) {
        this.plugin = plugin;
        this.dm = plugin.getDiamondManager();
    }

    // ─── /mbdm add <amount> ──────────────────────────────────────────────
    public boolean add(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm add <amount>");
            return true;
        }
        int amount;
        try { amount = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§c[Diamond] Amount must be positive");
            return true;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            dm.addDiamonds(player.getUniqueId(), amount);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);

            if (dm.getDiamonds(player.getUniqueId()) >= dm.getGoal()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        return true;
    }

    // ─── /mbdm remove <amount> ───────────────────────────────────────────
    public boolean remove(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm remove <amount>");
            return true;
        }
        int amount;
        try { amount = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§c[Diamond] Amount must be positive");
            return true;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            dm.removeDiamonds(player.getUniqueId(), amount);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
        return true;
    }

    // ─── /mbdm scan [seconds] ────────────────────────────────────────────
    // X-ray: replace blocks with Glass, keep Diamond Ore, Lava, Bedrock visible
    public boolean scan(CommandSender sender, String[] args) {
        int duration = plugin.getConfig().getInt("scan.duration-seconds", 10);
        int radius = plugin.getConfig().getInt("scan.radius", 30);
        if (args.length >= 1) {
            try { duration = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        final int durationTicks = duration * 20;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location center = player.getLocation();
            World world = center.getWorld();
            Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();

            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();
            int yMin = Math.max(world.getMinHeight(), cy - radius);
            int yMax = Math.min(world.getMaxHeight() - 1, cy + radius);

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        Block block = world.getBlockAt(cx + x, y, cz + z);
                        Material type = block.getType();
                        // Keep: air, diamond ore, lava, bedrock
                        if (type.isAir()) continue;
                        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) continue;
                        if (type == Material.LAVA || type == Material.BEDROCK) continue;
                        if (type == Material.GLASS) continue;
                        originalBlocks.put(block.getLocation(), block.getBlockData().clone());
                        block.setType(Material.GLASS, false);
                    }
                }
            }

            if (originalBlocks.isEmpty()) continue;

            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);

            // Restore after duration
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                    Block b = entry.getKey().getBlock();
                    if (b.getType() == Material.GLASS) {
                        b.setBlockData(entry.getValue(), false);
                    }
                }
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1f);
                }
            }, durationTicks);
        }
        return true;
    }

    // ─── /mbdm surround [seconds] ───────────────────────────────────────
    // Temporarily turns surrounding blocks into Diamond Ore
    public boolean surround(CommandSender sender, String[] args) {
        int duration = plugin.getConfig().getInt("surround.duration-seconds", 5);
        int radius = plugin.getConfig().getInt("surround.radius", 75);
        if (args.length >= 1) {
            try { duration = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        final int durationTicks = duration * 20;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location center = player.getLocation();
            World world = center.getWorld();
            Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block block = world.getBlockAt(
                                center.getBlockX() + x,
                                center.getBlockY() + y,
                                center.getBlockZ() + z);
                        Material type = block.getType();
                        if (type != Material.AIR && type != Material.CAVE_AIR
                                && type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE
                                && type != Material.BEDROCK && type.isSolid()) {
                            originalBlocks.put(block.getLocation(), block.getBlockData().clone());
                            block.setType(Material.DIAMOND_ORE, false);
                        }
                    }
                }
            }

            if (originalBlocks.isEmpty()) continue;

            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 2f);

            // Restore after duration
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                    Block b = entry.getKey().getBlock();
                    if (b.getType() == Material.DIAMOND_ORE) {
                        b.setBlockData(entry.getValue(), false);
                    }
                }
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.2f);
            }, durationTicks);
        }
        return true;
    }

    // ─── /mbdm item ───────────────────────────────────────────────────────
    public boolean item(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                giveStarterItems(p);
                p.sendMessage("§a[Diamond] Starter items checked/given.");
            }
            sender.sendMessage("§a[Diamond] Starter items given to all online players.");
            return true;
        }
        giveStarterItems(player);
        player.sendMessage("§a[Diamond] Starter items checked/given.");
        return true;
    }

    // ─── Starter item logic ───────────────────────────────────────────────
    public void giveStarterItems(Player player) {
        if (!hasItemType(player, Material.NETHERITE_SWORD)) {
            ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta meta = sword.getItemMeta();
            meta.addEnchant(Enchantment.SHARPNESS, 4, true);
            sword.setItemMeta(meta);
            player.getInventory().addItem(sword);
        }
        if (!hasItemType(player, Material.DIAMOND_PICKAXE)) {
            ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta meta = pick.getItemMeta();
            meta.addEnchant(Enchantment.EFFICIENCY, 50, true);
            meta.setUnbreakable(true);
            pick.setItemMeta(meta);
            player.getInventory().addItem(pick);
        }
        if (!hasItemType(player, Material.SHIELD)) {
            ItemStack shield = new ItemStack(Material.SHIELD);
            ItemMeta meta = shield.getItemMeta();
            meta.addEnchant(Enchantment.UNBREAKING, 2, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            shield.setItemMeta(meta);
            player.getInventory().addItem(shield);
        }
    }

    private boolean hasItemType(Player player, Material material) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) return true;
        }
        return false;
    }

    // ─── /mbdm reset ─────────────────────────────────────────────────────
    public boolean reset(CommandSender sender) {
        plugin.stopCountdown();
        World spawnWorld = Bukkit.getWorlds().get(0);
        Location spawnLoc = spawnWorld.getSpawnLocation();
        int surfaceY = spawnWorld.getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ());
        Location surface = new Location(spawnWorld, spawnLoc.getX(), surfaceY + 1, spawnLoc.getZ(),
                spawnLoc.getYaw(), spawnLoc.getPitch());

        for (Player player : Bukkit.getOnlinePlayers()) {
            dm.setDiamonds(player.getUniqueId(), 0);
            player.teleport(surface);
            player.playSound(surface, Sound.ENTITY_PLAYER_DEATH, 1f, 0.7f);
        }
        sender.sendMessage("§a[Diamond] All player diamonds reset to 0 and teleported to spawn");
        return true;
    }

    // ─── /mbdm time <seconds> ────────────────────────────────────────────
    public boolean time(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm time <seconds>");
            return true;
        }
        int seconds;
        try { seconds = Integer.parseInt(args[0]); }
        catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        if (seconds <= 0) {
            sender.sendMessage("§c[Diamond] Seconds must be greater than 0");
            return true;
        }
        plugin.getConfig().set("countdown-seconds", seconds);
        plugin.saveConfig();
        sender.sendMessage("§a[Diamond] Countdown duration set to §e" + seconds + " §aวินาที");
        return true;
    }

    // ─── /mbdm setgoal <amount> ──────────────────────────────────────────
    public boolean setGoal(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm setgoal <amount>");
            return true;
        }
        int goal;
        try { goal = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        plugin.getConfig().set("goal", goal);
        plugin.saveConfig();
        sender.sendMessage("§a[Diamond] Goal set to §b" + goal + " §adiamonds");

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
        }
        return true;
    }

    // ─── /mbdm status ────────────────────────────────────────────────────
    public boolean status(CommandSender sender) {
        int goal = dm.getGoal();
        for (Player player : Bukkit.getOnlinePlayers()) {
            int current = dm.getDiamonds(player.getUniqueId());
            double pct = goal > 0 ? (current * 100.0 / goal) : 0;
            player.sendTitle(
                    "§b" + current + " §f/ §e" + goal + " §f💎",
                    String.format("§7%.1f%% complete", pct),
                    5, 40, 10
            );
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
        }
        return true;
    }

    // ─── /mbdm hit3 <on|off> ──────────────────────────────────────────
    public boolean hit3(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm hit3 <on|off>");
            return true;
        }
        boolean on = args[0].equalsIgnoreCase("on");
        plugin.setHit3Enabled(on);
        sender.sendMessage("§a[Diamond] 3x3 mining " + (on ? "§aON" : "§cOFF"));
        return true;
    }

    // ─── /mbdm drop <on|off> ──────────────────────────────────────────
    public boolean drop(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm drop <on|off>");
            return true;
        }
        boolean on = args[0].equalsIgnoreCase("on");
        plugin.setAutoPickupDiamond(on);
        sender.sendMessage("§a[Diamond] Auto-pickup diamond " + (on ? "§aON §7(no drop)" : "§cOFF §7(drop on ground)"));
        return true;
    }

    // ─── /mbdm die <number> ───────────────────────────────────────────
    public boolean die(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm die <number>");
            return true;
        }
        int penalty;
        try { penalty = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        plugin.setDeathPenalty(Math.max(0, penalty));
        sender.sendMessage("§a[Diamond] Death penalty set to §c-" + plugin.getDeathPenalty() + " §adiamonds");
        return true;
    }

    // ─── /mbdm dragon — cost 3000, Ender Dragon explosion ────────────
    public boolean dragon(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dm.removeDiamonds(player.getUniqueId(), 3000);

            Location loc = player.getLocation();
            World world = loc.getWorld();

            // Spawn dragon above
            EnderDragon dragon = world.spawn(loc.clone().add(0, 30, 0), EnderDragon.class);
            player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);

            // Dragon dives and explodes after 3s
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location target = player.getLocation();
                player.playSound(target, Sound.ENTITY_ENDER_DRAGON_SHOOT, 2f, 0.8f);
                world.createExplosion(target, 25f, false, true);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 5, 3, 3, 3);
                player.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                dragon.remove();
            }, 60L);
        }
        return true;
    }

    // ─── /mbdm warden — cost 1000, spawn Warden to attack ────────────
    public boolean warden(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dm.removeDiamonds(player.getUniqueId(), 1000);

            Location loc = player.getLocation().add(
                    player.getLocation().getDirection().multiply(3));
            loc.setY(player.getLocation().getY());
            World world = loc.getWorld();

            Warden warden = world.spawn(loc, Warden.class);
            warden.setTarget(player);
            player.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 2f, 1f);

            // Particle aura around warden
            final int[] taskId = {-1};
            taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (!warden.isValid()) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    return;
                }
                world.spawnParticle(Particle.SONIC_BOOM, warden.getLocation().add(0, 1, 0), 1);
            }, 0L, 20L);
        }
        return true;
    }

    // ─── /mbdm lava <number> — place random lava blocks within 1 block ──
    public boolean lava(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm lava <number>");
            return true;
        }
        int count;
        try { count = Integer.parseInt(args[0]); }
        catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        Random rng = new Random();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            int bx = player.getLocation().getBlockX();
            int by = player.getLocation().getBlockY();
            int bz = player.getLocation().getBlockZ();
            for (int i = 0; i < count; i++) {
                int dx, dy, dz;
                do {
                    dx = rng.nextInt(3) - 1;
                    dy = rng.nextInt(3) - 1;
                    dz = rng.nextInt(3) - 1;
                } while (dx == 0 && dy == 0 && dz == 0);
                world.getBlockAt(bx + dx, by + dy, bz + dz).setType(Material.LAVA);
            }
            world.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1f);
        }
        sender.sendMessage("§a[Diamond] Spawned §e" + count + " §alava blocks around each player");
        return true;
    }

    // ─── /mbdm tnt <number> — spawn primed TNT around players ────────
    public boolean tnt(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c[Diamond] Usage: /mbdm tnt <number>");
            return true;
        }
        int count;
        try { count = Integer.parseInt(args[0]); }
        catch (NumberFormatException e) {
            sender.sendMessage("§c[Diamond] Invalid number: " + args[0]);
            return true;
        }
        Random rng = new Random();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            Location base = player.getLocation();
            int bx = base.getBlockX();
            int by = base.getBlockY();
            int bz = base.getBlockZ();
            for (int i = 0; i < count; i++) {
                int dx = rng.nextInt(7) - 3;
                int dz = rng.nextInt(7) - 3;
                int spawnY = by;
                for (int y = by; y <= by + 20; y++) {
                    if (world.getBlockAt(bx + dx, y, bz + dz).getType().isAir()
                            && world.getBlockAt(bx + dx, y + 1, bz + dz).getType().isAir()) {
                        spawnY = y;
                        break;
                    }
                }
                Location tntLoc = new Location(world, bx + dx + 0.5, spawnY, bz + dz + 0.5);
                TNTPrimed tnt = world.spawn(tntLoc, TNTPrimed.class);
                tnt.setFuseTicks(20);
            }
            player.playSound(base, Sound.ENTITY_TNT_PRIMED, 1f, 1f);
        }
        sender.sendMessage("§a[Diamond] Spawned §e" + count + " §aprimed TNT around each player");
        return true;
    }

    // ─── /mbdm golem — gain 1000, spawn Iron Golem with aura ─────────
    public boolean golem(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation().add(
                    player.getLocation().getDirection().multiply(3));
            loc.setY(player.getLocation().getY());
            World world = loc.getWorld();

            Block spawnBlock = world.getBlockAt(loc);
            if (spawnBlock.getType() == Material.BEDROCK) {
                while (world.getBlockAt(loc).getType() == Material.BEDROCK) {
                    loc.setY(loc.getY() + 1);
                }
            } else {
                world.getBlockAt(loc).setType(Material.AIR);
                world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()).setType(Material.AIR);
            }

            IronGolem golem = world.spawn(loc, IronGolem.class);
            player.playSound(loc, Sound.ENTITY_IRON_GOLEM_REPAIR, 2f, 1.2f);
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

            // Aura particles then give diamonds
            final int[] taskId = {-1};
            final int[] ticks = {0};
            taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (ticks[0] >= 60 || !golem.isValid()) {
                    golem.remove();
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    // Add diamonds when golem vanishes
                    dm.addDiamonds(player.getUniqueId(), 1000);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    return;
                }
                world.spawnParticle(Particle.HAPPY_VILLAGER,
                        golem.getLocation().add(0, 1.5, 0), 15, 1.5, 1.5, 1.5);
                world.spawnParticle(Particle.END_ROD,
                        golem.getLocation().add(0, 2, 0), 5, 0.5, 1, 0.5, 0.02);
                ticks[0] += 5;
            }, 0L, 5L);
        }
        return true;
    }
}
