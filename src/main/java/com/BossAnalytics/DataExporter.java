package com.BossAnalytics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Exports data to CSV (pandas-friendly flat format) and JSON.
 */
@Slf4j
public class DataExporter
{
    private final DataStore dataStore;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public DataExporter(DataStore dataStore)
    {
        this.dataStore = dataStore;
    }

    /**
     * Export kills to CSV. If bossName is null, exports all bosses.
     */
    public File exportKillsCsv(String bossName, File outputDir) throws Exception
    {
        List<KillRecord> kills = bossName == null
            ? dataStore.getAllKills()
            : dataStore.getKillsByBoss(bossName, 0);

        File csvFile = new File(outputDir, sanitize(bossName != null ? bossName : "all_bosses") + "_kills.csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile)))
        {
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

                Map<String, Integer> skills = kill.getSkillLevels();
                for (String s : new String[]{"Attack","Strength","Defence","Ranged","Magic","Prayer","Hitpoints"})
                    sj.add(safeGet(skills, s));

                Map<String, Integer> boosted = kill.getBoostedLevels();
                for (String s : new String[]{"Attack","Strength","Defence","Ranged","Magic","Prayer","Hitpoints"})
                    sj.add(safeGet(boosted, s));

                // Gear slots: 0=head,1=cape,2=amulet,3=weapon,4=body,5=shield,7=legs,10=boots,12=ring,13=ammo
                Map<Integer, Integer> gear = kill.getEquippedItemIds();
                Map<Integer, String> gearNames = kill.getEquippedItemNames();
                sj.add(safeGetInt(gear, 3));
                sj.add(csvEscape(safeGetStr(gearNames, 3)));
                sj.add(safeGetInt(gear, 0));
                sj.add(safeGetInt(gear, 4));
                sj.add(safeGetInt(gear, 7));
                sj.add(safeGetInt(gear, 10));
                sj.add(safeGetInt(gear, 1));
                sj.add(safeGetInt(gear, 12));
                sj.add(safeGetInt(gear, 13));
                sj.add(safeGetInt(gear, 5));

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
            return "\"" + val.replace("\"", "\"\"") + "\"";
        return val;
    }

    private String sanitize(String name)
    {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
