package com.pinpoint.data;

import com.pinpoint.PinpointPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PartyStorage {

    private final PinpointPlugin plugin;
    private File dataFile;
    private YamlConfiguration data;

    public PartyStorage(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "parties.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create parties.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save parties.yml", e);
        }
    }

    public Collection<Party> loadAll() {
        List<Party> result = new ArrayList<>();
        if (!data.contains("parties")) return result;
        for (String key : data.getConfigurationSection("parties").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<String> memberStrs = data.getStringList("parties." + key + ".members");
                Set<UUID> members = new HashSet<>();
                for (String s : memberStrs) {
                    try { members.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                if (members.size() >= 2) {
                    result.add(new Party(id, members));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load party " + key + ": " + e.getMessage());
            }
        }
        return result;
    }

    public void saveParty(Party party) {
        List<String> members = new ArrayList<>();
        party.getMembers().forEach(u -> members.add(u.toString()));
        data.set("parties." + party.getId() + ".members", members);
        save();
    }

    public void deleteParty(UUID partyId) {
        data.set("parties." + partyId, null);
        save();
    }

    /** Overwrites the entire parties section (used after merges/disbands). */
    public void saveAll(Collection<Party> parties) {
        data.set("parties", null);
        for (Party party : parties) {
            List<String> members = new ArrayList<>();
            party.getMembers().forEach(u -> members.add(u.toString()));
            data.set("parties." + party.getId() + ".members", members);
        }
        save();
    }
}
