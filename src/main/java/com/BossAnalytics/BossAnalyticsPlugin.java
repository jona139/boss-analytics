package com.BossAnalytics;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Boss Analytics",
    description = "Track boss kill times, gear, levels, and raid routes for data-driven analysis",
    tags = {"boss", "analytics", "kill", "time", "tracker", "data", "cox", "nex", "raids"}
)
public class BossAnalyticsPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private BossAnalyticsConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private EventBus eventBus;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    // Plugin components (manually constructed like FiftyFifty does)
    private DataStore dataStore;
    private BossRegistry bossRegistry;
    private PlayerStateTracker playerStateTracker;
    private KillTracker killTracker;
    private CoxHandler coxHandler;
    private NexHandler nexHandler;
    private DataExporter dataExporter;
    private RecentKillOverlay recentKillOverlay;
    private BossAnalyticsPanel pluginPanel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Boss Analytics plugin started!");

        // Initialize components
        dataStore = new DataStore();
        dataStore.open();

        bossRegistry = new BossRegistry();
        playerStateTracker = new PlayerStateTracker(client, itemManager);
        killTracker = new KillTracker(client, dataStore, playerStateTracker, bossRegistry, config);
        coxHandler = new CoxHandler(client, dataStore);
        nexHandler = new NexHandler(client);
        dataExporter = new DataExporter(dataStore);
        recentKillOverlay = new RecentKillOverlay(config);

        // Register event subscribers
        eventBus.register(killTracker);
        eventBus.register(coxHandler);
        eventBus.register(nexHandler);

        // Wire up kill listeners
        killTracker.addListener(coxHandler);
        killTracker.addListener(nexHandler);
        killTracker.addListener(record -> {
            recentKillOverlay.setRecentKill(record);
            if (pluginPanel != null)
            {
                pluginPanel.onKillRecorded(record);
            }
            if (config.showKillNotification())
            {
                clientThread.invoke(() -> {
                    int mins = (int) (record.getDurationSeconds() / 60);
                    double secs = record.getDurationSeconds() % 60;
                    client.addChatMessage(
                        net.runelite.api.ChatMessageType.GAMEMESSAGE,
                        "",
                        String.format("[Boss Analytics] %s kill recorded: %d:%04.1f",
                            record.getBossName(), mins, secs),
                        null
                    );
                });
            }
        });

        // Add overlay
        overlayManager.add(recentKillOverlay);

        // Initialize the plugin panel
        if (config.showPanel())
        {
            pluginPanel = new BossAnalyticsPanel(dataStore, dataExporter, config);
            final BufferedImage icon = BossAnalyticsPanel.createIcon();

            navButton = NavigationButton.builder()
                .tooltip("Boss Analytics")
                .icon(icon)
                .priority(10)
                .panel(pluginPanel)
                .build();

            clientToolbar.addNavigation(navButton);
        }

        log.info("Boss Analytics started — tracking {} bosses", bossRegistry.getAll().size());
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Boss Analytics plugin stopped!");

        overlayManager.remove(recentKillOverlay);

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
        killTracker.cleanStaleStates();
    }

    @Provides
    BossAnalyticsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BossAnalyticsConfig.class);
    }
}
