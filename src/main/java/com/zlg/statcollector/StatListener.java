package com.zlg.statcollector;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class StatListener implements Listener {

    private final StatCollector plugin;
    private final FileConfiguration trackedConfig;
    private final Connection connection;

    public StatListener(StatCollector plugin, Connection connection) {
        this.plugin = plugin;
        this.trackedConfig = plugin.getTrackedStatsConfig();
        this.connection = connection;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!trackedConfig.getBoolean("track.blocks_broken", false)) return;

        Player player = event.getPlayer();
        updateStat(player.getUniqueId(), "blocks_broken", 1);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!trackedConfig.getBoolean("track.deaths", false)) return;

        Player victim = event.getEntity();
        updateStat(victim.getUniqueId(), "deaths", 1);
    }

    @EventHandler
    public void onPlayerKill(EntityDamageByEntityEvent event) {
        if (!trackedConfig.getBoolean("track.kills", false)) return;

        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player killer = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            updateStat(killer.getUniqueId(), "kills", 1);
        }
    }

    private void updateStat(UUID uuid, String column, int amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO player_stats (uuid, name, " + column + ") VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " + column + " = " + column + " + VALUES(" + column + ")"
                );
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) return;

                stmt.setString(1, uuid.toString());
                stmt.setString(2, player.getName());
                stmt.setInt(3, amount);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update stat: " + column + " for " + uuid);
                e.printStackTrace();
            }
        });
    }
}
