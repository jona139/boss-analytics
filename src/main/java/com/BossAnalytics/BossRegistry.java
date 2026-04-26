package com.BossAnalytics;

import lombok.Builder;
import lombok.Data;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Registry of all tracked bosses with NPC IDs, kill time chat patterns,
 * and detection strategies.
 *
 * NOTE: NPC IDs are approximate and should be verified against RuneLite's
 * NpcID constants or the OSRS wiki before deploying.
 */
public class BossRegistry
{
    private final Map<Integer, BossDefinition> byNpcId = new HashMap<>();
    private final Map<String, BossDefinition> byName = new HashMap<>();
    private final Map<String, BossDefinition> byNormalizedName = new HashMap<>();

    public BossRegistry()
    {
        registerAll();
    }

    public Optional<BossDefinition> getByNpcId(int npcId)
    {
        return Optional.ofNullable(byNpcId.get(npcId));
    }

    public Optional<BossDefinition> getByName(String name)
    {
        if (name == null)
        {
            return Optional.empty();
        }

        BossDefinition exact = byName.get(name.toLowerCase());
        if (exact != null)
        {
            return Optional.of(exact);
        }

        return Optional.ofNullable(byNormalizedName.get(normalizeName(name)));
    }

    public Collection<BossDefinition> getAll()
    {
        return byName.values();
    }

    private void register(BossDefinition def)
    {
        byName.put(def.getName().toLowerCase(), def);
        byNormalizedName.put(normalizeName(def.getName()), def);
        for (int npcId : def.getNpcIds())
        {
            byNpcId.put(npcId, def);
        }
    }

