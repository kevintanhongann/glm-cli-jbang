package tui.lanterna.sidebar

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor
import core.LspServerInfo
import core.LspManager
import core.LspManager as LspClientManager
import core.SessionStatsManager
import tui.LanternaTheme
import tui.lanterna.widgets.Tooltip

class LspSection extends Panel {

    private String sessionId

    private Label headerLabel
    private Panel contentPanel
    private Panel headerPanel
    private Label toggleLabel
    private Map<String, Tooltip> activeTooltips = [:]

    private boolean expanded = true

    LspSection(String sessionId) {
        this.sessionId = sessionId
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        buildUI()
    }

    private void buildUI() {
        removeAllComponents()

        // Get LSP info from client manager
        def lspInfoList = LspManager.instance.getLspInfoForSidebar(sessionId)

        // Build header with mouse support
        headerPanel = new Panel()
        headerPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        toggleLabel = new Label(expanded ? '▼' : '▶')
        toggleLabel.setForegroundColor(LanternaTheme.getTextColor())
        headerPanel.addComponent(toggleLabel)

        headerLabel = new Label(' LSP')
        headerLabel.setForegroundColor(LanternaTheme.getTextColor())
        headerPanel.addComponent(headerLabel)

        // Show count if collapsed or has many servers
        if (!expanded || lspInfoList.size() > 2) {
            int connectedCount = lspInfoList.count { it.status == 'connected' }
            int errorCount = lspInfoList.count { it.status == 'error' }

            if (connectedCount > 0 && errorCount > 0) {
                def countLabel = new Label(" (${connectedCount} active, ${errorCount} error${errorCount > 1 ? 's' : ''})")
                countLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
                headerPanel.addComponent(countLabel)
            } else if (connectedCount > 0) {
                def countLabel = new Label(" (${connectedCount} active)")
                countLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
                headerPanel.addComponent(countLabel)
            } else if (errorCount > 0) {
                def countLabel = new Label(" (${errorCount} error${errorCount > 1 ? 's' : ''})")
                countLabel.setForegroundColor(TextColor.ANSI.RED)
                headerPanel.addComponent(countLabel)
            }
        }

        addComponent(headerPanel)

        // Add visual separator
        def separator = new Label("─${'─' * 38}")
        separator.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        addComponent(separator)
        addComponent(new Label(''))

        // Content
        if (expanded) {
            if (lspInfoList.isEmpty()) {
                def emptyLabel = new Label('  └─ No LSP servers active')
                emptyLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
                addComponent(emptyLabel)
            } else {
                for (lspInfo in lspInfoList) {
                    addLspServerItem(lspInfo)
                }
            }
        }

        // Separator
        addComponent(new Label(''))
    }

    private void addLspServerItem(LspServerInfo lspInfo) {
        def serverPanel = new Panel()
        serverPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        // Server name line
        def namePanel = new Panel()
        namePanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        // Status indicator
        def indicator = new Label('│  └─ •')
        if (lspInfo.status == 'connected') {
            indicator.setForegroundColor(TextColor.ANSI.GREEN)
        } else if (lspInfo.status == 'error') {
            indicator.setForegroundColor(TextColor.ANSI.RED)
        } else {
            indicator.setForegroundColor(LanternaTheme.getTextMutedColor())
        }
        namePanel.addComponent(indicator)

        // Server name
        def nameLabel = new Label(" ${lspInfo.lspId}")
        nameLabel.setForegroundColor(LanternaTheme.getTextColor())
        namePanel.addComponent(nameLabel)

        serverPanel.addComponent(namePanel)

        // Show root path if available
        if (lspInfo.root) {
            String shortRoot = shortenPath(lspInfo.root)
            def rootPanel = new Panel()
            rootPanel.setLayout(new LinearLayout(Direction.HORIZONTAL))

            def treePrefix = new Label('│     └─ ')
            treePrefix.setForegroundColor(LanternaTheme.getSidebarTreeColor())
            rootPanel.addComponent(treePrefix)

            def rootLabel = new Label(shortRoot)
            rootLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
            rootPanel.addComponent(rootLabel)

            serverPanel.addComponent(rootPanel)
        }

        // Show error if present with enhanced styling
        if (lspInfo.error) {
            def errorPanel = new Panel()
            errorPanel.setLayout(new LinearLayout(Direction.VERTICAL))

            def errorLine1 = new Panel()
            errorLine1.setLayout(new LinearLayout(Direction.HORIZONTAL))

            def errorTree = new Label('│     └─ ')
            errorTree.setForegroundColor(LanternaTheme.getSidebarTreeColor())
            errorLine1.addComponent(errorTree)

            def errorLabel = new Label("⚠ ${lspInfo.error}")
            errorLabel.setForegroundColor(TextColor.ANSI.RED)
            errorLine1.addComponent(errorLabel)

            errorPanel.addComponent(errorLine1)

            // Add tooltip hint
            def errorHint = new Label('│        (hover for details)')
            errorHint.setForegroundColor(LanternaTheme.getTextMutedColor())
            errorPanel.addComponent(errorHint)

            serverPanel.addComponent(errorPanel)
        }

        addComponent(serverPanel)
    }

    private String shortenPath(String path) {
        // Shorten path to fit in sidebar (max 30 chars)
        if (path.length() <= 30) return path

        // Take last parts of path
        def parts = path.split('/')
        if (parts.size() <= 2) return path

        return ".../${parts[-2..-1].join('/')}"
    }

    void toggleExpanded() {
        expanded = !expanded
        buildUI()
    }

    void refresh() {
        buildUI()
    }

    private void showTooltip(String serverName, String error) {
        // This would integrate with a tooltip system
        // For now, just log to console
        if (error) {
            System.out.println("LSP Error [${serverName}]: ${error}")
        }
    }

    private void hideTooltip(String serverName) {
        // Hide tooltip for specific server
        activeTooltips.remove(serverName)
    }

    private void hideAllTooltips() {
        activeTooltips.each { key, tooltip ->
            tooltip.close()
        }
        activeTooltips.clear()
    }

}
