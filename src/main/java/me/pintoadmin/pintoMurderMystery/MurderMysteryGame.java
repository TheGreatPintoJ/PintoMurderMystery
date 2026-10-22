package me.pintoadmin.pintoMurderMystery;

import static java.lang.Math.*;

import java.net.*;
import java.util.*;
import java.util.Random;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.entity.model.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.*;
import org.bukkit.profile.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;
import org.joml.*;

public final class MurderMysteryGame {
    // Construction
    private final PintoMurderMystery plugin = PintoMurderMystery.getInstance();
    private final FileConfiguration configFile;
    private final ConfigurationSection config;
    private final String name;
    private final World world;
    private final boolean isolatePlayerlists;
    private final Scoreboard scoreboard;

    public enum PlayerState {
        PLAYING,
        SPECTATING,
        LEFT,
    }

    public enum PlayerRole {
        INNOCENT,
        MURDERER,
        SHERIFF,
    }

    public final Map<Player, PlayerState> playerStates = new HashMap<>();
    public final Map<Player, PlayerRole> playerRoles = new HashMap<>();
    private final Team innocentTeam;
    private final Team murdererTeam;
    private final List<BukkitTask> runningTasks = new ArrayList<>();
    private BukkitTask coinTask;
    public final List<Item> coinEntities = new ArrayList<>();
    private final List<BukkitTask> giveKnifeTasks = new ArrayList<>();
    private final List<ItemDisplay> knifeEntities = new ArrayList<>();
    public final List<Arrow> shotArrows = new ArrayList<>();
    private final List<Mannequin> deadBodies = new ArrayList<>();

    public final ItemStack knife;
    public final ItemStack coin;
    public final ItemStack zapper;
    // Game settings
    private int gracePeriod;
    private int murderers;
    private int sheriffs;
    private int maxPlayers;
    private int coinsToBow;
    private boolean bowInstaShoot;
    private boolean deathWater;
    private boolean canDropItems;
    private int coinSpawnFrequency;
    private int maxCoins;

    private long timeOfDay;
    private String weather;

    private Location endLocation;
    private Map<Vector, Double[]> spawnLocations =
            new HashMap<>(); // List of spawn location vectors to facing coords (v[0]=x, v[1]=z)
    private Map<Vector, Integer> coinLocations = new HashMap<>(); // Location to radius
    private boolean wrongGuessKills = true;

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

        innocentTeam = scoreboard.registerNewTeam("innocents");
        innocentTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        innocentTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        murdererTeam = scoreboard.registerNewTeam("murderers");
        murdererTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        innocentTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        murdererTeam.setColor(ChatColor.RED);

        init();
        knife = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = knife.getItemMeta();
        assert meta != null;
        meta.setItemName(ChatColor.DARK_RED + "Knife");
        knife.setItemMeta(meta);

        coin = new ItemStack(Material.GOLD_NUGGET);
        meta = coin.getItemMeta();
        assert meta != null;
        meta.setItemName(ChatColor.GOLD + "Gold Coin");
        coin.setItemMeta(meta);