    private String normalizeName(String name)
    {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private void registerIfMissing(String name, String category)
    {
        registerIfMissing(name, category, new int[]{});
    }

    private void registerIfMissing(String name, String category, int[] npcIds)
    {
        if (getByName(name).isPresent())
        {
            return;
        }

        register(BossDefinition.builder()
            .name(name)
            .category(category)
            .npcIds(npcIds)
            .hasKillTimeChat(true)
            .multiPhase(false)
            .build());
    }

    @Data
    @Builder
    public static class BossDefinition
    {
        private String name;
        private String category;       // "solo", "raid", "group", "wilderness"
        private int[] npcIds;
        private boolean hasKillTimeChat;
        private boolean multiPhase;
        private String handlerClass;   // custom handler for complex bosses
    }

    // Common chat patterns (used by KillTracker)
    public static final Pattern FIGHT_DURATION = Pattern.compile(
        "Fight duration: (\\d+):(\\d+)\\.?(\\d+)?");
    public static final Pattern CHALLENGE_DURATION = Pattern.compile(
        "Challenge duration: (\\d+):(\\d+)\\.?(\\d+)?");
    public static final Pattern KC_PATTERN = Pattern.compile(
        "Your (.+) kill count is: ([\\d,]+)");
    public static final Pattern RAID_KC = Pattern.compile(
        "Your completed (.+) count is: ([\\d,]+)");
    public static final Pattern YAMA_CONTRACT = Pattern.compile(
        "You have completed ([\\d,]+) contracts with a total of ([\\d,]+) points\\.?");
    public static final Pattern PERSONAL_BEST = Pattern.compile(
        "Fight duration: (\\d+):(\\d+)\\.?(\\d+)? \\(new personal best\\)");

    private void registerAll()
    {
        // ========== SOLO BOSSES ==========
        register(BossDefinition.builder().name("Vorkath").category("solo")
            .npcIds(new int[]{8061}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Zulrah").category("solo")
            .npcIds(new int[]{2042, 2043, 2044}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("The Gauntlet").category("solo")
            .npcIds(new int[]{9021}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("The Corrupted Gauntlet").category("solo")
            .npcIds(new int[]{9036}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Phantom Muspah").category("solo")
            .npcIds(new int[]{12077, 12078, 12079, 12080}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("Grotesque Guardians").category("solo")
            .npcIds(new int[]{7851, 7852, 7853, 7884, 7885, 7886, 7888}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("Cerberus").category("solo")
            .npcIds(new int[]{5862}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Alchemical Hydra").category("solo")
            .npcIds(new int[]{8615, 8616, 8617, 8618, 8619, 8620, 8621, 8622}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("Phosani's Nightmare").category("solo")
            .npcIds(new int[]{9416, 9417, 9418, 9419, 9420, 9421, 9422, 9423, 9424}).hasKillTimeChat(true).multiPhase(true).build());

        // GWD
        register(BossDefinition.builder().name("General Graardor").category("solo")
            .npcIds(new int[]{2215}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Commander Zilyana").category("solo")
            .npcIds(new int[]{2205}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("K'ril Tsutsaroth").category("solo")
            .npcIds(new int[]{3129}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Kree'arra").category("solo")
            .npcIds(new int[]{3162}).hasKillTimeChat(true).multiPhase(false).build());

        // DT2
        register(BossDefinition.builder().name("Duke Sucellus").category("solo")
            .npcIds(new int[]{12166, 12167, 12191, 12192, 12193, 12194, 12195, 12196}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("The Leviathan").category("solo")
            .npcIds(new int[]{12214, 12215, 12219}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("The Whisperer").category("solo")
            .npcIds(new int[]{12204, 12205, 12206, 12207}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("Vardorvis").category("solo")
            .npcIds(new int[]{12223, 12224, 12225}).hasKillTimeChat(true).multiPhase(true).build());

        // Slayer bosses
        register(BossDefinition.builder().name("Kraken").category("solo")
            .npcIds(new int[]{492, 494, 6640, 6656}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Thermonuclear Smoke Devil").category("solo")
            .npcIds(new int[]{499, 13659}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Abyssal Sire").category("solo")
            .npcIds(new int[]{5886, 5887, 5888, 5889, 5890, 5891, 5908}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("Sarachnis").category("solo")
            .npcIds(new int[]{8713}).hasKillTimeChat(true).multiPhase(false).build());

        // Other solo
        register(BossDefinition.builder().name("Corporeal Beast").category("group")
            .npcIds(new int[]{319}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Kalphite Queen").category("solo")
            .npcIds(new int[]{963, 965, 4303, 4304, 6500, 6501}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("King Black Dragon").category("solo")
            .npcIds(new int[]{239}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Giant Mole").category("solo")
            .npcIds(new int[]{5779}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Dagannoth Rex").category("solo")
            .npcIds(new int[]{2267}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Dagannoth Prime").category("solo")
            .npcIds(new int[]{2266}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Dagannoth Supreme").category("solo")
            .npcIds(new int[]{2265}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Scurrius").category("solo")
            .npcIds(new int[]{7221, 7222, 7223}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Hespori").category("solo")
            .npcIds(new int[]{8583}).hasKillTimeChat(true).multiPhase(false).build());

        // ========== WILDERNESS ==========
        register(BossDefinition.builder().name("Callisto").category("wilderness")
            .npcIds(new int[]{6503, 6609}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Venenatis").category("wilderness")
            .npcIds(new int[]{6504, 6610}).hasKillTimeChat(true).multiPhase(false).build());

        register(BossDefinition.builder().name("Vet'ion").category("wilderness")
            .npcIds(new int[]{6611, 6612}).hasKillTimeChat(true).multiPhase(true).build());

        register(BossDefinition.builder().name("Chaos Elemental").category("wilderness")
            .npcIds(new int[]{2054}).hasKillTimeChat(true).multiPhase(false).build());

        // ========== GROUP ==========
        register(BossDefinition.builder().name("Nex").category("group")
            .npcIds(new int[]{11278, 11279, 11280, 11281, 11282}).hasKillTimeChat(true).multiPhase(true)
            .handlerClass("NexHandler").build());

        register(BossDefinition.builder().name("The Nightmare").category("group")
            .npcIds(new int[]{378, 9425, 9426, 9427, 9428, 9429, 9430, 9431, 9432, 9433, 9460, 9461, 9462, 9463, 9464})
            .hasKillTimeChat(true).multiPhase(true).build());

        // ========== RAIDS (detected via varbits, not NPC IDs) ==========
        register(BossDefinition.builder().name("Chambers of Xeric").category("raid")
            .npcIds(new int[]{}).hasKillTimeChat(true).multiPhase(false)
            .handlerClass("CoxHandler").build());

        register(BossDefinition.builder().name("Theatre of Blood").category("raid")
            .npcIds(new int[]{}).hasKillTimeChat(true).multiPhase(false)
            .handlerClass("TobHandler").build());

        register(BossDefinition.builder().name("Tombs of Amascut").category("raid")
            .npcIds(new int[]{}).hasKillTimeChat(true).multiPhase(false)
            .handlerClass("ToaHandler").build());

        // ========== Name Coverage (KC/duration chat fallback) ==========
        // Add all current boss names so KC/duration messages resolve even when an NPC ID
        // mapping is unavailable or outdated.
        registerIfMissing("Abyssal Sire", "solo");
        registerIfMissing("Alchemical Hydra", "solo");
        registerIfMissing("Amoxliatl", "solo", new int[]{13685, 13686, 13687, 13689});
        registerIfMissing("Araxxor", "solo", new int[]{13668, 13669});
        registerIfMissing("Barrows", "solo");
        registerIfMissing("Brutus", "solo");
        registerIfMissing("Bryophyta", "solo", new int[]{8195});
        registerIfMissing("Callisto", "wilderness");
        registerIfMissing("Cerberus", "solo");
        registerIfMissing("Chaos Elemental", "wilderness");
        registerIfMissing("Chaos Fanatic", "wilderness", new int[]{6619});
        registerIfMissing("Crazy Archaeologist", "wilderness", new int[]{6618});
        registerIfMissing("Chambers of Xeric", "raid");
        registerIfMissing("Chambers of Xeric: Challenge Mode", "raid");
        registerIfMissing("Corporeal Beast", "group");
        registerIfMissing("Commander Zilyana", "solo");
        registerIfMissing("Crystalline Hunllef", "solo", new int[]{9021, 9022, 9023, 9024, 12123});
        registerIfMissing("Corrupted Hunllef", "solo", new int[]{9035, 9036, 9037, 9038});
        registerIfMissing("Dagannoth Prime", "solo");
        registerIfMissing("Dagannoth Rex", "solo");
        registerIfMissing("Dagannoth Supreme", "solo");
        registerIfMissing("Deranged Archaeologist", "solo", new int[]{7806});
        register(BossDefinition.builder().name("Doom of Mokhaiotl").category("solo")
            .npcIds(new int[]{14707, 14708, 14709}).hasKillTimeChat(false).multiPhase(false).build());
        registerIfMissing("Duke Sucellus", "solo");
        registerIfMissing("Fortis Colosseum", "solo", new int[]{12821, 12827, 15554});
        registerIfMissing("General Graardor", "solo");
        registerIfMissing("Giant Mole", "solo");
        registerIfMissing("Grotesque Guardians", "solo");
        registerIfMissing("Hespori", "solo");
        registerIfMissing("The Hueycoatl", "solo", new int[]{14009, 14010, 14011, 14012, 14013, 14014, 14015, 14017});
        registerIfMissing("Kalphite Queen", "solo");
        registerIfMissing("King Black Dragon", "solo");
        registerIfMissing("Kraken", "solo");
        registerIfMissing("Kree'arra", "solo");
        registerIfMissing("K'ril Tsutsaroth", "solo");
        registerIfMissing("The Leviathan", "solo");
        registerIfMissing("The Mimic", "solo", new int[]{784, 7979, 8633});
        registerIfMissing("Moons of Peril", "solo", new int[]{13011, 13012, 13013, 13485, 13486, 13487});
        registerIfMissing("Nex", "group");
        registerIfMissing("The Nightmare", "group");
        registerIfMissing("Phosani's Nightmare", "solo");
        registerIfMissing("Obor", "solo", new int[]{7416});
        registerIfMissing("Phantom Muspah", "solo");
        registerIfMissing("Royal Titans", "group", new int[]{4067, 6299, 6360});
        registerIfMissing("Sarachnis", "solo");
        registerIfMissing("Scorpia", "wilderness", new int[]{6615});
        registerIfMissing("Scurrius", "solo");
        registerIfMissing("Shellbane Gryphon", "solo", new int[]{14860, 15010});
        registerIfMissing("Skotizo", "solo", new int[]{7286});
        registerIfMissing("Theatre of Blood: Entry Mode", "raid");
        registerIfMissing("Theatre of Blood", "raid");
        registerIfMissing("Theatre of Blood: Hard Mode", "raid");
        registerIfMissing("Thermonuclear Smoke Devil", "solo");
        registerIfMissing("Tombs of Amascut: Entry Mode", "raid");
        registerIfMissing("Tombs of Amascut", "raid");
        registerIfMissing("Tombs of Amascut: Expert Mode", "raid");
        registerIfMissing("TzHaar-Ket-Rak's Challenges (1 through 6)", "solo", new int[]{10621, 10622});
        registerIfMissing("TzHaar-Ket-Rak's Challenge 1", "solo", new int[]{10621, 10622});
        registerIfMissing("TzHaar-Ket-Rak's Challenge 2", "solo", new int[]{10621, 10622});
        registerIfMissing("TzHaar-Ket-Rak's Challenge 3", "solo", new int[]{10621, 10622});
        registerIfMissing("TzHaar-Ket-Rak's Challenge 4", "solo", new int[]{10621, 10622});
        registerIfMissing("TzHaar-Ket-Rak's Challenge 5", "solo", new int[]{10621, 10622});
        registerIfMissing("TzHaar-Ket-Rak's Challenge 6", "solo", new int[]{10621, 10622});
        registerIfMissing("TzKal-Zuk", "solo", new int[]{7706});
        registerIfMissing("TzTok-Jad", "solo", new int[]{3127, 6506, 13661, 15574});
        registerIfMissing("Vardorvis", "solo");
        registerIfMissing("Venenatis", "wilderness");
        registerIfMissing("Vet'ion", "wilderness");
        registerIfMissing("Vorkath", "solo");
        registerIfMissing("The Whisperer", "solo");
        registerIfMissing("Yama", "group", new int[]{124, 14176, 15555});
        registerIfMissing("Zalcano", "group", new int[]{9049, 9050, 12124});
        registerIfMissing("Zulrah", "solo");
    }
}
