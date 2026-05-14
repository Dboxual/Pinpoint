package com.waypointsystem.item;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemManager {

    private final WaypointPlugin plugin;

    public final NamespacedKey KEY_WAYPOINT_ITEM;
    public final NamespacedKey KEY_WAYPOINT_ID;
    public final NamespacedKey KEY_RECALL_ORB;
    public final NamespacedKey KEY_RECALL_ORB_ID;
    public final NamespacedKey KEY_RECALL_WAYPOINT_ID;
    public final NamespacedKey KEY_RECALL_WAYPOINT_NAME;
    public final NamespacedKey KEY_RECALL_OWNER;
    public final NamespacedKey KEY_RECALL_OWNER_NAME;

    public ItemManager(WaypointPlugin plugin) {
        this.plugin = plugin;
        KEY_WAYPOINT_ITEM      = new NamespacedKey(plugin, "waypoint_item");
        KEY_WAYPOINT_ID        = new NamespacedKey(plugin, "waypoint_id");
        KEY_RECALL_ORB         = new NamespacedKey(plugin, "recall_orb");
        KEY_RECALL_ORB_ID      = new NamespacedKey(plugin, "recall_orb_id");
        KEY_RECALL_WAYPOINT_ID = new NamespacedKey(plugin, "recall_waypoint_id");
        KEY_RECALL_WAYPOINT_NAME = new NamespacedKey(plugin, "recall_waypoint_name");
        KEY_RECALL_OWNER       = new NamespacedKey(plugin, "recall_owner");
        KEY_RECALL_OWNER_NAME  = new NamespacedKey(plugin, "recall_owner_name");
    }

    public void registerRecipes() {
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
        meta.displayName(text("Waypoint", NamedTextColor.AQUA));
        meta.lore(List.of(
                text("Place to set up a new waypoint.", NamedTextColor.GRAY),
                text("Right-click to begin naming.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(KEY_WAYPOINT_ITEM, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createNamedWaypointItem(UUID waypointId, String waypointName) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Waypoint: " + waypointName, NamedTextColor.AQUA));
        meta.lore(List.of(
                text("Right-click to open waypoint hub.", NamedTextColor.GRAY)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_WAYPOINT_ITEM, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_WAYPOINT_ID, PersistentDataType.STRING, waypointId.toString());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRecallOrb(UUID orbId, UUID waypointId, String waypointName,
                                     UUID ownerUuid, String ownerName, boolean canInvite) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Recall Orb: " + waypointName, NamedTextColor.LIGHT_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(text("Waypoint: " + waypointName, NamedTextColor.GRAY));
        lore.add(text("Owner: " + ownerName, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("Right-click: teleport to waypoint", NamedTextColor.YELLOW));
        if (canInvite) {
            lore.add(text("Right-click player: send teleport invite", NamedTextColor.YELLOW));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_RECALL_ORB, PersistentDataType.BYTE, (byte) 1);
        pdc.set(KEY_RECALL_ORB_ID, PersistentDataType.STRING, orbId.toString());
        pdc.set(KEY_RECALL_WAYPOINT_ID, PersistentDataType.STRING, waypointId.toString());
        pdc.set(KEY_RECALL_WAYPOINT_NAME, PersistentDataType.STRING, waypointName);
        pdc.set(KEY_RECALL_OWNER, PersistentDataType.STRING, ownerUuid.toString());
        pdc.set(KEY_RECALL_OWNER_NAME, PersistentDataType.STRING, ownerName);
        item.setItemMeta(meta);
        return item;
    }

    public void giveRecallOrb(Player player, Waypoint wp) {
        giveRecallOrbs(player, wp, 1);
    }

    public void giveRecallOrbs(Player player, Waypoint wp, int amount) {
        UUID orbId = UUID.randomUUID();
        wp.addRecallOrb(orbId);
        plugin.getWaypointManager().saveWaypoint(wp);
        ItemStack orb = createRecallOrb(orbId, wp.getId(), wp.getName(),
                player.getUniqueId(), player.getName(), true);
        orb.setAmount(Math.min(amount, 64));
        player.getInventory().addItem(orb);
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.getConfig().getString("messages.recall-orb-created",
                        "&aRecall Orb created for &b%s&a."), wp.getName()).replace("&", "§"));
    }

    // --- PDC reads ---

    public boolean isWaypointItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_WAYPOINT_ITEM, PersistentDataType.BYTE);
    }

    public boolean isNamedWaypointItem(ItemStack item) {
        if (!isWaypointItem(item)) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_WAYPOINT_ID, PersistentDataType.STRING);
    }

    public boolean isRecallOrb(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_RECALL_ORB, PersistentDataType.BYTE);
    }

    public String getWaypointId(ItemStack item) {
        if (!isNamedWaypointItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_WAYPOINT_ID, PersistentDataType.STRING);
    }

    public String getRecallWaypointId(ItemStack item) {
        if (!isRecallOrb(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_RECALL_WAYPOINT_ID, PersistentDataType.STRING);
    }

    public String getRecallOrbId(ItemStack item) {
        if (!isRecallOrb(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_RECALL_ORB_ID, PersistentDataType.STRING);
    }

    public String getRecallOwner(ItemStack item) {
        if (!isRecallOrb(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_RECALL_OWNER, PersistentDataType.STRING);
    }

    // --- Helper ---

    private Component text(String s, NamedTextColor color) {
        return Component.text(s).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
