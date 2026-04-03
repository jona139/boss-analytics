package com.BossAnalytics;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite-backed local data store. Abstracted so a REST backend can replace it later.
 */
@Slf4j
public class DataStore
{
    private static final Gson GSON = new Gson();
    private static final Type MAP_STRING_INT = new TypeToken<Map<String, Integer>>(){}.getType();
    private static final Type MAP_INT_INT = new TypeToken<Map<Integer, Integer>>(){}.getType();
    private static final Type MAP_INT_STRING = new TypeToken<Map<Integer, String>>(){}.getType();
    private static final Type MAP_STRING_STRING = new TypeToken<Map<String, String>>(){}.getType();
    private static final Type LIST_STRING = new TypeToken<List<String>>(){}.getType();
    private static final Type LIST_ROOM = new TypeToken<List<RaidRecord.RoomRecord>>(){}.getType();

    private Connection connection;
    private final File dbFile;

    public DataStore()
    {
        String runeliteDir = System.getProperty("user.home") + "/.runelite/boss-analytics";
        new File(runeliteDir).mkdirs();
        this.dbFile = new File(runeliteDir, "boss_analytics.db");
    }

    public void open() throws SQLException
    {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        connection.setAutoCommit(true);

        try (Statement stmt = connection.createStatement())
        {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        initSchema();
        log.info("Boss Analytics DB opened at {}", dbFile.getAbsolutePath());
    }

    public void close()
    {
        if (connection != null)
        {
            try { connection.close(); }
            catch (SQLException e) { log.warn("Error closing database", e); }
        }
    }

    private void initSchema() throws SQLException
    {
        try (Statement stmt = connection.createStatement())
        {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS kills ("
                + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  boss_name TEXT NOT NULL,"
                + "  boss_npc_id INTEGER,"
                + "  timestamp TEXT NOT NULL,"
                + "  duration_ticks INTEGER,"
                + "  duration_seconds REAL,"
                + "  duration_from_chat INTEGER DEFAULT 0,"
                + "  combat_level INTEGER,"
                + "  skill_levels TEXT,"
                + "  boosted_levels TEXT,"
                + "  combat_achievement_tier INTEGER DEFAULT 0,"
                + "  equipped_item_ids TEXT,"
                + "  equipped_item_names TEXT,"
                + "  inventory_item_ids TEXT,"
                + "  kill_count INTEGER,"
                + "  personal_best INTEGER,"
                + "  is_personal_best INTEGER DEFAULT 0,"
                + "  world INTEGER,"
                + "  is_task INTEGER DEFAULT 0,"
                + "  team_size INTEGER DEFAULT 1,"
                + "  team_members TEXT,"
                + "  metadata TEXT"
                + ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS raids ("
                + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  kill_record_id INTEGER NOT NULL REFERENCES kills(id),"
                + "  raid_type TEXT NOT NULL,"
                + "  total_points INTEGER,"
                + "  personal_points INTEGER,"
                + "  team_size INTEGER,"
                + "  raid_level INTEGER DEFAULT 0,"
                + "  rooms TEXT,"
                + "  route TEXT,"
                + "  purple_received INTEGER DEFAULT 0,"
                + "  purple_item_name TEXT,"
                + "  purple_item_id INTEGER,"
                + "  total_deaths INTEGER DEFAULT 0,"
                + "  personal_deaths INTEGER DEFAULT 0"
                + ")"
            );

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kills_boss ON kills(boss_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kills_timestamp ON kills(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kills_boss_timestamp ON kills(boss_name, timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_raids_type ON raids(raid_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_raids_kill_id ON raids(kill_record_id)");
        }
    }

    // ========== KILL RECORDS ==========

    public long insertKill(KillRecord kill) throws SQLException
    {
        String sql = "INSERT INTO kills (boss_name, boss_npc_id, timestamp, duration_ticks, "
            + "duration_seconds, duration_from_chat, combat_level, skill_levels, boosted_levels, "
            + "combat_achievement_tier, equipped_item_ids, equipped_item_names, inventory_item_ids, "
            + "kill_count, personal_best, is_personal_best, world, is_task, team_size, "
            + "team_members, metadata) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            ps.setString(1, kill.getBossName());
            ps.setInt(2, kill.getBossNpcId());
            ps.setString(3, kill.getTimestamp().toString());
            ps.setInt(4, kill.getDurationTicks());
            ps.setDouble(5, kill.getDurationSeconds());
            ps.setInt(6, kill.isDurationFromChat() ? 1 : 0);
            ps.setInt(7, kill.getCombatLevel());
            ps.setString(8, GSON.toJson(kill.getSkillLevels()));
            ps.setString(9, GSON.toJson(kill.getBoostedLevels()));
            ps.setInt(10, kill.getCombatAchievementTier());
            ps.setString(11, GSON.toJson(kill.getEquippedItemIds()));
            ps.setString(12, GSON.toJson(kill.getEquippedItemNames()));
            ps.setString(13, GSON.toJson(kill.getInventoryItemIds()));
            ps.setInt(14, kill.getKillCount());
            ps.setInt(15, kill.getPersonalBest());
            ps.setInt(16, kill.isPersonalBest() ? 1 : 0);
            ps.setInt(17, kill.getWorld());
            ps.setInt(18, kill.isTask() ? 1 : 0);
            ps.setInt(19, kill.getTeamSize());
            ps.setString(20, GSON.toJson(kill.getTeamMembers()));
            ps.setString(21, GSON.toJson(kill.getMetadata()));
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next())
            {
                long id = keys.getLong(1);
                log.debug("Inserted kill record {} for {} ({}s)", id, kill.getBossName(), kill.getDurationSeconds());
                return id;
            }
            return -1;
        }
    }

    public List<KillRecord> getKillsByBoss(String bossName, int limit) throws SQLException
    {
        String sql = "SELECT * FROM kills WHERE boss_name = ? ORDER BY timestamp DESC"
            + (limit > 0 ? " LIMIT " + limit : "");

        List<KillRecord> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, bossName);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                results.add(killFromResultSet(rs));
            }
        }
        return results;
    }

    public List<KillRecord> getAllKills() throws SQLException
    {
        List<KillRecord> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM kills ORDER BY timestamp DESC"))
        {
            while (rs.next())
            {
                results.add(killFromResultSet(rs));
            }
        }
        return results;
    }

    public Map<String, Integer> getKillCounts() throws SQLException
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT boss_name, COUNT(*) as cnt FROM kills GROUP BY boss_name ORDER BY cnt DESC"))
        {
            while (rs.next())
            {
                counts.put(rs.getString("boss_name"), rs.getInt("cnt"));
            }
        }
        return counts;
    }

