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
    @Data
    @Builder
    public static class GroupMemberSnapshot
    {
        private String name;
        private int combatLevel;
        private long gearValue;
        private Map<Integer, Integer> equippedItemIds;
        private Map<Integer, String> equippedItemNames;
    }

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
    private String worldTypes; // comma-separated WorldType flags (e.g. "MEMBERS,SEASONAL")
    private String gameMode;   // standard, leagues, deadman, beta, etc.
    private boolean leaguesWorld;
    private boolean task;
    private int wave;
    private String waveName;
    private int segmentDurationTicks;
    private int totalRunTicks;
    private boolean deathRecord;
    private int deathWave;
    private String activityVariant;

    // Group content
    private int teamSize;
    private String groupSizeLabel; // "1".."8" or "mass"
    private List<String> teamMembers;
    private List<GroupMemberSnapshot> otherPlayers;

    // Boss-specific metadata (flexible key-value for raids, etc.)
    private Map<String, String> metadata;

    // Start-of-fight snapshots
    private Map<Integer, Integer> startEquippedItemIds;
    private Map<Integer, String> startEquippedItemNames;
    private Map<Integer, Integer> startInventoryItemIds;

    // Resource tracking
    private int hpLost;
    private int hpRecovered;
    private int prayerLost;
    private int prayerRestored;

    // GP values
    private long startGearValue;
    private long startInventoryValue;
    private long endGearValue;
    private long endInventoryValue;

    // Prayer unlocks
    private boolean hasRigour;
    private boolean hasAugury;
    private boolean hasDeadeye;
    private boolean hasMysticVigour;

    // Account info
    private int combatAchievementPoints;
    private int totalLevel;
    private int playtimeMinutes;
    private int accountType; // 0=normal, 1=ironman, 2=ultimate, 3=hardcore, 4=group, 5=group hardcore, 6=unranked group
    private int effectiveAccountType; // accountType, or 100+accountType on leagues worlds
    private String accountTypeLabel;  // e.g. "normal", "ironman", "leagues_ironman"

    // Fight metrics
    private int personalDeaths;
    private int totalDamageDealt;
}
