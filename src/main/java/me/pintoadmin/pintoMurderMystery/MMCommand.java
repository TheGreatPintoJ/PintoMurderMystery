package me.pintoadmin.pintoMurderMystery;

import java.util.*;
import java.util.stream.*;
import org.bukkit.*;
import org.bukkit.GameRule;
import org.bukkit.command.*;

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
                    "timeofday");
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
     *     spawnlocation <set/remove> <coords> <f>
     *     coinlocation <set/remove> <coords> <r>
     * */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

            finalList.addAll(
                    switch (args.length) {
                        case 1 -> List.of("reload", "new", "edit");
                        case 2 ->
                                args[0].equalsIgnoreCase("reload")
                                        ? List.of()
                                        : allGames.isEmpty() ? List.of("<name>") : allGames;
                        case 3 -> args[0].equalsIgnoreCase("edit") ? allEditOptions : List.of();
                        case 4 ->
                                boolEditOptions.contains(args[2])
                                        ? List.of("true", "false")
                                        : intEditOptions.contains(args[2])
                                                ? List.of("<number>")
                                                : otherEditOptions.contains(args[2])
                                                        ? args[2].equalsIgnoreCase("weather")
                                                                ? List.of(
                                                                        "clear", "rain", "thunder")
                                                                : args[2].equalsIgnoreCase(
                                                                                        "spawnlocation")
                                                                                || args[2]
                                                                                        .equalsIgnoreCase(
                                                                                                "coinlocation")
                                                                        ? List.of("set", "remove")
                                                                        : args[2].equalsIgnoreCase(
                                                                                        "gamerule")
                                                                                ? gameruleStrings
                                                                                : List.of()
                                                        : List.of();
                        default -> List.of();
                    });
            return finalList.stream()
                    .filter((str) -> str.startsWith(args[args.length - 1]))
                    .collect(Collectors.toList());
        }
    }
}
