package tui.sidebar

import jexer.TApplication
import tui.JexerTheme
import core.SessionStatsManager

/**
 * Token usage section for Jexer sidebar.
 * Displays input/output tokens and cost.
 */
class TokenSection {

    private TApplication application
    private String sessionId
    private int width = 40

    private int inputTokens = 0
    private int outputTokens = 0
    private BigDecimal cost = 0.0000

    TokenSection(TApplication app, String sessionId, int width) {
        this.application = app
        this.sessionId = sessionId
        this.width = width
        refresh()
    }

    /**
     * Refresh token statistics.
     */
    void refresh() {
        try {
            def stats = SessionStatsManager.instance?.getStats(sessionId)
            if (stats != null) {
                this.inputTokens = stats.inputTokens ?: 0
                this.outputTokens = stats.outputTokens ?: 0
                this.cost = stats.cost ?: 0.0000
            }
        } catch (Exception e) {
            // Ignore if stats not available
        }
    }

    /**
     * Render section at specified Y position.
     * Returns Y position after rendering.
     */
    int render(int startY, int startX) {
        int y = startY

        // Section header with tree
        def headerLine = "│"
        setScreenCell(startX, y, headerLine, JexerTheme.createAttributes(
            JexerTheme.getSidebarTreeColor(),
            JexerTheme.getBackgroundColor()
        ))

        def headerText = "📊 Token Usage"
        for (int i = 0; i < headerText.length() && i + startX + 1 < width; i++) {
            char ch = headerText.charAt(i)
            setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                JexerTheme.getTextColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Input tokens
        def inLine = "│  Input:  ${inputTokens}"
        for (int i = 0; i < inLine.length() && i + startX + 1 < width; i++) {
            char ch = inLine.charAt(i)
            setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                JexerTheme.getTextMutedColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Output tokens
        def outLine = "│  Output: ${outputTokens}"
        for (int i = 0; i < outLine.length() && i + startX + 1 < width; i++) {
            char ch = outLine.charAt(i)
            setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                JexerTheme.getTextMutedColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Total tokens
        def totalTokens = inputTokens + outputTokens
        def totalLine = "│  Total:  ${totalTokens}"
        for (int i = 0; i < totalLine.length() && i + startX + 1 < width; i++) {
            char ch = totalLine.charAt(i)
            setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                JexerTheme.getTextMutedColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Cost
        String costStr = String.format('\$%.4f', cost)
        def costLine = "│  Cost:   ${costStr}"
        for (int i = 0; i < costLine.length() && i + startX + 1 < width; i++) {
            char ch = costLine.charAt(i)
            setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                JexerTheme.getSuccessColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Separator
        def sepLine = "└" + "─" * (width - 2)
        for (int i = 0; i < sepLine.length(); i++) {
            char ch = sepLine.charAt(i)
            setScreenCell(startX + i, y, ch, JexerTheme.createAttributes(
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
            int screenX = getX() + x
            int screenY = getY() + y

            if (screenX >= 0 && screenX < screen.getWidth() &&
                screenY >= 0 && screenY < screen.getHeight()) {
                screen.putChar(screenX, screenY, ch, attr)
            }
        }
    }

    /**
     * Get X position (to be overridden by parent).
     */
    int getX() {
        return 0
    }

    /**
     * Get Y position (to be overridden by parent).
     */
    int getY() {
        return 0
    }
}
