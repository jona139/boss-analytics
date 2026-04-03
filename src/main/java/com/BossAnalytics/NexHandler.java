package com.BossAnalytics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;

/**
 * Nex-specific tracking: phase durations, team size, kill classification.
 * Phase transitions detected via Nex overhead text.
 */
@Slf4j
public class NexHandler implements KillTracker.KillListener
{
    private final Client client;

    private static final Set<Integer> NEX_IDS = Set.of(11278, 11279, 11280, 11281, 11282);
    private static final int NEX_REGION = 11601;

    private static final Map<String, String> PHASE_TRIGGERS = new LinkedHashMap<>();
    static {
        PHASE_TRIGGERS.put("Fill my soul with smoke!", "smoke");
        PHASE_TRIGGERS.put("Darken my shadow!", "shadow");
        PHASE_TRIGGERS.put("Flood my lungs with blood!", "blood");
        PHASE_TRIGGERS.put("Infuse me with the power of ice!", "ice");
        PHASE_TRIGGERS.put("NOW, THE POWER OF ZAROS!", "zaros");
    }

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
    }

    public NexHandler(Client client)
    {
        this.client = client;
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (!isInNexRegion()) return;
        if (NEX_IDS.contains(event.getNpc().getId()) && !inFight)
        {
            startFight();
        }
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event)
    {
        if (!inFight || !(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        if (!NEX_IDS.contains(npc.getId())) return;

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

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event)
    {
        if (inFight && isInNexRegion())
        {
            playersInInstance.add(event.getPlayer().getName());
        }
    }

    @Override
    public void onKillRecorded(KillRecord record)
    {
        if (!"Nex".equals(record.getBossName()) || !inFight) return;

        finalizePhase();

        int teamSize = Math.max(1, playersInInstance.size());
        record.setTeamSize(teamSize);

        Map<String, String> metadata = record.getMetadata();
        if (metadata == null) metadata = new HashMap<>();

        for (PhaseData phase : phases)
        {
            metadata.put("phase_" + phase.name + "_ticks", String.valueOf(phase.durationTicks));
            metadata.put("phase_" + phase.name + "_seconds",
                String.format("%.1f", phase.durationTicks * 0.6));
        }

        metadata.put("team_size", String.valueOf(teamSize));
        metadata.put("team_deaths", String.valueOf(teamDeaths));
        metadata.put("kill_type", classifyKillType(teamSize));
        record.setMetadata(metadata);

        log.info("Nex kill enriched: team={}, type={}, phases={}",
            teamSize, classifyKillType(teamSize), phases.size());

        resetState();
    }

    private void startFight()
    {
        inFight = true;
        fightStartTick = client.getTickCount();
        phases.clear();
        playersInInstance.clear();
        teamDeaths = 0;
        currentPhase = "smoke";
        phaseStartTick = client.getTickCount();

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
    }

    private void finalizePhase()
    {
        if (currentPhase != null && phaseStartTick > 0)
        {
            PhaseData data = new PhaseData();
            data.name = currentPhase;
            data.durationTicks = client.getTickCount() - phaseStartTick;
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
        return client.getLocalPlayer() != null
            && client.getLocalPlayer().getWorldLocation().getRegionID() == NEX_REGION;
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
