package tui.widgets

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

    JexerStatusBar(TApplication app, int width) {
        super(app, '', width, 1, TWindow.NOCLOSEBOX | TWindow.ABSOLUTEXY)
        buildUI()
    }

    /**
     * Build status bar UI.
     */
    private void buildUI() {
        getChildren().clear()

        // Calculate positions
        int x = 1
        int y = 0

        // Model label
        modelLabel = new TLabel("Model: ${currentModel}")
        modelLabel.setX(x)
        modelLabel.setY(y)
        modelLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextColor())
        add(modelLabel)
        x += modelLabel.getText().length() + 4

        // Separator
        def sep1 = new TLabel('|')
        sep1.setX(x)
        sep1.setY(y)
        sep1.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(sep1)
        x += 3

        // Directory label (truncated)
        String dirName = Paths.get(currentDirectory).fileName?.toString() ?: currentDirectory
        if (dirName.length() > 15) {
            dirName = dirName[0..12] + '...'
        }
        directoryLabel = new TLabel("Dir: ${dirName}")
        directoryLabel.setX(x)
        directoryLabel.setY(y)
        directoryLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(directoryLabel)
        x += directoryLabel.getText().length() + 4

        // Separator
        def sep2 = new TLabel('|')
        sep2.setX(x)
        sep2.setY(y)
        sep2.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(sep2)
        x += 3

        // Scroll position label (only when not at bottom)
        scrollLabel = new TLabel('')
        scrollLabel.setX(x)
        scrollLabel.setY(y)
        scrollLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(scrollLabel)
        x += 20 // Reserve space

        // Separator
        def sep3 = new TLabel('|')
        sep3.setX(x)
        sep3.setY(y)
        sep3.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(sep3)
        x += 3

        // Shortcuts label
        shortcutsLabel = new TLabel('Ctrl+S:Save  Ctrl+C:Exit')
        shortcutsLabel.setX(x)
        shortcutsLabel.setY(y)
        shortcutsLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(shortcutsLabel)
        x += shortcutsLabel.getText().length() + 4

        // Separator
        def sep4 = new TLabel('|')
        sep4.setX(x)
        sep4.setY(y)
        sep4.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(sep4)
        x += 3

        // Agent label
        agentLabel = new TLabel(currentAgent)
        agentLabel.setX(x)
        agentLabel.setY(y)
        agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getAgentBuildColor())
        agentLabel.getScreenCellAttributes().setBold(true)
        add(agentLabel)

        // Tab hint
        def tabLabel = new TLabel(' (Tab to switch)')
        tabLabel.setX(x + currentAgent.length())
        tabLabel.setY(y)
        tabLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
        add(tabLabel)

        // Sidebar hint (if enabled)
        if (showSidebarHint) {
            x += tabLabel.getText().length() + 5
            def sep5 = new TLabel('|')
            sep5.setX(x)
            sep5.setY(y)
            sep5.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
            add(sep5)

            x += 3
            def sidebarLabel = new TLabel('/sidebar:Toggle')
            sidebarLabel.setX(x)
            sidebarLabel.setY(y)
            sidebarLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextMutedColor())
            add(sidebarLabel)
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
                agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getAgentBuildColor())
            } else if (agent == 'PLAN') {
                agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getAgentPlanColor())
            } else {
                agentLabel.getScreenCellAttributes().setForeColor(JexerTheme.getTextColor())
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
                scrollLabel.setText("Line ${current}/${total}")
            } else {
                scrollLabel.setText('')
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
        invalidate()
    }

    /**
     * Get height of status bar.
     */
    int getBarHeight() {
        return 1
    }

}
