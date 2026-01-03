package tui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import tui.LanternaTheme
import core.LspManager
import tui.sidebar.*

class SidebarPanel extends Panel {

    private String sessionId
    private MultiWindowTextGUI textGUI

    private SessionInfoSection sessionInfoSection
    private TokenSection tokenSection
    private LspSection lspSection
    private ModifiedFilesSection modifiedFilesSection

    private boolean expandedState = true
    private final int WIDTH = 42
    private Panel contentPanel
    private boolean showingScrollIndicator = false

    boolean getExpanded() {
        return expandedState
    }

    SidebarPanel(MultiWindowTextGUI textGUI, String sessionId) {
        this.textGUI = textGUI
        this.sessionId = sessionId

        setLayoutManager(new LinearLayout(Direction.VERTICAL))

        // Initialize sections
        sessionInfoSection = new SessionInfoSection(sessionId)
        tokenSection = new TokenSection(sessionId)
        lspSection = new LspSection(sessionId)
        modifiedFilesSection = new ModifiedFilesSection(sessionId)

        buildUI()
    }

    private void buildUI() {
        removeAllComponents()

        if (expandedState) {
            contentPanel = new Panel()
            contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

            // Add sections
            contentPanel.addComponent(sessionInfoSection)
            contentPanel.addComponent(tokenSection)
            contentPanel.addComponent(lspSection)
            contentPanel.addComponent(modifiedFilesSection)

            // Wrap in scrollable panel
            def scrollablePanel = new Panel()
            scrollablePanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

            // Add top border with scroll indicator
            def topBorder = new Panel()
            topBorder.setLayout(new LinearLayout(Direction.HORIZONTAL))

            def topLeft = new Label('┌')
            topLeft.setForegroundColor(LanternaTheme.getSidebarBorderColor())
            topBorder.addComponent(topLeft)

            def topLine = new Label('─' * (WIDTH - 2))
            topLine.setForegroundColor(LanternaTheme.getSidebarBorderColor())
            topBorder.addComponent(topLine)

            def topRight = new Label('┐')
            topRight.setForegroundColor(LanternaTheme.getSidebarBorderColor())
            topBorder.addComponent(topRight)

            scrollablePanel.addComponent(topBorder)

            // Add content
            scrollablePanel.addComponent(contentPanel)

            // Add bottom border
            def bottomBorder = new Panel()
            bottomBorder.setLayout(new LinearLayout(Direction.HORIZONTAL))

            def bottomLeft = new Label('└')
            bottomLeft.setForegroundColor(LanternaTheme.getSidebarBorderColor())
            bottomBorder.addComponent(bottomLeft)

            def bottomLine = new Label('─' * (WIDTH - 2))
            bottomLine.setForegroundColor(LanternaTheme.getSidebarBorderColor())
            bottomBorder.addComponent(bottomLine)

            def bottomRight = new Label('┘')
            bottomRight.setForegroundColor(LanternaTheme.getSidebarBorderColor())
            bottomBorder.addComponent(bottomRight)

            scrollablePanel.addComponent(bottomBorder)

            // Add scroll indicator if needed
            if (showingScrollIndicator) {
                def scrollIndicator = new Panel()
                scrollIndicator.setLayout(new LinearLayout(Direction.VERTICAL))

                def upScroll = new Label('│  ↑')
                upScroll.setForegroundColor(LanternaTheme.getSidebarTreeColor())
                scrollIndicator.addComponent(upScroll)

                def downScroll = new Label('│  ↓')
                downScroll.setForegroundColor(LanternaTheme.getSidebarTreeColor())
                scrollIndicator.addComponent(downScroll)

                scrollablePanel.addComponent(scrollIndicator)
            }

            addComponent(scrollablePanel)
        } else {
            // Collapsed state - just show a toggle indicator
            def collapsePanel = new Panel()
            collapsePanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

            // Click anywhere on collapsed panel to expand
            def clickLabel = new Label('▶ Sidebar')
            collapsePanel.addComponent(clickLabel)

            addComponent(collapsePanel)
        }

        invalidate()
    }

    void toggle() {
        expandedState = !expandedState
        buildUI()
    }

    void setExpanded(boolean expanded) {
        this.expandedState = expanded
        buildUI()
    }

    private void addMouseListener(Panel panel) {
    // Add mouse listener to detect clicks for section toggles
    // This is handled by individual sections
    }

    void refresh() {
        sessionInfoSection.refresh()
        tokenSection.refresh()

        // Update LSP diagnostic counts before refreshing
        LspManager.instance.updateDiagnosticCounts(sessionId)
        lspSection.refresh()

        modifiedFilesSection.refresh()

        // Check if scroll indicator should be shown
        updateScrollIndicator()
    }

    private void updateScrollIndicator() {
        // Calculate if content exceeds sidebar height
        // This is a simplified check - in reality, you'd need to check actual component heights
        def hasContent = sessionInfoSection || tokenSection || lspSection || modifiedFilesSection

        if (hasContent) {
            // Simple heuristic: show scroll indicator if multiple sections have content
            def lspServers = LspManager.instance.getLspInfoForSidebar(sessionId)
            def sessionStats = SessionStatsManager.instance.getStats(sessionId)
            def modifiedFiles = sessionStats?.modifiedFiles ?: []

            // Show scroll indicator if we have multiple LSP servers or modified files
            showingScrollIndicator = (lspServers.size() > 2) || (modifiedFiles.size() > 2)
        } else {
            showingScrollIndicator = false
        }
    }

}
