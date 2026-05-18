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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemManager {

    private final PinpointPlugin plugin;

    // Waypoint block item — PDC tag for the Lodestone item in inventory
    public final NamespacedKey KEY_WAYPOINT_BLOCK;
    // Waypoint Compass — PDC tag for the navigation compass item.
    // Key name kept as "waypoint_pearl" so old tagged Ender Pearl items are still recognised.
    public final NamespacedKey KEY_WAYPOINT_PEARL;

    public ItemManager(PinpointPlugin plugin) {
        this.plugin = plugin;
        KEY_WAYPOINT_BLOCK = new NamespacedKey(plugin, "waypoint_block");
        KEY_WAYPOINT_PEARL = new NamespacedKey(plugin, "waypoint_pearl");
    }

    public void registerRecipes() {
        // Waypoint Block: 8x Quartz + 1x Ender Eye -> Lodestone (tagged)
        NamespacedKey blockKey = new NamespacedKey(plugin, "waypoint_block_recipe");
        ShapedRecipe blockRecipe = new ShapedRecipe(blockKey, createWaypointBlockItem());
        blockRecipe.shape("QQQ", "QEQ", "QQQ");
        blockRecipe.setIngredient('Q', Material.QUARTZ);
        blockRecipe.setIngredient('E', Material.ENDER_EYE);
        plugin.getServer().addRecipe(blockRecipe);

        // Waypoint Compass: 4x Compass (corners) + 1x Ender Eye (center)
        NamespacedKey pearlKey = new NamespacedKey(plugin, "waypoint_pearl_recipe");
        ShapedRecipe pearlRecipe = new ShapedRecipe(pearlKey, createWaypointPearl());
        pearlRecipe.shape("C C", " E ", "C C");
        pearlRecipe.setIngredient('C', Material.COMPASS);
        pearlRecipe.setIngredient('E', Material.ENDER_EYE);
        plugin.getServer().addRecipe(pearlRecipe);

        plugin.getLogger().info("Crafting recipes registered (waypoint block + waypoint compass).");
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

    // --- Waypoint Compass ---

    public ItemStack createWaypointPearl() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Pinpoint Compass", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                text("Right-click: open accessible Pinpoints", NamedTextColor.GRAY),
                text("Right-click player: invite to a Pinpoint", NamedTextColor.DARK_GRAY),
                text("Shift+right-click player: invite or remove access", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(KEY_WAYPOINT_PEARL, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWaypointPearl(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_WAYPOINT_PEARL, PersistentDataType.BYTE);
    }

    public void giveWaypointPearl(Player player) {
        giveWaypointPearls(player, 1);
    }

    public void giveWaypointPearls(Player player, int amount) {
        ItemStack pearl = createWaypointPearl();
        pearl.setAmount(Math.min(amount, 64));
        player.getInventory().addItem(pearl);
    }

    // --- Helper ---

    private Component text(String s, NamedTextColor color) {
        return Component.text(s).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
