package com.gamecubejona.bossanalytics.bosses;

import com.gamecubejona.bossanalytics.data.DataStore;
import com.gamecubejona.bossanalytics.data.KillRecord;
import com.gamecubejona.bossanalytics.data.RaidRecord;
import com.gamecubejona.bossanalytics.tracking.KillTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Chambers of Xeric (CoX) specific tracking.
 *
 * Tracks:
 * - Which rooms are encountered and in what order (the "route")
 * - Time spent in each room
 * - Total and personal points
 * - Team size
 * - Deaths per room
 * - Whether a purple (unique) was received
 *
 * Key varbits and region IDs for CoX:
 * - Raid state: Varbit 5432 (in raid or not)
 * - Total points: Varbit 5422
 * - Personal points: Varbit 5431
 * - Team size: derived from points board or player count
 * - Room detection: via map region ID changes
 *
 * Room regions (approximate — verify against current game data):
 * Each CoX room corresponds to specific region IDs. The plugin
 * detects room transitions by monitoring region changes.
 */
@Slf4j
@Singleton
public class CoxHandler
{
    @Inject private Client client;
    @Inject private DataStore dataStore;

    // Varbit IDs
    private static final int VARBIT_IN_RAID = 5432;
    private static final int VARBIT_TOTAL_POINTS = 5422;
    private static final int VARBIT_PERSONAL_POINTS = 5431;

    // CoX region IDs (lobby + raid rooms)
    private static final int COX_LOBBY_REGION = 4919;
    private static final Set<Integer> COX_REGIONS = Set.of(
        12889, 13136, 13137, 13138, 13139, 13140, 13141, 13145,
        13393, 13394, 13395, 13396, 13397, 13401
    );

    // Room detection by region chunks
    // Map of region ID -> room name (simplified; real impl needs chunk-level granularity)
    private static final Map<Integer, String> ROOM_REGIONS = new HashMap<>();
    static {
        // These are approximations — the actual mapping depends on the
        // raid layout which is procedurally generated. A more robust approach
        // is to detect rooms by the NPCs/objects present.
        // This static map handles the most common cases.
    }

    // Known CoX combat rooms (detected by NPC presence)
    private static final Map<Integer, String> ROOM_NPCS = new HashMap<>();
    static {
        ROOM_NPCS.put(7540, "Tekton");
        ROOM_NPCS.put(7541, "Tekton");       // enraged
        ROOM_NPCS.put(7542, "Tekton");       // enraged
        ROOM_NPCS.put(7543, "Muttadile");    // small
        ROOM_NPCS.put(7544, "Muttadile");    // big
        ROOM_NPCS.put(7526, "Vanguards");
        ROOM_NPCS.put(7527, "Vanguards");
        ROOM_NPCS.put(7528, "Vanguards");
        ROOM_NPCS.put(7529, "Vasa Nistirio");
        ROOM_NPCS.put(7530, "Vasa Nistirio");
        ROOM_NPCS.put(7531, "Vespula");
        ROOM_NPCS.put(7532, "Vespula");
        ROOM_NPCS.put(7533, "Guardians");
        ROOM_NPCS.put(7534, "Guardians");
        ROOM_NPCS.put(7553, "Mystics");      // skeletal mystic
        ROOM_NPCS.put(7554, "Mystics");
        ROOM_NPCS.put(7555, "Mystics");
        ROOM_NPCS.put(7573, "Shamans");      // lizardman shaman
        ROOM_NPCS.put(7554, "Ice Demon");
        // Olm
        ROOM_NPCS.put(7551, "Great Olm");
        ROOM_NPCS.put(7552, "Great Olm");
        ROOM_NPCS.put(7553, "Great Olm");
        ROOM_NPCS.put(7554, "Great Olm");    // head
    }

    // Puzzle rooms (detected by game objects)
    private static final String[] PUZZLE_ROOMS = {
        "Crabs", "Ice Demon", "Thieving", "Tightrope"
    };

    // State
    private boolean inRaid = false;
    private int raidStartTick = -1;
    private List<RaidRecord.RoomRecord> rooms = new ArrayList<>();
    private String currentRoom = null;
    private int currentRoomStartTick = -1;
    private int roomOrder = 0;
    private int personalDeaths = 0;
    private int currentRoomDeaths = 0;
    private Set<String> detectedNpcs = new HashSet<>();

