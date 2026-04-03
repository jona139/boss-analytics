package com.BossAnalytics;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

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
    private final Map<String, Integer> lastInteractTick = new HashMap<>();

    // Pending kill context
    @Getter
    private String lastKilledBoss = null;
    private int lastKilledBossNpcId = -1;
    private int lastKillTick = -1;

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
            log.debug("Boss death detected: {} (npcId={})", lastKilledBoss, npcId);
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (event.getSource() != client.getLocalPlayer()) return;

        Actor target = event.getTarget();
        if (!(target instanceof NPC)) return;

        int npcId = ((NPC) target).getId();
        Optional<BossRegistry.BossDefinition> bossDef = bossRegistry.getByNpcId(npcId);
        if (bossDef.isPresent())
        {
            String bossName = bossDef.get().getName();
            if (!fightStartTicks.containsKey(bossName))
            {
                fightStartTicks.put(bossName, client.getTickCount());
                log.debug("Fight started: {} at tick {}", bossName, client.getTickCount());
            }
            lastInteractTick.put(bossName, client.getTickCount());
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
        Matcher kcMatcher = BossRegistry.KC_PATTERN.matcher(msg);
        if (kcMatcher.find())
        {
            killCount = Integer.parseInt(kcMatcher.group(2).replace(",", ""));
        }

        boolean isPb = BossRegistry.PERSONAL_BEST.matcher(msg).find();

        // If we have a duration and a recently killed boss, record the kill
        if (durationSeconds > 0 && lastKilledBoss != null
            && client.getTickCount() - lastKillTick < 10)
        {
            recordKill(lastKilledBoss, lastKilledBossNpcId, durationSeconds, true,
                killCount, isPb);
            clearFightState(lastKilledBoss);
            return;
        }

        // KC message without duration => tick-based timing fallback
        if (killCount > 0 && lastKilledBoss != null
            && client.getTickCount() - lastKillTick < 10)
        {
            int tickDuration = calculateTickDuration(lastKilledBoss);
            double tickBasedSeconds = tickDuration * 0.6;
            recordKill(lastKilledBoss, lastKilledBossNpcId, tickBasedSeconds, false,
                killCount, false);
            clearFightState(lastKilledBoss);
        }
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
        return startTick != null ? client.getTickCount() - startTick : -1;
    }

    private void clearFightState(String bossName)
    {
        fightStartTicks.remove(bossName);
        lastInteractTick.remove(bossName);
        lastKilledBoss = null;
        lastKilledBossNpcId = -1;
    }

    private void recordKill(String bossName, int npcId, double durationSeconds,
                            boolean fromChat, int killCount, boolean isPb)
    {
        try
        {
            int tickDuration = calculateTickDuration(bossName);
            if (tickDuration < 0) tickDuration = (int) Math.round(durationSeconds / 0.6);

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
                .isPersonalBest(isPb)
                .world(playerState.getWorld())
                .isTask(false) // TODO: detect slayer task
                .teamSize(1)
                .metadata(new HashMap<>())
                .build();

            long id = dataStore.insertKill(record);
            record.setId(id);

            log.info("Kill recorded: {} in {}s (kc={}, pb={})",
                bossName, String.format("%.1f", durationSeconds), killCount, isPb);

            for (KillListener listener : listeners)
            {
                listener.onKillRecorded(record);
            }
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
        fightStartTicks.entrySet().removeIf(entry ->
            currentTick - lastInteractTick.getOrDefault(entry.getKey(), 0) > 50);

        if (lastKilledBoss != null && currentTick - lastKillTick > 20)
        {
            clearFightState(lastKilledBoss);
        }
    }
}
