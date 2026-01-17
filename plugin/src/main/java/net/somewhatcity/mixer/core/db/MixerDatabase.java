package net.somewhatcity.mixer.core.db;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.somewhatcity.mixer.core.MixerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.h2.jdbcx.JdbcDataSource;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MixerDatabase {
    private final MixerPlugin plugin;
    private final String connectionString;
    private JdbcDataSource dataSource;

    public MixerDatabase(MixerPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            try {
                plugin.getDataFolder().mkdirs();
            } catch (Exception e) {
                plugin.logDebug(Level.SEVERE, "Failed to create data folder", e);
            }
        }

        File dbFile = new File(plugin.getDataFolder(), "database");
        this.connectionString = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";

        try {
            dataSource = new JdbcDataSource();
            dataSource.setURL(connectionString);
        } catch (Exception e) {
            plugin.logDebug(Level.SEVERE, "Failed to create H2 DataSource", e);
        }
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

            String sqlSpeakers = "CREATE TABLE IF NOT EXISTS speaker_dsp (" +
                    "speaker_id VARCHAR(36) PRIMARY KEY, " +
                    "settings TEXT NOT NULL" +
                    ")";
            stmt.execute(sqlSpeakers);

            plugin.logDebug(Level.INFO, "Database initialized successfully.", null);
        } catch (SQLException e) {
            plugin.logDebug(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    // --- Active Mixers (Jukeboxes) ---

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

    // --- Portable Speaker DSP Settings ---

    public void saveSpeakerDsp(UUID speakerId, JsonObject settings) {
        String sql = "MERGE INTO speaker_dsp (speaker_id, settings) KEY(speaker_id) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, speakerId.toString());
            pstmt.setString(2, settings.toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.logDebug(Level.WARNING, "Failed to save speaker DSP to DB", e);
        }
    }

    public JsonObject loadSpeakerDsp(UUID speakerId) {
        String sql = "SELECT settings FROM speaker_dsp WHERE speaker_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, speakerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("settings");
                    return JsonParser.parseString(json).getAsJsonObject();
                }
            }
        } catch (SQLException e) {
            plugin.logDebug(Level.WARNING, "Failed to load speaker DSP from DB", e);
        } catch (Exception e) {
            plugin.logDebug(Level.WARNING, "Failed to parse speaker DSP JSON", e);
        }
        return new JsonObject(); // Return empty if not found
    }
}