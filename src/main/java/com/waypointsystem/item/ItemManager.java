package com.waypointsystem.item;

import com.waypointsystem.WaypointPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class ItemManager {

    private final WaypointPlugin plugin;

    // PDC keys
    public final NamespacedKey KEY_WAYPOINT_ITEM;
    public final NamespacedKey KEY_WAYPOINT_ID;
    public final NamespacedKey KEY_RECALL_ORB;
    public final NamespacedKey KEY_RECALL_ORB_ID;
    public final NamespacedKey KEY_RECALL_WAYPOINT_ID;
    public final NamespacedKey KEY_RECALL_WAYPOINT_NAME;
    public final NamespacedKey KEY_RECALL_OWNER;

    public ItemManager(WaypointPlugin plugin) {
        this.plugin = plugin;
        KEY_WAYPOINT_ITEM = new NamespacedKey(plugin, "waypoint_item");
        KEY_WAYPOINT_ID = new NamespacedKey(plugin, "waypoint_id");
        KEY_RECALL_ORB = new NamespacedKey(plugin, "recall_orb");
        KEY_RECALL_ORB_ID = new NamespacedKey(plugin, "recall_orb_id");
        KEY_RECALL_WAYPOINT_ID = new NamespacedKey(plugin, "recall_waypoint_id");
        KEY_RECALL_WAYPOINT_NAME = new NamespacedKey(plugin, "recall_waypoint_name");
        KEY_RECALL_OWNER = new NamespacedKey(plugin, "recall_owner");
    }

    public void registerRecipes() {
        // Waypoint item recipe: compass + ender eye center, surrounded by quartz
        ItemStack waypointItem = createUnnamedWaypointItem();
        NamespacedKey recipeKey = new NamespacedKey(plugin, "waypoint_item");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, waypointItem);
        recipe.shape("QQQ", "QEQ", "QQQ");
        recipe.setIngredient('Q', Material.QUARTZ);
        recipe.setIngredient('E', Material.ENDER_EYE);
        plugin.getServer().addRecipe(recipe);
    }

    public ItemStack createUnnamedWaypointItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Waypoint")
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("Place to set up a new waypoint.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_WAYPOINT_ITEM, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createNamedWaypointItem(UUID waypointId, String waypointName) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Waypoint: " + waypointName)
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("Right-click to open waypoint hub.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_WAYPOINT_ITEM, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_WAYPOINT_ID, PersistentDataType.STRING, waypointId.toString());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRecallOrb(UUID orbId, UUID waypointId, String waypointName, UUID ownerUuid) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Recall Orb: " + waypointName)
                .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("Linked to: " + waypointName)
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("Right-click to teleport.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("Right-click a player to invite them.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_RECALL_ORB, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_RECALL_ORB_ID, PersistentDataType.STRING, orbId.toString());
        pdc.set(KEY_RECALL_WAYPOINT_ID, PersistentDataType.STRING, waypointId.toString());
        pdc.set(KEY_RECALL_WAYPOINT_NAME, PersistentDataType.STRING, waypointName);
        pdc.set(KEY_RECALL_OWNER, PersistentDataType.STRING, ownerUuid.toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWaypointItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KEY_WAYPOINT_ITEM, PersistentDataType.BYTE);
    }

    public boolean isNamedWaypointItem(ItemStack item) {
        if (!isWaypointItem(item)) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KEY_WAYPOINT_ID, PersistentDataType.STRING);
    }

    public boolean isRecallOrb(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KEY_RECALL_ORB, PersistentDataType.BYTE);
    }

    public String getWaypointId(ItemStack item) {
        if (!isNamedWaypointItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY_WAYPOINT_ID, PersistentDataType.STRING);
    }

    public String getRecallWaypointId(ItemStack item) {
        if (!isRecallOrb(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY_RECALL_WAYPOINT_ID, PersistentDataType.STRING);
    }

    public String getRecallOrbId(ItemStack item) {
        if (!isRecallOrb(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY_RECALL_ORB_ID, PersistentDataType.STRING);
    }

    public void giveRecallOrb(org.bukkit.entity.Player player, com.waypointsystem.data.Waypoint wp) {
        UUID orbId = UUID.randomUUID();
        wp.addRecallOrb(orbId);
        plugin.getWaypointManager().saveWaypoint(wp);
        ItemStack orb = createRecallOrb(orbId, wp.getId(), wp.getName(), player.getUniqueId());
        player.getInventory().addItem(orb);
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.getConfig().getString("messages.recall-orb-created", "&aRecall Orb created for %s."),
                        wp.getName()).replace("&", "§"));
    }

    public String getRecallOwner(ItemStack item) {
        if (!isRecallOrb(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY_RECALL_OWNER, PersistentDataType.STRING);
    }
}
