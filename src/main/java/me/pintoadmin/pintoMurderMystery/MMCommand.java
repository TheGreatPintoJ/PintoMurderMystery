package me.pintoadmin.pintoMurderMystery;

import java.util.*;
import java.util.stream.*;
import org.bukkit.*;
import org.bukkit.GameRule;
import org.bukkit.command.*;
import org.bukkit.util.Vector;

public class MMCommand implements CommandExecutor {
    private final PintoMurderMystery plugin;

    public MMCommand() {
        plugin = PintoMurderMystery.getInstance();
        plugin.getCommand("mm").setExecutor(this);
        plugin.getCommand("mm").setTabCompleter(new MMCommandCompleter());
    }

    List<String> boolEditOptions =
            List.of("enabled", "bowinstashoot", "deathwater", "wrongguesskills", "candropitems");
    List<String> intEditOptions =
            List.of(
                    "coinspawnfrequency",
                    "maxcoins",
                    "graceperiod",
                    "murderers",
                    "sheriffs",
                    "maxplayers",
                    "coinstobow",
                    "timeofday",
                    "timelimit");
    List<String> otherEditOptions =
            new ArrayList<>(
                    List.of("weather", "gamerule", "endlocation", "spawnlocation", "coinlocation"));

    /*
     * /mm
     *   reload
     *   new <name>
     *   edit <game>
     *     enabled <bool>
     *     coinspawnfrequency <int>
     *     maxcoins <int>
     *     graceperiod <int>
     *     murderers <int>
     *     sheriffs <int>
     *     maxplayers <int>
     *     coinstobow <int>
     *     bowinstashoot <bool>
     *     deathwater <bool>
     *     wrongguesskills <bool>
     *     candropitems <bool>
     *     timeofday <int>
     *     weather <string: "clear"|"rain"|"thunder">
     *     gamerule <id> <value>
     *     endlocation
     *     spawnlocation <add/remove> <coords> <fx> <fz>
     *     coinlocation <add/remove> <coords> <r>
     * */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
        } else if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /mm remove <game>");
                return true;
            }
            MurderMysteryGame game = plugin.getGame(args[1]);
            if (game == null) {
                sender.sendMessage(ChatColor.RED + "This game doesn't exist");
                return true;
            }
            // remove game
            plugin.configLoader.set(args[1], null);
        } else if (args[0].equalsIgnoreCase("edit")) {
            if (args.length < 4) {
                sender.sendMessage("Usage: /mm edit <game> <option> <value>");
                return true;
            }
            String gameName = args[1];
            if (boolEditOptions.contains(args[2])
                    || intEditOptions.contains(args[2])
                    || args[2].equalsIgnoreCase("weather")) {
                // set config value to args[3]
                Object parsed =
                        boolEditOptions.contains(args[2])
                                ? Boolean.parseBoolean(args[2])
                                : intEditOptions.contains(args[2])
                                        ? Integer.parseInt(args[3])
                                        : args[2];
                plugin.configLoader.set(gameName + "." + args[2], parsed);
            } else {
                if (args[2].equalsIgnoreCase("gamerule")) {
                    if (args.length < 5) {
                        sender.sendMessage("Usage: /mm edit <game> gamerule <id> <value>");
                        return true;
                    }
                    // set gamerule args[3] to value args[4]
                    String id = args[3];
                    boolean value = Boolean.parseBoolean(args[4]);
                    Map<String, Boolean> currentRules = plugin.configLoader.getGamerules(gameName);
                    currentRules.put(id, value);
                    plugin.configLoader.set(gameName + ".gamerules", currentRules);
                } else if (args[2].equalsIgnoreCase("spawnlocation")) {
                    if (args.length < 9) {
                        sender.sendMessage(
                                "Usage: /mm edit <game> spawnlocation "
                                        + args[3]
                                        + " <x> <y> <z> <fx> <fz>");
                        return true;
                    }
                    double x = Double.parseDouble(args[4]);
                    double y = Double.parseDouble(args[5]);
                    double z = Double.parseDouble(args[6]);
                    double fx = Double.parseDouble(args[7]);
                    double fz = Double.parseDouble(args[8]);

                    List<Map<String, Object>> currentSpawnLocations =
                            plugin.configLoader.getSpawnLocations(gameName);
                    Map<String, Object> newSpawnLoc = new LinkedHashMap<>();
                    newSpawnLoc.put("x", x);
                    newSpawnLoc.put("y", y);
                    newSpawnLoc.put("z", z);
                    newSpawnLoc.put("fx", fx);
                    newSpawnLoc.put("fz", fz);
                    if (args[3].equalsIgnoreCase("add")) {
                        // Add new entry or update existing with said coords
                        currentSpawnLocations.add(newSpawnLoc);
                    } else if (args[3].equalsIgnoreCase("remove")) {
                        // Remove specified entry
                        currentSpawnLocations.remove(newSpawnLoc);
                    }
                    plugin.configLoader.set(gameName + ".spawnlocations", currentSpawnLocations);
                } else if (args[2].equalsIgnoreCase("coinlocation")) {
                    if (args.length < 9) {
                        sender.sendMessage(
                                "Usage: /mm edit <game> coinlocation "
                                        + args[3]
                                        + " <x> <y> <z> <r>");
                        return true;
                    }
                    double x = Double.parseDouble(args[4]);
                    double y = Double.parseDouble(args[5]);
                    double z = Double.parseDouble(args[6]);
                    double r = Double.parseDouble(args[7]);

                    List<Map<String, Object>> currentCoinLocations =
                            plugin.configLoader.getSpawnLocations(gameName);
                    Map<String, Object> newCoinLoc = new LinkedHashMap<>();
                    newCoinLoc.put("x", x);
                    newCoinLoc.put("y", y);
                    newCoinLoc.put("z", z);
                    newCoinLoc.put("r", r);
                    if (args[3].equalsIgnoreCase("add")) {
                        // Add new entry or update existing with said coords
                        currentCoinLocations.add(newCoinLoc);

                    } else if (args[3].equalsIgnoreCase("remove")) {
                        // Remove specified entry
                        currentCoinLocations.remove(newCoinLoc);
                    }
                    plugin.configLoader.set(gameName + ".coinlocations", currentCoinLocations);
                }
            }
        }
        return false;
    }

    private class MMCommandCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(
                CommandSender sender, Command command, String label, String[] args) {
            List<String> finalList = new ArrayList<>();
            List<String> allGames = plugin.configLoader.getAllGames();
            List<String> allEditOptions = new ArrayList<>(boolEditOptions);
            allEditOptions.addAll(intEditOptions);
            allEditOptions.addAll(otherEditOptions);

            List<String> gameruleStrings =
                    new ArrayList<>(
                            Arrays.stream(GameRule.values()).map(GameRule::getName).toList());

            try {
                if (args.length == 1) {
                    finalList.addAll(List.of("reload", "edit", "remove"));
                } else if (args.length == 2) {
                    if (!args[0].equalsIgnoreCase("reload")) {
                        finalList.addAll(plugin.configLoader.getAllGames());
                    }
                } else if (args.length == 3) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        finalList.addAll(allEditOptions);
                    }
                } else if (args.length == 4) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        if (boolEditOptions.contains(args[2])) {
                            finalList.addAll(List.of("true", "false"));
                        } else if (intEditOptions.contains(args[2])) {
                            finalList.add("<number>");
                        } else if (args[2].equalsIgnoreCase("gamerule")) {
                            finalList.addAll(gameruleStrings);
                        } else if (otherEditOptions.contains(args[2])) {
                            if (args[2].equalsIgnoreCase("weather"))
                                finalList.addAll(List.of("rain", "clear", "thunder"));
                            else if (args[2].equalsIgnoreCase("spawnlocation")
                                    || args[2].equalsIgnoreCase("coinlocation"))
                                finalList.addAll(List.of("add", "remove"));
                        }
                    }
                } else if (args.length == 5) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        if (args[2].equalsIgnoreCase("gamerule")) {
                            finalList.addAll(List.of("true", "false"));
                        } else if (args[2].equalsIgnoreCase("spawnlocation")) {
                            finalList.addAll(
                                    plugin.getGame(args[1]).getSpawnLocations().keySet().stream()
                                            .map(Vector::getX)
                                            .map(Object::toString)
                                            .toList());
                        } else if (args[2].equalsIgnoreCase("coinlocation")) {
                            finalList.addAll(
                                    plugin.getGame(args[1]).getCoinLocations().keySet().stream()
                                            .map(Vector::getX)
                                            .map(Object::toString)
                                            .toList());
                        }
                    }
                } else if (args.length == 6) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        if (args[2].equalsIgnoreCase("spawnlocation")) {
                            finalList.addAll(
                                    plugin.getGame(args[1]).getSpawnLocations().keySet().stream()
                                            .filter(v -> Double.toString(v.getX()).equals(args[4]))
                                            .map(Vector::getY)
                                            .map(Object::toString)
                                            .toList());
                        } else if (args[2].equalsIgnoreCase("coinlocation")) {
                            finalList.addAll(
                                    plugin.getGame(args[1]).getCoinLocations().keySet().stream()
                                            .filter(v -> Double.toString(v.getX()).equals(args[4]))
                                            .map(Vector::getY)
                                            .map(Object::toString)
                                            .toList());
                        }
                    }
                } else if (args.length == 7) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        if (args[2].equalsIgnoreCase("spawnlocation")) {
                            finalList.addAll(
                                    plugin.getGame(args[1]).getSpawnLocations().keySet().stream()
                                            .filter(
                                                    v ->
                                                            Double.toString(v.getX())
                                                                            .equals(args[4])
                                                                    && Double.toString(v.getY())
                                                                            .equals(args[5]))
                                            .map(Vector::getZ)
                                            .map(Object::toString)
                                            .toList());
                        } else if (args[2].equalsIgnoreCase("coinlocation")) {
                            finalList.addAll(
                                    plugin.getGame(args[1]).getCoinLocations().keySet().stream()
                                            .filter(
                                                    v ->
                                                            Double.toString(v.getX())
                                                                            .equals(args[4])
                                                                    && Double.toString(v.getY())
                                                                            .equals(args[5]))
                                            .map(Vector::getZ)
                                            .map(Object::toString)
                                            .toList());
                        }
                    }
                } else if (args.length == 8) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        if (args[2].equalsIgnoreCase("spawnlocation")) {
                            finalList.add(
                                    plugin.getGame(args[1])
                                            .getSpawnLocations()
                                            .get(
                                                    new Vector(
                                                            Double.parseDouble(args[4]),
                                                            Double.parseDouble(args[5]),
                                                            Double.parseDouble(args[6])))[0]
                                            .toString());
                        } else if (args[2].equalsIgnoreCase("coinlocation")) {
                            finalList.add(
                                    plugin.getGame(args[1])
                                            .getCoinLocations()
                                            .get(
                                                    new Vector(
                                                            Double.parseDouble(args[4]),
                                                            Double.parseDouble(args[5]),
                                                            Double.parseDouble(args[6])))
                                            .toString());
                        }
                    }
                } else if (args.length == 9) {
                    if (args[0].equalsIgnoreCase("edit")) {
                        if (args[2].equalsIgnoreCase("spawnlocation")) {
                            finalList.add(
                                    plugin.getGame(args[1])
                                            .getSpawnLocations()
                                            .get(
                                                    new Vector(
                                                            Double.parseDouble(args[4]),
                                                            Double.parseDouble(args[5]),
                                                            Double.parseDouble(args[6])))[1]
                                            .toString());
                        }
                    }
                }
            } catch (NullPointerException ignored) {
            }

            return finalList.stream()
                    .filter((str) -> str.startsWith(args[args.length - 1]))
                    .collect(Collectors.toList());
        }
    }
}
