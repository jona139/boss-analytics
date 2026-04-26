package com.BossAnalytics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;

/**
 * Chambers of Xeric specific tracking: rooms, routes, points, deaths.
 */
@Slf4j
public class CoxHandler implements KillTracker.KillListener
{
    private final Client client;
    private final DataStore dataStore;

    private static final int VARBIT_IN_RAID = 5432;
    private static final int VARBIT_TOTAL_POINTS = 5422;
    private static final int VARBIT_PERSONAL_POINTS = 5431;
    private static final List<String> ROOM_FLAG_NAMES = Arrays.asList(
        "Tekton", "Muttadile", "Vanguards", "Vasa Nistirio", "Vespula",
        "Guardians", "Mystics", "Shamans", "Great Olm", "Thieving", "Ice Demon"
    );

    // Room detection by NPC presence
    private static final Map<Integer, String> ROOM_NPCS = new HashMap<>();
    static {
        ROOM_NPCS.put(7540, "Tekton");
        ROOM_NPCS.put(7541, "Tekton");
        ROOM_NPCS.put(7542, "Tekton");
        ROOM_NPCS.put(7543, "Muttadile");
        ROOM_NPCS.put(7544, "Muttadile");
        ROOM_NPCS.put(7526, "Vanguards");
        ROOM_NPCS.put(7527, "Vanguards");
        ROOM_NPCS.put(7528, "Vanguards");
        ROOM_NPCS.put(7529, "Vasa Nistirio");
        ROOM_NPCS.put(7530, "Vasa Nistirio");
        ROOM_NPCS.put(7531, "Vespula");
        ROOM_NPCS.put(7532, "Vespula");
        ROOM_NPCS.put(7533, "Guardians");
        ROOM_NPCS.put(7534, "Guardians");
        ROOM_NPCS.put(7553, "Mystics");
        ROOM_NPCS.put(7573, "Shamans");
        ROOM_NPCS.put(7551, "Great Olm");
        ROOM_NPCS.put(7552, "Great Olm");
        ROOM_NPCS.put(7554, "Great Olm");
    }

    // State
    private boolean inRaid = false;
    private int raidStartTick = -1;
    private List<RaidRecord.RoomRecord> rooms = new ArrayList<>();
    private String currentRoom = null;
    private int currentRoomStartTick = -1;
    private int roomOrder = 0;
    private int personalDeaths = 0;
    private int currentRoomDeaths = 0;

