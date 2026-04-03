package com.gamecubejona.bossanalytics.export;

import com.gamecubejona.bossanalytics.data.DataStore;
import com.gamecubejona.bossanalytics.data.KillRecord;
import com.gamecubejona.bossanalytics.data.RaidRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Exports collected data to CSV and JSON for external analysis.
 * 
 * CSV format is designed to be pandas-friendly:
 * - Flat structure (gear/levels unpacked into columns)
 * - Consistent column order
 * - ISO timestamps
 *
 * Use cases:
 * - Pull into Jupyter notebook for kill time regression analysis
 * - Feed into R for gear efficiency comparisons
 * - Generate charts for YouTube videos
 */
@Slf4j
@Singleton
public class DataExporter
{
    @Inject
    private DataStore dataStore;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Export all kills for a boss to CSV.
     * Columns: timestamp, boss, duration_s, combat_level, ca_tier,
     *          attack, strength, defence, ranged, magic, prayer, hitpoints,
     *          weapon, helm, body, legs, boots, cape, ring, ammo,
     *          kill_count, is_pb, team_size, is_task, world
     */
    public File exportKillsCsv(String bossName, File outputDir) throws Exception
    {
        List<KillRecord> kills = bossName == null
            ? dataStore.getAllKills()
            : dataStore.getKillsByBoss(bossName);

        File csvFile = new File(outputDir, sanitize(bossName != null ? bossName : "all_bosses") + "_kills.csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile)))
        {
            // Header
            pw.println("timestamp,boss,duration_seconds,duration_ticks,from_chat,"
                + "combat_level,ca_tier,"
                + "attack,strength,defence,ranged,magic,prayer,hitpoints,"
                + "boosted_attack,boosted_strength,boosted_defence,boosted_ranged,"
                + "boosted_magic,boosted_prayer,boosted_hitpoints,"
                + "weapon_id,weapon_name,helm_id,body_id,legs_id,boots_id,"
                + "cape_id,ring_id,ammo_id,shield_id,"
                + "kill_count,is_pb,team_size,is_task,world");

            for (KillRecord kill : kills)
            {
                StringJoiner sj = new StringJoiner(",");
                sj.add(kill.getTimestamp().toString());
                sj.add(csvEscape(kill.getBossName()));
                sj.add(String.format("%.2f", kill.getDurationSeconds()));
                sj.add(String.valueOf(kill.getDurationTicks()));
                sj.add(kill.isDurationFromChat() ? "1" : "0");
                sj.add(String.valueOf(kill.getCombatLevel()));
                sj.add(String.valueOf(kill.getCombatAchievementTier()));

                // Skill levels (real)
                Map<String, Integer> skills = kill.getSkillLevels();
                sj.add(safeGet(skills, "Attack"));
                sj.add(safeGet(skills, "Strength"));
                sj.add(safeGet(skills, "Defence"));
                sj.add(safeGet(skills, "Ranged"));
                sj.add(safeGet(skills, "Magic"));
                sj.add(safeGet(skills, "Prayer"));
                sj.add(safeGet(skills, "Hitpoints"));

                // Boosted levels
                Map<String, Integer> boosted = kill.getBoostedLevels();
                sj.add(safeGet(boosted, "Attack"));
                sj.add(safeGet(boosted, "Strength"));
                sj.add(safeGet(boosted, "Defence"));
                sj.add(safeGet(boosted, "Ranged"));
                sj.add(safeGet(boosted, "Magic"));
                sj.add(safeGet(boosted, "Prayer"));
                sj.add(safeGet(boosted, "Hitpoints"));

                // Gear (by equipment slot index)
                // Slots: 0=head, 1=cape, 2=amulet, 3=weapon, 4=body,
                //        5=shield, 6=?, 7=legs, 8=?, 9=gloves, 10=boots,
                //        11=?, 12=ring, 13=ammo
                Map<Integer, Integer> gear = kill.getEquippedItemIds();
                Map<Integer, String> gearNames = kill.getEquippedItemNames();
                sj.add(safeGetInt(gear, 3));  // weapon id
                sj.add(csvEscape(safeGetStr(gearNames, 3)));  // weapon name
                sj.add(safeGetInt(gear, 0));  // helm
                sj.add(safeGetInt(gear, 4));  // body
                sj.add(safeGetInt(gear, 7));  // legs
                sj.add(safeGetInt(gear, 10)); // boots
                sj.add(safeGetInt(gear, 1));  // cape
                sj.add(safeGetInt(gear, 12)); // ring
                sj.add(safeGetInt(gear, 13)); // ammo
                sj.add(safeGetInt(gear, 5));  // shield

                sj.add(String.valueOf(kill.getKillCount()));
                sj.add(kill.isPersonalBest() ? "1" : "0");
                sj.add(String.valueOf(kill.getTeamSize()));
                sj.add(kill.isTask() ? "1" : "0");
                sj.add(String.valueOf(kill.getWorld()));

                pw.println(sj.toString());
            }
        }

        log.info("Exported {} kills to {}", kills.size(), csvFile.getAbsolutePath());
        return csvFile;
    }

    /**
     * Export raids to CSV with room-level detail.
     */
    public File exportRaidsCsv(String raidType, File outputDir) throws Exception
    {
        List<RaidRecord> raids = dataStore.getRaidsByType(raidType);

        File csvFile = new File(outputDir, sanitize(raidType) + "_raids.csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile)))
        {
            pw.println("raid_id,kill_id,type,total_points,personal_points,"
                + "team_size,raid_level,route,purple,purple_item,"
                + "total_deaths,personal_deaths,room_count");

            for (RaidRecord raid : raids)
            {
                StringJoiner sj = new StringJoiner(",");
                sj.add(String.valueOf(raid.getId()));
                sj.add(String.valueOf(raid.getKillRecordId()));
                sj.add(csvEscape(raid.getRaidType()));
                sj.add(String.valueOf(raid.getTotalPoints()));
                sj.add(String.valueOf(raid.getPersonalPoints()));
                sj.add(String.valueOf(raid.getTeamSize()));
                sj.add(String.valueOf(raid.getRaidLevel()));
                sj.add(csvEscape(raid.getRoute()));
                sj.add(raid.isPurpleReceived() ? "1" : "0");
                sj.add(csvEscape(raid.getPurpleItemName() != null ? raid.getPurpleItemName() : ""));
                sj.add(String.valueOf(raid.getTotalDeaths()));
                sj.add(String.valueOf(raid.getPersonalDeaths()));
                sj.add(String.valueOf(raid.getRooms() != null ? raid.getRooms().size() : 0));
                pw.println(sj.toString());
            }
        }

        // Also export room-level detail
        File roomsCsv = new File(outputDir, sanitize(raidType) + "_rooms.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(roomsCsv)))
        {
            pw.println("raid_id,room_name,room_type,order,duration_ticks,duration_seconds,deaths");

            for (RaidRecord raid : raids)
            {
                if (raid.getRooms() == null) continue;
                for (RaidRecord.RoomRecord room : raid.getRooms())
                {
                    StringJoiner sj = new StringJoiner(",");
                    sj.add(String.valueOf(raid.getId()));
                    sj.add(csvEscape(room.getRoomName()));
                    sj.add(csvEscape(room.getRoomType()));
                    sj.add(String.valueOf(room.getOrderInRaid()));
                    sj.add(String.valueOf(room.getDurationTicks()));
                    sj.add(String.format("%.1f", room.getDurationTicks() * 0.6));
                    sj.add(String.valueOf(room.getDeathsInRoom()));
                    pw.println(sj.toString());
                }
            }
        }

        log.info("Exported {} raids to {}", raids.size(), csvFile.getAbsolutePath());
        return csvFile;
    }

    /**
     * Export everything as a single JSON file.
     */
    public File exportAllJson(File outputDir) throws Exception
    {
        Map<String, Object> data = Map.of(
            "kills", dataStore.getAllKills(),
            "cox_raids", dataStore.getRaidsByType("cox"),
            "cox_cm_raids", dataStore.getRaidsByType("cox_cm"),
            "toa_raids", dataStore.getRaidsByType("toa"),
            "summary", Map.of(
                "kill_counts", dataStore.getKillCounts(),
                "avg_kill_times", dataStore.getAverageKillTimes()
            )
        );

        File jsonFile = new File(outputDir, "boss_analytics_export.json");
        try (FileWriter fw = new FileWriter(jsonFile))
        {
            GSON.toJson(data, fw);
        }

        log.info("Exported all data to {}", jsonFile.getAbsolutePath());
        return jsonFile;
    }

    // ========== HELPERS ==========

    private String safeGet(Map<String, Integer> map, String key)
    {
        if (map == null) return "";
        Integer val = map.get(key);
        return val != null ? String.valueOf(val) : "";
    }

    private String safeGetInt(Map<Integer, Integer> map, int key)
    {
        if (map == null) return "";
        Integer val = map.get(key);
        return val != null ? String.valueOf(val) : "";
    }

    private String safeGetStr(Map<Integer, String> map, int key)
    {
        if (map == null) return "";
        return map.getOrDefault(key, "");
    }

    private String csvEscape(String val)
    {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n"))
        {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private String sanitize(String name)
    {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
