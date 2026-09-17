package com.lumiczi.lurpg.core.hook;

import com.lumiczi.lurpg.LuRPGPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Hook for the Vault plugin (economy integration).
 * <p>
 * Detects Vault and retrieves the registered Economy service provider.
 * Provides convenience methods for balance checks, deposits, and withdrawals.
 * All methods return {@code false} or {@code 0.0} if Vault is not available.
 */
public class VaultHook {

    private final LuRPGPlugin plugin;
    private Economy economy;
    private boolean available;

    public VaultHook(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects Vault and registers the economy service.
     */
    public void initialize() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            this.available = false;
            return;
        }

        try {
            RegisteredServiceProvider<Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
                this.available = economy != null;
                if (available) {
                    plugin.getLogger().info("Vault economy hooked.");
                } else {
                    plugin.getLogger().warning("Vault found but no economy provider registered.");
                }
            } else {
                this.available = false;
                plugin.getLogger().warning("Vault found but no economy provider registered.");
            }
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().warning("Failed to hook Vault: " + t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Checks if the player has at least the specified amount.
     *
     * @param player the offline player
     * @param amount the amount to check
     * @return true if the player has enough money, false if Vault is unavailable or insufficient funds
     */
    public boolean hasMoney(OfflinePlayer player, double amount) {
        if (!available || economy == null) {
            return false;
        }
        return economy.has(player, amount);
    }

    /**
     * Withdraws money from the player's account.
     *
     * @param player the offline player
     * @param amount the amount to withdraw
     * @return true if the transaction succeeded
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!available || economy == null) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Deposits money into the player's account.
     *
     * @param player the offline player
     * @param amount the amount to deposit
     * @return true if the transaction succeeded
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!available || economy == null) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Returns the player's current balance.
     *
     * @param player the offline player
     * @return the balance, or 0.0 if Vault is unavailable
     */
    public double getBalance(OfflinePlayer player) {
        if (!available || economy == null) {
            return 0.0;
        }
        return economy.getBalance(player);
    }

    /**
     * Returns the raw Vault Economy instance.
     *
     * @return the Economy, or null if not available
     */
    public Economy getEconomy() {
        return economy;
    }
}
