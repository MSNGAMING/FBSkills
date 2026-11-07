package com.fbskills.hook;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillProgress;
import com.fbskills.model.SkillType;
import com.fbskills.util.ProgressBarUtil;
import com.fbskills.util.TextUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Native PlaceholderAPI expansion providing instant, cached skill placeholders for FBSkills.
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final FBSkillsPlugin plugin;

    public PlaceholderAPIHook(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fbskills";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Sriram";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }

        PlayerProfile profile = plugin.getStorageManager().getProfile(offlinePlayer.getUniqueId());
        if (profile == null) {
            return "...";
        }

        String lower = params.toLowerCase(Locale.ROOT);

        if (lower.equals("total_level")) {
            return String.valueOf(profile.getTotalLevel());
        }

        if (lower.equals("total_xp")) {
            return TextUtil.formatDouble(profile.getTotalXp());
        }

        double base = plugin.getConfigManager().getBaseFormulaXp();
        double multi = plugin.getConfigManager().getFormulaMultiplier();
        int maxLevel = plugin.getConfigManager().getMaxLevel();

        // Check level_<skill>
        if (lower.startsWith("level_")) {
            String skillKey = lower.substring("level_".length());
            return SkillType.fromId(skillKey)
                    .map(type -> String.valueOf(profile.getLevel(type)))
                    .orElse("0");
        }

        // Check xp_<skill>
        if (lower.startsWith("xp_")) {
            String skillKey = lower.substring("xp_".length());
            return SkillType.fromId(skillKey)
                    .map(type -> TextUtil.formatDouble(profile.getXp(type)))
                    .orElse("0.0");
        }

        // Check required_xp_<skill>
        if (lower.startsWith("required_xp_")) {
            String skillKey = lower.substring("required_xp_".length());
            return SkillType.fromId(skillKey)
                    .map(type -> TextUtil.formatDouble(profile.getProgress(type).getRequiredXp(base, multi)))
                    .orElse("0.0");
        }

        // Check progress_bar_<skill>
        if (lower.startsWith("progress_bar_")) {
            String skillKey = lower.substring("progress_bar_".length());
            return SkillType.fromId(skillKey)
                    .map(type -> {
                        SkillProgress p = profile.getProgress(type);
                        return ProgressBarUtil.buildDefaultBar(p.getProgressRatio(base, multi, maxLevel));
                    })
                    .orElse("[□□□□□□□□□□] 0.0%");
        }

        // Check progress_percent_<skill>
        if (lower.startsWith("progress_percent_")) {
            String skillKey = lower.substring("progress_percent_".length());
            return SkillType.fromId(skillKey)
                    .map(type -> {
                        SkillProgress p = profile.getProgress(type);
                        return String.format("%.1f%%", p.getProgressRatio(base, multi, maxLevel) * 100.0);
                    })
                    .orElse("0.0%");
        }

        return null;
    }
}
