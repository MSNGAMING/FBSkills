package com.fbskills.listener;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance combat listener handling Fighting, Archery, and Defense skills.
 * Thread-safe across Folia regions (never performs cross-region entity lookups).
 */
public class CombatListener implements Listener {

    private final FBSkillsPlugin plugin;
    private final Map<UUID, Long> lastDefenseHit = new ConcurrentHashMap<>();

    private final NamespacedKey keyLaunchX;
    private final NamespacedKey keyLaunchY;
    private final NamespacedKey keyLaunchZ;

    public CombatListener(FBSkillsPlugin plugin) {
        this.plugin = plugin;
        this.keyLaunchX = new NamespacedKey(plugin, "launch_x");
        this.keyLaunchY = new NamespacedKey(plugin, "launch_y");
        this.keyLaunchZ = new NamespacedKey(plugin, "launch_z");
    }

    /**
     * Records the launch coordinates on the projectile to allow thread-safe distance calculation on Folia.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player) {
            Location loc = projectile.getLocation();
            PersistentDataContainer pdc = projectile.getPersistentDataContainer();
            pdc.set(keyLaunchX, PersistentDataType.DOUBLE, loc.getX());
            pdc.set(keyLaunchY, PersistentDataType.DOUBLE, loc.getY());
            pdc.set(keyLaunchZ, PersistentDataType.DOUBLE, loc.getZ());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (target instanceof ArmorStand) {
            return;
        }

        Entity damager = event.getDamager();

        // 1. Archery Processing (Arrows, Spectral Arrows, Tridents)
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            if (shooter.getGameMode() == GameMode.CREATIVE || shooter.getGameMode() == GameMode.SPECTATOR) {
                return;
            }

            // Folia Safety: Do NOT call shooter.getLocation() on target's thread!
            // Retrieve coordinates stored in the projectile's PDC.
            PersistentDataContainer pdc = projectile.getPersistentDataContainer();
            double distance;
            Double launchX = pdc.get(keyLaunchX, PersistentDataType.DOUBLE);
            Double launchY = pdc.get(keyLaunchY, PersistentDataType.DOUBLE);
            Double launchZ = pdc.get(keyLaunchZ, PersistentDataType.DOUBLE);

            if (launchX != null && launchY != null && launchZ != null) {
                Location targetLoc = target.getLocation();
                double dx = targetLoc.getX() - launchX;
                double dy = targetLoc.getY() - launchY;
                double dz = targetLoc.getZ() - launchZ;
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            } else {
                distance = projectile.getLocation().distance(target.getLocation());
            }

            double baseHit = plugin.getConfigManager().getXpReward(SkillType.ARCHERY, "BASE_HIT");
            if (baseHit <= 0) baseHit = 20.0;
            double multi = plugin.getConfigManager().getXpReward(SkillType.ARCHERY, "DISTANCE_MULTIPLIER");
            if (multi <= 0) multi = 1.8;

            double archeryXp = baseHit + (distance * multi);
            plugin.awardXp(shooter, SkillType.ARCHERY, archeryXp);
        }

        // 2. Fighting Processing (Melee Damage Dealt)
        if (damager instanceof Player meleePlayer && target instanceof LivingEntity) {
            if (meleePlayer.getGameMode() != GameMode.CREATIVE && meleePlayer.getGameMode() != GameMode.SPECTATOR) {
                double damage = event.getFinalDamage();
                if (damage > 0.0) {
                    double ratio = plugin.getConfigManager().getXpReward(SkillType.FIGHTING, "DAMAGE_DEALT_RATIO");
                    if (ratio <= 0) ratio = 2.0;
                    plugin.awardXp(meleePlayer, SkillType.FIGHTING, damage * ratio);
                }
            }
        }

        // 3. Defense Processing (Damage Taken or Shield Block)
        if (target instanceof Player defender && damager instanceof LivingEntity) {
            if (defender.getGameMode() == GameMode.CREATIVE || defender.getGameMode() == GameMode.SPECTATOR) {
                return;
            }

            // Shield block
            if (defender.isBlocking()) {
                double shieldXp = plugin.getConfigManager().getXpReward(SkillType.DEFENSE, "SHIELD_BLOCK_HIT");
                if (shieldXp <= 0) shieldXp = 25.0;
                plugin.awardXp(defender, SkillType.DEFENSE, shieldXp);
                return;
            }

            // Damage absorption with rate limiting
            long now = System.currentTimeMillis();
            long cooldown = plugin.getConfig().getLong("cooldowns.defense_hit_ms", 500);
            Long lastHit = lastDefenseHit.get(defender.getUniqueId());

            if (lastHit == null || (now - lastHit) >= cooldown) {
                lastDefenseHit.put(defender.getUniqueId(), now);
                double damageTaken = event.getFinalDamage();
                if (damageTaken > 0.0) {
                    double ratio = plugin.getConfigManager().getXpReward(SkillType.DEFENSE, "DAMAGE_TAKEN_RATIO");
                    if (ratio <= 0) ratio = 8.0;
                    plugin.awardXp(defender, SkillType.DEFENSE, damageTaken * ratio);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof ArmorStand) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null || killer.getGameMode() == GameMode.CREATIVE || killer.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        double xp = plugin.getConfigManager().getXpReward(SkillType.FIGHTING, entity.getType().name());
        if (xp <= 0.0) {
            xp = 15.0; // Default kill reward
        }

        plugin.awardXp(killer, SkillType.FIGHTING, xp);
    }
}
