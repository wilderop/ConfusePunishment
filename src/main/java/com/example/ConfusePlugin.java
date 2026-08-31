package com.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfusePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<Player> confusedPlayers = new HashSet<>();
    private final Set<UUID> confusedUUIDs = new HashSet<>();
    private final Random random = new Random();
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        dataFile = new File(getDataFolder(), "confused.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadConfusedPlayers();

        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("confuse").setExecutor(this);

        // Scheduler for random teleports
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : new HashSet<>(confusedPlayers)) {
                    // Probability 1/300 per second for average 300 seconds
                    if (Math.random() < 1.0 / 300.0) {
                        Location tpLoc = new Location(player.getWorld(), 0, 100, 0);
                        player.teleport(tpLoc);
                        player.sendMessage("You've been teleported back to spawn due to confusion!");
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Every second (20 ticks)
    }

    @Override
    public void onDisable() {
        saveConfusedPlayers();
    }

    private void loadConfusedPlayers() {
        if (dataConfig.contains("confused")) {
            for (String uuidStr : dataConfig.getStringList("confused")) {
                try {
                    confusedUUIDs.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID in confused.yml: " + uuidStr);
                }
            }
        }
        // Add online confused players to the set
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (confusedUUIDs.contains(player.getUniqueId())) {
                confusedPlayers.add(player);
            }
        }
    }

    private void saveConfusedPlayers() {
        List<String> uuidList = new ArrayList<>();
        for (UUID uuid : confusedUUIDs) {
            uuidList.add(uuid.toString());
        }
        dataConfig.set("confused", uuidList);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /confuse <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found or offline.");
            return true;
        }

        UUID targetUUID = target.getUniqueId();
        if (confusedUUIDs.contains(targetUUID)) {
            confusedUUIDs.remove(targetUUID);
            confusedPlayers.remove(target);
            saveConfusedPlayers();
            sender.sendMessage("Removed confusion from " + target.getName());
            target.sendMessage("You are no longer confused.");
        } else {
            confusedUUIDs.add(targetUUID);
            confusedPlayers.add(target);
            saveConfusedPlayers();
            sender.sendMessage("Applied confusion to " + target.getName());
            target.sendMessage("You are now confused!");
        }

        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (confusedUUIDs.contains(player.getUniqueId())) {
            confusedPlayers.add(player);
            player.sendMessage("You are still confused!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        confusedPlayers.remove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!confusedPlayers.contains(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.equals(to)) {
            return;
        }

        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length <= 0) {
            return;
        }

        // Randomize horizontal direction, keep vertical component
        double randomYaw = Math.random() * 360;
        Vector newDir = new Vector(
                Math.sin(Math.toRadians(randomYaw)) * length,
                dir.getY(),
                Math.cos(Math.toRadians(randomYaw)) * length
        );

        Location newTo = from.clone().add(newDir);
        newTo.setPitch(to.getPitch());
        newTo.setYaw(to.getYaw());  // Keep facing direction the same, only movement changes

        event.setTo(newTo);
    }
}
