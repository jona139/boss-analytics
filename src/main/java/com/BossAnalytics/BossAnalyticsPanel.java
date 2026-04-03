package com.BossAnalytics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;

@Slf4j
public class BossAnalyticsPanel extends PluginPanel
{
    private final DataStore dataStore;
    private final DataExporter dataExporter;
    private final BossAnalyticsConfig config;

    private final JPanel bossListPanel;
    private final JShadowedLabel sessionKillsLabel;
    private final JShadowedLabel lastKillLabel;
    private final PluginErrorPanel errorPanel;

    private int sessionKills = 0;

    public BossAnalyticsPanel(DataStore dataStore, DataExporter dataExporter, BossAnalyticsConfig config)
    {
        super();
        this.dataStore = dataStore;
        this.dataExporter = dataExporter;
        this.config = config;

        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout(0, 10));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        headerPanel.setOpaque(false);

        JShadowedLabel title = new JShadowedLabel("Boss Analytics");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        headerPanel.add(title, BorderLayout.NORTH);

        JPanel sessionPanel = new JPanel(new GridLayout(2, 1));
        sessionPanel.setOpaque(false);
        sessionPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

        sessionKillsLabel = new JShadowedLabel("Session kills: 0");
        sessionKillsLabel.setFont(FontManager.getRunescapeSmallFont());
        sessionKillsLabel.setForeground(Color.WHITE);

        lastKillLabel = new JShadowedLabel("Last kill: —");
        lastKillLabel.setFont(FontManager.getRunescapeSmallFont());
        lastKillLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        sessionPanel.add(sessionKillsLabel);
        sessionPanel.add(lastKillLabel);
        headerPanel.add(sessionPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Boss list
        bossListPanel = new JPanel();
        bossListPanel.setLayout(new BoxLayout(bossListPanel, BoxLayout.Y_AXIS));
        bossListPanel.setOpaque(false);

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(bossListPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        buttonPanel.setOpaque(false);

        ColorJButton csvButton = new ColorJButton("Export CSV", ColorScheme.DARK_GRAY_COLOR);
        csvButton.setFont(FontManager.getRunescapeSmallFont());
        csvButton.setFocusPainted(false);
        csvButton.addActionListener(e -> exportCsv());

        ColorJButton jsonButton = new ColorJButton("Export JSON", ColorScheme.DARK_GRAY_COLOR);
        jsonButton.setFont(FontManager.getRunescapeSmallFont());
        jsonButton.setFocusPainted(false);
        jsonButton.addActionListener(e -> exportJson());

        buttonPanel.add(csvButton);
        buttonPanel.add(jsonButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Error panel (shown when no data)
        errorPanel = new PluginErrorPanel();
        errorPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        errorPanel.setContent("No data yet", "Go kill some bosses!");

        // Initial data load
        SwingUtilities.invokeLater(this::refreshBossList);
    }

    public void onKillRecorded(KillRecord record)
    {
        sessionKills++;
        SwingUtilities.invokeLater(() -> {
            sessionKillsLabel.setText("Session kills: " + sessionKills);
            int mins = (int) (record.getDurationSeconds() / 60);
            double secs = record.getDurationSeconds() % 60;
            lastKillLabel.setText(String.format("Last: %s — %d:%04.1f",
                record.getBossName(), mins, secs));
            refreshBossList();
        });
    }

    private void refreshBossList()
    {
        try
        {
            Map<String, Integer> counts = dataStore.getKillCounts();
            Map<String, Double> averages = dataStore.getAverageKillTimes();

            bossListPanel.removeAll();

            if (counts.isEmpty())
            {
                bossListPanel.add(errorPanel);
            }
            else
            {
                for (Map.Entry<String, Integer> entry : counts.entrySet())
                {
                    String boss = entry.getKey();
                    int count = entry.getValue();
                    Double avg = averages.get(boss);
                    bossListPanel.add(createBossRow(boss, count, avg));
                    bossListPanel.add(Box.createRigidArea(new Dimension(0, 3)));
                }
            }

            bossListPanel.revalidate();
            bossListPanel.repaint();
        }
        catch (Exception e)
        {
            log.error("Failed to refresh boss list", e);
        }
    }

    private JPanel createBossRow(String bossName, int killCount, Double avgTime)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JShadowedLabel nameLabel = new JShadowedLabel(bossName);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setForeground(Color.WHITE);
        row.add(nameLabel, BorderLayout.WEST);

        String info = killCount + " kc";
        if (avgTime != null)
        {
            int mins = (int) (avgTime / 60);
            double secs = avgTime % 60;
            info += String.format("  |  avg %d:%04.1f", mins, secs);
        }
        JShadowedLabel infoLabel = new JShadowedLabel(info);
        infoLabel.setFont(FontManager.getRunescapeSmallFont());
        infoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(infoLabel, BorderLayout.EAST);

        return row;
    }

    private void exportCsv()
    {
        try
        {
            File exportDir = getExportDir();
            dataExporter.exportKillsCsv(null, exportDir);
            JOptionPane.showMessageDialog(this,
                "Exported to: " + exportDir.getAbsolutePath(),
                "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception e)
        {
            log.error("CSV export failed", e);
            JOptionPane.showMessageDialog(this,
                "Export failed: " + e.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportJson()
    {
        try
        {
            File exportDir = getExportDir();
            dataExporter.exportAllJson(exportDir);
            JOptionPane.showMessageDialog(this,
                "Exported to: " + exportDir.getAbsolutePath(),
                "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception e)
        {
            log.error("JSON export failed", e);
            JOptionPane.showMessageDialog(this,
                "Export failed: " + e.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File getExportDir()
    {
        String path = config.exportPath();
        if (path == null || path.isEmpty())
        {
            path = System.getProperty("user.home") + "/.runelite/boss-analytics/export";
        }
        File dir = new File(path);
        dir.mkdirs();
        return dir;
    }

    /**
     * Creates the sidebar icon.
     */
    public static BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(80, 80, 80));
        g.fillOval(0, 0, 24, 24);
        g.setColor(new Color(220, 220, 220));
        g.drawOval(0, 0, 23, 23);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(9f));
        g.drawString("BA", 4, 16);
        g.dispose();
        return image;
    }
}
