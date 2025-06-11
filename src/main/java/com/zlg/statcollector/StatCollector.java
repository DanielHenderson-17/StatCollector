package com.zlg.statcollector;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class StatCollector extends JavaPlugin {

    private Connection connection;
    private FileConfiguration trackedStatsConfig;

    @Override
    public void onEnable() {
    saveDefaultConfig();
    saveTrackedStatsDefault();
    loadTrackedStatsConfig();     
    setupDatabase();
    getServer().getPluginManager().registerEvents(new StatListener(this, connection), this);
    startPlaytimeTracking();
    getLogger().info("StatCollector enabled.");
}


    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
                getLogger().info("Database connection closed.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        getLogger().info("StatCollector disabled.");
    }

    private void setupDatabase() {
        FileConfiguration config = getConfig();
        String host = config.getString("mysql.host");
        int port = config.getInt("mysql.port");
        String database = config.getString("mysql.database");
        String username = config.getString("mysql.username");
        String password = config.getString("mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

        try {
            connection = DriverManager.getConnection(url, username, password);
            Statement statement = connection.createStatement();
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16)," +
                "kills INT DEFAULT 0," +
                "deaths INT DEFAULT 0," +
                "blocks_broken INT DEFAULT 0," +
                "playtime_seconds INT DEFAULT 0" +
                ");"
            );
            Bukkit.getLogger().info("[StatCollector] Connected to database and ensured table exists.");
        } catch (Exception e) {
            e.printStackTrace();
            getLogger().severe("Failed to connect to database or create table.");
        }
    }

    // 🔽 These two handle tracked-stats.yml

    private void saveTrackedStatsDefault() {
        File file = new File(getDataFolder(), "tracked-stats.yml");
        if (!file.exists()) {
            saveResource("tracked-stats.yml", false);
        }
    }

    private void loadTrackedStatsConfig() {
        File file = new File(getDataFolder(), "tracked-stats.yml");
        trackedStatsConfig = YamlConfiguration.loadConfiguration(file);
    }

    // Optional: getter to access tracked stats config from other classes
    public FileConfiguration getTrackedStatsConfig() {
        return trackedStatsConfig;
    }
    private void startPlaytimeTracking() {
    if (!trackedStatsConfig.getBoolean("track.playtime_seconds", false)) return;

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
        Bukkit.getOnlinePlayers().forEach(player -> {
            try {
                var stmt = connection.prepareStatement(
                    "INSERT INTO player_stats (uuid, name, playtime_seconds) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE playtime_seconds = playtime_seconds + VALUES(playtime_seconds)"
                );
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, player.getName());
                stmt.setInt(3, 60);
                stmt.executeUpdate();
            } catch (Exception e) {
                getLogger().severe("Failed to update playtime for " + player.getName());
                e.printStackTrace();
            }
        });
    }, 20L * 60, 20L * 60);
}

}
