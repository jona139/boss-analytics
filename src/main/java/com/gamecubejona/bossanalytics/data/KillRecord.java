package com.gamecubejona.bossanalytics.data;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a single boss kill with all associated context.
 * This is the core unit of data the plugin collects.
 */
@Data
@Builder
public class KillRecord
{
    // Identity
    private long id;
    private String bossName;
    private int bossNpcId;

    // Timing
    private Instant timestamp;
    private int durationTicks;        // from game tick tracking
    private double durationSeconds;   // parsed from chat message if available
    private boolean durationFromChat; // true = parsed from boss kill time message

    // Player state at kill
    private int combatLevel;
    private Map<String, Integer> skillLevels;    // skill name -> level
    private Map<String, Integer> boostedLevels;  // skill name -> boosted level
    private int combatAchievementTier;           // 0=none, 1=easy, 2=med, 3=hard, 4=elite, 5=master, 6=grandmaster
    private String prayerBook;                   // normal or ancients (for prayer detection)

    // Gear snapshot (equipment slot -> item id)
    private Map<Integer, Integer> equippedItemIds;
    private Map<Integer, String> equippedItemNames;

    // Inventory snapshot (slot -> item id) — useful for supply tracking
    private Map<Integer, Integer> inventoryItemIds;

    // Kill context
    private int killCount;           // kc at time of kill (from chat message)
    private int personalBest;        // pb if reported in chat
    private boolean isPersonalBest;
    private int world;
    private boolean isTask;          // was this a slayer task kill

    // Group content
    private int teamSize;            // 1 for solo
    private List<String> teamMembers;

    // Boss-specific metadata (flexible key-value for raids, etc.)
    private Map<String, String> metadata;
}
