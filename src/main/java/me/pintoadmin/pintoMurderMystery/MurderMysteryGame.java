package me.pintoadmin.pintoMurderMystery;

import org.bukkit.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;

public class MurderMysteryGame {
    // Construction
    private final PintoMurderMystery plugin;
    private final FileConfiguration configFile;
    private final ConfigurationSection config;
    private final String name;
    private final World world;
    // Game settings
    private int murderers = 2;
//    private final int sheriffs = 2;
    private int maxPlayers = 16;
    private int coinsToBow = 10;
    private boolean deathWater = true;
    // Game status
    private boolean playing;
    private boolean ended;

    public MurderMysteryGame(PintoMurderMystery plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        configFile = plugin.getConfig();
        config = configFile.getConfigurationSection(name);
        world = Bukkit.getWorld(name);
        init();
    }

    // Game operation methods
    private void init(){
        // Fetch all config values and store
    }
    public void start(){
        // Teleport all players to a random entry in a config list
        // Wait 15-20s (configurable)
        // Announce roles and give murderers their weapons
    }
    public void end(){
        // Teleport all players to end zone w/ leaderboards and stuff
        // Reveal murderers
        // Announce who won
    }
    public void join(Player player){
        // if playing add as spec
        // if not add as player
    }
    public void leave(Player player){
        // remove player from game
        // if player was playing, check if all murderers gone
    }
    public void onDeath(Player player){
        // Set to spectate
    }
    /*
    public void newSheriff(Player player)
     */

    // Game status methods
    public boolean isPlaying(){
        return playing;
    }
    public boolean isEnded(){
        return ended;
    }

    // Utility methods

}
