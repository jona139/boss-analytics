package com.BossAnalytics;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Captures snapshots of player state at kill time: levels, gear, CA tier.
 */
@Slf4j
public class PlayerStateTracker
{
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
}
