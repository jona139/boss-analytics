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
        if (!inRaid) return;

        String roomName = ROOM_NPCS.get(event.getNpc().getId());
        if (roomName != null && !roomName.equals(currentRoom))
        {
            transitionToRoom(roomName);
        }
    }

    @Override
    public void onKillRecorded(KillRecord record)
    {
        if (!record.getBossName().equals("Chambers of Xeric") || !inRaid)
        {
            return;
        }

        finalizeCurrentRoom();

        int totalPoints = client.getVarbitValue(VARBIT_TOTAL_POINTS);
        int personalPoints = client.getVarbitValue(VARBIT_PERSONAL_POINTS);
        int teamSize = estimateTeamSize(totalPoints, personalPoints);

        StringBuilder route = new StringBuilder();
        for (RaidRecord.RoomRecord room : rooms)
        {
            if (route.length() > 0) route.append(",");
            route.append(room.getRoomName().toLowerCase());
        }

        try
        {
            RaidRecord raid = RaidRecord.builder()
                .killRecordId(record.getId())
                .raidType(isChallengeMode() ? "cox_cm" : "cox")
                .totalPoints(totalPoints)
                .personalPoints(personalPoints)
                .teamSize(teamSize)
                .raidLevel(isChallengeMode() ? 1 : 0)
                .rooms(new ArrayList<>(rooms))
                .route(route.toString())
                .purpleReceived(false) // TODO: detect from chat
                .totalDeaths(personalDeaths)
                .personalDeaths(personalDeaths)
                .build();

            dataStore.insertRaid(raid);
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
