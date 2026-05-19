package com.pinpoint.item;

import com.pinpoint.PinpointPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemManager {

    private final PinpointPlugin plugin;

    // PDC tag for the Lodestone placement item
    public final NamespacedKey KEY_WAYPOINT_BLOCK;
    // PDC tag for the Compass navigation item.
    // String value kept as "waypoint_pearl" — changing it would invalidate existing items.
    public final NamespacedKey KEY_WAYPOINT_COMPASS;

    public ItemManager(PinpointPlugin plugin) {
        this.plugin = plugin;
        KEY_WAYPOINT_BLOCK   = new NamespacedKey(plugin, "waypoint_block");
        KEY_WAYPOINT_COMPASS = new NamespacedKey(plugin, "waypoint_pearl");
    }

    public void registerRecipes() {
        // Waypoint Block: 8x Quartz + 1x Ender Eye -> tagged Lodestone
        NamespacedKey blockKey = new NamespacedKey(plugin, "waypoint_block_recipe");
        ShapedRecipe blockRecipe = new ShapedRecipe(blockKey, createWaypointBlockItem());
        blockRecipe.shape("QQQ", "QEQ", "QQQ");
        blockRecipe.setIngredient('Q', Material.QUARTZ);
        blockRecipe.setIngredient('E', Material.ENDER_EYE);
        plugin.getServer().addRecipe(blockRecipe);

        // Pinpoint Compass: 1x Ender Eye (top-center) + 1x Compass (center)
        NamespacedKey compassKey = new NamespacedKey(plugin, "waypoint_compass_recipe");
        ShapedRecipe compassRecipe = new ShapedRecipe(compassKey, createWaypointCompass());
        compassRecipe.shape(" E ", " C ", "   ");
        compassRecipe.setIngredient('E', Material.ENDER_EYE);
        compassRecipe.setIngredient('C', Material.COMPASS);
        plugin.getServer().addRecipe(compassRecipe);

        plugin.getLogger().info("Crafting recipes registered (waypoint block + pinpoint compass).");
    }

    // --- Waypoint Block Item ---

    public ItemStack createWaypointBlockItem() {
        ItemStack item = new ItemStack(Material.LODESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Pinpoint", NamedTextColor.AQUA));
        meta.lore(List.of(
                text("Place to create a new Pinpoint.", NamedTextColor.GRAY),
                text("Right-click the placed block to manage.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(KEY_WAYPOINT_BLOCK, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWaypointBlockItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_WAYPOINT_BLOCK, PersistentDataType.BYTE);
    }

    // --- Pinpoint Compass ---

    public ItemStack createWaypointCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Pinpoint Compass", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                text("Right-click: open accessible Pinpoints", NamedTextColor.GRAY),
                text("Right-click player: invite to a Pinpoint", NamedTextColor.DARK_GRAY),
                text("Shift+right-click player: invite or remove access", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(KEY_WAYPOINT_COMPASS, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Returns true only for COMPASS items carrying the Pinpoint PDC tag. */
    public boolean isWaypointCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_WAYPOINT_COMPASS, PersistentDataType.BYTE);
    }

    public void giveWaypointCompass(Player player) {
        giveWaypointCompasses(player, 1);
    }

    public void giveWaypointCompasses(Player player, int amount) {
        ItemStack compass = createWaypointCompass();
        compass.setAmount(Math.min(amount, 64));
        player.getInventory().addItem(compass);
    }

    // --- Helper ---

    private Component text(String s, NamedTextColor color) {
        return Component.text(s).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
