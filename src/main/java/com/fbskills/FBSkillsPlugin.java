package com.fbskills;

import com.fbskills.api.event.PlayerSkillXpGainEvent;
import com.fbskills.command.SkillsCommand;
import com.fbskills.config.ConfigManager;
import com.fbskills.gui.GuiListener;
import com.fbskills.gui.GuiManager;
import com.fbskills.hook.PlaceholderAPIHook;
import com.fbskills.hook.VaultHook;
import com.fbskills.listener.*;
import com.fbskills.model.PlayerProfile;
import com.fbskills.model.SkillType;
import com.fbskills.reward.RewardManager;
import com.fbskills.scheduler.PlatformScheduler;
import com.fbskills.scheduler.SchedulerAdapter;
import com.fbskills.storage.StorageManager;
import com.fbskills.util.BlockTracker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Level;

/**
 * FBSkills - High-performance, modern SMP Skills & Progression plugin.
 * Cross-platform compatible with Paper, Purpur, and Folia (1.21 - 26.3+).
 */
public final class FBSkillsPlugin extends JavaPlugin {

    private static FBSkillsPlugin instance;

    private SchedulerAdapter schedulerAdapter;
    private ConfigManager configManager;
    private StorageManager storageManager;
    private RewardManager rewardManager;
    private GuiManager guiManager;
    private BlockTracker blockTracker;
    private PlaceholderAPIHook papiHook;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();

        getLogger().info("Initializing FBSkills engine...");

        // 1. Multi-Platform Scheduler Detection
        this.schedulerAdapter = PlatformScheduler.create(this);

        // 2. Configuration Subsystem
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        // 3. Anti-Exploit Block Tracker
        long blockTtl = getConfig().getLong("cache.placed_blocks_tracker_ttl_minutes", 120);
        this.blockTracker = new BlockTracker(blockTtl);
        schedulerAdapter.runAsyncRepeating(blockTracker::purgeExpired, 12000L, 12000L);

        // 4. Persistence & Cache Engine
        this.storageManager = new StorageManager(this);
        this.storageManager.init();

        // 5. Reward & Progression Manager
        this.rewardManager = new RewardManager(this);

        // 6. User Interface Manager
        this.guiManager = new GuiManager(this);

        // 7. Event Listeners Registration
        registerListeners();

        // 8. Command Registration (Core command: /skills)
        registerCommands();

        // 9. Ecosystem Hooks (Vault & PlaceholderAPI)
        this.vaultHook = new VaultHook(this);
        hookPlaceholderAPI();

        long elapsed = System.currentTimeMillis() - startTime;
        getLogger().info("=================================================");
        getLogger().info(" FBSkills v" + getPluginMeta().getVersion() + " successfully enabled! (" + elapsed + "ms)");
        getLogger().info(" Platform: " + (PlatformScheduler.isFolia() ? "Folia (Threaded Regions)" : "Paper / Purpur"));
        getLogger().info(" Storage: " + getConfig().getString("storage.type", "SQLITE"));
        getLogger().info(" Target: Minecraft 1.21 - 26.3 Modern API");
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down FBSkills...");

        if (papiHook != null) {
            papiHook.unregister();
            papiHook = null;
        }

        if (storageManager != null) {
            storageManager.shutdown();
        }

        if (schedulerAdapter != null) {
            schedulerAdapter.cancelAll();
        }

        if (blockTracker != null) {
            blockTracker.clear();
        }

        getLogger().info("FBSkills cleanly disabled.");
        instance = null;
    }

    /**
     * Hot-reloads all configuration files and resets cached values.
     */
    public void reload() {
        configManager.loadConfigs();
        storageManager.invalidateLeaderboardCache();
        getLogger().info("FBSkills configurations reloaded.");
    }

    /**
     * Centralized, thread-safe method to award skill XP to players.
     * Automatically routes execution to the player's region thread if running on Folia.
     */
    public void awardXp(Player player, SkillType skill, double amount) {
        if (amount <= 0.0 || player == null || !player.isOnline()) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Folia Region Thread Safety: Ensure entity execution occurs on the owning thread
        if (!PlatformScheduler.isOwnedByCurrentRegion(player)) {
            schedulerAdapter.runEntity(player, p -> awardXp((Player) p, skill, amount));
            return;
        }

        // Apply permission-based XP multipliers
        double multiplier = 1.0;
        for (Map.Entry<String, Double> entry : configManager.getPermissionMultipliers().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                multiplier = Math.max(multiplier, entry.getValue());
            }
        }
        double finalAmount = amount * multiplier;

        // Fire custom developer API event (cancellable & mutable)
        PlayerSkillXpGainEvent event = new PlayerSkillXpGainEvent(player, skill, finalAmount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getAmount() <= 0.0) {
            return;
        }
        finalAmount = event.getAmount();

        PlayerProfile profile = storageManager.getProfile(player.getUniqueId());
        if (profile == null) {
            return;
        }

        int oldLevel = profile.getLevel(skill);
        int gained = profile.addXp(
                skill,
                finalAmount,
                configManager.getBaseFormulaXp(),
                configManager.getFormulaMultiplier(),
                configManager.getMaxLevel()
        );

        rewardManager.notifyXpGain(player, skill, finalAmount, profile);

        if (gained > 0) {
            int newLevel = profile.getLevel(skill);
            rewardManager.processLevelUp(player, skill, oldLevel, newLevel);
        }
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new MiningListener(this), this);
        pm.registerEvents(new FarmingListener(this), this);
        pm.registerEvents(new ExcavationListener(this), this);
        pm.registerEvents(new FishingListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new AgilityListener(this), this);
        pm.registerEvents(new ForgingListener(this), this);
        pm.registerEvents(new EnchantingListener(this), this);
        pm.registerEvents(new AlchemyListener(this), this);
    }

    private void registerCommands() {
        PluginCommand cmd = getCommand("skills");
        if (cmd != null) {
            SkillsCommand handler = new SkillsCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        // Native Paper mode: registered during bootstrap via LifecycleEvents.COMMANDS
    }

    private void hookPlaceholderAPI() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.papiHook = new PlaceholderAPIHook(this);
            if (this.papiHook.register()) {
                getLogger().info("Successfully hooked into PlaceholderAPI.");
            }
        }
    }

    public static FBSkillsPlugin getInstance() {
        return instance;
    }

    public SchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public BlockTracker getBlockTracker() {
        return blockTracker;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }
}
