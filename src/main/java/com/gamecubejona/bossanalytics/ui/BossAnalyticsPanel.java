package com.gamecubejona.bossanalytics.ui;

import com.gamecubejona.bossanalytics.BossAnalyticsConfig;
import com.gamecubejona.bossanalytics.BossAnalyticsPlugin;
import com.gamecubejona.bossanalytics.data.DataStore;
import com.gamecubejona.bossanalytics.data.KillRecord;
import com.gamecubejona.bossanalytics.export.DataExporter;
import com.gamecubejona.bossanalytics.tracking.KillTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.Map;

/**
 * Side panel showing:
 * - Session summary (kills this session, last kill)
 * - Per-boss kill counts and average times
 * - Export buttons (CSV, JSON)
 */
@Slf4j
public class BossAnalyticsPanel extends PluginPanel
{
    private final BossAnalyticsPlugin plugin;
    private final DataStore dataStore;
    private final DataExporter dataExporter;
    private final BossAnalyticsConfig config;

    // UI components
    private final JPanel statsPanel;
    private final JLabel sessionKillsLabel;
    private final JLabel lastKillLabel;
    private final JPanel bossListPanel;

    private int sessionKills = 0;

    public BossAnalyticsPanel(BossAnalyticsPlugin plugin, DataStore dataStore,
                              DataExporter dataExporter, BossAnalyticsConfig config)
    {
        super(false);
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.dataExporter = dataExporter;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Boss Analytics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(ColorScheme.BRAND_ORANGE);
        headerPanel.add(title, BorderLayout.NORTH);

        // Session info
        JPanel sessionPanel = new JPanel(new GridLayout(2, 1));
        sessionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sessionPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

        sessionKillsLabel = new JLabel("Session kills: 0");
        sessionKillsLabel.setForeground(Color.WHITE);
        lastKillLabel = new JLabel("Last kill: —");
        lastKillLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        sessionPanel.add(sessionKillsLabel);
        sessionPanel.add(lastKillLabel);
        headerPanel.add(sessionPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Stats panel (boss list with counts + averages)
        statsPanel = new JPanel();
        statsPanel.setLayout(new BorderLayout());
        statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        bossListPanel = new JPanel();
        bossListPanel.setLayout(new BoxLayout(bossListPanel, BoxLayout.Y_AXIS));
        bossListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(bossListPanel);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        statsPanel.add(scrollPane, BorderLayout.CENTER);

        add(statsPanel, BorderLayout.CENTER);

        // Export buttons
        JPanel exportPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        exportPanel.setBorder(new EmptyBorder(5, 10, 10, 10));
        exportPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton csvButton = new JButton("Export CSV");
        csvButton.addActionListener(e -> exportCsv());
        exportPanel.add(csvButton);

        JButton jsonButton = new JButton("Export JSON");
        jsonButton.addActionListener(e -> exportJson());
        exportPanel.add(jsonButton);

        add(exportPanel, BorderLayout.SOUTH);

        // Load initial data
        SwingUtilities.invokeLater(this::refreshBossList);
    }

    /**
     * Called by the plugin when a kill is recorded.
     */
    public void onKillRecorded(KillRecord record)
    {
        sessionKills++;
        SwingUtilities.invokeLater(() -> {
            sessionKillsLabel.setText("Session kills: " + sessionKills);
            lastKillLabel.setText(String.format("Last: %s — %.1fs",
                record.getBossName(), record.getDurationSeconds()));
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
                JLabel emptyLabel = new JLabel("No kills recorded yet");
                emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
                bossListPanel.add(emptyLabel);
            }
            else
            {
                for (Map.Entry<String, Integer> entry : counts.entrySet())
                {
                    String boss = entry.getKey();
                    int count = entry.getValue();
                    Double avg = averages.get(boss);

                    JPanel row = createBossRow(boss, count, avg);
                    bossListPanel.add(row);
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
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(5, 10, 5, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel nameLabel = new JLabel(bossName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        row.add(nameLabel, BorderLayout.WEST);

        String info = killCount + " kills";
        if (avgTime != null)
        {
            int mins = (int) (avgTime / 60);
            double secs = avgTime % 60;
            info += String.format("  |  avg %d:%04.1f", mins, secs);
        }
        JLabel infoLabel = new JLabel(info);
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
}
