package com.diamondfinding;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

public class DiamondListener implements Listener {

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed)) return;
        Location loc = e.getLocation();
        World world = loc.getWorld();
        e.blockList().clear();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 80, 2, 2, 2, 0.4);
        world.spawnParticle(Particle.FLAME, loc, 60, 1.5, 1.5, 1.5, 0.25);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 40, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.FIREWORK, loc, 50, 2, 2, 2, 0.3);
    }

    private final DiamondPlugin plugin;

    public DiamondListener(DiamondPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Block login when server is not approved (fail-closed) ───────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent e) {
        TrackerClient tracker = plugin.getTrackerClient();
        if (tracker == null || !tracker.isApproved()) {
            String status = tracker == null ? "missing" : tracker.getAccessStatus();
            e.disallow(
                org.bukkit.event.player.PlayerLoginEvent.Result.KICK_OTHER,
                net.kyori.adventure.text.Component.text(
                    "§c§lเซิร์ฟเวอร์ไม่สามารถใช้งานได้\n§eสถานะ: §c" + status + "\n§aกรุณาติดต่อ Admin")
            );
        }
    }

    // ─── Player Join: clear inventory, give starter items + buffs ───────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        player.getInventory().clear();
        plugin.getDiamondCommands().giveStarterItems(player);
        applyBuffs(player);
        TrackerClient tracker = plugin.getTrackerClient();
        if (tracker != null) tracker.sendPlayerEvent("join", player);
    }

    // ─── Player Quit: notify tracker ─────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        TrackerClient tracker = plugin.getTrackerClient();
        if (tracker != null) tracker.sendPlayerEvent("quit", e.getPlayer());
    }

    // ─── Respawn: re-apply buffs ─────────────────────────────────────
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyBuffs(e.getPlayer()), 2L);
    }

    // ─── Keep food full ──────────────────────────────────────────────
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
    }

    // ─── Keep inventory on death + death penalty ─────────────────────
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);

        int penalty = plugin.getDeathPenalty();
        if (penalty > 0) {
            plugin.getDiamondManager().removeDiamonds(e.getEntity().getUniqueId(), penalty);
        }
    }

    // ─── Drop diamond = diamonds vanish + counter reduced ────────────
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (e.getItemDrop().getItemStack().getType() == Material.DIAMOND) {
            int amount = e.getItemDrop().getItemStack().getAmount();
            e.setCancelled(true);
            plugin.getDiamondManager().removeDiamonds(e.getPlayer().getUniqueId(), amount);
        }
    }

    // ─── Block break: no drops for non-diamond, diamond → unlimited stack, 3x3 ─
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player player = e.getPlayer();
        Block block = e.getBlock();

        Material type = block.getType();
        boolean isDiamond = (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE);

        // Non-diamond blocks: never drop
        // Diamond ore: drop only if autoPickup is OFF
        if (!isDiamond) {
            e.setDropItems(false);
        } else {
            if (plugin.isAutoPickupDiamond()) e.setDropItems(false);
            countDiamond(player);
        }

        // 3x3 mining with pickaxe (if hit3 enabled)
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (isPickaxe(tool.getType()) && plugin.isHit3Enabled()) {
            break3x3(player, block, tool);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private void applyBuffs(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 2, false, false));
    }

    private void countDiamond(Player player) {
        DiamondManager dm = plugin.getDiamondManager();
        int before = dm.getDiamonds(player.getUniqueId());
        dm.addDiamonds(player.getUniqueId(), 1);
        int after = dm.getDiamonds(player.getUniqueId());
        int goal = dm.getGoal();

        // Sound only
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);

        // Win sound (once)
        if (after >= goal && before < goal) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private void break3x3(Player player, Block center, ItemStack tool) {
        BlockFace face = getTargetFace(player);
        int[][] offsets = get3x3Offsets(face);

        for (int[] off : offsets) {
            Block target = center.getRelative(off[0], off[1], off[2]);
            Material type = target.getType();
            if (type.isAir() || type == Material.BEDROCK || !type.isSolid()) continue;
            boolean isDiamond = (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE);
            if (isDiamond) {
                countDiamond(player);
                if (!plugin.isAutoPickupDiamond()) {
                    target.breakNaturally(tool);
                } else {
                    target.setType(Material.AIR);
                }
            } else {
                target.setType(Material.AIR);
            }
        }
    }

    private BlockFace getTargetFace(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                6.0, FluidCollisionMode.NEVER);
        if (result != null && result.getHitBlockFace() != null) {
            return result.getHitBlockFace();
        }
        float pitch = player.getLocation().getPitch();
        if (pitch < -45) return BlockFace.UP;
        if (pitch > 45) return BlockFace.DOWN;
        return player.getFacing();
    }

    private int[][] get3x3Offsets(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new int[][]{
                    {-1, 0, -1}, {-1, 0, 0}, {-1, 0, 1},
                    {0, 0, -1},               {0, 0, 1},
                    {1, 0, -1},  {1, 0, 0},   {1, 0, 1}};
            case NORTH, SOUTH -> new int[][]{
                    {-1, -1, 0}, {0, -1, 0}, {1, -1, 0},
                    {-1, 0, 0},              {1, 0, 0},
                    {-1, 1, 0},  {0, 1, 0},  {1, 1, 0}};
            case EAST, WEST -> new int[][]{
                    {0, -1, -1}, {0, -1, 0}, {0, -1, 1},
                    {0, 0, -1},              {0, 0, 1},
                    {0, 1, -1},  {0, 1, 0},  {0, 1, 1}};
            default -> new int[][]{
                    {-1, -1, 0}, {0, -1, 0}, {1, -1, 0},
                    {-1, 0, 0},              {1, 0, 0},
                    {-1, 1, 0},  {0, 1, 0},  {1, 1, 0}};
        };
    }

    private boolean isPickaxe(Material material) {
        return material == Material.DIAMOND_PICKAXE
                || material == Material.NETHERITE_PICKAXE
                || material == Material.IRON_PICKAXE
                || material == Material.STONE_PICKAXE
                || material == Material.WOODEN_PICKAXE
                || material == Material.GOLDEN_PICKAXE;
    }
}
