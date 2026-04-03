package com.BossAnalytics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("bossanalytics")
public interface BossAnalyticsConfig extends Config
{
    @ConfigSection(
        name = "Tracking",
        description = "Configure which content to track",
        position = 0
    )
    String trackingSection = "tracking";

    @ConfigSection(
        name = "Display",
        description = "Display settings",
        position = 1
    )
    String displaySection = "display";

    @ConfigSection(
        name = "Export",
        description = "Data export settings",
        position = 2
    )
    String exportSection = "export";

    @ConfigItem(
        keyName = "trackSoloBosses",
        name = "Track Solo Bosses",
        description = "Record kill data for solo bosses (Vorkath, Zulrah, GWD, etc.)",
        section = trackingSection,
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
        section = trackingSection,
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
        section = trackingSection,
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
        section = trackingSection,
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
        section = trackingSection,
        position = 4
    )
    default boolean snapshotInventory()
    {
        return false;
    }

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
        description = "Show a chat notification when a kill is recorded",
        section = displaySection,
        position = 1
    )
    default boolean showKillNotification()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showRecentKillOverlay",
        name = "Show Recent Kill Overlay",
        description = "Display an overlay with the most recent kill info",
        section = displaySection,
        position = 2
    )
    default boolean showRecentKillOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "exportPath",
        name = "Export Directory",
        description = "Directory for CSV/JSON exports (default: ~/.runelite/boss-analytics/export)",
        section = exportSection,
        position = 0
    )
    default String exportPath()
    {
        return "";
    }
}
