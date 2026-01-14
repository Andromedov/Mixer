package net.somewhatcity.mixer.core.db;

import net.somewhatcity.mixer.core.MixerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MixerDatabase {
    private final MixerPlugin plugin;
    private final String connectionString;

    public MixerDatabase(MixerPlugin plugin) {
        this.plugin = plugin;
        File dbFile = new File(plugin.getDataFolder(), "database");
        this.connectionString = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL";
    }

    public void init() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create a table for active mixers
            String sql = "CREATE TABLE IF NOT EXISTS active_mixers (" +
                    "id VARCHAR(255) PRIMARY KEY, " +
                    "world VARCHAR(255) NOT NULL, " +
                    "x INT NOT NULL, " +
                    "y INT NOT NULL, " +
                    "z INT NOT NULL, " +
                    "uri TEXT NOT NULL" +
                    ")";
            stmt.execute(sql);

            plugin.logDebug(Level.INFO, "Database initialized successfully.", null);
        } catch (SQLException e) {
            plugin.logDebug(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString);
    }

    private String getLocationKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public void saveMixer(Location loc, String uri) {
        String sql = "MERGE INTO active_mixers (id, world, x, y, z, uri) KEY(id) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, getLocationKey(loc));
            pstmt.setString(2, loc.getWorld().getName());
            pstmt.setInt(3, loc.getBlockX());
            pstmt.setInt(4, loc.getBlockY());
            pstmt.setInt(5, loc.getBlockZ());
            pstmt.setString(6, uri);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.logDebug(Level.WARNING, "Failed to save mixer state to DB", e);
        }
    }

    public void removeMixer(Location loc) {
        String sql = "DELETE FROM active_mixers WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, getLocationKey(loc));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.logDebug(Level.WARNING, "Failed to remove mixer state from DB", e);
        }
    }

    public Map<Location, String> loadMixers() {
        Map<Location, String> mixers = new HashMap<>();
        String sql = "SELECT * FROM active_mixers";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String worldName = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String uri = rs.getString("uri");

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    mixers.put(loc, uri);
                }
            }
        } catch (SQLException e) {
            plugin.logDebug(Level.SEVERE, "Failed to load mixers from DB", e);
        }

        return mixers;
    }
}