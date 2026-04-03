package com.gamecubejona.bossanalytics;

import com.gamecubejona.bossanalytics.bosses.CoxHandler;
import com.gamecubejona.bossanalytics.bosses.NexHandler;
import com.gamecubejona.bossanalytics.data.DataStore;
import com.gamecubejona.bossanalytics.export.DataExporter;
import com.gamecubejona.bossanalytics.tracking.BossRegistry;
import com.gamecubejona.bossanalytics.tracking.KillTracker;
import com.gamecubejona.bossanalytics.tracking.PlayerStateTracker;
import com.gamecubejona.bossanalytics.ui.BossAnalyticsPanel;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Boss Analytics",
    description = "Track boss kill times, gear, levels, and raid routes for data analysis",
    tags = {"boss", "analytics", "kill", "time", "tracker", "data", "cox", "nex", "raids"}
)
public class BossAnalyticsPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private EventBus eventBus;
    @Inject private ClientToolbar clientToolbar;
    @Inject private BossAnalyticsConfig config;

    @Inject private DataStore dataStore;
    @Inject private BossRegistry bossRegistry;
    @Inject private KillTracker killTracker;
    @Inject private PlayerStateTracker playerStateTracker;
    @Inject private CoxHandler coxHandler;
    @Inject private NexHandler nexHandler;
    @Inject private DataExporter dataExporter;

    private BossAnalyticsPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Boss Analytics starting up");

        // Open database
        dataStore.open();

        // Register event subscribers for tracking components
        eventBus.register(killTracker);
        eventBus.register(coxHandler);
        eventBus.register(nexHandler);

        // Wire up kill listeners
        killTracker.addListener(nexHandler);
        killTracker.addListener(record -> {
            if (panel != null)
            {
                panel.onKillRecorded(record);
            }
        });

        // Set up side panel
        if (config.showPanel())
        {
            panel = new BossAnalyticsPanel(this, dataStore, dataExporter, config);
            final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
            navButton = NavigationButton.builder()
                .tooltip("Boss Analytics")
                .icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
                .priority(10)
                .panel(panel)
                .build();
            clientToolbar.addNavigation(navButton);
        }

        log.info("Boss Analytics started — tracking {} bosses", bossRegistry.getAll().size());
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Boss Analytics shutting down");

        eventBus.unregister(killTracker);
        eventBus.unregister(coxHandler);
        eventBus.unregister(nexHandler);

        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }

        dataStore.close();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Clean up stale fight tracking states
        killTracker.cleanStaleStates();
    }

    @Provides
    BossAnalyticsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BossAnalyticsConfig.class);
    }

    @Provides
    BossRegistry provideBossRegistry()
    {
        return new BossRegistry();
    }

    // Public accessors for the panel
    public DataStore getDataStore()
    {
        return dataStore;
    }

    public DataExporter getDataExporter()
    {
        return dataExporter;
    }
}
