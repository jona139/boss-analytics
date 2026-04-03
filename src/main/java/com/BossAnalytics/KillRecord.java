package com.BossAnalytics;

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
    private long id;
    private String bossName;
    private int bossNpcId;

    // Timing
    private Instant timestamp;
    private int durationTicks;
    private double durationSeconds;
    private boolean durationFromChat;

    // Player state at kill
    private int combatLevel;
    private Map<String, Integer> skillLevels;
    private Map<String, Integer> boostedLevels;
    private int combatAchievementTier;

    // Gear snapshot (equipment slot -> item id/name)
    private Map<Integer, Integer> equippedItemIds;
    private Map<Integer, String> equippedItemNames;

    // Inventory snapshot
    private Map<Integer, Integer> inventoryItemIds;

    // Kill context
    private int killCount;
    private int personalBestTime;
    private boolean personalBest;
    private int world;
    private boolean task;

    // Group content
    private int teamSize;
    private List<String> teamMembers;

    // Boss-specific metadata (flexible key-value for raids, etc.)
    private Map<String, String> metadata;
}
