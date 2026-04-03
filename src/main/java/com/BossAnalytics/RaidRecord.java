package com.BossAnalytics;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Extended record for raid content (Chambers of Xeric, Tombs of Amascut, Theatre of Blood).
 * Links to a parent KillRecord for the overall completion.
 */
@Data
@Builder
public class RaidRecord
{
    private long id;
    private long killRecordId;

    private String raidType; // "cox", "cox_cm", "toa", "tob"

    // Raid-level data
    private int totalPoints;
    private int personalPoints;
    private int teamSize;
    private int raidLevel; // ToA invocation level, CoX CM flag

    // Room breakdown
    private List<RoomRecord> rooms;

    // Route (CoX specific)
    private String route; // e.g. "tekton,vasa,vespula,muttadile,mystics,olm"

    // Loot
    private boolean purpleReceived;
    private String purpleItemName;
    private int purpleItemId;

    // Deaths
    private int totalDeaths;
    private int personalDeaths;

    @Data
    @Builder
    public static class RoomRecord
    {
        private String roomName;
        private String roomType; // "combat", "puzzle", "boss"
        private int orderInRaid;
        private int durationTicks;
        private int deathsInRoom;
        private Map<String, String> metadata;
    }
}
