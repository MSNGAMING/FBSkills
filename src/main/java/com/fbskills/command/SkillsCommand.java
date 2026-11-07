package com.fbskills.command;

import com.fbskills.FBSkillsPlugin;
import com.fbskills.model.LeaderboardEntry;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import com.fbskills.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Handles all in-game commands and tab-completion for FBSkills.
 * Core command: /skills
 */
public class SkillsCommand implements CommandExecutor, TabCompleter {

    private final FBSkillsPlugin plugin;

    public SkillsCommand(FBSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.parse("<red>Only in-game players can view the skills GUI.</red>"));
                return true;
            }
            plugin.getGuiManager().openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "top", "leaderboard" -> handleTop(sender, args);
            case "givexp", "addxp" -> handleGiveXp(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "reload" -> handleReload(sender);
            case "help" -> sendHelp(sender);
            default -> {
                // If user typed a skill directly (e.g. /skills mining)
                SkillType.fromId(sub).ifPresentOrElse(skill -> {
                    if (sender instanceof Player player) {
                        plugin.getGuiManager().openSkillDetail(player, skill);
                    } else {
                        handleTop(sender, new String[]{"top", sub});
                    }
                }, () -> {
                    sender.sendMessage(TextUtil.parse("<red>Unknown sub-command. Type <yellow>/" + label + " help</yellow> for help.</red>"));
                });
            }
        }

        return true;
    }

    private void handleTop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.parse("<red>Usage: /skills top <skill></red>"));
            return;
        }

        Optional<SkillType> skillOpt = SkillType.fromId(args[1]);
        if (skillOpt.isEmpty()) {
            sender.sendMessage(TextUtil.parse("<red>Invalid skill: " + args[1] + ". Available: " + getSkillNamesList() + "</red>"));
            return;
        }

        SkillType skill = skillOpt.get();
        sender.sendMessage(TextUtil.parse("<gray>Fetching top 10 rankings for <yellow>" + skill.getDefaultDisplayName() + "</yellow>...</gray>"));

        plugin.getStorageManager().getLeaderboard(skill, 10).thenAccept(entries -> {
            sender.sendMessage(TextUtil.parse("<gradient:#ffd700:#ffaa00><bold>=== Top 10 " + skill.getDefaultDisplayName() + " Leaderboard ===</bold></gradient>"));
            if (entries.isEmpty()) {
                sender.sendMessage(TextUtil.parse("<gray>No ranked player data available yet.</gray>"));
                return;
            }

            for (LeaderboardEntry entry : entries) {
                String color = switch (entry.rank()) {
                    case 1 -> "<gold><bold>#1</bold></gold>";
                    case 2 -> "<white><bold>#2</bold></white>";
                    case 3 -> "<yellow>#3</yellow>";
                    default -> "<gray>#" + entry.rank() + "</gray>";
                };
                String line = color + " <aqua>" + entry.username() + "</aqua> <dark_gray>-</dark_gray> <yellow>Lvl " + entry.level() + "</yellow> <gray>(" + TextUtil.formatDouble(entry.xp()) + " XP)</gray>";
                sender.sendMessage(TextUtil.parse(line));
            }
        });
    }

    private void handleGiveXp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fbskills.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You do not have permission to use this command.</red>"));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(TextUtil.parse("<red>Usage: /skills givexp <player> <skill> <amount></red>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(TextUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return;
        }

        Optional<SkillType> skillOpt = SkillType.fromId(args[2]);
        if (skillOpt.isEmpty()) {
            sender.sendMessage(TextUtil.parse("<red>Invalid skill. Available: " + getSkillNamesList() + "</red>"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
            if (amount <= 0) {
                sender.sendMessage(TextUtil.parse("<red>XP amount must be positive.</red>"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.parse("<red>Invalid XP amount: " + args[3] + "</red>"));
            return;
        }

        SkillType skill = skillOpt.get();
        plugin.awardXp(target, skill, amount);
        sender.sendMessage(TextUtil.parse("<green>Granted <aqua>" + TextUtil.formatDouble(amount) + " XP</aqua> in <yellow>" + skill.getDefaultDisplayName() + "</yellow> to <gold>" + target.getName() + "</gold>!</green>"));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fbskills.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You do not have permission to use this command.</red>"));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(TextUtil.parse("<red>Usage: /skills setlevel <player> <skill> <level></red>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(TextUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return;
        }

        Optional<SkillType> skillOpt = SkillType.fromId(args[2]);
        if (skillOpt.isEmpty()) {
            sender.sendMessage(TextUtil.parse("<red>Invalid skill. Available: " + getSkillNamesList() + "</red>"));
            return;
        }

        int newLevel;
        try {
            newLevel = Integer.parseInt(args[3]);
            if (newLevel < 1 || newLevel > plugin.getConfigManager().getMaxLevel()) {
                sender.sendMessage(TextUtil.parse("<red>Level must be between 1 and " + plugin.getConfigManager().getMaxLevel() + ".</red>"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.parse("<red>Invalid level: " + args[3] + "</red>"));
            return;
        }

        SkillType skill = skillOpt.get();
        PlayerProfile profile = plugin.getStorageManager().getProfile(target.getUniqueId());
        if (profile == null) {
            sender.sendMessage(TextUtil.parse("<red>Target player profile is not loaded.</red>"));
            return;
        }

        int oldLevel = profile.getLevel(skill);
        profile.setLevel(skill, newLevel);
        profile.setXp(skill, 0.0);

        if (newLevel > oldLevel) {
            plugin.getRewardManager().processLevelUp(target, skill, oldLevel, newLevel);
        }

        sender.sendMessage(TextUtil.parse("<green>Set <gold>" + target.getName() + "'s</gold> <yellow>" + skill.getDefaultDisplayName() + "</yellow> level to <aqua>" + newLevel + "</aqua>!</green>"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("fbskills.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You do not have permission to use this command.</red>"));
            return;
        }

        plugin.reload();
        sender.sendMessage(TextUtil.parse("<green>FBSkills configurations and rewards reloaded successfully!</green>"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.parse("<gradient:#ffd700:#ffaa00><bold>=== FBSkills Commands ===</bold></gradient>"));
        sender.sendMessage(TextUtil.parse("<yellow>/skills</yellow> <gray>- Open skills profile GUI</gray>"));
        sender.sendMessage(TextUtil.parse("<yellow>/skills top <skill></yellow> <gray>- View top 10 leaderboard for a skill</gray>"));
        if (sender.hasPermission("fbskills.admin")) {
            sender.sendMessage(TextUtil.parse("<yellow>/skills givexp <player> <skill> <amount></yellow> <gray>- Grant skill XP</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/skills setlevel <player> <skill> <level></yellow> <gray>- Set skill level</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/skills reload</yellow> <gray>- Hot-reload plugin configurations</gray>"));
        }
    }

    private String getSkillNamesList() {
        return Arrays.stream(SkillType.values()).map(SkillType::getId).reduce((a, b) -> a + ", " + b).orElse("");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(List.of("top", "help"));
            if (sender.hasPermission("fbskills.admin")) {
                sub.addAll(List.of("givexp", "setlevel", "reload"));
            }
            return filterPrefix(sub, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("top")) {
                return filterPrefix(Arrays.stream(SkillType.values()).map(SkillType::getId).toList(), args[1]);
            }
            if (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("setlevel")) {
                return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("setlevel"))) {
            return filterPrefix(Arrays.stream(SkillType.values()).map(SkillType::getId).toList(), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("givexp")) {
            return filterPrefix(List.of("100", "500", "1000", "5000"), args[3]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("setlevel")) {
            return filterPrefix(List.of("1", "10", "25", "50", "100"), args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
