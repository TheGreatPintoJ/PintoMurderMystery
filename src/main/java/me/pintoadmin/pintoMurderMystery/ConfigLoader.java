package me.pintoadmin.pintoMurderMystery;

import java.util.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;

public class ConfigLoader {
    private final PintoMurderMystery pintoMurderMystery;
    private FileConfiguration config;

    public ConfigLoader(PintoMurderMystery pintoMurderMystery) {
        this.pintoMurderMystery = pintoMurderMystery;
        start();
    }

    public void start() {
        config = pintoMurderMystery.getConfig();
    }

    public Map<String, Boolean> getGamerules(String world) {
        Map<String, Boolean> result = new HashMap<>();
        ConfigurationSection worldSection = config.getConfigurationSection(world);
        if (worldSection == null) return new HashMap<>();
        ConfigurationSection gamerules = worldSection.getConfigurationSection("gamerules");
        if (gamerules == null) return new HashMap<>();
        for (String key : gamerules.getKeys(false)) {
            Object value = gamerules.get(key);
            if (value instanceof Boolean) {
                result.put(key, (Boolean) value);
            }
        }
        return result;
    }

    public List<String> getEnabledGames() {
        List<String> list = new ArrayList<>();
        for (String key : config.getKeys(false))
            if (config.isConfigurationSection(key))
                if (config.getBoolean(key + ".enabled", false)) list.add(key);
        return list;
    }

    public List<String> getAllGames() {
        List<String> list = new ArrayList<>();
        for (String key : config.getKeys(false))
            if (config.isConfigurationSection(key)) list.add(key);
        return list;
    }

    public List<Map<String, Object>> getSpawnLocations(String game) {
        List<Map<String, Object>> finalList = new LinkedList<>();
        for (Map<?, ?> map : config.getMapList(game + ".spawnlocations")) {
            try {
                Map<String, Object> newMap = (Map<String, Object>) map;
                finalList.add(newMap);
            } catch (ClassCastException _) {
            }
        }
        return finalList;
    }

    private List<ConfigurationSection> getAllSections(FileConfiguration config) {
        List<ConfigurationSection> finalList = new ArrayList<>();
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null && section.contains("enabled")) {
                finalList.add(section);
            }
        }
        return finalList;
    }

    public void set(String path, Object value) {
        config.set(path, value);
        pintoMurderMystery.saveConfig();
    }
}