    public CoxHandler(Client client, DataStore dataStore)
    {
        this.client = client;
        this.dataStore = dataStore;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        int inRaidValue = client.getVarbitValue(VARBIT_IN_RAID);
        if (inRaidValue == 1 && !inRaid)
        {
            startRaid();
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        String roomName = ROOM_NPCS.get(event.getNpc().getId());
        if (roomName != null)
        {
            if (!inRaid)
            {
                startRaid();
            }
            if (!roomName.equals(currentRoom))
            {
                transitionToRoom(roomName);
            }
        }
    }

    @Override
    public void onKillRecorded(KillRecord record)
    {
        String bossName = record.getBossName();
        if (bossName == null || !bossName.startsWith("Chambers of Xeric") || !inRaid)
        {
            return;
        }

        finalizeCurrentRoom();
        boolean challengeMode = isChallengeMode() || bossName.contains("Challenge Mode");
        int nowTick = client.getTickCount();

        if (record.getDurationTicks() <= 0 && raidStartTick > 0)
        {
            int totalTicks = Math.max(0, nowTick - raidStartTick);
            record.setDurationTicks(totalTicks);
            record.setDurationSeconds(totalTicks * 0.6);
            record.setDurationFromChat(false);
        }
        if (record.getDurationSeconds() <= 0 && record.getDurationTicks() > 0)
        {
            record.setDurationSeconds(record.getDurationTicks() * 0.6);
        }

        int totalPoints = client.getVarbitValue(VARBIT_TOTAL_POINTS);
        int personalPoints = client.getVarbitValue(VARBIT_PERSONAL_POINTS);
        int estimatedTeamSize = estimateTeamSize(totalPoints, personalPoints);
        int teamSize = Math.max(record.getTeamSize(), estimatedTeamSize);
        record.setTeamSize(teamSize);
        record.setGroupSizeLabel(teamSize > 8 ? "mass" : String.valueOf(teamSize));
        record.setActivityVariant(challengeMode ? "challenge_mode" : "normal");

        StringBuilder route = new StringBuilder();
        for (RaidRecord.RoomRecord room : rooms)
        {
            if (route.length() > 0) route.append(",");
            route.append(room.getRoomName().toLowerCase());
        }

        Map<String, String> killMetadata = record.getMetadata() != null
            ? new HashMap<>(record.getMetadata())
            : new HashMap<>();
        Set<String> roomsSeen = new HashSet<>();
        int olmTicks = 0;
        for (RaidRecord.RoomRecord room : rooms)
        {
            String roomName = room.getRoomName();
            if (roomName != null)
            {
                roomsSeen.add(roomName.toLowerCase());
            }
            if (roomName != null && roomName.toLowerCase().contains("olm"))
            {
                olmTicks += room.getDurationTicks();
            }
        }

        for (String roomName : ROOM_FLAG_NAMES)
        {
            String key = "cox_room_" + roomName.toLowerCase().replaceAll("[^a-z0-9]+", "_");
            killMetadata.put(key, roomsSeen.contains(roomName.toLowerCase()) ? "1" : "0");
        }
        killMetadata.put("cox_total_ticks", String.valueOf(record.getDurationTicks()));
        killMetadata.put("cox_olm_ticks", String.valueOf(olmTicks));
        killMetadata.put("cox_olm_seconds", String.format("%.1f", olmTicks * 0.6));
        killMetadata.put("cox_is_cm", challengeMode ? "1" : "0");
        killMetadata.put("cox_group_size", record.getGroupSizeLabel());
        if (record.getTeamSize() > 8)
        {
            killMetadata.put("cox_team_snapshot", "mass");
        }
        else if (record.getTeamMembers() != null)
        {
            killMetadata.put("cox_team_snapshot", String.join("|", record.getTeamMembers()));
        }
        record.setMetadata(killMetadata);

        try
        {
            RaidRecord raid = RaidRecord.builder()
                .killRecordId(record.getId())
                .raidType(challengeMode ? "cox_cm" : "cox")
                .totalPoints(totalPoints)
                .personalPoints(personalPoints)
                .teamSize(teamSize)
                .raidLevel(challengeMode ? 1 : 0)
                .rooms(new ArrayList<>(rooms))
                .route(route.toString())
                .purpleReceived(false) // TODO: detect from chat
                .totalDeaths(personalDeaths)
                .personalDeaths(personalDeaths)
                .build();

            dataStore.insertRaid(raid);
            dataStore.upsertCoxRun(CoxRunRecord.builder()
                .killRecordId(record.getId())
                .challengeMode(challengeMode)
                .totalPoints(totalPoints)
                .personalPoints(personalPoints)
                .teamSize(record.getTeamSize())
                .groupSizeLabel(record.getGroupSizeLabel())
                .totalTicks(record.getDurationTicks())
                .totalSeconds(record.getDurationSeconds())
                .olmTicks(olmTicks)
                .olmSeconds(olmTicks * 0.6)
                .route(route.toString())
                .roomTekton(roomsSeen.contains("tekton") ? 1 : 0)
                .roomMuttadile(roomsSeen.contains("muttadile") ? 1 : 0)
                .roomVanguards(roomsSeen.contains("vanguards") ? 1 : 0)
                .roomVasaNistirio(roomsSeen.contains("vasa nistirio") ? 1 : 0)
                .roomVespula(roomsSeen.contains("vespula") ? 1 : 0)
                .roomGuardians(roomsSeen.contains("guardians") ? 1 : 0)
                .roomMystics(roomsSeen.contains("mystics") ? 1 : 0)
                .roomShamans(roomsSeen.contains("shamans") ? 1 : 0)
                .roomGreatOlm(roomsSeen.contains("great olm") ? 1 : 0)
                .roomThieving(roomsSeen.contains("thieving") ? 1 : 0)
                .roomIceDemon(roomsSeen.contains("ice demon") ? 1 : 0)
                .build());
            log.info("CoX completion recorded: {} points, route={}, team={}",
                totalPoints, route, teamSize);
        }
        catch (Exception e)
        {
            log.error("Failed to record CoX raid", e);
        }
        finally
        {
            resetState();
        }
    }

    private void startRaid()
    {
        inRaid = true;
        raidStartTick = client.getTickCount();
        rooms.clear();
        currentRoom = null;
        currentRoomStartTick = -1;
        roomOrder = 0;
        personalDeaths = 0;
        currentRoomDeaths = 0;
        log.info("CoX raid started");
    }

    private void transitionToRoom(String roomName)
    {
        finalizeCurrentRoom();
        currentRoom = roomName;
        currentRoomStartTick = client.getTickCount();
        currentRoomDeaths = 0;
        roomOrder++;
        log.debug("Entered CoX room: {} (order={})", roomName, roomOrder);
    }

    private void finalizeCurrentRoom()
    {
        if (currentRoom != null && currentRoomStartTick > 0)
        {
            int duration = client.getTickCount() - currentRoomStartTick;
            rooms.add(RaidRecord.RoomRecord.builder()
                .roomName(currentRoom)
                .roomType(currentRoom.contains("Olm") ? "boss" : "combat")
                .orderInRaid(roomOrder)
                .durationTicks(duration)
                .deathsInRoom(currentRoomDeaths)
                .metadata(new HashMap<>())
                .build());
        }
    }

    private boolean isChallengeMode()
    {
        return client.getVarbitValue(6385) > 0;
    }

    private int estimateTeamSize(int totalPoints, int personalPoints)
    {
        if (personalPoints <= 0 || totalPoints <= 0) return 1;
        double ratio = (double) personalPoints / totalPoints;
        if (ratio > 0.95) return 1;
        if (ratio > 0.45) return 2;
        if (ratio > 0.30) return 3;
        return Math.max(1, (int) Math.round(1.0 / ratio));
    }

    private void resetState()
    {
        inRaid = false;
        raidStartTick = -1;
        rooms.clear();
        currentRoom = null;
        currentRoomStartTick = -1;
        roomOrder = 0;
        personalDeaths = 0;
        currentRoomDeaths = 0;
    }
}
