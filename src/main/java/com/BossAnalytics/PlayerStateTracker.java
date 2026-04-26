package com.BossAnalytics;

import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Data;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Captures snapshots of player state at kill time: levels, gear, CA tier.
 */
@Slf4j
public class PlayerStateTracker
{
    @Data
    @Builder
    public static class InstanceSnapshot
    {
        private int teamSize;
        private String groupSizeLabel;
        private List<String> teamMembers;
        private List<KillRecord.GroupMemberSnapshot> otherPlayers;
    }

    private final Client client;
    private final ItemManager itemManager;

    // Varbit for Combat Achievement tier (0=none through 6=grandmaster)
    // NOTE: verify this varbit is still current before deploying
    private static final int COMBAT_ACHIEVEMENT_TIER_VARBIT = 12862;

    @Inject
    public PlayerStateTracker(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    public Map<String, Integer> getSkillLevels()
    {
        Map<String, Integer> levels = new HashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) continue;
            levels.put(skill.getName(), client.getRealSkillLevel(skill));
        }
        return levels;
    }

    public Map<String, Integer> getBoostedLevels()
    {
        Map<String, Integer> levels = new HashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) continue;
            levels.put(skill.getName(), client.getBoostedSkillLevel(skill));
        }
        return levels;
    }

    public int getCombatLevel()
    {
        return client.getLocalPlayer() != null
            ? client.getLocalPlayer().getCombatLevel()
            : -1;
    }

    public Map<Integer, Integer> getEquippedItemIds()
    {
        Map<Integer, Integer> gear = new HashMap<>();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) return gear;

        Item[] items = equipment.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (items[slot].getId() != -1)
            {
                gear.put(slot, items[slot].getId());
            }
        }
        return gear;
    }

    public Map<Integer, String> getEquippedItemNames()
    {
        Map<Integer, String> gear = new HashMap<>();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) return gear;

        Item[] items = equipment.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (items[slot].getId() != -1)
            {
                gear.put(slot, itemManager.getItemComposition(items[slot].getId()).getName());
            }
        }
        return gear;
    }

    public Map<Integer, Integer> getInventoryItemIds()
    {
        Map<Integer, Integer> inv = new HashMap<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return inv;

        Item[] items = inventory.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (items[slot].getId() != -1)
            {
                inv.put(slot, items[slot].getId());
            }
        }
        return inv;
    }

    public int getCombatAchievementTier()
    {
        return client.getVarbitValue(COMBAT_ACHIEVEMENT_TIER_VARBIT);
    }

    public int getWorld()
    {
        return client.getWorld();
    }

    public EnumSet<WorldType> getWorldTypes()
    {
        EnumSet<WorldType> types = client.getWorldType();
        if (types == null || types.isEmpty())
        {
            return EnumSet.noneOf(WorldType.class);
        }
        return EnumSet.copyOf(types);
    }

    public String getWorldTypesString()
    {
        EnumSet<WorldType> types = getWorldTypes();
        if (types.isEmpty())
        {
            return "";
        }

        List<String> names = new ArrayList<>();
        for (WorldType type : types)
        {
            names.add(type.name());
        }
        names.sort(String::compareTo);
        return String.join(",", names);
    }

    public boolean isLeaguesWorld()
    {
        return getWorldTypes().contains(WorldType.SEASONAL);
    }

    public String getGameMode()
    {
        EnumSet<WorldType> types = getWorldTypes();
        if (types.contains(WorldType.SEASONAL)) return "leagues";
        if (types.contains(WorldType.DEADMAN)) return "deadman";
        if (types.contains(WorldType.FRESH_START_WORLD)) return "fresh_start";
        if (types.contains(WorldType.BETA_WORLD)) return "beta";
        if (types.contains(WorldType.TOURNAMENT_WORLD)) return "tournament";
        if (types.contains(WorldType.LAST_MAN_STANDING)) return "lms";
        if (types.contains(WorldType.PVP_ARENA)) return "pvp_arena";
        if (types.contains(WorldType.PVP)) return "pvp";
        if (types.contains(WorldType.QUEST_SPEEDRUNNING)) return "quest_speedrunning";
        if (types.contains(WorldType.NOSAVE_MODE)) return "nosave";
        return "standard";
    }

    public long calculateEquipmentValue()
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) return 0;
        long total = 0;
        for (Item item : equipment.getItems())
        {
            if (item.getId() != -1)
            {
                total += (long) itemManager.getItemPrice(item.getId()) * Math.max(1, item.getQuantity());
            }
        }
        return total;
    }

    public long calculateInventoryValue()
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) return 0;
        long total = 0;
        for (Item item : inventory.getItems())
        {
            if (item.getId() != -1)
            {
                total += (long) itemManager.getItemPrice(item.getId()) * Math.max(1, item.getQuantity());
            }
        }
        return total;
    }

    public boolean hasRigour()
    {
        return client.getVarbitValue(5451) == 1;
    }

    public boolean hasAugury()
    {
        return client.getVarbitValue(5452) == 1;
    }

    public boolean hasDeadeye()
    {
        return client.getVarbitValue(14862) == 1; // Verify varbit
    }

    public boolean hasMysticVigour()
    {
        return client.getVarbitValue(14863) == 1; // Verify varbit
    }

    public int getCombatAchievementPoints()
    {
        return client.getVarbitValue(12063);
    }

    public int getTotalLevel()
    {
        int total = 0;
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) continue;
            total += client.getRealSkillLevel(skill);
        }
        return total;
    }

    public int getPlaytimeMinutes()
    {
        return (int)(client.getVarpValue(788) * 0.6 / 60);
    }

    public int getAccountType()
    {
        return resolveBaseAccountType();
    }

    public int getEffectiveAccountType()
    {
        int base = resolveBaseAccountType();
        return isLeaguesWorld() ? 100 + Math.max(base, 0) : base;
    }

    public String getAccountTypeLabel()
    {
        String base = getBaseAccountTypeLabel(resolveBaseAccountType());
        return isLeaguesWorld() ? "leagues_" + base : base;
    }

    private int resolveBaseAccountType()
    {
        // Leagues worlds report non-standard values (for example 1000). For analytics purposes
        // they should be treated as ironman-like progression accounts.
        if (isLeaguesWorld())
        {
            return 1;
        }
        return client.getVarpValue(281);
    }

    private String getBaseAccountTypeLabel(int accountType)
    {
        switch (accountType)
        {
            case 0: return "normal";
            case 1: return "ironman";
            case 2: return "ultimate_ironman";
            case 3: return "hardcore_ironman";
            case 4: return "group_ironman";
            case 5: return "hardcore_group_ironman";
            case 6: return "unranked_group_ironman";
            default: return "unknown_" + accountType;
        }
    }

    public InstanceSnapshot captureInstanceSnapshot()
    {
        String localName = sanitizeName(client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null);
        List<KillRecord.GroupMemberSnapshot> others = captureOtherPlayersInInstance();

        List<String> teamMembers = new ArrayList<>();
        if (localName != null && !localName.isEmpty())
        {
            teamMembers.add(localName);
        }
        for (KillRecord.GroupMemberSnapshot other : others)
        {
            if (other.getName() != null && !other.getName().isEmpty())
            {
                teamMembers.add(other.getName());
            }
        }

        int teamSize = Math.max(1, teamMembers.size());
        String groupSizeLabel = teamSize > 8 ? "mass" : String.valueOf(teamSize);
        return InstanceSnapshot.builder()
            .teamSize(teamSize)
            .groupSizeLabel(groupSizeLabel)
            .teamMembers(teamMembers)
            .otherPlayers(others)
            .build();
    }

    private List<KillRecord.GroupMemberSnapshot> captureOtherPlayersInInstance()
    {
        if (!client.isInInstancedRegion())
        {
            return new ArrayList<>();
        }

        Player localPlayer = client.getLocalPlayer();
        Set<String> seenNames = new HashSet<>();
        List<KillRecord.GroupMemberSnapshot> snapshots = new ArrayList<>();
        for (Player player : client.getPlayers())
        {
            if (player == null || player == localPlayer)
            {
                continue;
            }

            String playerName = sanitizeName(player.getName());
            if (playerName == null || playerName.isEmpty() || !seenNames.add(playerName.toLowerCase()))
            {
                continue;
            }

            Map<Integer, Integer> equipped = getPlayerEquippedItemIds(player);
            Map<Integer, String> equippedNames = getItemNames(equipped);

            snapshots.add(KillRecord.GroupMemberSnapshot.builder()
                .name(playerName)
                .combatLevel(player.getCombatLevel())
                .gearValue(calculateGearValue(equipped))
                .equippedItemIds(equipped)
                .equippedItemNames(equippedNames)
                .build());
        }

        snapshots.sort(Comparator.comparing(KillRecord.GroupMemberSnapshot::getName, String.CASE_INSENSITIVE_ORDER));
        return snapshots;
    }

    private Map<Integer, Integer> getPlayerEquippedItemIds(Player player)
    {
        Map<Integer, Integer> equipped = new HashMap<>();
        PlayerComposition composition = player.getPlayerComposition();
        if (composition == null)
        {
            return equipped;
        }

        int[] equipmentIds = composition.getEquipmentIds();
        if (equipmentIds == null)
        {
            return equipped;
        }

        for (int slot = 0; slot < equipmentIds.length; slot++)
        {
            int rawId = equipmentIds[slot];
            if (rawId >= PlayerComposition.ITEM_OFFSET)
            {
                int itemId = rawId - PlayerComposition.ITEM_OFFSET;
                if (itemId > 0)
                {
                    equipped.put(slot, itemId);
                }
            }
        }
        return equipped;
    }

    private Map<Integer, String> getItemNames(Map<Integer, Integer> equipped)
    {
        Map<Integer, String> names = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : equipped.entrySet())
        {
            int itemId = entry.getValue();
            if (itemId <= 0)
            {
                continue;
            }

            try
            {
                names.put(entry.getKey(), itemManager.getItemComposition(itemId).getName());
            }
            catch (Exception e)
            {
                log.debug("Unable to resolve item name for {}", itemId, e);
            }
        }
        return names;
    }

    private long calculateGearValue(Map<Integer, Integer> equipped)
    {
        long total = 0;
        for (int itemId : equipped.values())
        {
            if (itemId > 0)
            {
                total += itemManager.getItemPrice(itemId);
            }
        }
        return total;
    }

    private String sanitizeName(String name)
    {
        return name == null ? null : name.replace('\u00A0', ' ').trim();
    }
}
