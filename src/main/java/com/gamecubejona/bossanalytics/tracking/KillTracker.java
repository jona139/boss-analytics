package com.gamecubejona.bossanalytics.tracking;

import com.gamecubejona.bossanalytics.data.DataStore;
import com.gamecubejona.bossanalytics.data.KillRecord;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core kill detection engine.
 *
 * Two-pronged approach:
 * 1. PRIMARY: Parse boss kill time chat messages (most accurate)
 * 2. FALLBACK: Track NPC death events + tick counting
 *
 * Chat message approach is preferred because:
 * - The game's own timer is authoritative
 * - Handles multi-phase bosses correctly
 * - Includes personal best detection
 */
@Slf4j
@Singleton
public class KillTracker
{
    @Inject private Client client;
    @Inject private DataStore dataStore;
    @Inject private PlayerStateTracker playerState;
    @Inject private BossRegistry bossRegistry;

    // Active fight tracking (for tick-based fallback timing)
    private final Map<String, Integer> fightStartTicks = new HashMap<>();
    private final Map<String, Integer> lastInteractTick = new HashMap<>();

    // Pending kill context — between NPC death and chat message
    @Getter
    private String lastKilledBoss = null;
    private int lastKilledBossNpcId = -1;
    private int lastKillTick = -1;

    // Chat patterns
    private static final Pattern FIGHT_DURATION = Pattern.compile(
        "Fight duration: (\\d+):(\\d+)\\.?(\\d+)?");
    private static final Pattern CHALLENGE_DURATION = Pattern.compile(
        "Challenge duration: (\\d+):(\\d+)\\.?(\\d+)?");
    private static final Pattern KC_PATTERN = Pattern.compile(
        "Your (.+) kill count is: ([\\d,]+)");
    private static final Pattern PERSONAL_BEST = Pattern.compile(
        "Fight duration: (\\d+):(\\d+)\\.?(\\d+)? \\(new personal best\\)");
    private static final Pattern SLAYER_TASK = Pattern.compile(
        "You're assigned to kill (.+); only (\\d+) more to go\\.");

    private boolean onSlayerTask = false;

    // Kill event listeners
    private final List<KillListener> listeners = new ArrayList<>();

    public interface KillListener
    {
        void onKillRecorded(KillRecord record);
    }

    public void addListener(KillListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Track NPC deaths to know which boss was killed.
     */
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
            lastKilledBoss = bossDef.get().getName();
            lastKilledBossNpcId = npcId;
            lastKillTick = client.getTickCount();

            log.debug("Boss death detected: {} (npcId={})", lastKilledBoss, npcId);
        }
    }

    /**
     * Track fight start when player begins attacking a boss.
     */
    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (event.getSource() != client.getLocalPlayer())
        {
            return;
        }

        Actor target = event.getTarget();
        if (!(target instanceof NPC))
        {
            return;
        }

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

    /**
     * Parse chat messages for kill times, KC, and personal bests.
     * This is the PRIMARY kill detection mechanism.
     */
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String msg = event.getMessage()
            .replaceAll("<[^>]+>", "") // strip color tags
            .trim();

        // Try to parse a fight duration
        double durationSeconds = parseDuration(msg);

        // Try to parse kill count
        int killCount = -1;
        Matcher kcMatcher = KC_PATTERN.matcher(msg);
        if (kcMatcher.find())
        {
            killCount = Integer.parseInt(kcMatcher.group(2).replace(",", ""));
        }

        // Check personal best
        boolean isPb = PERSONAL_BEST.matcher(msg).find();

        // If we have a duration and a recently killed boss, record the kill
        if (durationSeconds > 0 && lastKilledBoss != null
            && client.getTickCount() - lastKillTick < 10) // within 10 ticks of death
        {
            recordKill(lastKilledBoss, lastKilledBossNpcId, durationSeconds, true,
                killCount, isPb);
            clearFightState(lastKilledBoss);
            return;
        }

        // If we got a KC message without a duration, try tick-based timing
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

    /**
     * Parse MM:SS.ms format from chat messages.
     */
    private double parseDuration(String msg)
    {
        Matcher fightMatcher = FIGHT_DURATION.matcher(msg);
        if (fightMatcher.find())
        {
            return parseDurationGroups(fightMatcher);
        }

        Matcher challengeMatcher = CHALLENGE_DURATION.matcher(msg);
        if (challengeMatcher.find())
        {
            return parseDurationGroups(challengeMatcher);
        }
        return -1;
    }

    private double parseDurationGroups(Matcher m)
    {
        int minutes = Integer.parseInt(m.group(1));
        int seconds = Integer.parseInt(m.group(2));
        int millis = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        // Pad millis: "4" -> 400, "40" -> 400, "400" -> 400
        String msStr = m.group(3) != null ? m.group(3) : "0";
        while (msStr.length() < 3) msStr += "0";
        millis = Integer.parseInt(msStr);
        return minutes * 60 + seconds + millis / 1000.0;
    }

    private int calculateTickDuration(String bossName)
    {
        Integer startTick = fightStartTicks.get(bossName);
        if (startTick == null)
        {
            return -1;
        }
        return client.getTickCount() - startTick;
    }

    private void clearFightState(String bossName)
    {
        fightStartTicks.remove(bossName);
        lastInteractTick.remove(bossName);
        lastKilledBoss = null;
        lastKilledBossNpcId = -1;
    }

    /**
     * Build and persist a full KillRecord with all player context.
     */
    private void recordKill(String bossName, int npcId, double durationSeconds,
                            boolean fromChat, int killCount, boolean isPb)
    {
        try
        {
            int tickDuration = calculateTickDuration(bossName);
            if (tickDuration < 0)
            {
                tickDuration = (int) Math.round(durationSeconds / 0.6);
            }

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
                .inventoryItemIds(playerState.getInventoryItemIds())
                .killCount(killCount)
                .isPersonalBest(isPb)
                .world(playerState.getWorld())
                .isTask(onSlayerTask)
                .teamSize(1) // overridden by boss-specific handlers for group content
                .metadata(new HashMap<>())
                .build();

            long id = dataStore.insertKill(record);
            record.setId(id);

            log.info("Kill recorded: {} in {}s (kc={}, pb={})",
                bossName, String.format("%.1f", durationSeconds), killCount, isPb);

            // Notify listeners (panel update, boss-specific handlers, etc.)
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
     * Clean up stale fight states (if player teleports, dies, etc.).
     * Called on GameTick.
     */
    public void cleanStaleStates()
    {
        int currentTick = client.getTickCount();
        fightStartTicks.entrySet().removeIf(entry ->
            currentTick - lastInteractTick.getOrDefault(entry.getKey(), 0) > 50);

        // Clear lastKilledBoss if it's been too long
        if (lastKilledBoss != null && currentTick - lastKillTick > 20)
        {
            clearFightState(lastKilledBoss);
        }
    }
}
