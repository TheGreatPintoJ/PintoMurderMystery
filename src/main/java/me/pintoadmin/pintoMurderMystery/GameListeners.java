package me.pintoadmin.pintoMurderMystery;

import static org.bukkit.Bukkit.getServer;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;

public class GameListeners implements Listener {
    private final PintoMurderMystery plugin;

    public GameListeners(PintoMurderMystery plugin) {
        this.plugin = plugin;
        getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        Player player = event.getPlayer();
        Block block = event.getBlock();

        MurderMysteryGame game = plugin.getGame(worldName);
        if (game == null) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent event) {
        World world = event.getEntity().getWorld();
        MurderMysteryGame game = plugin.getGame(world.getName());
        if (game == null) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE) {
                // Cancel if player shouldn't be able to take damage by gamemode
                event.setCancelled(true);
                return;
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean doFallDamage =
                    plugin.configLoader
                            .getGamerules(world.getName())
                            .getOrDefault("fallDamage", false);
            event.setCancelled(!doFallDamage);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            World world = player.getWorld();
            MurderMysteryGame game = plugin.getGame(world.getName());
            if (game != null) {
                event.setCancelled(true);
                player.setSaturation(1);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        MurderMysteryGame game = plugin.getGame(player.getWorld().getName());

        if (game == null) return;

        game.onDeath(player, event);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        MurderMysteryGame game = plugin.getGame(player.getWorld().getName());
        if (game != null) {
            event.setCancelled(!game.canDrop());
        }
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        MurderMysteryGame game = plugin.getGame(player.getWorld().getName());
        if (game != null) {
            event.setJoinMessage(null);
            new BukkitRunnable() {
                @Override
                public void run() {
                    game.join(player);
                }
            }.runTask(plugin);
        } else plugin.debug("Game is null: " + player.getWorld().getName());
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        MurderMysteryGame game = plugin.getGame(world.getName());
        if (game != null) {
            try {
                game.leave(player, world);
            } catch (IllegalPluginAccessException ignored) {
            }
            plugin.getLogger().warning("Player quit while in game: " + game.getName());
            event.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onPlayerChangeWorldEvent(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();
        World fromWorld = event.getFrom();

        MurderMysteryGame game = plugin.getGame(toWorld.getName());
        MurderMysteryGame fromGame = plugin.getGame(fromWorld.getName());

        if (fromGame != null && fromGame != game) {
            fromGame.leave(player, toWorld);
        }
        if (game == null) return;
        // Delegate to Game.join for all join logic
        game.join(player);
    }

    @EventHandler
    public void onItemDamaged(PlayerItemDamageEvent event) {
        MurderMysteryGame game = plugin.getGame(event.getPlayer().getWorld().getName());
        if (game != null) event.setCancelled(true);
    }

    @EventHandler
    public void onArrowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        MurderMysteryGame game = plugin.getGame(player.getWorld().getName());
        if (game == null) return;
        //        game.arrowShoot(player);
    }

    @EventHandler
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event) {
        MurderMysteryGame game = plugin.getGame(event.getEntity().getWorld().getName());
        if (game == null) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker)) return;
        if (victim == attacker) return;

        if (attacker.getInventory().getItemInMainHand().isSimilar(game.zapper)
                && event.getDamageSource().getDamageType() != DamageType.ARROW) {
            event.setCancelled(true);
            game.instaShoot(attacker);
            return;
        }

        game.damaged(victim, attacker, event);
    }

    @EventHandler
    public void onInteractEvent(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        MurderMysteryGame game = plugin.getGame(player.getWorld().getName());
        if (game == null) return;
        if (event.getItem() == null) return;
        if (event.getAction().name().contains("RIGHT_CLICK")) {
            event.setCancelled(true);
            if (event.getItem().isSimilar(game.knife)) game.tryThrowKnife(player);
        }

        if (event.getAction().name().contains("LEFT_CLICK")
                && event.getItem().isSimilar(game.zapper)) game.instaShoot(player);
    }

    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        MurderMysteryGame game = plugin.getGame(player.getWorld().getName());
        if (game == null) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        game.shotArrows.add(arrow);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        MurderMysteryGame game = plugin.getGame(p.getWorld().getName());
        if (game == null) return;
        if (e.getItem().getItemStack().isSimilar(game.coin)) game.pickupCoin(p);
    }

    @EventHandler
    public void onItemMerge(ItemMergeEvent e) {
        MurderMysteryGame game = plugin.getGame(e.getEntity().getWorld().getName());
        if (game == null) return;
        if (e.getEntity().getItemStack().isSimilar(game.coin)) e.setCancelled(true);
    }
}
