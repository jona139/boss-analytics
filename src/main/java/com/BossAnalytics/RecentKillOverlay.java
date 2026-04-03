package com.BossAnalytics;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Slf4j
public class RecentKillOverlay extends OverlayPanel
{
    private final BossAnalyticsConfig config;

    private String recentBossName = null;
    private double recentDuration = 0;
    private int recentKc = 0;
    private boolean recentPb = false;
    private long lastKillTime = 0;
    private static final long DISPLAY_TIME = 8000;

    @Inject
    public RecentKillOverlay(BossAnalyticsConfig config)
    {
        this.config = config;
        setPriority(OverlayPriority.HIGH);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    public void setRecentKill(KillRecord record)
    {
        this.recentBossName = record.getBossName();
        this.recentDuration = record.getDurationSeconds();
        this.recentKc = record.getKillCount();
        this.recentPb = record.isPersonalBest();
        this.lastKillTime = System.currentTimeMillis();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showRecentKillOverlay() || recentBossName == null)
        {
            return null;
        }

        if (System.currentTimeMillis() - lastKillTime > DISPLAY_TIME)
        {
            recentBossName = null;
            return null;
        }

        panelComponent.getChildren().clear();

        Color titleColor = recentPb ? Color.GREEN : new Color(255, 152, 31);

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Kill Recorded" + (recentPb ? " (PB!)" : ""))
            .color(titleColor)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Boss:")
            .right(recentBossName)
            .leftColor(Color.WHITE)
            .rightColor(Color.WHITE)
            .build());

        int mins = (int) (recentDuration / 60);
        double secs = recentDuration % 60;
        String timeStr = String.format("%d:%04.1f", mins, secs);

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Time:")
            .right(timeStr)
            .leftColor(Color.WHITE)
            .rightColor(recentPb ? Color.GREEN : Color.WHITE)
            .build());

        if (recentKc > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("KC:")
                .right(String.valueOf(recentKc))
                .leftColor(Color.WHITE)
                .rightColor(Color.WHITE)
                .build());
        }

        panelComponent.setPreferredSize(new Dimension(180, 80));
        return super.render(graphics);
    }
}
