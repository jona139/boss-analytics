package com.gamecubejona.bossanalytics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("bossanalytics")
public interface BossAnalyticsConfig extends Config
{
    @ConfigSection(
        name = "General",
        description = "General tracking settings",
        position = 0
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "trackSoloBosses",
        name = "Track Solo Bosses",
        description = "Record kill data for solo bosses (Vorkath, Zulrah, etc.)",
        section = generalSection,
        position = 0
    )
    default boolean trackSoloBosses()
    {
        return true;
    }

    @ConfigItem(
        keyName = "trackRaids",
        name = "Track Raids",
        description = "Record detailed room/route data for CoX, ToB, ToA",
        section = generalSection,
        position = 1
    )
    default boolean trackRaids()
    {
        return true;
    }

    @ConfigItem(
        keyName = "trackGroupBosses",
        name = "Track Group Bosses",
        description = "Record data for Nex, Nightmare, Corp, etc.",
        section = generalSection,
        position = 2
    )
    default boolean trackGroupBosses()
    {
        return true;
    }

    @ConfigItem(
        keyName = "trackWildyBosses",
        name = "Track Wilderness Bosses",
        description = "Record data for wilderness bosses",
        section = generalSection,
        position = 3
    )
    default boolean trackWildyBosses()
    {
        return true;
    }

    @ConfigItem(
        keyName = "snapshotInventory",
        name = "Snapshot Inventory",
        description = "Save inventory contents at kill time (useful for supply usage analysis)",
        section = generalSection,
        position = 4
    )
    default boolean snapshotInventory()
    {
        return false;
    }

    // === Data Section ===

    @ConfigSection(
        name = "Data & Export",
        description = "Data management and export settings",
        position = 1
    )
    String dataSection = "data";

    @ConfigItem(
        keyName = "exportPath",
        name = "Export Directory",
        description = "Directory for CSV/JSON exports (default: ~/.runelite/boss-analytics/export)",
        section = dataSection,
        position = 0
    )
    default String exportPath()
    {
        return "";
    }

    // === Display Section ===

    @ConfigSection(
        name = "Display",
        description = "Panel display settings",
        position = 2
    )
    String displaySection = "display";

    @ConfigItem(
        keyName = "showPanel",
        name = "Show Side Panel",
        description = "Show the Boss Analytics panel in the sidebar",
        section = displaySection,
        position = 0
    )
    default boolean showPanel()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showKillNotification",
        name = "Kill Notification",
        description = "Show a notification when a kill is recorded",
        section = displaySection,
        position = 1
    )
    default boolean showKillNotification()
    {
        return true;
    }
}
