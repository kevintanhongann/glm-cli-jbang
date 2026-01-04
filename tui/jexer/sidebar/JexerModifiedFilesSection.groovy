package tui.jexer.sidebar

import jexer.TApplication
import tui.JexerTheme
import core.SessionStatsManager

/**
 * Modified files section for Jexer sidebar.
 * Displays list of files changed in current session.
 */
class ModifiedFilesSection {

    private TApplication application
    private String sessionId
    private int width = 40

    private List<String> modifiedFiles = []

    ModifiedFilesSection(TApplication app, String sessionId, int width) {
        this.application = app
        this.sessionId = sessionId
        this.width = width
        refresh()
    }

    /**
     * Refresh modified files list.
     */
    void refresh() {
        modifiedFiles = []

        try {
            def stats = SessionStatsManager.instance?.getStats(sessionId)
            if (stats != null) {
                modifiedFiles = stats.modifiedFiles ?: []
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

        if (modifiedFiles.isEmpty()) {
            // No modified files - show message
            def headerLine = "│"
            setScreenCell(startX, y, headerLine, JexerTheme.createAttributes(
                JexerTheme.getSidebarTreeColor(),
                JexerTheme.getBackgroundColor()
            ))

            def headerText = "📄 No modified files"
            for (int i = 0; i < headerText.length() && i + startX + 1 < width; i++) {
                char ch = headerText.charAt(i)
                setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                    JexerTheme.getTextMutedColor(),
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

        // Section header with tree
        def headerLine = "│"
        setScreenCell(startX, y, headerLine, JexerTheme.createAttributes(
            JexerTheme.getSidebarTreeColor(),
            JexerTheme.getBackgroundColor()
        ))

        def headerText = "📄 Modified Files (${modifiedFiles.size()})"
        for (int i = 0; i < headerText.length() && i + startX + 1 < width; i++) {
            char ch = headerText.charAt(i)
            setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                JexerTheme.getTextColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // List modified files (max 5 shown)
        int shownCount = Math.min(5, modifiedFiles.size())
        modifiedFiles[0..<shownCount].each { filePath ->
            // Get filename only
            String fileName = filePath.contains('/') ?
                filePath.substring(filePath.lastIndexOf('/') + 1) :
                filePath

            def fileLine = "│  📄 ${fileName}"
            for (int i = 0; i < fileLine.length() && i + startX + 1 < width; i++) {
                char ch = fileLine.charAt(i)
                setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                    JexerTheme.getTextMutedColor(),
                    JexerTheme.getBackgroundColor()
                ))
            }

            y++
        }

        // Show "and X more" if more files
        if (modifiedFiles.size() > shownCount) {
            def moreLine = "│  ... and ${modifiedFiles.size() - shownCount} more"
            for (int i = 0; i < moreLine.length() && i + startX + 1 < width; i++) {
                char ch = moreLine.charAt(i)
                setScreenCell(startX + i + 1, y, ch, JexerTheme.createAttributes(
                    JexerTheme.getTextMutedColor(),
                    JexerTheme.getBackgroundColor()
                ))
            }

            y++
        }

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
