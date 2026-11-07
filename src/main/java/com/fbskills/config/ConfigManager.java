package com.fbskills.config;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.SkillReward;
import com.fbskills.model.SkillType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Manages configuration files (config.yml, skills.yml, rewards.yml) with hot-reload support.
 */
public class ConfigManager {

    private final FBSkillsPlugin plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration skillsConfig;
    private FileConfiguration rewardsConfig;

    private double baseFormulaXp;
    private double formulaMultiplier;
    private int maxLevel;
    private boolean allowSilkTouchOreXp;
    private boolean trackPistonMovements;

    private final Map<SkillType, SkillDefinition> skillDefinitions = new EnumMap<>(SkillType.class);
    private final Map<Integer, SkillReward> globalRewards = new HashMap<>();
    private final Map<SkillType, Map<Integer, SkillReward>> skillRewards = new EnumMap<>(SkillType.class);
    private final Map<String, Double> permissionMultipliers = new LinkedHashMap<>();

    public record SkillDefinition(
            SkillType skillType,
            String displayName,
            Material icon,
            int guiSlot,
            List<String> description,
            Map<String, Double> xpRewards
    ) {}

    public ConfigManager(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.mainConfig = plugin.getConfig();

        this.skillsConfig = loadCustomConfig("skills.yml");
        this.rewardsConfig = loadCustomConfig("rewards.yml");

        // Leveling formula
        this.baseFormulaXp = mainConfig.getDouble("leveling.base_xp", 100.0);
        this.formulaMultiplier = mainConfig.getDouble("leveling.multiplier", 1.28);
        this.maxLevel = mainConfig.getInt("leveling.max_level", 100);

        // Anti-exploit flags
        this.allowSilkTouchOreXp = mainConfig.getBoolean("anti_exploit.allow_silk_touch_ore_xp", false);
        this.trackPistonMovements = mainConfig.getBoolean("anti_exploit.track_piston_movements", true);

        loadMultipliers();
        loadSkillsData();
        loadRewardsData();
    }

    private FileConfiguration loadCustomConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void loadMultipliers() {
        permissionMultipliers.clear();
        if (mainConfig.getBoolean("multipliers.enabled", true)) {
            ConfigurationSection sec = mainConfig.getConfigurationSection("multipliers.permissions");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    permissionMultipliers.put(key.toLowerCase(Locale.ROOT), sec.getDouble(key, 1.0));
                }
            }
        }
    }

    private void loadSkillsData() {
        skillDefinitions.clear();
        ConfigurationSection section = skillsConfig.getConfigurationSection("skills");
        if (section == null) return;

        for (SkillType type : SkillType.values()) {
            ConfigurationSection skillSec = section.getConfigurationSection(type.getId());
            if (skillSec == null) continue;

            String name = skillSec.getString("name", "<gold>" + type.getDefaultDisplayName() + "</gold>");
            String matName = skillSec.getString("icon", "BOOK");
            Material icon = Material.matchMaterial(matName);
            if (icon == null) icon = Material.BOOK;

            int slot = skillSec.getInt("slot", 0);
            List<String> desc = skillSec.getStringList("description");

            Map<String, Double> rewards = new HashMap<>();
            ConfigurationSection xpSec = skillSec.getConfigurationSection("xp_rewards");
            if (xpSec != null) {
                for (String key : xpSec.getKeys(false)) {
                    rewards.put(key.toUpperCase(Locale.ROOT), xpSec.getDouble(key, 0.0));
                }
            }

            skillDefinitions.put(type, new SkillDefinition(type, name, icon, slot, desc, rewards));
        }
    }

    private void loadRewardsData() {
        globalRewards.clear();
        skillRewards.clear();

        ConfigurationSection globalSec = rewardsConfig.getConfigurationSection("global_milestones");
        if (globalSec != null) {
            for (String key : globalSec.getKeys(false)) {
                try {
                    int lvl = Integer.parseInt(key);
                    String desc = globalSec.getString(key + ".description", "");
                    List<String> cmds = globalSec.getStringList(key + ".commands");
                    String attr = globalSec.getString(key + ".modifier.attribute", null);
                    double val = globalSec.getDouble(key + ".modifier.amount", 0.0);
                    double money = globalSec.getDouble(key + ".money", 0.0);
                    globalRewards.put(lvl, new SkillReward(lvl, desc, cmds, attr, val, money));
                } catch (NumberFormatException ignored) {}
            }
        }

        ConfigurationSection skillsSec = rewardsConfig.getConfigurationSection("skills");
        if (skillsSec != null) {
            for (SkillType type : SkillType.values()) {
                ConfigurationSection typeSec = skillsSec.getConfigurationSection(type.getId());
                if (typeSec == null) continue;

                Map<Integer, SkillReward> map = new HashMap<>();
                for (String key : typeSec.getKeys(false)) {
                    try {
                        int lvl = Integer.parseInt(key);
                        String desc = typeSec.getString(key + ".description", "");
                        List<String> cmds = typeSec.getStringList(key + ".commands");
                        String attr = typeSec.getString(key + ".modifier.attribute", null);
                        double val = typeSec.getDouble(key + ".modifier.amount", 0.0);
                        double money = typeSec.getDouble(key + ".money", 0.0);
                        map.put(lvl, new SkillReward(lvl, desc, cmds, attr, val, money));
                    } catch (NumberFormatException ignored) {}
                }
                skillRewards.put(type, map);
            }
        }
    }

    public double getBaseFormulaXp() {
        return baseFormulaXp;
    }

    public double getFormulaMultiplier() {
        return formulaMultiplier;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean isAllowSilkTouchOreXp() {
        return allowSilkTouchOreXp;
    }

    public boolean isTrackPistonMovements() {
        return trackPistonMovements;
    }

    public Map<String, Double> getPermissionMultipliers() {
        return permissionMultipliers;
    }

    public SkillDefinition getSkillDefinition(SkillType type) {
        return skillDefinitions.get(type);
    }

    public double getXpReward(SkillType type, String actionKey) {
        SkillDefinition def = skillDefinitions.get(type);
        if (def == null) return 0.0;
        return def.xpRewards().getOrDefault(actionKey.toUpperCase(Locale.ROOT), 0.0);
    }

    public List<SkillReward> getRewardsForSkill(SkillType type) {
        Map<Integer, SkillReward> specific = skillRewards.getOrDefault(type, Collections.emptyMap());
        Map<Integer, SkillReward> combined = new TreeMap<>();
        combined.putAll(globalRewards);
        combined.putAll(specific);
        return new ArrayList<>(combined.values());
    }

    public Optional<SkillReward> getRewardAtLevel(SkillType type, int level) {
        Map<Integer, SkillReward> specific = skillRewards.get(type);
        if (specific != null && specific.containsKey(level)) {
            return Optional.of(specific.get(level));
        }
        return Optional.ofNullable(globalRewards.get(level));
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }
}
