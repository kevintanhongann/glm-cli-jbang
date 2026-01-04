package tui.jexer.widgets

import jexer.TApplication
import jexer.TWindow
import jexer.TLabel
import jexer.TAction
import jexer.TTimer
import tui.JexerTheme
import jexer.bits.CellAttributes
import java.nio.file.Paths

/**
 * Status bar widget displaying model, directory, scroll position, and shortcuts.
 * Located at bottom of main window.
 */
class JexerStatusBar extends TWindow {

    private TLabel modelLabel
    private TLabel directoryLabel
    private TLabel scrollLabel
    private TLabel agentLabel
    private TLabel shortcutsLabel
    private String currentModel = ''
    private String currentDirectory = ''
    private String currentAgent = 'BUILD'
    private int scrollPosition = 0
    private int totalLines = 0
    private boolean showSidebarHint = false

    // Track managed widgets for proper cleanup
    private List<jexer.TWidget> managedWidgets = []

    JexerStatusBar(TApplication app, int width) {
        super(app, '', width, 1, TWindow.NOCLOSEBOX | TWindow.ABSOLUTEXY)
        buildUI()
    }

    /**
     * Build status bar UI.
     */
    private void buildUI() {
        // Remove managed widgets properly
        managedWidgets.each { widget ->
            remove(widget)
        }
        managedWidgets.clear()

        // Calculate positions
        int x = 1
        int y = 0

        // Model label
        String modelText = "Model: ${currentModel}"
        modelLabel = new TLabel(this, modelText, x, y)
        managedWidgets.add(modelLabel)
        x += modelText.length() + 4

        // Separator
        def sep1 = new TLabel(this, '|', x, y)
        managedWidgets.add(sep1)
        x += 3

        // Directory label (truncated)
        String dirName = Paths.get(currentDirectory).fileName?.toString() ?: currentDirectory
        if (dirName.length() > 15) {
            dirName = dirName[0..12] + '...'
        }
        directoryLabel = new TLabel(this, "Dir: ${dirName}", x, y)
        managedWidgets.add(directoryLabel)
        x += ("Dir: ${dirName}").length() + 4

        // Separator
        def sep2 = new TLabel(this, '|', x, y)
        managedWidgets.add(sep2)
        x += 3

        // Scroll position label (only when not at bottom)
        scrollLabel = new TLabel(this, '', x, y)
        managedWidgets.add(scrollLabel)
        x += 20 // Reserve space

        // Separator
        def sep3 = new TLabel(this, '|', x, y)
        managedWidgets.add(sep3)
        x += 3

        // Shortcuts label
        String shortcutsText = 'Ctrl+S:Save  Ctrl+C:Exit'
        shortcutsLabel = new TLabel(this, shortcutsText, x, y)
        managedWidgets.add(shortcutsLabel)
        x += shortcutsText.length() + 4

        // Separator
        def sep4 = new TLabel(this, '|', x, y)
        managedWidgets.add(sep4)
        x += 3

        // Agent label
        agentLabel = new TLabel(this, currentAgent, x, y)
        managedWidgets.add(agentLabel)

        // Tab hint
        String tabText = ' (Tab to switch)'
        def tabLabel = new TLabel(this, tabText, x + currentAgent.length(), y)
        managedWidgets.add(tabLabel)

        // Sidebar hint (if enabled)
        if (showSidebarHint) {
            x += tabText.length() + 5
            def sep5 = new TLabel(this, '|', x, y)
            managedWidgets.add(sep5)

            x += 3
            def sidebarLabel = new TLabel(this, '/sidebar:Toggle', x, y)
            managedWidgets.add(sidebarLabel)
        }
    }

    /**
     * Set current model.
     */
    void setModel(String model) {
        this.currentModel = model
        rebuild()
    }

    /**
     * Set current directory.
     */
    void setDirectory(String directory) {
        this.currentDirectory = directory
        rebuild()
    }

    /**
     * Set current agent.
     */
    void setAgent(String agent) {
        this.currentAgent = agent

        // Update agent label color
        if (agentLabel != null) {
            if (agent == 'BUILD') {
            // agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getAgentBuildColor())
            } else if (agent == 'PLAN') {
            // agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getAgentPlanColor())
            } else {
            // agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextColor())
            }
        }

        rebuild()
    }

    /**
     * Update scroll position.
     */
    void setScrollPosition(int current, int total) {
        this.scrollPosition = current
        this.totalLines = total

        if (scrollLabel != null) {
            // Only show when not at bottom
            if (current < total - 5) {
                scrollLabel.setLabel("Line ${current}/${total}")
            } else {
                scrollLabel.setLabel('')
            }
        }
    }

    /**
     * Show/hide sidebar toggle hint.
     */
    void setSidebarEnabled(boolean enabled) {
        this.showSidebarHint = enabled
        rebuild()
    }

    /**
     * Rebuild status bar content.
     */
    private void rebuild() {
        buildUI()
// invalidate()
    }

    /**
     * Get height of status bar.
     */
    int getBarHeight() {
        return 1
    }

}
