package tui.widgets

import jexer.TApplication
import jexer.TWindow
import jexer.TLabel
import jexer.TAction
import jexer.TTimer
import tui.JexerTheme
import tui.sidebar.*
import core.SessionStatsManager
import core.LspManager
import java.nio.file.Paths

/**
 * Sidebar widget showing session info, tokens, LSP status, and modified files.
 * Collapsible panel with multiple sections.
 */
class JexerSidebar extends TWindow {

    private static final int WIDTH = 42
    private static final int HEADER_HEIGHT = 1
    private static final int SECTION_GAP = 1

    private String sessionId
    private TApplication application
    private boolean expanded = true

    // Sections
    private SessionInfoSection sessionInfoSection
    private TokenSection tokenSection
    private LspSection lspSection
    private ModifiedFilesSection modifiedFilesSection

    // Header
    private TLabel collapseLabel
    private TLabel separatorLabel

    JexerSidebar(TApplication app, String sessionId, int height) {
        super(app, '', WIDTH, height, TWindow.NOCLOSEBOX | TWindow.ABSOLUTEXY)
        this.sessionId = sessionId
        this.application = app

        initSections()
        buildHeader()
        rebuildContent()
    }

    /**
     * Initialize sidebar sections.
     */
    private void initSections() {
        sessionInfoSection = new SessionInfoSection(application, sessionId, WIDTH - 2)
        tokenSection = new TokenSection(application, sessionId, WIDTH - 2)
        lspSection = new LspSection(application, sessionId, WIDTH - 2)
        modifiedFilesSection = new ModifiedFilesSection(application, sessionId, WIDTH - 2)
    }

    /**
     * Build sidebar header with collapse toggle.
     */
    private void buildHeader() {
        int x = 0
        int y = 0

        // Top border
        for (int i = 0; i < WIDTH; i++) {
            setScreenCell(x + i, y, '─', JexerTheme.createAttributes(
                JexerTheme.getSidebarBorderColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        // Collapse indicator
        collapseLabel = new TLabel(this, expanded ? '▼' : '▶', x + 1, y + 1)
        // collapseLabel.getScreenCellAttributes().setForeColor(JexerTheme.getAccentColor())

        // Title
        def title = new TLabel(this, ' Sidebar ', x + 3, y + 1)
        // title.getScreenCellAttributes().setForeColor(JexerTheme.getTextColor())

        // Separator line
        separatorLabel = new TLabel(this, '─' * (WIDTH - 2), x + 1, y + 2)
    // separatorLabel.getScreenCellAttributes().setForeColor(JexerTheme.getSidebarBorderColor())
    }

    /**
     * Rebuild sidebar content based on expanded state.
     */
    private void rebuildContent() {
        getChildren().clear()

        if (!expanded) {
            // Collapsed state - just show expand hint
            return
        }

        // Clear content area below header
        int startY = HEADER_HEIGHT + 1
        int contentHeight = getHeight() - startY - 1

        // Draw sections
        int currentY = startY
        currentY = sessionInfoSection.render(currentY, 1)
        currentY += SECTION_GAP

        currentY = tokenSection.render(currentY, 1)
        currentY += SECTION_GAP

        currentY = lspSection.render(currentY, 1)
        currentY += SECTION_GAP

        currentY = modifiedFilesSection.render(currentY, 1)

        // Draw bottom border
        drawBottomBorder(currentY + 1)
    }

    /**
     * Draw bottom border of sidebar.
     */
    private void drawBottomBorder(int y) {
        for (int i = 0; i < WIDTH; i++) {
            setScreenCell(i, y, '─', JexerTheme.createAttributes(
                JexerTheme.getSidebarBorderColor(),
                JexerTheme.getBackgroundColor()
            ))
        }
        setScreenCell(0, y, '└', JexerTheme.createAttributes(
            JexerTheme.getSidebarBorderColor(),
            JexerTheme.getBackgroundColor()
        ))
        for (int i = 1; i < WIDTH - 1; i++) {
            setScreenCell(i, y, '─', JexerTheme.createAttributes(
                JexerTheme.getSidebarBorderColor(),
                JexerTheme.getBackgroundColor()
            ))
        }
        setScreenCell(WIDTH - 1, y, '┘', JexerTheme.createAttributes(
            JexerTheme.getSidebarBorderColor(),
            JexerTheme.getBackgroundColor()
        ))
    }

    /**
     * Toggle sidebar expansion.
     */
    void toggle() {
        expanded = !expanded
        updateCollapseIndicator()
        rebuildContent()

        // Return focus to main window
        application.getDesktop().getActiveChild()?.activate()
    }

    /**
     * Set sidebar expanded state.
     */
    void setExpanded(boolean expanded) {
        this.expanded = expanded
        updateCollapseIndicator()
        rebuildContent()
    }

    /**
     * Update collapse indicator icon.
     */
    private void updateCollapseIndicator() {
        if (collapseLabel != null) {
            collapseLabel.setLabel(expanded ? '▼' : '▶')
        }
    }

    /**
     * Refresh all sidebar sections.
     */
    void refresh() {
        sessionInfoSection.refresh()
        tokenSection.refresh()

        // Update LSP diagnostic counts
        try {
            LspManager.instance.updateDiagnosticCounts(sessionId)
        } catch (Exception e) {
        // Ignore if LSP not available
        }

        lspSection.refresh()
        modifiedFilesSection.refresh()

        rebuildContent()
    }

    /**
     * Get sidebar width.
     */
    int getSidebarWidth() {
        return expanded ? WIDTH : 1
    }

    /**
     * Handle keypress for collapse toggle.
     */
    @Override
    public void onKeypress(jexer.event.TKeypressEvent keypress) {
        // Allow collapse with '/' when focused
        if (keypress.getKey().equals(jexer.TKeypress.kbSlash)) {
            toggle()
            return
        }
        super.onKeypress(keypress)
    }

    /**
     * Set screen cell at position.
     */
    private void setScreenCell(int x, int y, char ch, jexer.bits.CellAttributes attr) {
        if (application.getScreen() != null) {
            application.getScreen().putChar(getX() + x, getY() + y, ch, attr)
        }
    }

}