    /**
     * Monitor raid entry/exit via varbit.
     */
    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        int inRaidValue = client.getVarbitValue(VARBIT_IN_RAID);

        if (inRaidValue == 1 && !inRaid)
        {
            startRaid();
        }
        else if (inRaidValue == 0 && inRaid)
        {
            // Raid ended (could be completion or leaving)
            // Completion is detected via chat message
        }
    }

    /**
     * Detect room transitions by monitoring NPCs that spawn.
     * More reliable than region-based detection since CoX layouts are random.
     */
    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (!inRaid)
        {
            return;
        }

        int npcId = event.getNpc().getId();
        String roomName = ROOM_NPCS.get(npcId);
        if (roomName != null && !roomName.equals(currentRoom))
        {
            transitionToRoom(roomName, "combat");
        }
    }

    /**
     * Track deaths in raid.
     */
    @Subscribe
    public void onPlayerDeath(PlayerDeath event)
    {
        if (!inRaid)
        {
            return;
        }

        // Check if it's the local player
        if (event.getActor() == client.getLocalPlayer())
        {
            personalDeaths++;
            currentRoomDeaths++;
            log.debug("Player death in CoX room: {} (total: {})", currentRoom, personalDeaths);
        }
    }

    /**
     * Parse raid completion message.
     */
    public void onRaidComplete(String chatMessage, KillRecord baseKill)
    {
        if (!inRaid)
        {
            return;
        }

        // Finalize current room
        finalizeCurrentRoom();

        int totalPoints = client.getVarbitValue(VARBIT_TOTAL_POINTS);
        int personalPoints = client.getVarbitValue(VARBIT_PERSONAL_POINTS);
        int teamSize = estimateTeamSize(totalPoints, personalPoints);

        // Build route string
        StringBuilder route = new StringBuilder();
        for (RaidRecord.RoomRecord room : rooms)
        {
            if (route.length() > 0) route.append(",");
            route.append(room.getRoomName().toLowerCase());
        }

        boolean isPurple = chatMessage.contains("special loot");
        // TODO: Parse purple item name from loot message

        try
        {
            RaidRecord raid = RaidRecord.builder()
                .killRecordId(baseKill.getId())
                .raidType(isChallengeMode() ? "cox_cm" : "cox")
                .totalPoints(totalPoints)
                .personalPoints(personalPoints)
                .teamSize(teamSize)
                .raidLevel(isChallengeMode() ? 1 : 0)
                .rooms(new ArrayList<>(rooms))
                .route(route.toString())
                .purpleReceived(isPurple)
                .totalDeaths(personalDeaths) // TODO: track team deaths
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
        detectedNpcs.clear();
        log.info("CoX raid started");
    }

    private void transitionToRoom(String roomName, String roomType)
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
            RaidRecord.RoomRecord room = RaidRecord.RoomRecord.builder()
                .roomName(currentRoom)
                .roomType(isOlm(currentRoom) ? "boss" : "combat")
                .orderInRaid(roomOrder)
                .durationTicks(duration)
                .deathsInRoom(currentRoomDeaths)
                .metadata(new HashMap<>())
                .build();
            rooms.add(room);
        }
    }

    private boolean isOlm(String roomName)
    {
        return roomName != null && roomName.contains("Olm");
    }

    private boolean isChallengeMode()
    {
        // CM is detected via a different varbit or the "Challenge Mode" text
        // Varbit 6385 indicates CM when value > 0
        return client.getVarbitValue(6385) > 0;
    }

    private int estimateTeamSize(int totalPoints, int personalPoints)
    {
        if (personalPoints <= 0 || totalPoints <= 0)
        {
            return 1;
        }
        // Rough estimate: if personal ≈ total, it's solo
        double ratio = (double) personalPoints / totalPoints;
        if (ratio > 0.95) return 1;
        if (ratio > 0.45) return 2;
        if (ratio > 0.30) return 3;
        // For larger teams, use player list if available
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
        detectedNpcs.clear();
    }
}
