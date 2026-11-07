package com.fbskills.reward;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.api.event.PlayerSkillLevelUpEvent;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillReward;
import com.fbskills.model.SkillType;
import com.fbskills.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Executes milestone rewards, sound effects, titles, broadcasts, Vault payouts,
 * and passive attribute modifiers across 1.21 - 26.3+.
 */
public class RewardManager {

    private final FBSkillsPlugin plugin;

    public RewardManager(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles XP gain notifications in the action bar.
     */
    public void notifyXpGain(Player player, SkillType skill, double amount, PlayerProfile profile) {
        if (!plugin.getConfig().getBoolean("notifications.actionbar.enabled", true)) {
            return;
        }

        int level = profile.getLevel(skill);
        double ratio = profile.getProgress(skill).getProgressRatio(
                plugin.getConfigManager().getBaseFormulaXp(),
                plugin.getConfigManager().getFormulaMultiplier(),
                plugin.getConfigManager().getMaxLevel()
        );
        String percentStr = String.format("%.1f", ratio * 100.0);

        String rawFormat = plugin.getConfig().getString("notifications.actionbar.format",
                "<gradient:#00ff87:#60efff>+<amount> XP</gradient> <dark_gray>•</dark_gray> <gray><skill></gray> <dark_gray>[<yellow>Lvl <level></yellow> <white><percent>%</white>]</dark_gray>");

        String formatted = rawFormat
                .replace("<amount>", TextUtil.formatDouble(amount))
                .replace("<skill>", skill.getDefaultDisplayName())
                .replace("<level>", String.valueOf(level))
                .replace("<percent>", percentStr);

        player.sendActionBar(TextUtil.parse(formatted));
    }

    /**
     * Triggers complete level-up sequence (titles, sounds, broadcasts, rewards, events).
     */
    public void processLevelUp(Player player, SkillType skill, int oldLevel, int newLevel) {
        List<SkillReward> earnedRewards = new ArrayList<>();
        for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
            plugin.getConfigManager().getRewardAtLevel(skill, lvl).ifPresent(earnedRewards::add);
        }

        // Fire custom developer API event
        PlayerSkillLevelUpEvent event = new PlayerSkillLevelUpEvent(player, skill, oldLevel, newLevel, earnedRewards);
        Bukkit.getPluginManager().callEvent(event);

        // Run entity-bound notifications and awards
        plugin.getSchedulerAdapter().runEntity(player, p -> {
            Player pl = (Player) p;

            // Sound
            if (plugin.getConfig().getBoolean("notifications.levelup.sound_enabled", true)) {
                try {
                    String soundKey = plugin.getConfig().getString("notifications.levelup.sound", "ui.toast.challenge_complete");
                    pl.playSound(pl.getLocation(), soundKey, 1.0f, 1.0f);
                } catch (Exception ignored) {
                    pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }

            // Title & Subtitle
            if (plugin.getConfig().getBoolean("notifications.levelup.title_enabled", true)) {
                String titleRaw = plugin.getConfig().getString("notifications.levelup.title", "<gold><bold>LEVEL UP!</bold></gold>");
                String subRaw = plugin.getConfig().getString("notifications.levelup.subtitle", "<yellow><skill></yellow> is now level <gold><level></gold>!")
                        .replace("<skill>", skill.getDefaultDisplayName())
                        .replace("<level>", String.valueOf(newLevel));

                Title title = Title.title(
                        TextUtil.parse(titleRaw),
                        TextUtil.parse(subRaw),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(500))
                );
                pl.showTitle(title);
            }

            // Milestone rewards
            for (SkillReward reward : earnedRewards) {
                applyReward(pl, skill, reward);
            }
        });

        // Broadcast checks
        List<Integer> milestones = plugin.getConfig().getIntegerList("notifications.levelup.broadcast_milestones");
        if (milestones.contains(newLevel)) {
            String bcastRaw = plugin.getConfig().getString("notifications.levelup.broadcast_message",
                    "<gradient:#00e5ff:#1200ff><bold>[FBSKILLS]</bold></gradient> <aqua><player></aqua> reached <gold><skill> Level <level></gold>!")
                    .replace("<player>", player.getName())
                    .replace("<skill>", skill.getDefaultDisplayName())
                    .replace("<level>", String.valueOf(newLevel));

            Component bcastComp = TextUtil.parse(bcastRaw);
            plugin.getSchedulerAdapter().runGlobal(() -> Bukkit.broadcast(bcastComp));
        }
    }

    private void applyReward(Player player, SkillType skill, SkillReward reward) {
        // 1. Vault economy deposit
        if (reward.hasMoneyReward() && plugin.getVaultHook() != null && plugin.getVaultHook().isHooked()) {
            boolean success = plugin.getVaultHook().deposit(player, reward.getMoney());
            if (success) {
                player.sendMessage(TextUtil.parse("<gold>+ $" + TextUtil.formatDouble(reward.getMoney()) + " deposited to your account!</gold>"));
            }
        }

        // 2. Execute console commands
        for (String cmd : reward.getCommands()) {
            String parsedCmd = cmd.replace("%player%", player.getName())
                    .replace("%skill%", skill.getId())
                    .replace("%level%", String.valueOf(reward.getLevel()));

            plugin.getSchedulerAdapter().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd));
        }

        // 3. Apply attribute modifier if defined
        if (reward.hasAttributeModifier()) {
            applyAttribute(player, reward.getAttributeName(), reward.getAttributeValue());
        }
    }

    /**
     * Resolves attributes safely across Minecraft 1.21 through 26.3+.
     */
    private void applyAttribute(Player player, String attributeName, double amount) {
        try {
            Attribute attribute = resolveAttribute(attributeName);
            if (attribute == null) {
                return;
            }

            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                String cleanKey = attributeName.toLowerCase(Locale.ROOT)
                        .replace("generic.", "")
                        .replace("generic_", "")
                        .replace('.', '_');
                NamespacedKey modifierKey = new NamespacedKey(plugin, "fbskills_" + cleanKey);
                instance.removeModifier(modifierKey);

                AttributeModifier modifier = new AttributeModifier(
                        modifierKey,
                        amount,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ANY
                );
                instance.addModifier(modifier);
            }
        } catch (Throwable ignored) {
            // Graceful fallback across minor API variations
        }
    }

    private Attribute resolveAttribute(String name) {
        String clean = name.toLowerCase(Locale.ROOT)
                .replace("generic.", "")
                .replace("generic_", "");

        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(clean));
        if (attr != null) return attr;

        // Fallback for legacy generic.* keys
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + clean));
    }
}