        zapper = new ItemStack(Material.BOW);
        meta = zapper.getItemMeta();
        assert meta != null;
        meta.setItemName(ChatColor.AQUA + "Zapper");
        zapper.setItemMeta(meta);
    }

    // Game operation methods
    private void init() {
        // Fetch all config values and store
        gracePeriod = config.getInt("graceperiod", 15);
        murderers = config.getInt("murderers", 2);
        sheriffs = config.getInt("sheriffs", 2);
        maxPlayers = config.getInt("maxplayers", 16);
        coinsToBow = config.getInt("coinstobow", 10);
        bowInstaShoot = config.getBoolean("bowinstashoot", false);
        deathWater = config.getBoolean("deathwater", true);
        wrongGuessKills = config.getBoolean("wrongguesskills", true);
        canDropItems = config.getBoolean("candrop", false);
        maxCoins = config.getInt("maxcoins", 200);

        timeOfDay = config.getLong("timeofday", 6000);
        weather = config.getString("weather", "clear");

        endLocation =
                getVector("endlocation")
                        .toLocation(Bukkit.getWorld(config.getString("endlocation.world")));

        coinSpawnFrequency = config.getInt("coinspawnfrequency");

        try {
            List<Map<?, ?>> spawnLocations = config.getMapList("spawnlocations");
            for (Map<?, ?> map : spawnLocations) {
                double x = (double) map.get("x");
                double y = (double) map.get("y");
                double z = (double) map.get("z");
                double fx = (double) map.get("fx");
                double fz = (double) map.get("fz");
                this.spawnLocations.put(new Vector(x, y, z), new Double[] {fx, fz});
            }
        } catch (ClassCastException e) {
            plugin.getLogger()
                    .severe(
                            "Fix your config! Could not retrieve spawn locations: "
                                    + e.getMessage());
        }
        try {
            List<Map<?, ?>> coinlocations = config.getMapList("coinlocations");
            for (Map<?, ?> map : coinlocations) {
                double x = (double) map.get("x");
                double y = (double) map.get("y");
                double z = (double) map.get("z");
                int r = (int) map.get("r");
                this.coinLocations.put(new Vector(x, y, z), r);
            }
        } catch (ClassCastException e) {
            plugin.getLogger()
                    .severe(
                            "Fix your config! Could not retrieve coin locations: "
                                    + e.getMessage());
        }
    }

    public void start() {
        playing = true;
        world.setGameRule(GameRule.PVP, false);

        switch (weather) {
            case "rain":
                world.setStorm(true);
                world.setThundering(false);
                break;
            case "thunder":
                world.setStorm(true);
                world.setThundering(true);
                break;
            case "clear":
                world.setStorm(false);
                world.setThundering(false);
                break;
        }
        world.setTime(timeOfDay);

        // Teleport all players to a random entry in a config list
        List<Player> playingPlayers =
                playerStates.keySet().stream()
                        .filter(u -> playerStates.get(u) == PlayerState.PLAYING)
                        .toList();
        Map.Entry<Vector, Double[]>[] possibleLocations =
                spawnLocations.entrySet().toArray(new Map.Entry[0]);
        for (Player player : playingPlayers) {
            // Change skins and hide names
            try {
                PlayerProfile profile = player.getPlayerProfile();
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(
                        new URI(
                                        "https://textures.minecraft.net/texture/538d5d3d842fe200ae180d28c54238861d581c8629e42ac24c0d4dba3f5aeab4")
                                .toURL());
            } catch (URISyntaxException | MalformedURLException e) {
                plugin.getLogger().severe("Could not load skin: " + e.getMessage());
                e.printStackTrace();
            }

            player.getInventory().clear();

            Random random = new Random();
            Map.Entry<Vector, Double[]> randomEntry =
                    possibleLocations[random.nextInt(possibleLocations.length)];
            Location loc = randomEntry.getKey().toLocation(world);
            Vector facing =
                    new Vector(randomEntry.getValue()[0], loc.getY(), randomEntry.getValue()[1]);

            // Get direction from facing coords
            Vector direction = facing.clone().subtract(loc.toVector()).normalize();
            loc.setDirection(direction);

            // Teleport player
            player.teleport(loc);
            innocentTeam.addEntry(player.getName());
            player.getActivePotionEffects().clear();
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
                                        String.valueOf(countdown),
                                        "seconds until roles are revealed",
                                        0,
                                        25,
                                        0);
                            }
                            countdown--;
                        } else {
                            // Announce roles and give murderers their weapons
                            Random random = new Random();
                            List<Player> availablePlayers = new ArrayList<>(playingPlayers);
                            for (int i = 0; i < murderers; i++) {
                                Player randPlayer =
                                        playingPlayers.get(random.nextInt(playingPlayers.size()));
                                playerRoles.put(randPlayer, PlayerRole.MURDERER);
                            }
                            availablePlayers.removeAll(playerRoles.keySet());
                            for (int i = 0; i < sheriffs; i++) {
                                Player randPlayer =
                                        availablePlayers.get(
                                                random.nextInt(availablePlayers.size()));
                                playerRoles.put(randPlayer, PlayerRole.SHERIFF);
                            }

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
                                            murderers.size() == 2
                                                    ? "Your companion is: "
                                                            + otherMurderers.getFirst().getName()
                                                    : murderers.size() >= 3
                                                            ? "Your companions are: "
                                                                    + otherMurderers.stream()
                                                                            .map(Player::getName)
                                                                            .toList()
                                                                            .toString()
                                                                            .replace("[", "")
                                                                            .replace("]", "")
                                                            : "";
                                    player.sendTitle(
                                            ChatColor.DARK_RED + "" + ChatColor.BOLD + "MURDERER",
                                            ChatColor.DARK_GRAY
                                                    + "Kill all the innocents"
                                                    + companionString,
                                            0,
                                            60,
                                            0);
                                    innocentTeam.removeEntry(player.getName());
                                    murdererTeam.addEntry(player.getName());
                                    player.getInventory().addItem(knife);
                                } else if (playerRoles.get(player) == null) {
                                    player.sendTitle(
                                            ChatColor.GREEN + "" + ChatColor.BOLD + "INNOCENT",
                                            ChatColor.DARK_GRAY + "Survive for as long as possible",
                                            0,
                                            60,
                                            0);
                                    innocentTeam.addEntry(player.getName());
                                    playerRoles.put(player, PlayerRole.INNOCENT);
                                } else if (playerRoles.get(player) == PlayerRole.SHERIFF) {
                                    player.sendTitle(
                                            ChatColor.AQUA + "" + ChatColor.BOLD + "SHERIFF",
                                            "Find and shoot the Murderers",
                                            0,
                                            60,
                                            0);
                                    player.getInventory().addItem(zapper);
                                    player.getInventory().addItem(new ItemStack(Material.ARROW));
                                }
                            }
                            world.setGameRule(GameRule.PVP, true);
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L)); // 20L = 1 second

        coinTask =
                new BukkitRunnable() {
                    final Random random = new Random();

                    @Override
                    public void run() {
                        if (coinEntities.size() >= maxCoins) {
                            plugin.getLogger()
                                    .warning(
                                            "Too many coins in game: "
                                                    + name
                                                    + " - clearing coins...");
                            coinEntities.forEach(Item::remove);
                            coinEntities.clear();
                        }
                        if (coinLocations.isEmpty()) return;
                        Vector chosenLocation =
                                coinLocations.keySet().stream()
                                        .toList()
                                        .get(random.nextInt(coinLocations.size()));
                        int radius = coinLocations.get(chosenLocation);

                        List<Location> possibleRadius = new ArrayList<>();
                        for (int x = -radius; x <= radius; x++) {
                            for (int y = -radius; y <= radius; y++) {
                                for (int z = -radius; z <= radius; z++) {
                                    if (x * x + y * y + z * z <= radius * radius) {
                                        Location loc =
                                                new Vector(
                                                                chosenLocation.getX() + x,
                                                                chosenLocation.getY() + y,
                                                                chosenLocation.getZ() + z)
                                                        .toLocation(world);
                                        // Filter to air blocks with non-air below them
                                        if (!loc.clone()
                                                        .subtract(0, 1, 0)
                                                        .getBlock()
                                                        .getType()
                                                        .isAir()
                                                && loc.getBlock().getType().isAir()) {
                                            if (!loc.getBlock().getType().isOccluding())
                                                loc.subtract(0, 0.75, 0);
                                            possibleRadius.add(loc);
                                        }
                                    }
                                }
                            }
                        }

                        if (possibleRadius.isEmpty()) return;
                        Location chosenBlock =
                                possibleRadius.get(random.nextInt(possibleRadius.size()));

                        coinEntities.add(
                                world.spawn(
                                        chosenBlock,
                                        Item.class,
                                        i -> {
                                            i.setItemStack(coin);
                                            i.setPickupDelay(5);
                                        }));
                    }
                }.runTaskTimer(plugin, 0L, coinSpawnFrequency);
    }

    public void end(boolean force) {
        playing = false;
        ended = true;
        // Reveal murderers
        List<Player> livingMurderers =
                playerRoles.keySet().stream()
                        .filter(
                                p ->
                                        playerRoles.get(p) == PlayerRole.MURDERER
                                                && playerStates.get(p) == PlayerState.PLAYING)
                        .toList();
        livingMurderers.forEach(
                p ->
                        p.addPotionEffect(
                                new PotionEffect(PotionEffectType.GLOWING, 60, 1, false, false)));
        // Announce who won
        List<Player> allPlayers = playerRoles.keySet().stream().toList();
        if (livingMurderers.isEmpty()) {
            for (Player player : allPlayers) {
                player.sendTitle(
                        ChatColor.GREEN + "INNOCENTS WIN",
                        ChatColor.GRAY + "The Murderers have been stopped",
                        0,
                        100,
                        0);
            }
        } else {
            for (Player player : allPlayers) {
                player.sendTitle(
                        ChatColor.DARK_RED + "MURDERERS WIN",
                        ChatColor.GRAY + "The Murderers have killed all of the Innocents",
                        0,
                        100,
                        0);
            }
        }
        // Teleport all players to end after 3s
        BukkitRunnable cleanupTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        giveKnifeTasks.forEach(BukkitTask::cancel);
                        giveKnifeTasks.clear();
                        knifeEntities.forEach(ItemDisplay::remove);
                        knifeEntities.clear();
                        coinTask.cancel();
                        coinEntities.forEach(Item::remove);
                        coinEntities.clear();
                        shotArrows.forEach(Arrow::remove);
                        shotArrows.clear();
                        deadBodies.forEach(Mannequin::remove);
                        deadBodies.clear();
                        for (Player player : world.getPlayers()) {
                            innocentTeam.removeEntry(player.getName());
                            murdererTeam.removeEntry(player.getName());
                            player.teleport(endLocation);
                            player.setGameMode(GameMode.SURVIVAL);
                            player.getInventory().clear();
                        }
                        playerRoles.clear();
                        ended = false;
                    }
                };
        if (force) cleanupTask.run();
        else runningTasks.add(cleanupTask.runTaskLater(plugin, 60L));
    }

    public void join(Player player) {
        // if not playing add as player
        if (!playing) {
            playerStates.put(player, PlayerState.PLAYING);
            // Show/hide players (isolatedplayerlist)
            // Join messages
            for (Player oldPlayer : player.getWorld().getPlayers()) {
                oldPlayer.sendMessage(
                        player.getName()
                                + ChatColor.YELLOW
                                + " has joined the game "
                                + ChatColor.AQUA
                                + "(%s/%s)"
                                        .formatted(
                                                player.getWorld().getPlayers().size(), maxPlayers));
            }

            // Check if game full -> start game
            if (playerStates.keySet().stream()
                            .filter(p -> playerStates.get(p) == PlayerState.PLAYING)
                            .toList()
                            .size()
                    >= maxPlayers) start();
        }
        // if so add as spec
        else playerStates.put(player, PlayerState.SPECTATING);
        player.setScoreboard(scoreboard);
    }

    public void leave(Player player, World toWorld) {
        // remove player from game
        playerStates.remove(player);
        playerRoles.remove(player);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        // Show/hide players (isolatedplayerlist)

        // leave messages
        for (Player oldPlayer : world.getPlayers()) {
            if (ended) continue;
            if (!playing) {
                oldPlayer.sendMessage(
                        player.getName()
                                + ChatColor.YELLOW
                                + " has left the game "
                                + ChatColor.AQUA
                                + String.format("(%s/%s)", world.getPlayers().size(), maxPlayers));
            } else oldPlayer.sendMessage(player.getName() + ChatColor.YELLOW + " has left");
        }
        // if player was playing, check if all murderers gone
        if (playerStates.get(player) == PlayerState.PLAYING) {
            List<Player> playingMurderers =
                    playerRoles.keySet().stream()
                            .filter(
                                    p ->
                                            playerRoles.get(p) == PlayerRole.MURDERER
                                                    && playerStates.get(p) == PlayerState.PLAYING)
                            .toList();
            if (playingMurderers.isEmpty()) end(false);
        }
    }

    public void onDeath(Player player, PlayerDeathEvent event) {
        // Set to spectate
        playerStates.put(player, PlayerState.SPECTATING);
        event.setDeathMessage(null);
        event.getDrops().clear();
        Location loc = player.getLocation();

        List<Player> livingPlayers =
                playerStates.keySet().stream()
                        .filter(
                                p ->
                                        playerStates.get(p) == PlayerState.PLAYING
                                                && playerRoles.get(p) != PlayerRole.MURDERER)
                        .toList();
        List<Player> livingMurderers =
                playerStates.keySet().stream()
                        .filter(
                                p ->
                                        playerStates.get(p) == PlayerState.PLAYING
                                                && playerRoles.get(p) == PlayerRole.MURDERER)
                        .toList();
        if (livingPlayers.isEmpty() || livingMurderers.isEmpty()) end(false);

        spawnDeadBody(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(loc);
            }
        }.runTask(plugin);
    }

    public void damaged(Player victim, Player attacker, EntityDamageByEntityEvent event) {
        event.setCancelled(true);

        if (playerStates.get(victim) != PlayerState.PLAYING
                || playerStates.get(attacker) != PlayerState.PLAYING) return;

        if (playerRoles.get(attacker) == PlayerRole.MURDERER
                && playerRoles.get(victim) != PlayerRole.MURDERER
                && attacker.getInventory().getItemInMainHand().isSimilar(knife)) {
            victim.damage(victim.getAttribute(Attribute.MAX_HEALTH).getValue());
            playerStates.keySet().stream()
                    .filter(p -> playerStates.get(p) != PlayerState.LEFT)
                    .forEach(p -> p.playSound(p, Sound.ENTITY_PLAYER_HURT, 0.5f, 0.1f));
        } else if (playerRoles.get(attacker) != PlayerRole.MURDERER) {
            if (event.getDamageSource().getDamageType() != DamageType.ARROW) return;
            event.getDamageSource().getDirectEntity().remove();

            if (playerRoles.get(victim) != PlayerRole.MURDERER && wrongGuessKills)
                attacker.damage(attacker.getAttribute(Attribute.MAX_HEALTH).getValue());
            else victim.damage(victim.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
    }

    public void tryThrowKnife(Player player) {
        if (playerRoles.get(player) != PlayerRole.MURDERER) return;
        // is murderer, is holding knife

        ItemDisplay knifeEntity =
                world.spawn(
                        player.getEyeLocation(),
                        ItemDisplay.class,
                        e -> {
                            e.setItemStack(knife);
                            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            e.setInterpolationDuration(1);
                            e.setInterpolationDelay(0);
                        });

        knifeEntities.add(knifeEntity);

        runningTasks.add(
                new BukkitRunnable() {
                    float angle = 0f;
                    Location eyeLoc = player.getEyeLocation();
                    float yawRad = (float) toRadians(eyeLoc.getYaw());
                    Vector direction = player.getLocation().getDirection();

                    @Override
                    public void run() {
                        if (!knifeEntity.getLocation().getBlock().getType().isAir())
                            knifeEntity.remove();
                        List<Entity> nearEntities = knifeEntity.getNearbyEntities(1, 1, 0.3);
                        nearEntities.stream()
                                .filter(e -> e instanceof LivingEntity)
                                .forEach(
                                        e -> {
                                            LivingEntity le = (LivingEntity) e;
                                            if ((le instanceof Player p)
                                                    && playerRoles.get(p) == PlayerRole.MURDERER)
                                                return;
                                            le.damage(
                                                    le.getAttribute(Attribute.MAX_HEALTH)
                                                            .getValue());
                                        });
                        if (knifeEntity.isDead()) {
                            this.cancel();
                            return;
                        }

                        knifeEntity.teleport(
                                knifeEntity
                                        .getLocation()
                                        .add(
                                                direction.divide(
                                                        new Vector(1, 1, 1)))); // Movement speed

                        angle += (float) PI / 3; // Rotation speed
                        if (angle >= PI * 2) {
                            angle = 0f;
                        }

                        // Construct a raw 4x4 matrix
                        Vector3f pivot = new Vector3f(-0.125f, -0.125f, 0.0f);
                        Matrix4f matrix =
                                new Matrix4f()
                                        .rotateY(
                                                (float) (-yawRad - (PI / 2))) // point toward facing
                                        .translate(pivot)
                                        .rotateZ(angle)
                                        .translate(-pivot.x, -pivot.y, -pivot.z);

                        knifeEntity.setTransformationMatrix(matrix);

                        knifeEntity.setInterpolationDelay(0);
                        knifeEntity.setInterpolationDuration(1);
                    }
                }.runTaskTimer(plugin, 0L, 1L));
        player.getInventory().remove(knife);

        giveKnifeTasks.add(
                new BukkitRunnable() {
                    int countdown = 5; // seconds

                    @Override
                    public void run() {
                        if (countdown <= 0) {
                            player.setLevel(0);
                            player.getInventory().addItem(knife);
                            this.cancel();
                        } else {
                            player.setLevel(countdown);
                            countdown--;
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L));
    }

    public void instaShoot(Player player) {
        if (!bowInstaShoot) return;
        if (!player.getInventory().contains(Material.ARROW)) return;

        Arrow arrow = player.launchProjectile(Arrow.class, player.getLocation().getDirection());
        arrow.setVelocity(player.getLocation().getDirection().multiply(3.0));
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

        shotArrows.add(arrow);

        player.getInventory().removeItem(new ItemStack(Material.ARROW, 1));
    }

    public void pickupCoin(Player player, Item coinItem) {
        runningTasks.add(
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        coinEntities.remove(coinItem);
                        if (!player.getInventory().containsAtLeast(coin, coinsToBow)) return;
                        ItemStack toBowCoins = coin.clone();
                        toBowCoins.setAmount(coinsToBow);

                        if (playerRoles.get(player) != PlayerRole.MURDERER) {
                            if (!player.getInventory().contains(zapper)) {
                                player.getInventory().addItem(zapper);
                            }
                            if (!(player.getInventory().contains(Material.ARROW)
                                    && wrongGuessKills)) {
                                player.getInventory().addItem(new ItemStack(Material.ARROW));
                                player.getInventory().removeItem(toBowCoins);
                            }
                        }
                    }
                }.runTask(plugin) // Run on next tick to include the pickedup coin
                );
    }

    private void spawnDeadBody(Player player) {
        Location loc = player.getLocation();
        Vector newDir = loc.getDirection().rotateAroundY(new Random().nextDouble(PI * 2));
        loc.setDirection(newDir);
        Mannequin body =
                loc.getWorld()
                        .spawn(
                                loc,
                                Mannequin.class,
                                m -> {
                                    // Skin: copies the victim's profile (name + textures)
                                    m.setPlayerProfile(player.getPlayerProfile());
                                    // Lying down
                                    m.setPose(Pose.SLEEPING);
                                    // Don't get pushed around / fall through anything
                                    m.setImmovable(true);
                                    m.setCollidable(false);
                                    // Hide the "Mannequin" subtitle shown above vanilla mannequins
                                    m.setHideDescription(true);
                                    // Show all skin layers (hat, jacket, sleeves, pants)
                                    for (PlayerModelPart part : PlayerModelPart.values()) {
                                        m.setModelPartShown(part, true);
                                    }
                                    m.setMainHand(player.getMainHand());
                                    m.setGravity(false);
                                    m.setSilent(true);
                                    m.setInvulnerable(true);
                                    m.setCustomNameVisible(false);
                                });
        deadBodies.add(body);
    }

    /*
    public void newSheriff(Player player)
     */

    // Game properties getters
    public boolean canDrop() {
        return canDropItems;
    }

    public String getName() {
        return name;
    }

    // Game status methods
    public boolean isPlaying() {
        return playing;
    }

    public boolean isEnded() {
        return ended;
    }

    // Utility methods
    private Vector getVector(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        return new Vector(x, y, z);
    }
}
