package com.pinpoint;

import com.pinpoint.commands.PartyCommand;
import com.pinpoint.commands.WaypointCommand;
import com.pinpoint.data.PartyManager;
import com.pinpoint.data.PartyStorage;
import com.pinpoint.data.WaypointManager;
import com.pinpoint.data.WaypointStorage;
import com.pinpoint.economy.EconomyManager;
import com.pinpoint.gui.GuiManager;
import com.pinpoint.gui.PartyGuiManager;
import com.pinpoint.item.ItemManager;
import com.pinpoint.listeners.BlockBreakListener;
import com.pinpoint.listeners.BlockPlaceListener;
import com.pinpoint.listeners.ChatInputListener;
import com.pinpoint.listeners.PartyListener;
import com.pinpoint.listeners.TeleportCancelListener;
import com.pinpoint.listeners.WaypointInteractListener;
import com.pinpoint.util.TeleportHelper;
import org.bukkit.plugin.java.JavaPlugin;

public class PinpointPlugin extends JavaPlugin {

    private WaypointStorage waypointStorage;
    private WaypointManager waypointManager;
    private PartyStorage partyStorage;
    private PartyManager partyManager;
    private ItemManager itemManager;
    private EconomyManager economyManager;
    private GuiManager guiManager;
    private PartyGuiManager partyGuiManager;
    private TeleportHelper teleportHelper;
    private WaypointCommand commandHandler;
    private PartyCommand partyCommand;
    private ChatInputListener chatInputListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getLogger().info("Pinpoint v" + getDescription().getVersion() + " starting...");

        waypointStorage = new WaypointStorage(this);
        waypointStorage.load();

        waypointManager = new WaypointManager(this, waypointStorage);
        waypointManager.loadAll();

        partyStorage = new PartyStorage(this);
        partyStorage.load();

        partyManager = new PartyManager();
        for (com.pinpoint.data.Party party : partyStorage.loadAll()) {
            partyManager.loadParty(party);
        }
        getLogger().info("Loaded " + partyManager.getAllParties().size() + " parties.");

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
        partyGuiManager = new PartyGuiManager(this);

        commandHandler = new WaypointCommand(this);
        getCommand("waypoint").setExecutor(commandHandler);
        getCommand("waypoint").setTabCompleter(commandHandler);

        partyCommand = new PartyCommand(this);
        getCommand("party").setExecutor(partyCommand);
        getCommand("party").setTabCompleter(partyCommand);

        chatInputListener = new ChatInputListener(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new WaypointInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportCancelListener(this), this);
        getServer().getPluginManager().registerEvents(chatInputListener, this);
        getServer().getPluginManager().registerEvents(new PartyListener(this), this);

        getLogger().info("Pinpoint enabled with " + waypointManager.getAllWaypoints().size() + " waypoints loaded.");
    }

    @Override
    public void onDisable() {
        if (partyStorage != null && partyManager != null) {
            partyStorage.saveAll(partyManager.getAllParties());
        }
        getLogger().info("Pinpoint disabled.");
    }

    public void reload() {
        reloadConfig();
        waypointStorage.load();
        waypointManager.loadAll();
        partyStorage.load();
        partyManager.clear();
        for (com.pinpoint.data.Party party : partyStorage.loadAll()) {
            partyManager.loadParty(party);
        }
        economyManager.setup();
        getLogger().info("Pinpoint reloaded. Waypoints: " + waypointManager.getAllWaypoints().size()
                + ", Parties: " + partyManager.getAllParties().size());
    }

    public String msg(String key) {
        return getConfig().getString("messages." + key, "").replace("&", "§");
    }

    public String msgCfg(String key) {
        return getConfig().getString("messages." + key, "").replace("&", "§");
    }

    public WaypointManager getWaypointManager()   { return waypointManager; }
    public WaypointStorage getWaypointStorage()    { return waypointStorage; }
    public PartyManager getPartyManager()          { return partyManager; }
    public PartyStorage getPartyStorage()          { return partyStorage; }
    public ItemManager getItemManager()            { return itemManager; }
    public EconomyManager getEconomyManager()      { return economyManager; }
    public GuiManager getGuiManager()              { return guiManager; }
    public PartyGuiManager getPartyGuiManager()    { return partyGuiManager; }
    public TeleportHelper getTeleportHelper()      { return teleportHelper; }
    public WaypointCommand getCommandHandler()     { return commandHandler; }
    public PartyCommand getPartyCommand()          { return partyCommand; }
    public ChatInputListener getChatInputListener(){ return chatInputListener; }
}
