package com.lumiczi.lurpg.core.database;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MySQL-based implementation of {@link DataProvider}.
 * <p>
 * Uses the bundled (shaded) MySQL JDBC driver. Connection parameters are read
 * from config.yml under the {@code database.mysql} section. All public methods
 * are synchronized to ensure thread safety when called from multiple async
 * threads. The connection is automatically reconnected if it becomes stale.
 */
public class MySQLProvider implements DataProvider {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                class_id VARCHAR(64) NOT NULL DEFAULT '',
                level INT NOT NULL DEFAULT 1,
                xp BIGINT NOT NULL DEFAULT 0,
                skill_points INT NOT NULL DEFAULT 0,
                current_resource DOUBLE NOT NULL DEFAULT 0.0,
                learned_skills TEXT NOT NULL,
                equipped_skills TEXT NOT NULL,
                updated_at BIGINT NOT NULL DEFAULT 0
            )
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO player_data
                (uuid, class_id, level, xp, skill_points, current_resource, learned_skills, equipped_skills, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                class_id = VALUES(class_id),
                level = VALUES(level),
                xp = VALUES(xp),
                skill_points = VALUES(skill_points),
                current_resource = VALUES(current_resource),
                learned_skills = VALUES(learned_skills),
                equipped_skills = VALUES(equipped_skills),
                updated_at = VALUES(updated_at)
            """;

    private static final String SELECT_SQL = """
            SELECT class_id, level, xp, skill_points, current_resource, learned_skills, equipped_skills
            FROM player_data WHERE uuid = ?
            """;

    private static final String EXISTS_SQL = "SELECT 1 FROM player_data WHERE uuid = ?";

    private static final String INSERT_IGNORE_SQL = """
            INSERT IGNORE INTO player_data (uuid, class_id) VALUES (?, ?)
            """;

    private final LuRPGPlugin plugin;
    private Connection connection;
    private String url;
    private String username;
    private String password;

    public MySQLProvider(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws Exception {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();

        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "lurpg");
        this.username = config.getString("database.mysql.username", "root");
        this.password = config.getString("database.mysql.password", "");

        // Build connection URL with properties
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);

        // Append connection properties
        var propsSection = config.getConfigurationSection("database.mysql.properties");
        if (propsSection != null && !propsSection.getKeys(false).isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (String key : propsSection.getKeys(false)) {
                if (!first) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(key).append("=").append(propsSection.getString(key));
                first = false;
            }
        } else {
            // Default parameters
            urlBuilder.append("?useSSL=false&useUnicode=true&characterEncoding=utf8&autoReconnect=true");
        }

        this.url = urlBuilder.toString();

        // Load the MySQL JDBC driver (try original name, then shaded/relocated name)
        loadDriver();

        connection = DriverManager.getConnection(url, username, password);
        connection.createStatement().executeUpdate(CREATE_TABLE_SQL);

        plugin.getLogger().info("MySQL database initialized: " + host + ":" + port + "/" + database);
    }

    private void loadDriver() throws ClassNotFoundException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Try relocated class name (after maven-shade-plugin relocation)
            Class.forName("com.lumiczi.lurpg.libs.mysql.cj.jdbc.Driver");
        }
    }

    /**
     * Returns a valid connection, reconnecting if the current one is closed or invalid.
     * Caller must be holding the monitor (methods are synchronized).
     */
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(5)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            connection = DriverManager.getConnection(url, username, password);
        }
        return connection;
    }

    @Override
    public synchronized void savePlayerData(UUID uuid, String classId, int level, long xp, int skillPoints,
                                            double currentResource, Set<String> learnedSkills,
                                            Set<String> equippedSkills) {
        try (PreparedStatement ps = getConnection().prepareStatement(UPSERT_SQL)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, classId != null ? classId : "");
            ps.setInt(3, level);
            ps.setLong(4, xp);
            ps.setInt(5, skillPoints);
            ps.setDouble(6, currentResource);
            ps.setString(7, toJsonArray(learnedSkills));
            ps.setString(8, toJsonArray(equippedSkills));
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + uuid, e);
        }
    }

    @Override
    public synchronized PlayerDataRecord loadPlayerData(UUID uuid) {
        try (PreparedStatement ps = getConnection().prepareStatement(SELECT_SQL)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerDataRecord(
                            uuid,
                            rs.getString("class_id"),
                            rs.getInt("level"),
                            rs.getLong("xp"),
                            rs.getInt("skill_points"),
                            rs.getDouble("current_resource"),
                            fromJsonArray(rs.getString("learned_skills")),
                            fromJsonArray(rs.getString("equipped_skills"))
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
        }
        return null;
    }

    @Override
    public synchronized boolean playerExists(UUID uuid) {
        try (PreparedStatement ps = getConnection().prepareStatement(EXISTS_SQL)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check player existence for " + uuid, e);
        }
        return false;
    }

    @Override
    public synchronized void createPlayer(UUID uuid, String classId) {
        try (PreparedStatement ps = getConnection().prepareStatement(INSERT_IGNORE_SQL)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, classId != null ? classId : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create player " + uuid, e);
        }
    }

    @Override
    public synchronized void shutdown() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing MySQL connection", e);
            }
            connection = null;
        }
    }

    // ---- JSON helpers (simple string array serialization) ----

    private static String toJsonArray(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : set) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(s)).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static Set<String> fromJsonArray(String json) {
        Set<String> result = new HashSet<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        String content = json.trim();
        if (content.equals("[]") || content.isEmpty()) {
            return result;
        }
        if (content.startsWith("[")) {
            content = content.substring(1);
        }
        if (content.endsWith("]")) {
            content = content.substring(0, content.length() - 1);
        }
        content = content.trim();
        if (content.isEmpty()) {
            return result;
        }
        for (String part : content.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
                trimmed = unescapeJson(trimmed);
            }
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
