package tui.jexer.sidebar

import jexer.TApplication
import tui.JexerTheme
import core.LspManager

/**
 * LSP status section for Jexer sidebar.
 * Displays connected LSP servers and diagnostic counts.
 */
class LspSection {

    private TApplication application
    private String sessionId
    private int width = 40

    private List<Map> lspServers = []

    LspSection(TApplication app, String sessionId, int width) {
        this.application = app
        this.sessionId = sessionId
        this.width = width
        refresh()
    }

    /**
     * Refresh LSP server information.
     */
    void refresh() {
        lspServers = []

        try {
            def lspManager = LspManager.instance
            if (lspManager != null) {
                def lspInfo = lspManager.getLspInfoForSidebar(sessionId)

                lspInfo.each { serverId, info ->
                    int diagCount = info.diagnosticCount ?: 0
                    String status = info.status ?: 'unknown'

                    lspServers << [
                        id: serverId,
                        name: info.name ?: serverId,
                        status: status,
                        diagnosticCount: diagCount,
                        root: info.root
                    ]
                }
            }
        } catch (Exception e) {
            // Ignore if LSP not available
        }
    }

    /**
     * Render section at specified Y position.
     * Returns Y position after rendering.
     */
    int render(int startY, int startX, int windowX = 0, int windowY = 0) {
        int y = startY

        if (lspServers.isEmpty()) {
            // No LSP servers - show message
            def headerLine = "│"
            setScreenCell(startX + windowX, y + windowY, headerLine, JexerTheme.createAttributes(
                JexerTheme.getSidebarTreeColor(),
                JexerTheme.getBackgroundColor()
            ))

            def headerText = "📡 No LSP servers"
            for (int i = 0; i < headerText.length() && i + startX + 1 < width; i++) {
                char ch = headerText.charAt(i)
                setScreenCell(startX + i + 1 + windowX, y + windowY, ch, JexerTheme.createAttributes(
                    JexerTheme.getTextMutedColor(),
                    JexerTheme.getBackgroundColor()
                ))
            }

            y++

            // Separator
            def sepLine = "└" + "─" * (width - 2)
            for (int i = 0; i < sepLine.length(); i++) {
                char ch = sepLine.charAt(i)
                setScreenCell(startX + i + windowX, y + windowY, ch, JexerTheme.createAttributes(
                    JexerTheme.getSidebarBorderColor(),
                    JexerTheme.getBackgroundColor()
                ))
            }

            y++

            // Empty line gap
            y++

            return y
        }

        // Section header with tree
        def headerLine = "│"
        setScreenCell(startX + windowX, y + windowY, headerLine, JexerTheme.createAttributes(
            JexerTheme.getSidebarTreeColor(),
            JexerTheme.getBackgroundColor()
        ))

        def headerText = "📡 LSP Servers (${lspServers.size()})"
        for (int i = 0; i < headerText.length() && i + startX + 1 < width; i++) {
            char ch = headerText.charAt(i)
            setScreenCell(startX + i + 1 + windowX, y + windowY, ch, JexerTheme.createAttributes(
                JexerTheme.getTextColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // List LSP servers
        lspServers.each { server ->
            def statusIcon = server.status == 'connected' ? '✓' : '⚠'
            def statusColor = server.status == 'connected' ?
                JexerTheme.getSuccessColor() :
                JexerTheme.getWarningColor()

            // Server name line
            def nameLine = "│  ${statusIcon} ${server.name}"
            for (int i = 0; i < nameLine.length() && i + startX + 1 < width; i++) {
                char ch = nameLine.charAt(i)
                setScreenCell(startX + i + 1 + windowX, y + windowY, ch, JexerTheme.createAttributes(
                    statusColor,
                    JexerTheme.getBackgroundColor()
                ))
            }

            y++

            // Diagnostic count (if any)
            if (server.diagnosticCount > 0) {
                String diagText = "│    ${server.diagnosticCount} diagnostics"
                String diagColor = server.diagnosticCount > 0 ?
                    JexerTheme.getErrorColor() :
                    JexerTheme.getTextMutedColor()

                for (int i = 0; i < diagText.length() && i + startX + 1 < width; i++) {
                    char ch = diagText.charAt(i)
                    setScreenCell(startX + i + 1 + windowX, y + windowY, ch, JexerTheme.createAttributes(
                        diagColor,
                        JexerTheme.getBackgroundColor()
                    ))
                }

                y++
            }
        }

        // Separator
        def sepLine = "└" + "─" * (width - 2)
        for (int i = 0; i < sepLine.length(); i++) {
            char ch = sepLine.charAt(i)
            setScreenCell(startX + i + windowX, y + windowY, ch, JexerTheme.createAttributes(
                JexerTheme.getSidebarBorderColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Empty line gap
        y++

        return y
    }

    /**
     * Set screen cell at position.
     */
    private void setScreenCell(int x, int y, char ch, jexer.bits.CellAttributes attr) {
        def screen = application.getScreen()
        if (screen != null) {
            if (x >= 0 && x < screen.getWidth() &&
                y >= 0 && y < screen.getHeight()) {
                screen.putChar(x, y, ch, attr)
            }
        }
    }
}
