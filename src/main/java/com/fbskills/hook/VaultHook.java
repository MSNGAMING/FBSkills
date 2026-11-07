package com.fbskills.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * Graceful integration hook for Vault economy operations.
 */
public class VaultHook {

    private final Plugin plugin;
    private Economy economy;
    private boolean hooked;

    public VaultHook(Plugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }

        this.economy = rsp.getProvider();
        this.hooked = (this.economy != null);
        if (hooked) {
            plugin.getLogger().info("Successfully hooked into Vault Economy (" + economy.getName() + ").");
        }
    }

    public boolean isHooked() {
        return hooked && economy != null;
    }

    public boolean deposit(Player player, double amount) {
        if (!isHooked() || player == null || amount <= 0.0) {
            return false;
        }

        try {
            var response = economy.depositPlayer(player, amount);
            return response.transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing Vault deposit for " + player.getName(), e);
            return false;
        }
    }
}