    public Map<String, Double> getAverageKillTimes() throws SQLException
    {
        Map<String, Double> avgs = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT boss_name, AVG(duration_seconds) as avg_time "
                 + "FROM kills WHERE duration_seconds > 0 "
                 + "GROUP BY boss_name ORDER BY avg_time"))
        {
            while (rs.next())
            {
                avgs.put(rs.getString("boss_name"), rs.getDouble("avg_time"));
            }
        }
        return avgs;
    }

    // ========== RAID RECORDS ==========

    public long insertRaid(RaidRecord raid) throws SQLException
    {
        String sql = "INSERT INTO raids (kill_record_id, raid_type, total_points, personal_points, "
            + "team_size, raid_level, rooms, route, purple_received, purple_item_name, "
            + "purple_item_id, total_deaths, personal_deaths) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            ps.setLong(1, raid.getKillRecordId());
            ps.setString(2, raid.getRaidType());
            ps.setInt(3, raid.getTotalPoints());
            ps.setInt(4, raid.getPersonalPoints());
            ps.setInt(5, raid.getTeamSize());
            ps.setInt(6, raid.getRaidLevel());
            ps.setString(7, GSON.toJson(raid.getRooms()));
            ps.setString(8, raid.getRoute());
            ps.setInt(9, raid.isPurpleReceived() ? 1 : 0);
            ps.setString(10, raid.getPurpleItemName());
            ps.setInt(11, raid.getPurpleItemId());
            ps.setInt(12, raid.getTotalDeaths());
            ps.setInt(13, raid.getPersonalDeaths());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getLong(1) : -1;
        }
    }

    public List<RaidRecord> getRaidsByType(String raidType) throws SQLException
    {
        String sql = "SELECT * FROM raids WHERE raid_type = ? ORDER BY id DESC";
        List<RaidRecord> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, raidType);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                results.add(raidFromResultSet(rs));
            }
        }
        return results;
    }

    // ========== HELPERS ==========

    private KillRecord killFromResultSet(ResultSet rs) throws SQLException
    {
        return KillRecord.builder()
            .id(rs.getLong("id"))
            .bossName(rs.getString("boss_name"))
            .bossNpcId(rs.getInt("boss_npc_id"))
            .timestamp(Instant.parse(rs.getString("timestamp")))
            .durationTicks(rs.getInt("duration_ticks"))
            .durationSeconds(rs.getDouble("duration_seconds"))
            .durationFromChat(rs.getInt("duration_from_chat") == 1)
            .combatLevel(rs.getInt("combat_level"))
            .skillLevels(GSON.fromJson(rs.getString("skill_levels"), MAP_STRING_INT))
            .boostedLevels(GSON.fromJson(rs.getString("boosted_levels"), MAP_STRING_INT))
            .combatAchievementTier(rs.getInt("combat_achievement_tier"))
            .equippedItemIds(GSON.fromJson(rs.getString("equipped_item_ids"), MAP_INT_INT))
            .equippedItemNames(GSON.fromJson(rs.getString("equipped_item_names"), MAP_INT_STRING))
            .inventoryItemIds(GSON.fromJson(rs.getString("inventory_item_ids"), MAP_INT_INT))
            .killCount(rs.getInt("kill_count"))
            .personalBest(rs.getInt("personal_best"))
            .isPersonalBest(rs.getInt("is_personal_best") == 1)
            .world(rs.getInt("world"))
            .isTask(rs.getInt("is_task") == 1)
            .teamSize(rs.getInt("team_size"))
            .teamMembers(GSON.fromJson(rs.getString("team_members"), LIST_STRING))
            .metadata(GSON.fromJson(rs.getString("metadata"), MAP_STRING_STRING))
            .build();
    }

    private RaidRecord raidFromResultSet(ResultSet rs) throws SQLException
    {
        return RaidRecord.builder()
            .id(rs.getLong("id"))
            .killRecordId(rs.getLong("kill_record_id"))
            .raidType(rs.getString("raid_type"))
            .totalPoints(rs.getInt("total_points"))
            .personalPoints(rs.getInt("personal_points"))
            .teamSize(rs.getInt("team_size"))
            .raidLevel(rs.getInt("raid_level"))
            .rooms(GSON.fromJson(rs.getString("rooms"), LIST_ROOM))
            .route(rs.getString("route"))
            .purpleReceived(rs.getInt("purple_received") == 1)
            .purpleItemName(rs.getString("purple_item_name"))
            .purpleItemId(rs.getInt("purple_item_id"))
            .totalDeaths(rs.getInt("total_deaths"))
            .personalDeaths(rs.getInt("personal_deaths"))
            .build();
    }
}
