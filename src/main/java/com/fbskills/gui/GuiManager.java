package com.fbskills.gui;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.config.ConfigManager;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillProgress;
import com.fbskills.model.SkillReward;
import com.fbskills.model.SkillType;
import com.fbskills.util.ProgressBarUtil;
import com.fbskills.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Component-based GUI builder and orchestrator for FBSkills.
 */
public class GuiManager {

    private final FBSkillsPlugin plugin;

    public GuiManager(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main skills overview GUI for the specified player.
     */
    public void openMainMenu(Player player) {
        plugin.getSchedulerAdapter().runEntity(player, p -> {
            Player pl = (Player) p;
            PlayerProfile profile = plugin.getStorageManager().getProfile(pl.getUniqueId());
            if (profile == null) {
                pl.sendMessage(TextUtil.parse("<red>Your profile is still loading, please wait...</red>"));
                return;
            }

            SkillsGuiHolder holder = new SkillsGuiHolder(pl.getUniqueId());
            var inventory = Bukkit.createInventory(holder, 45, TextUtil.parse("<gradient:#ffd700:#ffaa00><bold>FBSkills & Progression</bold></gradient>"));
            holder.setInventory(inventory);

            // Fill background with border panes
            ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
            for (int i = 0; i < 45; i++) {
                if (i < 9 || i > 35 || i % 9 == 0 || i % 9 == 8) {
                    inventory.setItem(i, filler);
                }
            }

            // Player Profile Head at Slot 4
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(pl);
                skullMeta.displayName(TextUtil.parse("<gold><bold>" + pl.getName() + "'s Progression</bold></gold>"));
                List<Component> headLore = new ArrayList<>();
                headLore.add(TextUtil.parse("<gray>Total Level: <gold><bold>" + profile.getTotalLevel() + "</bold></gold></gray>"));
                headLore.add(TextUtil.parse("<gray>Total XP: <aqua>" + TextUtil.formatDouble(profile.getTotalXp()) + "</aqua></gray>"));
                headLore.add(Component.empty());
                headLore.add(TextUtil.parse("<yellow>Track your mastery and unlock legendary perks!</yellow>"));
                skullMeta.lore(headLore);
                head.setItemMeta(skullMeta);
            }
            inventory.setItem(4, head);

            // Populate all 11 Skills
            double base = plugin.getConfigManager().getBaseFormulaXp();
            double multi = plugin.getConfigManager().getFormulaMultiplier();
            int maxLevel = plugin.getConfigManager().getMaxLevel();

            for (SkillType type : SkillType.values()) {
                ConfigManager.SkillDefinition def = plugin.getConfigManager().getSkillDefinition(type);
                if (def == null) continue;

                SkillProgress progress = profile.getProgress(type);
                int level = progress.getLevel();
                double currentXp = progress.getCurrentXp();
                double requiredXp = progress.getRequiredXp(base, multi);
                double ratio = progress.getProgressRatio(base, multi, maxLevel);

                List<Component> lore = new ArrayList<>();
                for (String descLine : def.description()) {
                    lore.add(TextUtil.parse(descLine));
                }
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<dark_gray>---------------------------</dark_gray>"));
                lore.add(TextUtil.parse("<gray>Level: <gold><bold>" + level + "</bold></gold> / <dark_gray>" + maxLevel + "</dark_gray>"));
                lore.add(TextUtil.parse("<gray>XP: <aqua>" + TextUtil.formatDouble(currentXp) + "</aqua> / <dark_aqua>" + TextUtil.formatDouble(requiredXp) + "</dark_aqua>"));
                lore.add(TextUtil.parse(ProgressBarUtil.buildDefaultBar(ratio)));
                lore.add(TextUtil.parse("<dark_gray>---------------------------</dark_gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>▶ Click to inspect perks & rewards!</yellow>"));

                ItemStack skillItem = createItem(def.icon(), TextUtil.parse(def.displayName()), lore);
                inventory.setItem(def.guiSlot(), skillItem);
            }

            // Close Button at Slot 40
            ItemStack closeButton = createItem(Material.BARRIER, TextUtil.parse("<red><bold>Close Menu</bold></red>"), List.of(TextUtil.parse("<gray>Click to exit GUI</gray>")));
            inventory.setItem(40, closeButton);

            pl.openInventory(inventory);
        });
    }

