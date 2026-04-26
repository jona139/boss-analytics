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
    private static final Gson COMPACT_GSON = new Gson();

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
                + "kill_count,is_pb,team_size,group_size,team_members,other_players,is_task,world,world_types,game_mode,is_leagues_world,"
                + "wave,wave_name,segment_duration_ticks,total_run_ticks,is_death_record,death_wave,activity_variant,"
                + "hp_lost,hp_recovered,prayer_lost,prayer_restored,"
                + "start_gear_value,start_inventory_value,end_gear_value,end_inventory_value,"
                + "has_rigour,has_augury,has_deadeye,has_mystic_vigour,"
                + "combat_achievement_points,total_level,playtime_minutes,account_type,effective_account_type,account_type_label,"
                + "personal_deaths,total_damage_dealt");

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
                sj.add(csvEscape(kill.getGroupSizeLabel() != null ? kill.getGroupSizeLabel() : ""));
                sj.add(csvEscape(toJsonOrEmpty(kill.getTeamMembers())));
                sj.add(csvEscape(toJsonOrEmpty(kill.getOtherPlayers())));
                sj.add(kill.isTask() ? "1" : "0");
                sj.add(String.valueOf(kill.getWorld()));
                sj.add(csvEscape(kill.getWorldTypes()));
                sj.add(csvEscape(kill.getGameMode()));
                sj.add(kill.isLeaguesWorld() ? "1" : "0");
                sj.add(String.valueOf(kill.getWave()));
                sj.add(csvEscape(kill.getWaveName()));
                sj.add(String.valueOf(kill.getSegmentDurationTicks()));
                sj.add(String.valueOf(kill.getTotalRunTicks()));
                sj.add(kill.isDeathRecord() ? "1" : "0");
                sj.add(String.valueOf(kill.getDeathWave()));
                sj.add(csvEscape(kill.getActivityVariant()));

                // New fields
                sj.add(String.valueOf(kill.getHpLost()));
                sj.add(String.valueOf(kill.getHpRecovered()));
                sj.add(String.valueOf(kill.getPrayerLost()));
                sj.add(String.valueOf(kill.getPrayerRestored()));
                sj.add(String.valueOf(kill.getStartGearValue()));
                sj.add(String.valueOf(kill.getStartInventoryValue()));
                sj.add(String.valueOf(kill.getEndGearValue()));
                sj.add(String.valueOf(kill.getEndInventoryValue()));
                sj.add(kill.isHasRigour() ? "1" : "0");
                sj.add(kill.isHasAugury() ? "1" : "0");
                sj.add(kill.isHasDeadeye() ? "1" : "0");
                sj.add(kill.isHasMysticVigour() ? "1" : "0");
                sj.add(String.valueOf(kill.getCombatAchievementPoints()));
                sj.add(String.valueOf(kill.getTotalLevel()));
                sj.add(String.valueOf(kill.getPlaytimeMinutes()));
                sj.add(String.valueOf(kill.getAccountType()));
                sj.add(String.valueOf(kill.getEffectiveAccountType()));
                sj.add(csvEscape(kill.getAccountTypeLabel()));
                sj.add(String.valueOf(kill.getPersonalDeaths()));
                sj.add(String.valueOf(kill.getTotalDamageDealt()));

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

    public File exportCoxRunsCsv(File outputDir) throws Exception
    {
        List<CoxRunRecord> runs = dataStore.getCoxRuns();
        File csvFile = new File(outputDir, "cox_runs.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile)))
        {
            pw.println("id,kill_record_id,is_challenge_mode,total_points,personal_points,team_size,group_size,total_ticks,total_seconds,olm_ticks,olm_seconds,route,"
                + "tekton,muttadile,vanguards,vasa_nistirio,vespula,guardians,mystics,shamans,great_olm,thieving,ice_demon");
            for (CoxRunRecord run : runs)
            {
                StringJoiner sj = new StringJoiner(",");
                sj.add(String.valueOf(run.getId()));
                sj.add(String.valueOf(run.getKillRecordId()));
                sj.add(run.isChallengeMode() ? "1" : "0");
                sj.add(String.valueOf(run.getTotalPoints()));
                sj.add(String.valueOf(run.getPersonalPoints()));
                sj.add(String.valueOf(run.getTeamSize()));
                sj.add(csvEscape(run.getGroupSizeLabel()));
                sj.add(String.valueOf(run.getTotalTicks()));
                sj.add(String.format("%.2f", run.getTotalSeconds()));
                sj.add(String.valueOf(run.getOlmTicks()));
                sj.add(String.format("%.2f", run.getOlmSeconds()));
                sj.add(csvEscape(run.getRoute()));
                sj.add(String.valueOf(run.getRoomTekton()));
                sj.add(String.valueOf(run.getRoomMuttadile()));
                sj.add(String.valueOf(run.getRoomVanguards()));
                sj.add(String.valueOf(run.getRoomVasaNistirio()));
                sj.add(String.valueOf(run.getRoomVespula()));
                sj.add(String.valueOf(run.getRoomGuardians()));
                sj.add(String.valueOf(run.getRoomMystics()));
                sj.add(String.valueOf(run.getRoomShamans()));
                sj.add(String.valueOf(run.getRoomGreatOlm()));
                sj.add(String.valueOf(run.getRoomThieving()));
                sj.add(String.valueOf(run.getRoomIceDemon()));
                pw.println(sj.toString());
            }
        }
        return csvFile;
    }

    public File exportToaRunsCsv(File outputDir) throws Exception
    {
        List<ToaRunRecord> runs = dataStore.getToaRuns();
        File csvFile = new File(outputDir, "toa_runs.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile)))
        {
            pw.println("id,kill_record_id,mode,invocation_level,raid_damage,team_size,group_size,total_ticks,total_seconds,team_snapshot");
            for (ToaRunRecord run : runs)
            {
                StringJoiner sj = new StringJoiner(",");
                sj.add(String.valueOf(run.getId()));
                sj.add(String.valueOf(run.getKillRecordId()));
                sj.add(csvEscape(run.getMode()));
                sj.add(String.valueOf(run.getInvocationLevel()));
                sj.add(String.valueOf(run.getRaidDamage()));
                sj.add(String.valueOf(run.getTeamSize()));
                sj.add(csvEscape(run.getGroupSizeLabel()));
                sj.add(String.valueOf(run.getTotalTicks()));
                sj.add(String.format("%.2f", run.getTotalSeconds()));
                sj.add(csvEscape(run.getTeamSnapshot()));
                pw.println(sj.toString());
            }
        }
        return csvFile;
    }

    public File exportWaveRunsCsv(File outputDir) throws Exception
    {
        List<WaveRunRecord> runs = dataStore.getWaveRuns();
        File csvFile = new File(outputDir, "wave_runs.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile)))
        {
            pw.println("id,kill_record_id,activity,wave,wave_name,segment_duration_ticks,total_run_ticks,is_death_record,death_wave,activity_variant,team_size,group_size");
            for (WaveRunRecord run : runs)
            {
                StringJoiner sj = new StringJoiner(",");
                sj.add(String.valueOf(run.getId()));
                sj.add(String.valueOf(run.getKillRecordId()));
                sj.add(csvEscape(run.getActivityName()));
                sj.add(String.valueOf(run.getWave()));
                sj.add(csvEscape(run.getWaveName()));
                sj.add(String.valueOf(run.getSegmentDurationTicks()));
                sj.add(String.valueOf(run.getTotalRunTicks()));
                sj.add(run.isDeathRecord() ? "1" : "0");
                sj.add(String.valueOf(run.getDeathWave()));
                sj.add(csvEscape(run.getActivityVariant()));
                sj.add(String.valueOf(run.getTeamSize()));
                sj.add(csvEscape(run.getGroupSizeLabel()));
                pw.println(sj.toString());
            }
        }
        return csvFile;
    }

    public File exportAllJson(File outputDir) throws Exception
    {
        Map<String, Object> data = Map.of(
            "kills", dataStore.getAllKills(),
            "cox_raids", dataStore.getRaidsByType("cox"),
            "cox_cm_raids", dataStore.getRaidsByType("cox_cm"),
            "toa_raids", dataStore.getRaidsByType("toa"),
            "cox_runs", dataStore.getCoxRuns(),
            "toa_runs", dataStore.getToaRuns(),
            "wave_runs", dataStore.getWaveRuns(),
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

    private String toJsonOrEmpty(Object value)
    {
        return value != null ? COMPACT_GSON.toJson(value) : "";
    }

    private String sanitize(String name)
    {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
