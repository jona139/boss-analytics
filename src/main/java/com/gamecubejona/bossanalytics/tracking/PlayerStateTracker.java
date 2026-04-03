package com.gamecubejona.bossanalytics.tracking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Captures snapshots of player state: levels, gear, CA tier.
 * Call snapshot methods at kill completion to record context.
 */
@Slf4j
@Singleton
public class PlayerStateTracker
{
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    // Varbit for Combat Achievement tier (0=none through 6=grandmaster)
    private static final int COMBAT_ACHIEVEMENT_TIER_VARBIT = 12862;

    /**
     * Get all skill levels (real, not boosted).
     */
    public Map<String, Integer> getSkillLevels()
    {
        Map<String, Integer> levels = new HashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            levels.put(skill.getName(), client.getRealSkillLevel(skill));
        }
        return levels;
    }

    /**
     * Get all boosted skill levels.
     */
    public Map<String, Integer> getBoostedLevels()
    {
        Map<String, Integer> levels = new HashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            levels.put(skill.getName(), client.getBoostedSkillLevel(skill));
        }
        return levels;
    }

    /**
     * Get combat level.
     */
    public int getCombatLevel()
    {
        return client.getLocalPlayer() != null
            ? client.getLocalPlayer().getCombatLevel()
            : -1;
    }

    /**
     * Snapshot equipped items: slot index -> item ID.
     */
    public Map<Integer, Integer> getEquippedItemIds()
    {
        Map<Integer, Integer> gear = new HashMap<>();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null)
        {
            return gear;
        }

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

    /**
     * Snapshot equipped item names: slot index -> item name.
     */
    public Map<Integer, String> getEquippedItemNames()
    {
        Map<Integer, String> gear = new HashMap<>();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null)
        {
            return gear;
        }

        Item[] items = equipment.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (items[slot].getId() != -1)
            {
                String name = itemManager.getItemComposition(items[slot].getId()).getName();
                gear.put(slot, name);
            }
        }
        return gear;
    }

    /**
     * Snapshot inventory: slot index -> item ID.
     */
    public Map<Integer, Integer> getInventoryItemIds()
    {
        Map<Integer, Integer> inv = new HashMap<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            return inv;
        }

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

    /**
     * Get combat achievement tier. Returns 0-6.
     */
    public int getCombatAchievementTier()
    {
        return client.getVarbitValue(COMBAT_ACHIEVEMENT_TIER_VARBIT);
    }

    /**
     * Get current world number.
     */
    public int getWorld()
    {
        return client.getWorld();
    }
}
