package me.pintoadmin.pintoMurderMystery;

import java.io.*;
import java.net.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.profile.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;

public class MurderMysteryGame {
    // Construction
    private final PintoMurderMystery plugin = PintoMurderMystery.getInstance();
    private final FileConfiguration configFile;
    private final ConfigurationSection config;
    private final String name;
    private final World world;
    private final boolean isolatePlayerlists;
    private final Scoreboard scoreboard;
    private final Team hideNameTeam;
    private final Team murdererTeam;

    public enum PlayerState {
        PLAYING,
        SPECTATING,
        LEFT,
    }

    public enum PlayerRole {
        INNOCENT,
        MURDERER,
        //        SHERIFF,
    }

    public final Map<Player, PlayerState> playerStates = new HashMap<>();
    public final Map<Player, PlayerRole> playerRoles = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    public final ItemStack knife;
    // Game settings
    private int gracePeriod;
    private int murderers;
    //    private final int sheriffs = 2;
    private int maxPlayers;
    private int coinsToBow;
    private boolean bowInstaShoot;
    private boolean deathWater;

    private Map<Vector, Integer[]> spawnLocations =
            new HashMap<>(); // List of spawn location vectors to facing coords (v[0]=x, v[1]=z)
    //        private boolean sheriffWrongGuessKills = true;

    // Game status
    private boolean playing;
    private boolean ended;

    public MurderMysteryGame(String name) {
        this.name = name;
        configFile = plugin.getConfig();
        config = configFile.getConfigurationSection(name);
        world = Bukkit.getWorld(name);
        isolatePlayerlists = configFile.getBoolean("isolateplayerlists");
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        hideNameTeam = scoreboard.registerNewTeam("innocents");
        hideNameTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        murdererTeam = scoreboard.registerNewTeam("murderers");
        murdererTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        murdererTeam.setColor(ChatColor.RED);

        init();
        knife = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = knife.getItemMeta();
        meta.setItemName(ChatColor.DARK_RED + "Knife");
        knife.setItemMeta(meta);
    }

    // Game operation methods
    private void init() {
        // Fetch all config values and store
        gracePeriod = config.getInt("graceperiod", 15);
        murderers = config.getInt("murderers", 2);
        maxPlayers = config.getInt("maxplayers", 16);
        coinsToBow = config.getInt("coinstobow", 10);
        bowInstaShoot = config.getBoolean("bowinstashoot", false);
        deathWater = config.getBoolean("deathwater", true);

        try {
            List<Map<?, ?>> spawnLocations = config.getMapList("spawnlocations");
            for (Map<?, ?> map : spawnLocations) {
                float x = (float) map.get("x");
                float y = (float) map.get("y");
                float z = (float) map.get("z");
                int fx = (int) map.get("fx");
                int fz = (int) map.get("fz");
                this.spawnLocations.put(new Vector(x, y, z), new Integer[] {fx, fz});
            }
        } catch (ClassCastException e) {
            plugin.getLogger().severe("Fix your config! Could not retrieve spawn locations");
        }
    }

