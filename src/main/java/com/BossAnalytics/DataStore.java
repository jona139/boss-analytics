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
    private static final Type LIST_GROUP_MEMBER = new TypeToken<List<KillRecord.GroupMemberSnapshot>>(){}.getType();
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
        migrateSchema();
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
                + "  world_types TEXT,"
                + "  game_mode TEXT DEFAULT 'standard',"
                + "  is_leagues INTEGER DEFAULT 0,"
                + "  is_task INTEGER DEFAULT 0,"
                + "  wave INTEGER DEFAULT 0,"
                + "  wave_name TEXT,"
                + "  segment_duration_ticks INTEGER DEFAULT 0,"
                + "  total_run_ticks INTEGER DEFAULT 0,"
                + "  is_death_record INTEGER DEFAULT 0,"
                + "  death_wave INTEGER DEFAULT 0,"
                + "  activity_variant TEXT,"
                + "  team_size INTEGER DEFAULT 1,"
                + "  group_size_label TEXT DEFAULT '1',"
                + "  team_members TEXT,"
                + "  other_players TEXT,"
                + "  metadata TEXT,"
                + "  effective_account_type INTEGER DEFAULT 0,"
                + "  account_type_label TEXT"
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

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS cox_runs ("
                + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  kill_record_id INTEGER NOT NULL UNIQUE REFERENCES kills(id),"
                + "  is_challenge_mode INTEGER DEFAULT 0,"
                + "  total_points INTEGER DEFAULT 0,"
                + "  personal_points INTEGER DEFAULT 0,"
                + "  team_size INTEGER DEFAULT 1,"
                + "  group_size_label TEXT DEFAULT '1',"
                + "  total_ticks INTEGER DEFAULT 0,"
                + "  total_seconds REAL DEFAULT 0,"
                + "  olm_ticks INTEGER DEFAULT 0,"
                + "  olm_seconds REAL DEFAULT 0,"
                + "  route TEXT,"
                + "  room_tekton INTEGER DEFAULT 0,"
                + "  room_muttadile INTEGER DEFAULT 0,"
                + "  room_vanguards INTEGER DEFAULT 0,"
                + "  room_vasa_nistirio INTEGER DEFAULT 0,"
                + "  room_vespula INTEGER DEFAULT 0,"
                + "  room_guardians INTEGER DEFAULT 0,"
                + "  room_mystics INTEGER DEFAULT 0,"
                + "  room_shamans INTEGER DEFAULT 0,"
                + "  room_great_olm INTEGER DEFAULT 0,"
                + "  room_thieving INTEGER DEFAULT 0,"
                + "  room_ice_demon INTEGER DEFAULT 0"
                + ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS toa_runs ("
                + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  kill_record_id INTEGER NOT NULL UNIQUE REFERENCES kills(id),"
                + "  mode TEXT NOT NULL,"
                + "  invocation_level INTEGER DEFAULT 0,"
                + "  raid_damage INTEGER DEFAULT 0,"
                + "  team_size INTEGER DEFAULT 1,"
                + "  group_size_label TEXT DEFAULT '1',"
                + "  total_ticks INTEGER DEFAULT 0,"
                + "  total_seconds REAL DEFAULT 0,"
                + "  team_snapshot TEXT"
                + ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS wave_runs ("
                + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  kill_record_id INTEGER NOT NULL UNIQUE REFERENCES kills(id),"
                + "  activity_name TEXT NOT NULL,"
                + "  wave INTEGER DEFAULT 0,"
                + "  wave_name TEXT,"
                + "  segment_duration_ticks INTEGER DEFAULT 0,"
                + "  total_run_ticks INTEGER DEFAULT 0,"
                + "  is_death_record INTEGER DEFAULT 0,"
                + "  death_wave INTEGER DEFAULT 0,"
                + "  activity_variant TEXT,"
                + "  team_size INTEGER DEFAULT 1,"
                + "  group_size_label TEXT DEFAULT '1'"
                + ")"
            );

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kills_boss ON kills(boss_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kills_timestamp ON kills(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kills_boss_timestamp ON kills(boss_name, timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_raids_type ON raids(raid_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_raids_kill_id ON raids(kill_record_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cox_runs_kill_id ON cox_runs(kill_record_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cox_runs_cm ON cox_runs(is_challenge_mode)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_toa_runs_kill_id ON toa_runs(kill_record_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_toa_runs_mode ON toa_runs(mode)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_wave_runs_activity ON wave_runs(activity_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_wave_runs_kill_id ON wave_runs(kill_record_id)");
        }
    }

    private void migrateSchema() throws SQLException
    {
        addColumnIfMissing("kills", "start_equipped_item_ids", "TEXT");
        addColumnIfMissing("kills", "start_equipped_item_names", "TEXT");
        addColumnIfMissing("kills", "start_inventory_item_ids", "TEXT");
        addColumnIfMissing("kills", "hp_lost", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "hp_recovered", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "prayer_lost", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "prayer_restored", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "start_gear_value", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "start_inventory_value", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "end_gear_value", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "end_inventory_value", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "has_rigour", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "has_augury", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "has_deadeye", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "has_mystic_vigour", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "combat_achievement_points", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "total_level", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "playtime_minutes", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "account_type", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "effective_account_type", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "account_type_label", "TEXT");
        addColumnIfMissing("kills", "world_types", "TEXT");
        addColumnIfMissing("kills", "game_mode", "TEXT DEFAULT 'standard'");
        addColumnIfMissing("kills", "is_leagues", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "wave", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "wave_name", "TEXT");
        addColumnIfMissing("kills", "segment_duration_ticks", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "total_run_ticks", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "is_death_record", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "death_wave", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "activity_variant", "TEXT");
        addColumnIfMissing("kills", "personal_deaths", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "total_damage_dealt", "INTEGER DEFAULT 0");
        addColumnIfMissing("kills", "group_size_label", "TEXT DEFAULT '1'");
        addColumnIfMissing("kills", "other_players", "TEXT");
    }

    private void addColumnIfMissing(String table, String column, String type) throws SQLException
    {
        try (Statement stmt = connection.createStatement())
        {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
        catch (SQLException e)
        {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    // ========== KILL RECORDS ==========

    public long insertKill(KillRecord kill) throws SQLException
    {
        String sql = "INSERT INTO kills (boss_name, boss_npc_id, timestamp, duration_ticks, "
            + "duration_seconds, duration_from_chat, combat_level, skill_levels, boosted_levels, "
            + "combat_achievement_tier, equipped_item_ids, equipped_item_names, inventory_item_ids, "
            + "kill_count, personal_best, is_personal_best, world, world_types, game_mode, is_leagues, "
            + "is_task, wave, wave_name, segment_duration_ticks, total_run_ticks, is_death_record, death_wave, activity_variant, "
            + "team_size, "
            + "group_size_label, team_members, other_players, metadata, "
            + "start_equipped_item_ids, start_equipped_item_names, start_inventory_item_ids, "
            + "hp_lost, hp_recovered, prayer_lost, prayer_restored, "
            + "start_gear_value, start_inventory_value, end_gear_value, end_inventory_value, "
            + "has_rigour, has_augury, has_deadeye, has_mystic_vigour, "
            + "combat_achievement_points, total_level, playtime_minutes, account_type, "
            + "effective_account_type, account_type_label, "
            + "personal_deaths, total_damage_dealt"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql))
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
            ps.setInt(15, kill.getPersonalBestTime());
            ps.setInt(16, kill.isPersonalBest() ? 1 : 0);
            ps.setInt(17, kill.getWorld());
            ps.setString(18, kill.getWorldTypes());
            ps.setString(19, kill.getGameMode());
            ps.setInt(20, kill.isLeaguesWorld() ? 1 : 0);
            ps.setInt(21, kill.isTask() ? 1 : 0);
            ps.setInt(22, kill.getWave());
            ps.setString(23, kill.getWaveName());
            ps.setInt(24, kill.getSegmentDurationTicks());
            ps.setInt(25, kill.getTotalRunTicks());
            ps.setInt(26, kill.isDeathRecord() ? 1 : 0);
            ps.setInt(27, kill.getDeathWave());
            ps.setString(28, kill.getActivityVariant());
            ps.setInt(29, kill.getTeamSize());
            ps.setString(30, kill.getGroupSizeLabel());
            ps.setString(31, GSON.toJson(kill.getTeamMembers()));
            ps.setString(32, GSON.toJson(kill.getOtherPlayers()));
            ps.setString(33, GSON.toJson(kill.getMetadata()));
            ps.setString(34, GSON.toJson(kill.getStartEquippedItemIds()));
            ps.setString(35, GSON.toJson(kill.getStartEquippedItemNames()));
            ps.setString(36, GSON.toJson(kill.getStartInventoryItemIds()));
            ps.setInt(37, kill.getHpLost());
            ps.setInt(38, kill.getHpRecovered());
            ps.setInt(39, kill.getPrayerLost());
            ps.setInt(40, kill.getPrayerRestored());
            ps.setLong(41, kill.getStartGearValue());
            ps.setLong(42, kill.getStartInventoryValue());
            ps.setLong(43, kill.getEndGearValue());
            ps.setLong(44, kill.getEndInventoryValue());
            ps.setInt(45, kill.isHasRigour() ? 1 : 0);
            ps.setInt(46, kill.isHasAugury() ? 1 : 0);
            ps.setInt(47, kill.isHasDeadeye() ? 1 : 0);
            ps.setInt(48, kill.isHasMysticVigour() ? 1 : 0);
            ps.setInt(49, kill.getCombatAchievementPoints());
            ps.setInt(50, kill.getTotalLevel());
            ps.setInt(51, kill.getPlaytimeMinutes());
            ps.setInt(52, kill.getAccountType());
            ps.setInt(53, kill.getEffectiveAccountType());
            ps.setString(54, kill.getAccountTypeLabel());
            ps.setInt(55, kill.getPersonalDeaths());
            ps.setInt(56, kill.getTotalDamageDealt());
            ps.executeUpdate();

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()"))
            {
                if (rs.next())
                {
                    long id = rs.getLong(1);
                    log.debug("Inserted kill record {} for {} ({}s)", id, kill.getBossName(), kill.getDurationSeconds());
                    return id;
                }
            }
            return -1;
        }
    }

    public void updateKill(KillRecord kill) throws SQLException
    {
        String sql = "UPDATE kills SET "
            + "boss_name=?, boss_npc_id=?, timestamp=?, duration_ticks=?, duration_seconds=?, duration_from_chat=?, "
            + "combat_level=?, skill_levels=?, boosted_levels=?, combat_achievement_tier=?, "
            + "equipped_item_ids=?, equipped_item_names=?, inventory_item_ids=?, "
            + "kill_count=?, personal_best=?, is_personal_best=?, world=?, world_types=?, game_mode=?, is_leagues=?, "
            + "is_task=?, wave=?, wave_name=?, segment_duration_ticks=?, total_run_ticks=?, is_death_record=?, death_wave=?, activity_variant=?, "
            + "team_size=?, group_size_label=?, team_members=?, other_players=?, metadata=?, "
            + "start_equipped_item_ids=?, start_equipped_item_names=?, start_inventory_item_ids=?, "
            + "hp_lost=?, hp_recovered=?, prayer_lost=?, prayer_restored=?, "
            + "start_gear_value=?, start_inventory_value=?, end_gear_value=?, end_inventory_value=?, "
            + "has_rigour=?, has_augury=?, has_deadeye=?, has_mystic_vigour=?, "
            + "combat_achievement_points=?, total_level=?, playtime_minutes=?, "
            + "account_type=?, effective_account_type=?, account_type_label=?, "
            + "personal_deaths=?, total_damage_dealt=? "
            + "WHERE id=?";

        try (PreparedStatement ps = connection.prepareStatement(sql))
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
            ps.setInt(15, kill.getPersonalBestTime());
            ps.setInt(16, kill.isPersonalBest() ? 1 : 0);
            ps.setInt(17, kill.getWorld());
            ps.setString(18, kill.getWorldTypes());
            ps.setString(19, kill.getGameMode());
            ps.setInt(20, kill.isLeaguesWorld() ? 1 : 0);
            ps.setInt(21, kill.isTask() ? 1 : 0);
            ps.setInt(22, kill.getWave());
            ps.setString(23, kill.getWaveName());
            ps.setInt(24, kill.getSegmentDurationTicks());
            ps.setInt(25, kill.getTotalRunTicks());
            ps.setInt(26, kill.isDeathRecord() ? 1 : 0);
            ps.setInt(27, kill.getDeathWave());
            ps.setString(28, kill.getActivityVariant());
            ps.setInt(29, kill.getTeamSize());
            ps.setString(30, kill.getGroupSizeLabel());
            ps.setString(31, GSON.toJson(kill.getTeamMembers()));
            ps.setString(32, GSON.toJson(kill.getOtherPlayers()));
            ps.setString(33, GSON.toJson(kill.getMetadata()));
            ps.setString(34, GSON.toJson(kill.getStartEquippedItemIds()));
            ps.setString(35, GSON.toJson(kill.getStartEquippedItemNames()));
            ps.setString(36, GSON.toJson(kill.getStartInventoryItemIds()));
            ps.setInt(37, kill.getHpLost());
            ps.setInt(38, kill.getHpRecovered());
            ps.setInt(39, kill.getPrayerLost());
            ps.setInt(40, kill.getPrayerRestored());
            ps.setLong(41, kill.getStartGearValue());
            ps.setLong(42, kill.getStartInventoryValue());
            ps.setLong(43, kill.getEndGearValue());
            ps.setLong(44, kill.getEndInventoryValue());
            ps.setInt(45, kill.isHasRigour() ? 1 : 0);
            ps.setInt(46, kill.isHasAugury() ? 1 : 0);
            ps.setInt(47, kill.isHasDeadeye() ? 1 : 0);
            ps.setInt(48, kill.isHasMysticVigour() ? 1 : 0);
            ps.setInt(49, kill.getCombatAchievementPoints());
            ps.setInt(50, kill.getTotalLevel());
            ps.setInt(51, kill.getPlaytimeMinutes());
            ps.setInt(52, kill.getAccountType());
            ps.setInt(53, kill.getEffectiveAccountType());
            ps.setString(54, kill.getAccountTypeLabel());
            ps.setInt(55, kill.getPersonalDeaths());
            ps.setInt(56, kill.getTotalDamageDealt());
            ps.setLong(57, kill.getId());
            ps.executeUpdate();
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

        try (PreparedStatement ps = connection.prepareStatement(sql))
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
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()"))
            {
                return rs.next() ? rs.getLong(1) : -1;
            }
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

    public long upsertCoxRun(CoxRunRecord run) throws SQLException
    {
        deleteStructuredRowByKillId("cox_runs", run.getKillRecordId());
        String sql = "INSERT INTO cox_runs ("
            + "kill_record_id, is_challenge_mode, total_points, personal_points, team_size, group_size_label, "
            + "total_ticks, total_seconds, olm_ticks, olm_seconds, route, "
            + "room_tekton, room_muttadile, room_vanguards, room_vasa_nistirio, room_vespula, "
            + "room_guardians, room_mystics, room_shamans, room_great_olm, room_thieving, room_ice_demon"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setLong(1, run.getKillRecordId());
            ps.setInt(2, run.isChallengeMode() ? 1 : 0);
            ps.setInt(3, run.getTotalPoints());
            ps.setInt(4, run.getPersonalPoints());
            ps.setInt(5, run.getTeamSize());
            ps.setString(6, run.getGroupSizeLabel());
            ps.setInt(7, run.getTotalTicks());
            ps.setDouble(8, run.getTotalSeconds());
            ps.setInt(9, run.getOlmTicks());
            ps.setDouble(10, run.getOlmSeconds());
            ps.setString(11, run.getRoute());
            ps.setInt(12, run.getRoomTekton());
            ps.setInt(13, run.getRoomMuttadile());
            ps.setInt(14, run.getRoomVanguards());
            ps.setInt(15, run.getRoomVasaNistirio());
            ps.setInt(16, run.getRoomVespula());
            ps.setInt(17, run.getRoomGuardians());
            ps.setInt(18, run.getRoomMystics());
            ps.setInt(19, run.getRoomShamans());
            ps.setInt(20, run.getRoomGreatOlm());
            ps.setInt(21, run.getRoomThieving());
            ps.setInt(22, run.getRoomIceDemon());
            ps.executeUpdate();
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()"))
        {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public long upsertToaRun(ToaRunRecord run) throws SQLException
    {
        deleteStructuredRowByKillId("toa_runs", run.getKillRecordId());
        String sql = "INSERT INTO toa_runs (kill_record_id, mode, invocation_level, raid_damage, "
            + "team_size, group_size_label, total_ticks, total_seconds, team_snapshot) "
            + "VALUES (?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setLong(1, run.getKillRecordId());
            ps.setString(2, run.getMode());
            ps.setInt(3, run.getInvocationLevel());
            ps.setInt(4, run.getRaidDamage());
            ps.setInt(5, run.getTeamSize());
            ps.setString(6, run.getGroupSizeLabel());
            ps.setInt(7, run.getTotalTicks());
            ps.setDouble(8, run.getTotalSeconds());
            ps.setString(9, run.getTeamSnapshot());
            ps.executeUpdate();
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()"))
        {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public long upsertWaveRun(WaveRunRecord run) throws SQLException
    {
        deleteStructuredRowByKillId("wave_runs", run.getKillRecordId());
        String sql = "INSERT INTO wave_runs (kill_record_id, activity_name, wave, wave_name, "
            + "segment_duration_ticks, total_run_ticks, is_death_record, death_wave, activity_variant, "
            + "team_size, group_size_label) VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setLong(1, run.getKillRecordId());
            ps.setString(2, run.getActivityName());
            ps.setInt(3, run.getWave());
            ps.setString(4, run.getWaveName());
            ps.setInt(5, run.getSegmentDurationTicks());
            ps.setInt(6, run.getTotalRunTicks());
            ps.setInt(7, run.isDeathRecord() ? 1 : 0);
            ps.setInt(8, run.getDeathWave());
            ps.setString(9, run.getActivityVariant());
            ps.setInt(10, run.getTeamSize());
            ps.setString(11, run.getGroupSizeLabel());
            ps.executeUpdate();
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()"))
        {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    public List<CoxRunRecord> getCoxRuns() throws SQLException
    {
        List<CoxRunRecord> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM cox_runs ORDER BY id DESC"))
        {
            while (rs.next())
            {
                results.add(coxRunFromResultSet(rs));
            }
        }
        return results;
    }

    public List<ToaRunRecord> getToaRuns() throws SQLException
    {
        List<ToaRunRecord> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM toa_runs ORDER BY id DESC"))
        {
            while (rs.next())
            {
                results.add(toaRunFromResultSet(rs));
            }
        }
        return results;
    }

    public List<WaveRunRecord> getWaveRuns() throws SQLException
    {
        List<WaveRunRecord> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM wave_runs ORDER BY id DESC"))
        {
            while (rs.next())
            {
                results.add(waveRunFromResultSet(rs));
            }
        }
        return results;
    }

    private void deleteStructuredRowByKillId(String table, long killRecordId) throws SQLException
    {
        String sql = "DELETE FROM " + table + " WHERE kill_record_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setLong(1, killRecordId);
            ps.executeUpdate();
        }
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
            .personalBestTime(rs.getInt("personal_best"))
            .personalBest(rs.getInt("is_personal_best") == 1)
            .world(rs.getInt("world"))
            .worldTypes(rs.getString("world_types"))
            .gameMode(rs.getString("game_mode"))
            .leaguesWorld(rs.getInt("is_leagues") == 1)
            .task(rs.getInt("is_task") == 1)
            .wave(rs.getInt("wave"))
            .waveName(rs.getString("wave_name"))
            .segmentDurationTicks(rs.getInt("segment_duration_ticks"))
            .totalRunTicks(rs.getInt("total_run_ticks"))
            .deathRecord(rs.getInt("is_death_record") == 1)
            .deathWave(rs.getInt("death_wave"))
            .activityVariant(rs.getString("activity_variant"))
            .teamSize(rs.getInt("team_size"))
            .groupSizeLabel(rs.getString("group_size_label"))
            .teamMembers(GSON.fromJson(rs.getString("team_members"), LIST_STRING))
            .otherPlayers(GSON.fromJson(rs.getString("other_players"), LIST_GROUP_MEMBER))
            .metadata(GSON.fromJson(rs.getString("metadata"), MAP_STRING_STRING))
            .startEquippedItemIds(GSON.fromJson(rs.getString("start_equipped_item_ids"), MAP_INT_INT))
            .startEquippedItemNames(GSON.fromJson(rs.getString("start_equipped_item_names"), MAP_INT_STRING))
            .startInventoryItemIds(GSON.fromJson(rs.getString("start_inventory_item_ids"), MAP_INT_INT))
            .hpLost(rs.getInt("hp_lost"))
            .hpRecovered(rs.getInt("hp_recovered"))
            .prayerLost(rs.getInt("prayer_lost"))
            .prayerRestored(rs.getInt("prayer_restored"))
            .startGearValue(rs.getLong("start_gear_value"))
            .startInventoryValue(rs.getLong("start_inventory_value"))
            .endGearValue(rs.getLong("end_gear_value"))
            .endInventoryValue(rs.getLong("end_inventory_value"))
            .hasRigour(rs.getInt("has_rigour") == 1)
            .hasAugury(rs.getInt("has_augury") == 1)
            .hasDeadeye(rs.getInt("has_deadeye") == 1)
            .hasMysticVigour(rs.getInt("has_mystic_vigour") == 1)
            .combatAchievementPoints(rs.getInt("combat_achievement_points"))
            .totalLevel(rs.getInt("total_level"))
            .playtimeMinutes(rs.getInt("playtime_minutes"))
            .accountType(rs.getInt("account_type"))
            .effectiveAccountType(rs.getInt("effective_account_type"))
            .accountTypeLabel(rs.getString("account_type_label"))
            .personalDeaths(rs.getInt("personal_deaths"))
            .totalDamageDealt(rs.getInt("total_damage_dealt"))
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

    private CoxRunRecord coxRunFromResultSet(ResultSet rs) throws SQLException
    {
        return CoxRunRecord.builder()
            .id(rs.getLong("id"))
            .killRecordId(rs.getLong("kill_record_id"))
            .challengeMode(rs.getInt("is_challenge_mode") == 1)
            .totalPoints(rs.getInt("total_points"))
            .personalPoints(rs.getInt("personal_points"))
            .teamSize(rs.getInt("team_size"))
            .groupSizeLabel(rs.getString("group_size_label"))
            .totalTicks(rs.getInt("total_ticks"))
            .totalSeconds(rs.getDouble("total_seconds"))
            .olmTicks(rs.getInt("olm_ticks"))
            .olmSeconds(rs.getDouble("olm_seconds"))
            .route(rs.getString("route"))
            .roomTekton(rs.getInt("room_tekton"))
            .roomMuttadile(rs.getInt("room_muttadile"))
            .roomVanguards(rs.getInt("room_vanguards"))
            .roomVasaNistirio(rs.getInt("room_vasa_nistirio"))
            .roomVespula(rs.getInt("room_vespula"))
            .roomGuardians(rs.getInt("room_guardians"))
            .roomMystics(rs.getInt("room_mystics"))
            .roomShamans(rs.getInt("room_shamans"))
            .roomGreatOlm(rs.getInt("room_great_olm"))
            .roomThieving(rs.getInt("room_thieving"))
            .roomIceDemon(rs.getInt("room_ice_demon"))
            .build();
    }

    private ToaRunRecord toaRunFromResultSet(ResultSet rs) throws SQLException
    {
        return ToaRunRecord.builder()
            .id(rs.getLong("id"))
            .killRecordId(rs.getLong("kill_record_id"))
            .mode(rs.getString("mode"))
            .invocationLevel(rs.getInt("invocation_level"))
            .raidDamage(rs.getInt("raid_damage"))
            .teamSize(rs.getInt("team_size"))
            .groupSizeLabel(rs.getString("group_size_label"))
            .totalTicks(rs.getInt("total_ticks"))
            .totalSeconds(rs.getDouble("total_seconds"))
            .teamSnapshot(rs.getString("team_snapshot"))
            .build();
    }

    private WaveRunRecord waveRunFromResultSet(ResultSet rs) throws SQLException
    {
        return WaveRunRecord.builder()
            .id(rs.getLong("id"))
            .killRecordId(rs.getLong("kill_record_id"))
            .activityName(rs.getString("activity_name"))
            .wave(rs.getInt("wave"))
            .waveName(rs.getString("wave_name"))
            .segmentDurationTicks(rs.getInt("segment_duration_ticks"))
            .totalRunTicks(rs.getInt("total_run_ticks"))
            .deathRecord(rs.getInt("is_death_record") == 1)
            .deathWave(rs.getInt("death_wave"))
            .activityVariant(rs.getString("activity_variant"))
            .teamSize(rs.getInt("team_size"))
            .groupSizeLabel(rs.getString("group_size_label"))
            .build();
    }
}
