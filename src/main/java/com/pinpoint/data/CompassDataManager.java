package com.pinpoint.data;

import com.pinpoint.PinpointPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CompassDataManager {

    private final PinpointPlugin plugin;
    private final File file;
    private final Set<UUID> received = new HashSet<>();

    public CompassDataManager(PinpointPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "compass-received.yml");
    }

    public void load() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create compass-received.yml: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        received.clear();
        if (yaml.isConfigurationSection("players")) {
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                try {
                    if (yaml.getBoolean("players." + key, false)) {
                        received.add(UUID.fromString(key));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("Compass distribution data loaded for " + received.size() + " player(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (UUID uuid : received) {
            yaml.set("players." + uuid, true);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save compass-received.yml: " + e.getMessage());
        }
    }

    public boolean hasReceived(UUID uuid) {
        return received.contains(uuid);
    }

    public void markReceived(UUID uuid) {
        received.add(uuid);
        save();
    }
}
