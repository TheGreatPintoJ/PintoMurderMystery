package me.pintoadmin.pintoMurderMystery;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class PintoMurderMystery extends JavaPlugin {
    private final Map<String, MurderMysteryGame> games = new HashMap<>(); // world to game instance

    public ConfigLoader configLoader;

    private GameListeners gameListeners;

    @Override
    public void onEnable() {
        // Save default config
        File configFile = new File(getDataFolder() + "/config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        // Load classes
        FileConfiguration config = getConfig();
        configLoader = new ConfigLoader(this, config);

        new MMCommand();

        // Load config games
        configLoader.start();

        // Initialize Game objects for each game/world
        for (String gameName : configLoader.getEnabledGames()) {
            getLogger().warning("Enabled games: " + configLoader.getEnabledGames());
            games.put(gameName, new MurderMysteryGame(gameName));
        }

        // Listeners
        gameListeners = new GameListeners(this);
    }

    @Override
    public void onDisable() {
        for (Map.Entry<String, MurderMysteryGame> entry : games.entrySet()) {
            String key = entry.getKey();
            MurderMysteryGame game = entry.getValue();
            if (game.isPlaying()) {
                game.end(true);
            }
        }
        // Clear game map
        games.clear();

        // Unregister all event listeners to prevent memory leaks
        HandlerList.unregisterAll(gameListeners);

        getLogger().info("PintoMurderMystery plugin disabled and cleaned up");
    }

    public void debug(String... messages) {
        String finalString = "[DEBUG]";
        for (String msg : messages) {
            finalString += " " + msg;
        }
        getLogger().info(finalString);
    }

    public MurderMysteryGame getGame(String name) {
        return games.get(name);
    }

    public static PintoMurderMystery getInstance() {
        return getPlugin(PintoMurderMystery.class);
    }
}