    /**
     * Opens the detailed perk milestones GUI for a specific skill.
     */
    public void openSkillDetail(Player player, SkillType skill) {
        plugin.getSchedulerAdapter().runEntity(player, p -> {
            Player pl = (Player) p;
            PlayerProfile profile = plugin.getStorageManager().getProfile(pl.getUniqueId());
            if (profile == null) return;

            SkillDetailGuiHolder holder = new SkillDetailGuiHolder(pl.getUniqueId(), skill);
            var inventory = Bukkit.createInventory(holder, 54, TextUtil.parse("<gradient:#ffd700:#ffaa00><bold>" + skill.getDefaultDisplayName() + " Milestones</bold></gradient>"));
            holder.setInventory(inventory);

            // Fill borders
            ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
            for (int i = 0; i < 54; i++) {
                if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8) {
                    inventory.setItem(i, filler);
                }
            }

            // Skill Header at Slot 4
            ConfigManager.SkillDefinition def = plugin.getConfigManager().getSkillDefinition(skill);
            SkillProgress progress = profile.getProgress(skill);
            double base = plugin.getConfigManager().getBaseFormulaXp();
            double multi = plugin.getConfigManager().getFormulaMultiplier();
            int maxLevel = plugin.getConfigManager().getMaxLevel();

            List<Component> headerLore = new ArrayList<>();
            headerLore.add(TextUtil.parse("<gray>Current Level: <gold><bold>" + progress.getLevel() + "</bold></gold> / <dark_gray>" + maxLevel + "</dark_gray>"));
            headerLore.add(TextUtil.parse("<gray>Current XP: <aqua>" + TextUtil.formatDouble(progress.getCurrentXp()) + "</aqua> / <dark_aqua>" + TextUtil.formatDouble(progress.getRequiredXp(base, multi)) + "</dark_aqua>"));
            headerLore.add(TextUtil.parse(ProgressBarUtil.buildDefaultBar(progress.getProgressRatio(base, multi, maxLevel))));

            ItemStack headerItem = createItem(def != null ? def.icon() : Material.BOOK,
                    TextUtil.parse(def != null ? def.displayName() : skill.getDefaultDisplayName()),
                    headerLore);
            inventory.setItem(4, headerItem);

            // Rewards Layout
            List<SkillReward> rewards = plugin.getConfigManager().getRewardsForSkill(skill);
            int[] rewardSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

            int slotIdx = 0;
            for (SkillReward reward : rewards) {
                if (slotIdx >= rewardSlots.length) break;

                boolean unlocked = progress.getLevel() >= reward.getLevel();
                Material icon = unlocked ? Material.NETHER_STAR : Material.IRON_BARS;
                String title = unlocked
                        ? "<green><bold>Level " + reward.getLevel() + " [UNLOCKED]</bold></green>"
                        : "<red><bold>Level " + reward.getLevel() + " [LOCKED]</bold></red>";

                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Perk: " + reward.getDescription() + "</gray>"));
                if (reward.hasMoneyReward()) {
                    lore.add(TextUtil.parse("<gold>Reward: $" + TextUtil.formatDouble(reward.getMoney()) + "</gold>"));
                }
                lore.add(Component.empty());
                if (unlocked) {
                    lore.add(TextUtil.parse("<green>✔ Unlocked and active!</green>"));
                } else {
                    lore.add(TextUtil.parse("<red>✘ Reach Level " + reward.getLevel() + " to unlock!</red>"));
                }

                ItemStack perkItem = createItem(icon, TextUtil.parse(title), lore);
                inventory.setItem(rewardSlots[slotIdx++], perkItem);
            }

            // Back Button at Slot 49
            ItemStack backButton = createItem(Material.ARROW, TextUtil.parse("<yellow><bold>⬅ Back to Skills Menu</bold></yellow>"),
                    List.of(TextUtil.parse("<gray>Click to return to the main overview</gray>")));
            inventory.setItem(49, backButton);

            pl.openInventory(inventory);
        });
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }
}
