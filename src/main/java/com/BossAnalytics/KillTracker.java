package com.BossAnalytics;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import lombok.Builder;
import lombok.Data;
import net.runelite.api.Hitsplat;
import net.runelite.api.Skill;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Core kill detection engine.
 *
 * Two-pronged approach:
 * 1. PRIMARY: Parse boss kill time chat messages (most accurate, game's own timer)
 * 2. FALLBACK: Track NPC death events + tick counting
 */
@Slf4j
public class KillTracker
{
    private final Client client;
    private final DataStore dataStore;
    private final PlayerStateTracker playerState;
    private final BossRegistry bossRegistry;
    private final BossAnalyticsConfig config;

    // Active fight tracking (for tick-based fallback timing)
    private final Map<String, Integer> fightStartTicks = new HashMap<>();

    // Start-of-fight snapshots
    private final Map<String, FightSnapshot> fightSnapshots = new HashMap<>();
    private final Map<String, PlayerStateTracker.InstanceSnapshot> fightInstanceSnapshots = new HashMap<>();

    // Per-fight stat tracking
    private final Map<String, int[]> fightHpTracking = new HashMap<>();   // [hpLost, hpRecovered, lastKnownHp]
    private final Map<String, int[]> fightPrayerTracking = new HashMap<>(); // [prayerLost, prayerRestored, lastKnownPrayer]
    private final Map<String, Integer> fightDeaths = new HashMap<>();
    private final Map<String, Integer> fightDamageDealt = new HashMap<>();

    // Track which boss the player is currently fighting (for stat change attribution)
    private String activeFightBoss = null;

    @Data @Builder
    public static class FightSnapshot {
        private Map<Integer, Integer> equippedItemIds;
        private Map<Integer, String> equippedItemNames;
        private Map<Integer, Integer> inventoryItemIds;
        private long gearValue;
        private long inventoryValue;
    }

    // Pending kill context
    @Getter
    private String lastKilledBoss = null;
    private int lastKilledBossNpcId = -1;
    private int lastKillTick = -1;
    private boolean lastKilledBossHasKillTimeChat = true;

    // Kill event listeners
    private final List<KillListener> listeners = new ArrayList<>();

    public interface KillListener
    {
        void onKillRecorded(KillRecord record);
    }

    public KillTracker(Client client, DataStore dataStore, PlayerStateTracker playerState,
                       BossRegistry bossRegistry, BossAnalyticsConfig config)
    {
        this.client = client;
        this.dataStore = dataStore;
        this.playerState = playerState;
        this.bossRegistry = bossRegistry;
        this.config = config;
    }

    public void addListener(KillListener listener)
    {
        listeners.add(listener);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();
        if (!npc.isDead())
        {
            return;
        }

        int npcId = npc.getId();
        Optional<BossRegistry.BossDefinition> bossDef = bossRegistry.getByNpcId(npcId);
        if (bossDef.isPresent())
        {
            // Check category filters
            String cat = bossDef.get().getCategory();
            if ("solo".equals(cat) && !config.trackSoloBosses()) return;
            if ("group".equals(cat) && !config.trackGroupBosses()) return;
            if ("wilderness".equals(cat) && !config.trackWildyBosses()) return;
            if ("raid".equals(cat) && !config.trackRaids()) return;

            lastKilledBoss = bossDef.get().getName();
            lastKilledBossNpcId = npcId;
            lastKillTick = client.getTickCount();
            lastKilledBossHasKillTimeChat = bossDef.get().isHasKillTimeChat();
            log.info("Boss death detected: {} (npcId={})", lastKilledBoss, npcId);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        int npcId = npc.getId();
        Optional<BossRegistry.BossDefinition> bossDef = bossRegistry.getByNpcId(npcId);
        if (bossDef.isPresent())
        {
            String bossName = bossDef.get().getName();
            if ("Kraken".equals(bossName))
            {
                // Kraken can be present while dormant; start timing on first player hit.
                return;
            }

            int ratio = npc.getHealthRatio();
            int scale = npc.getHealthScale();
            // ratio == -1 means unknown (just spawned, not yet in combat) which is full HP
            // ratio == scale means full HP
            boolean fullHp = ratio == -1 || ratio == scale;
            if (fullHp && !fightStartTicks.containsKey(bossName))
            {
                startFightTracking(bossName, "boss spawned at full HP", npcId);
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String msg = event.getMessage()
            .replaceAll("<[^>]+>", "")
            .trim();

        double durationSeconds = parseDuration(msg);

        int killCount = -1;
        String kcBossName = null;
        Matcher kcMatcher = BossRegistry.KC_PATTERN.matcher(msg);
        if (kcMatcher.find())
        {
            kcBossName = kcMatcher.group(1);
            killCount = Integer.parseInt(kcMatcher.group(2).replace(",", ""));
        }
        else
        {
            Matcher raidKcMatcher = BossRegistry.RAID_KC.matcher(msg);
            if (raidKcMatcher.find())
            {
                kcBossName = raidKcMatcher.group(1);
                killCount = Integer.parseInt(raidKcMatcher.group(2).replace(",", ""));
            }
            else
            {
                Matcher yamaContractsMatcher = BossRegistry.YAMA_CONTRACT.matcher(msg);
                if (yamaContractsMatcher.find()
                    && ("Yama".equals(lastKilledBoss)
                    || "Yama".equals(activeFightBoss)
                    || fightStartTicks.containsKey("Yama")))
                {
                    kcBossName = "Yama";
                    killCount = Integer.parseInt(yamaContractsMatcher.group(1).replace(",", ""));
                }
            }
        }

        boolean isPb = BossRegistry.PERSONAL_BEST.matcher(msg).find();

        // Resolve the boss: prefer NPC death context, fall back to KC message boss name
        String resolvedBoss = lastKilledBoss;
        int resolvedNpcId = lastKilledBossNpcId;
        if (resolvedBoss == null && kcBossName != null)
        {
            Optional<BossRegistry.BossDefinition> def = bossRegistry.getByName(kcBossName);
            if (def.isPresent())
            {
                resolvedBoss = def.get().getName();
                resolvedNpcId = def.get().getNpcIds().length > 0 ? def.get().getNpcIds()[0] : -1;
                log.info("Boss resolved from KC message: {}", resolvedBoss);
            }
        }

        if (durationSeconds > 0 || killCount > 0)
        {
            int ticksSinceDeath = lastKillTick >= 0 ? client.getTickCount() - lastKillTick : -1;
            log.info("Kill chat detected: duration={}s kc={} pb={} boss={} ticksSinceDeath={}",
                durationSeconds, killCount, isPb, resolvedBoss, ticksSinceDeath);
        }

        if (resolvedBoss == null)
        {
            return;
        }

        // If we have a duration, record with chat timing
        if (durationSeconds > 0)
        {
            recordKill(resolvedBoss, resolvedNpcId, durationSeconds, true,
                killCount, isPb);
            clearFightState(resolvedBoss);
            return;
        }

        // KC message without duration => tick-based timing fallback
        if (killCount > 0)
        {
            int tickDuration = calculateTickDuration(resolvedBoss);
            double tickBasedSeconds = tickDuration > 0 ? tickDuration * 0.6 : -1;
            recordKill(resolvedBoss, resolvedNpcId, tickBasedSeconds, false,
                killCount, false);
            clearFightState(resolvedBoss);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (activeFightBoss == null) return;

        if (event.getSkill() == Skill.HITPOINTS)
        {
            int[] tracking = fightHpTracking.get(activeFightBoss);
            if (tracking != null)
            {
                int delta = event.getBoostedLevel() - tracking[2];
                if (delta < 0) tracking[0] += Math.abs(delta); // hpLost
                if (delta > 0) tracking[1] += delta;            // hpRecovered
                tracking[2] = event.getBoostedLevel();
            }
        }
        else if (event.getSkill() == Skill.PRAYER)
        {
            int[] tracking = fightPrayerTracking.get(activeFightBoss);
            if (tracking != null)
            {
                int delta = event.getBoostedLevel() - tracking[2];
                if (delta < 0) tracking[0] += Math.abs(delta); // prayerLost
                if (delta > 0) tracking[1] += delta;            // prayerRestored
                tracking[2] = event.getBoostedLevel();
            }
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (event.getActor() == client.getLocalPlayer() && activeFightBoss != null)
        {
            fightDeaths.merge(activeFightBoss, 1, Integer::sum);
            log.info("Player death during fight: {}", activeFightBoss);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        Optional<BossRegistry.BossDefinition> bossDef = bossRegistry.getByNpcId(npc.getId());
        if (bossDef.isPresent())
        {
            Hitsplat hitsplat = event.getHitsplat();

            String bossName = bossDef.get().getName();
            if ("Kraken".equals(bossName) && hitsplat.isMine() && !fightStartTicks.containsKey("Kraken"))
            {
                startFightTracking("Kraken", "first player hitsplat", npc.getId());
            }

            if (activeFightBoss != null && bossName.equals(activeFightBoss) && hitsplat.isMine())
            {
                fightDamageDealt.merge(activeFightBoss, hitsplat.getAmount(), Integer::sum);
            }
        }
    }

    private void startFightTracking(String bossName, String reason, int npcId)
    {
        if (fightStartTicks.containsKey(bossName))
        {
            return;
        }

        fightStartTicks.put(bossName, client.getTickCount());
        log.info("Fight timer started ({}): {} at tick {} (npcId={})",
            reason, bossName, client.getTickCount(), npcId);

        FightSnapshot snapshot = FightSnapshot.builder()
            .equippedItemIds(playerState.getEquippedItemIds())
            .equippedItemNames(playerState.getEquippedItemNames())
            .inventoryItemIds(playerState.getInventoryItemIds())
            .gearValue(playerState.calculateEquipmentValue())
            .inventoryValue(playerState.calculateInventoryValue())
            .build();
        fightSnapshots.put(bossName, snapshot);

        fightInstanceSnapshots.put(bossName, playerState.captureInstanceSnapshot());

        int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        fightHpTracking.put(bossName, new int[]{0, 0, currentHp});
        fightPrayerTracking.put(bossName, new int[]{0, 0, currentPrayer});
        fightDeaths.put(bossName, 0);
        fightDamageDealt.put(bossName, 0);
        activeFightBoss = bossName;
    }

    private double parseDuration(String msg)
    {
        Matcher fightMatcher = BossRegistry.FIGHT_DURATION.matcher(msg);
        if (fightMatcher.find()) return parseDurationGroups(fightMatcher);

        Matcher challengeMatcher = BossRegistry.CHALLENGE_DURATION.matcher(msg);
        if (challengeMatcher.find()) return parseDurationGroups(challengeMatcher);

        return -1;
    }

    private double parseDurationGroups(Matcher m)
    {
        int minutes = Integer.parseInt(m.group(1));
        int seconds = Integer.parseInt(m.group(2));
        String msStr = m.group(3) != null ? m.group(3) : "0";
        while (msStr.length() < 3) msStr += "0";
        int millis = Integer.parseInt(msStr);
        return minutes * 60 + seconds + millis / 1000.0;
    }

    private int calculateTickDuration(String bossName)
    {
        Integer startTick = fightStartTicks.get(bossName);
        int currentTick = client.getTickCount();
        log.info("calculateTickDuration: boss={} startTick={} currentTick={} fightMap={}",
            bossName, startTick, currentTick, fightStartTicks);
        return startTick != null ? currentTick - startTick : -1;
    }

    private void clearFightState(String bossName)
    {
        fightStartTicks.remove(bossName);
        fightSnapshots.remove(bossName);
        fightInstanceSnapshots.remove(bossName);
        fightHpTracking.remove(bossName);
        fightPrayerTracking.remove(bossName);
        fightDeaths.remove(bossName);
        fightDamageDealt.remove(bossName);
        lastKilledBoss = null;
        lastKilledBossNpcId = -1;
        lastKillTick = -1;
        lastKilledBossHasKillTimeChat = true;
        if (bossName.equals(activeFightBoss))
        {
            activeFightBoss = null;
        }
    }

    private void recordKill(String bossName, int npcId, double durationSeconds,
                            boolean fromChat, int killCount, boolean isPb)
    {
        try
        {
            int tickDuration = calculateTickDuration(bossName);
            if (tickDuration < 0) tickDuration = (int) Math.round(durationSeconds / 0.6);

            FightSnapshot snapshot = fightSnapshots.get(bossName);
            PlayerStateTracker.InstanceSnapshot instanceSnapshot = fightInstanceSnapshots.get(bossName);
            if (instanceSnapshot == null)
            {
                // Fallback for content tracked primarily from chat/KC messages (e.g., raids).
                instanceSnapshot = playerState.captureInstanceSnapshot();
            }
            int[] hpTracking = fightHpTracking.get(bossName);
            int[] prayerTracking = fightPrayerTracking.get(bossName);
            int teamSize = instanceSnapshot != null ? instanceSnapshot.getTeamSize() : 1;
            String groupSizeLabel = instanceSnapshot != null ? instanceSnapshot.getGroupSizeLabel() : "1";
            Map<String, String> metadata = new HashMap<>();
            metadata.put("team_size", String.valueOf(teamSize));
            metadata.put("group_size", groupSizeLabel);
            metadata.put("kill_type", teamSize > 8 ? "mass" : (teamSize > 1 ? "group" : "solo"));

            KillRecord record = KillRecord.builder()
                .bossName(bossName)
                .bossNpcId(npcId)
                .timestamp(Instant.now())
                .durationTicks(tickDuration)
                .durationSeconds(durationSeconds)
                .durationFromChat(fromChat)
                .combatLevel(playerState.getCombatLevel())
                .skillLevels(playerState.getSkillLevels())
                .boostedLevels(playerState.getBoostedLevels())
                .combatAchievementTier(playerState.getCombatAchievementTier())
                .equippedItemIds(playerState.getEquippedItemIds())
                .equippedItemNames(playerState.getEquippedItemNames())
                .inventoryItemIds(config.snapshotInventory() ? playerState.getInventoryItemIds() : null)
                .killCount(killCount)
                .personalBestTime(0)
                .personalBest(isPb)
                .world(playerState.getWorld())
                .worldTypes(playerState.getWorldTypesString())
                .gameMode(playerState.getGameMode())
                .leaguesWorld(playerState.isLeaguesWorld())
                .task(false) // TODO: detect slayer task
                .teamSize(teamSize)
                .groupSizeLabel(groupSizeLabel)
                .teamMembers(instanceSnapshot != null ? instanceSnapshot.getTeamMembers() : Collections.emptyList())
                .otherPlayers(instanceSnapshot != null ? instanceSnapshot.getOtherPlayers() : Collections.emptyList())
                .wave(0)
                .waveName(null)
                .segmentDurationTicks(0)
                .totalRunTicks(0)
                .deathRecord(false)
                .deathWave(0)
                .activityVariant(null)
                .metadata(metadata)
                // Start-of-fight snapshot
                .startEquippedItemIds(snapshot != null ? snapshot.getEquippedItemIds() : null)
                .startEquippedItemNames(snapshot != null ? snapshot.getEquippedItemNames() : null)
                .startInventoryItemIds(snapshot != null ? snapshot.getInventoryItemIds() : null)
                // Resource tracking
                .hpLost(hpTracking != null ? hpTracking[0] : 0)
                .hpRecovered(hpTracking != null ? hpTracking[1] : 0)
                .prayerLost(prayerTracking != null ? prayerTracking[0] : 0)
                .prayerRestored(prayerTracking != null ? prayerTracking[1] : 0)
                // GP values
                .startGearValue(snapshot != null ? snapshot.getGearValue() : 0)
                .startInventoryValue(snapshot != null ? snapshot.getInventoryValue() : 0)
                .endGearValue(playerState.calculateEquipmentValue())
                .endInventoryValue(playerState.calculateInventoryValue())
                // Prayer unlocks
                .hasRigour(playerState.hasRigour())
                .hasAugury(playerState.hasAugury())
                .hasDeadeye(playerState.hasDeadeye())
                .hasMysticVigour(playerState.hasMysticVigour())
                // Account info
                .combatAchievementPoints(playerState.getCombatAchievementPoints())
                .totalLevel(playerState.getTotalLevel())
                .playtimeMinutes(playerState.getPlaytimeMinutes())
                .accountType(playerState.getAccountType())
                .effectiveAccountType(playerState.getEffectiveAccountType())
                .accountTypeLabel(playerState.getAccountTypeLabel())
                // Fight metrics
                .personalDeaths(fightDeaths.getOrDefault(bossName, 0))
                .totalDamageDealt(fightDamageDealt.getOrDefault(bossName, 0))
                .build();

            long id = dataStore.insertKill(record);
            record.setId(id);

            log.info("Kill recorded: {} in {}s (kc={}, pb={})",
                bossName, String.format("%.1f", durationSeconds), killCount, isPb);

            for (KillListener listener : listeners)
            {
                listener.onKillRecorded(record);
            }

            // Persist any listener-side enrichments (team/wave/raid metadata updates).
            dataStore.updateKill(record);
        }
        catch (Exception e)
        {
            log.error("Failed to record kill for {}", bossName, e);
        }
    }

    /**
     * Clean up stale fight states. Called each game tick.
     */
    public void cleanStaleStates()
    {
        int currentTick = client.getTickCount();
        // Clean up fights older than 30 minutes (3000 ticks) — should never hit this
        List<String> staleBosses = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : fightStartTicks.entrySet())
        {
            if (currentTick - entry.getValue() > 3000)
            {
                staleBosses.add(entry.getKey());
            }
        }
        for (String staleBoss : staleBosses)
        {
            clearFightState(staleBoss);
        }

        // If a boss has no reliable kill chat signal, finalize from despawn context after a short grace window.
        if (lastKilledBoss != null && !lastKilledBossHasKillTimeChat && currentTick - lastKillTick > 10)
        {
            int tickDuration = calculateTickDuration(lastKilledBoss);
            double tickBasedSeconds = tickDuration > 0 ? tickDuration * 0.6 : -1;
            recordKill(lastKilledBoss, lastKilledBossNpcId, tickBasedSeconds, false, -1, false);
            clearFightState(lastKilledBoss);
            return;
        }

        // Only clear stale death context, NOT the active fight state
        // (a new fight may already be in progress via onNpcSpawned)
        if (lastKilledBoss != null && currentTick - lastKillTick > 100)
        {
            lastKilledBoss = null;
            lastKilledBossNpcId = -1;
            lastKillTick = -1;
            lastKilledBossHasKillTimeChat = true;
        }
    }
}