    public void start() {
        playing = true;
        // Teleport all players to a random entry in a config list
        List<Player> playingPlayers =
                playerStates.keySet().stream()
                        .filter(u -> playerStates.get(u) == PlayerState.PLAYING)
                        .toList();
        Map.Entry<Vector, Integer[]>[] possibleLocations =
                spawnLocations.entrySet().toArray(new Map.Entry[0]);
        for (Player player : playingPlayers) {
            // Change skins and hide names
            try {
                PlayerProfile profile = player.getPlayerProfile();
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URI("https://textures.minecraft.net/texture/538d5d3d842fe200ae180d28c54238861d581c8629e42ac24c0d4dba3f5aeab4").toURL());
            } catch (URISyntaxException | MalformedURLException e){
                plugin.getLogger().severe("Could not load skin: "+e.getMessage());
                e.printStackTrace();
            }

            Random random = new Random();
            Map.Entry<Vector, Integer[]> randomEntry =
                    possibleLocations[random.nextInt(possibleLocations.length)];
            Location loc = randomEntry.getKey().toLocation(world);
            Vector facing =
                    new Vector(randomEntry.getValue()[0], loc.getY(), randomEntry.getValue()[1]);

            // Get direction from facing coords
            Vector direction = facing.clone().subtract(loc.toVector()).normalize();
            loc.setDirection(direction);

            // Teleport player
            player.teleport(loc);
        }
        // Wait 15-20s (configurable)
        runningTasks.add(
                new BukkitRunnable() {
                    int countdown = gracePeriod;
                    @Override
                    public void run() {
                        if (countdown > 0) {
                            for (Player player : playingPlayers) {
                                player.sendTitle(
                                        "",
                                        countdown + " seconds until roles are revealed",
                                        0,
                                        20,
                                        0);
                            }
                            countdown--;
                        } else {
                            // Announce roles and give murderers their weapons
                            Random random = new Random();
                            for (int i = 0; i < murderers; i++) {
                                Player randPlayer =
                                        playingPlayers.get(random.nextInt(playingPlayers.size()));
                                playerRoles.put(randPlayer, PlayerRole.MURDERER);
                            }
                            //                            for(int i = 0; i < sheriffs; i++) {
                            //                                Player randPlayer =
                            // playingPlayers.get(random.nextInt(playingPlayers.size()));
                            //                                playerRoles.put(randPlayer,
                            // PlayerRole.SHERIFF);
                            //                            }

                            for (Player player : playingPlayers) {
                                if (playerRoles.get(player) == PlayerRole.MURDERER) {
                                    List<Player> murderers =
                                            playerRoles.keySet().stream()
                                                    .filter(
                                                            p ->
                                                                    playerRoles.get(p)
                                                                            == PlayerRole.MURDERER)
                                                    .toList();
                                    List<Player> otherMurderers = new ArrayList<>(murderers);
                                    otherMurderers.remove(player);
                                    String companionString =
                                            murderers.size() > 1
                                                    ? "Your companion is: "
                                                            + otherMurderers.getFirst().getName()
                                                    : "Your companions are: "
                                                            + otherMurderers.stream()
                                                                    .map(Player::getName)
                                                                    .toString()
                                                                    .replace("[", "")
                                                                    .replace("]", "");
                                    player.sendTitle(
                                            ChatColor.DARK_RED + "" + ChatColor.BOLD + "MURDERER",
                                            ChatColor.DARK_GRAY
                                                    + "Kill all the innocents."
                                                    + companionString,
                                            0,
                                            60,
                                            0);
                                    murdererTeam.addEntry(player.getName());
                                    player.getInventory().addItem(knife);
                                } else if (playerRoles.get(player) == null){
                                    player.sendTitle(
                                            ChatColor.AQUA + "" + ChatColor.BOLD + "INNOCENT",
                                            ChatColor.DARK_GRAY
                                                    + "Survive and take care of the murderer",
                                            0,
                                            60,
                                            0);
                                    hideNameTeam.addEntry(player.getName());
                                }
                                //                                else if(playerRoles.get(player) ==
                                // PlayerRole.SHERIFF)
                                //
                                // player.sendTitle(ChatColor.GRAY+""+ChatColor.BOLD+"SHERIFF", "",
                                // 0, 60, 0);

                            }
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L)); // 20L = 1 second
    }

    public void end(boolean force) {
        // Reveal murderers
        List<Player> murderers = playerRoles.keySet().stream().filter(p -> playerRoles.get(p) == PlayerRole.MURDERER).toList();

        // Announce who won
        // Teleport all players to end after 15s
    }

    public void join(Player player) {
        // if playing add as spec
        if (playing) playerStates.put(player, PlayerState.SPECTATING);
        // if not add as player
        else playerStates.put(player, PlayerState.PLAYING);

        // Show/hide players (isolatedplayerlist)
        // Join messages

        // Check if game full -> start game
    }

    public void leave(Player player, World toWorld) {
        // remove player from game
        // Show/hide players (isolatedplayerlist)
        // leave messages
        // if player was playing, check if all murderers gone
    }

    public void onDeath(Player player) {
        // Set to spectate
        playerStates.put(player, PlayerState.SPECTATING);
    }

    /*
    public void newSheriff(Player player)
     */

    // Game status methods
    public boolean isPlaying() {
        return playing;
    }

    public boolean isEnded() {
        return ended;
    }

    // Utility methods

}
