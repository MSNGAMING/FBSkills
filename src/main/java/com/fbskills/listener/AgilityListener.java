package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event listener managing sprint pacing, movement stamina, and Agility skill progression.
 * Hardened against wall-running / macro farming.
 */
public class AgilityListener implements Listener {

    private final FBSkillsPlugin plugin;
    private final Map<UUID, Long> lastSprintMap = new ConcurrentHashMap<>();

    public AgilityListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Ignore pure pitch/yaw rotations
        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Prevent wall-running AFK exploits: verify actual horizontal distance moved
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if ((dx * dx + dz * dz) < 0.04) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSprinting() || player.isInsideVehicle() || player.isGliding()) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("cooldowns.agility_sprint_ms", 1500);
        Long lastSprint = lastSprintMap.get(player.getUniqueId());

        if (lastSprint == null || (now - lastSprint) >= cooldown) {
            lastSprintMap.put(player.getUniqueId(), now);
            double sprintXp = plugin.getConfigManager().getXpReward(SkillType.AGILITY, "SPRINT_TICK_BURST");
            if (sprintXp <= 0) sprintXp = 8.0;
            plugin.awardXp(player, SkillType.AGILITY, sprintXp);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        double damage = event.getDamage();
        PlayerProfile profile = plugin.getStorageManager().getProfile(player.getUniqueId());

        // Passive Perk: Roll / Kinetic dampening based on agility level
        if (profile != null && profile.getLevel(SkillType.AGILITY) >= 5) {
            double reductionPercent = Math.min(0.50, profile.getLevel(SkillType.AGILITY) * 0.005);
            event.setDamage(Math.max(0.5, damage * (1.0 - reductionPercent)));
        }

        double ratio = plugin.getConfigManager().getXpReward(SkillType.AGILITY, "FALL_DAMAGE_CUSHION_RATIO");
        if (ratio <= 0) ratio = 15.0;
        plugin.awardXp(player, SkillType.AGILITY, damage * ratio);
    }
}
