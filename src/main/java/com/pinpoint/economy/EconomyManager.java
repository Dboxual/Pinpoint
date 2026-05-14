package com.pinpoint.economy;

import com.pinpoint.PinpointPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final PinpointPlugin plugin;
    private Economy economy = null;
    private boolean enabled = false;

    public EconomyManager(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found - fee system disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy provider registered with Vault - fee system disabled.");
            return false;
        }
        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("Vault economy hooked: " + economy.getName());
        return true;
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public double getBalance(Player player) {
        if (!enabled) return 0;
        return economy.getBalance(player);
    }

    public boolean hasBalance(Player player, double amount) {
        if (!enabled) return true;
        return economy.has(player, amount);
    }

    public boolean charge(Player player, double amount) {
        if (!enabled || amount <= 0) return true;
        if (!economy.has(player, amount)) return false;
        economy.withdrawPlayer(player, amount);
        return true;
    }

    public String format(double amount) {
        if (!enabled || economy == null) return String.format("%.2f", amount);
        return economy.format(amount);
    }

    public void deposit(Player player, double amount) {
        if (!enabled || amount <= 0) return;
        economy.depositPlayer(player, amount);
    }

    public String getProviderName() {
        if (!enabled || economy == null) return "none";
        return economy.getName();
    }
}
