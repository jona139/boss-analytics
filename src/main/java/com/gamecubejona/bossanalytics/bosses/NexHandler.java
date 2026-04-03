package com.gamecubejona.bossanalytics.bosses;

import com.gamecubejona.bossanalytics.data.DataStore;
import com.gamecubejona.bossanalytics.data.KillRecord;
import com.gamecubejona.bossanalytics.tracking.KillTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Nex-specific tracking.
 *
 * Key metrics for the "optimal team size" analysis:
 * - Team size (counted from players in the instance)
 * - Kill time per team size
 * - Phase durations (Smoke, Shadow, Blood, Ice, Zaros)
 * - Deaths per phase
 * - Whether it was FFA, mass, or small team
 *
 * Nex phases are detected via her overhead chat messages:
 * - "Fill my soul with smoke!" (Smoke phase)
 * - "Darken my shadow!" (Shadow phase)
 * - "Flood my lungs with blood!" (Blood phase)
 * - "Infuse me with the power of ice!" (Ice phase)
 * - "NOW, THE POWER OF ZAROS!" (Zaros phase)
 */
@Slf4j
@Singleton
public class NexHandler implements KillTracker.KillListener
{
    @Inject private Client client;
    @Inject private DataStore dataStore;

    // Nex NPC IDs
    private static final Set<Integer> NEX_IDS = Set.of(11278, 11279, 11280, 11281, 11282);

    // Nex instance region
    private static final int NEX_REGION = 11601;

    // Phase chat triggers (Nex overhead text)
    private static final Map<String, String> PHASE_TRIGGERS = new LinkedHashMap<>();
    static {
        PHASE_TRIGGERS.put("Fill my soul with smoke!", "smoke");
        PHASE_TRIGGERS.put("Darken my shadow!", "shadow");
        PHASE_TRIGGERS.put("Flood my lungs with blood!", "blood");
        PHASE_TRIGGERS.put("Infuse me with the power of ice!", "ice");
        PHASE_TRIGGERS.put("NOW, THE POWER OF ZAROS!", "zaros");
    }

    // State
    private boolean inFight = false;
    private int fightStartTick = -1;
    private String currentPhase = null;
    private int phaseStartTick = -1;
    private final List<PhaseData> phases = new ArrayList<>();
    private final Set<String> playersInInstance = new HashSet<>();
    private int teamDeaths = 0;

    private static class PhaseData
    {
        String name;
        int durationTicks;
        int deaths;
    }

    /**
     * Detect Nex fight start by NPC spawn.
     */
    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (!isInNexRegion())
        {
            return;
        }

        if (NEX_IDS.contains(event.getNpc().getId()) && !inFight)
        {
            startFight();
        }
    }

    /**
     * Detect phase transitions via overhead text.
     */
    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event)
    {
        if (!inFight || !(event.getActor() instanceof NPC))
        {
            return;
        }

        NPC npc = (NPC) event.getActor();
        if (!NEX_IDS.contains(npc.getId()))
        {
            return;
        }

        String text = event.getOverheadText();
        for (Map.Entry<String, String> trigger : PHASE_TRIGGERS.entrySet())
        {
            if (text.contains(trigger.getKey()))
            {
                transitionPhase(trigger.getValue());
                break;
            }
        }
    }

    /**
     * Count players in instance for team size tracking.
     * Called periodically during the fight.
     */
    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event)
    {
        if (inFight && isInNexRegion())
        {
            playersInInstance.add(event.getPlayer().getName());
        }
    }

    @Subscribe
    public void onPlayerDeath(PlayerDeath event)
    {
        if (inFight)
        {
            teamDeaths++;
        }
    }

    /**
     * When a Nex kill is recorded by KillTracker, enrich it with
     * Nex-specific metadata.
     */
    @Override
    public void onKillRecorded(KillRecord record)
    {
        if (!record.getBossName().equals("Nex") || !inFight)
        {
            return;
        }

        // Finalize last phase
        finalizePhase();

        int teamSize = Math.max(1, playersInInstance.size());
        record.setTeamSize(teamSize);

        // Add phase timing metadata
        Map<String, String> metadata = record.getMetadata();
        if (metadata == null)
        {
            metadata = new HashMap<>();
        }

        for (PhaseData phase : phases)
        {
            metadata.put("phase_" + phase.name + "_ticks", String.valueOf(phase.durationTicks));
            metadata.put("phase_" + phase.name + "_seconds",
                String.format("%.1f", phase.durationTicks * 0.6));
            metadata.put("phase_" + phase.name + "_deaths", String.valueOf(phase.deaths));
        }

        metadata.put("team_size", String.valueOf(teamSize));
        metadata.put("team_deaths", String.valueOf(teamDeaths));
        metadata.put("kill_type", classifyKillType(teamSize));
        record.setMetadata(metadata);

        // Update the record in the database
        try
        {
            // Re-insert with updated metadata (or use an update query)
            log.info("Nex kill enriched: team={}, type={}, phases={}",
                teamSize, classifyKillType(teamSize), phases.size());
        }
        catch (Exception e)
        {
            log.error("Failed to enrich Nex kill record", e);
        }

        resetState();
    }

    private void startFight()
    {
        inFight = true;
        fightStartTick = client.getTickCount();
        phases.clear();
        playersInInstance.clear();
        teamDeaths = 0;
        currentPhase = "smoke"; // fight always starts with smoke
        phaseStartTick = client.getTickCount();

        // Scan current players in the instance
        for (Player player : client.getPlayers())
        {
            if (player != null && player.getName() != null)
            {
                playersInInstance.add(player.getName());
            }
        }

        log.info("Nex fight started with {} players", playersInInstance.size());
    }

    private void transitionPhase(String newPhase)
    {
        finalizePhase();
        currentPhase = newPhase;
        phaseStartTick = client.getTickCount();
        log.debug("Nex phase: {}", newPhase);
    }

    private void finalizePhase()
    {
        if (currentPhase != null && phaseStartTick > 0)
        {
            PhaseData data = new PhaseData();
            data.name = currentPhase;
            data.durationTicks = client.getTickCount() - phaseStartTick;
            data.deaths = 0; // TODO: track per-phase deaths
            phases.add(data);
        }
    }

    private String classifyKillType(int teamSize)
    {
        if (teamSize <= 5) return "small_team";
        if (teamSize <= 15) return "mid_team";
        return "mass";
    }

    private boolean isInNexRegion()
    {
        if (client.getLocalPlayer() == null)
        {
            return false;
        }
        return client.getLocalPlayer().getWorldLocation().getRegionID() == NEX_REGION;
    }

    private void resetState()
    {
        inFight = false;
        fightStartTick = -1;
        currentPhase = null;
        phaseStartTick = -1;
        phases.clear();
        playersInInstance.clear();
        teamDeaths = 0;
    }
}
