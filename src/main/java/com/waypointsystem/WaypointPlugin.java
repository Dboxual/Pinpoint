package com.waypointsystem;

import com.waypointsystem.commands.WaypointCommand;
import com.waypointsystem.data.WaypointManager;
import com.waypointsystem.data.WaypointStorage;
import com.waypointsystem.economy.EconomyManager;
import com.waypointsystem.gui.GuiManager;
import com.waypointsystem.item.ItemManager;
import com.waypointsystem.listeners.BlockBreakListener;
import com.waypointsystem.listeners.BlockPlaceListener;
import com.waypointsystem.listeners.ChatInputListener;
import com.waypointsystem.listeners.WaypointInteractListener;
import com.waypointsystem.util.TeleportHelper;
import org.bukkit.plugin.java.JavaPlugin;

public class WaypointPlugin extends JavaPlugin {

    private WaypointStorage waypointStorage;
    private WaypointManager waypointManager;
    private ItemManager itemManager;
    private EconomyManager economyManager;
    private GuiManager guiManager;
    private TeleportHelper teleportHelper;
    private WaypointCommand commandHandler;
    private ChatInputListener chatInputListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getLogger().info("WaypointSystem v" + getDescription().getVersion() + " starting...");

        waypointStorage = new WaypointStorage(this);
        waypointStorage.load();

        waypointManager = new WaypointManager(this, waypointStorage);
        waypointManager.loadAll();

        itemManager = new ItemManager(this);
        itemManager.registerRecipes();
        getLogger().info("Crafting recipe registered.");

        economyManager = new EconomyManager(this);
        boolean vaultFound = economyManager.setup();
        if (vaultFound) {
            getLogger().info("Vault found - economy/fee system active (" + economyManager.getProviderName() + ").");
        } else {
            getLogger().info("Vault not found - fee system disabled.");
        }

        teleportHelper = new TeleportHelper(this);
        guiManager = new GuiManager(this);

        commandHandler = new WaypointCommand(this);
        getCommand("waypoint").setExecutor(commandHandler);
        getCommand("waypoint").setTabCompleter(commandHandler);

        chatInputListener = new ChatInputListener(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new WaypointInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(chatInputListener, this);

        getLogger().info("WaypointSystem enabled with " + waypointManager.getAllWaypoints().size() + " waypoints loaded.");
    }

    @Override
    public void onDisable() {
        getLogger().info("WaypointSystem disabled.");
    }

    public void reload() {
        reloadConfig();
        waypointStorage.load();
        waypointManager.loadAll();
        economyManager.setup();
        getLogger().info("WaypointSystem reloaded. Waypoints: " + waypointManager.getAllWaypoints().size());
    }

    public String msg(String key) {
        return getConfig().getString("messages." + key, "").replace("&", "§");
    }

    public String msgCfg(String key) {
        return getConfig().getString("messages." + key, "").replace("&", "§");
    }

    public WaypointManager getWaypointManager() { return waypointManager; }
    public ItemManager getItemManager() { return itemManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public TeleportHelper getTeleportHelper() { return teleportHelper; }
    public WaypointCommand getCommandHandler() { return commandHandler; }
    public ChatInputListener getChatInputListener() { return chatInputListener; }
}
