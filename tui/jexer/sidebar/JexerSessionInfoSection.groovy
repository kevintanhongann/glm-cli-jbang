package tui.jexer.sidebar

import jexer.TApplication
import tui.JexerTheme
import java.nio.file.Paths

/**
 * Session info section for Jexer sidebar.
 * Displays session title, directory, and ID.
 */
class SessionInfoSection {

    private TApplication application
    private String sessionId
    private String title = ""
    private String directory = ""
    private int width = 40

    SessionInfoSection(TApplication app, String sessionId, int width) {
        this.application = app
        this.sessionId = sessionId
        this.width = width
        refresh()
    }

    /**
     * Refresh session information.
     */
    void refresh() {
        // Get session info from SessionManager
        try {
            def sessionManager = core.SessionManager.instance
            def session = sessionManager?.getSession(sessionId)
            this.title = session?.title ?: "New Session"
        } catch (Exception e) {
            this.title = "New Session"
        }

        // Get current directory
        this.directory = System.getProperty("user.dir")
        String dirName = Paths.get(directory).fileName?.toString() ?: directory
        this.directory = dirName
    }

    /**
     * Render section at specified Y position.
     * Returns Y position after rendering.
     */
    int render(int startY, int startX, int windowX = 0, int windowY = 0) {
        int y = startY

        // Section header with tree
        def headerLine = "│"
        setScreenCell(startX + windowX, y + windowY, headerLine, JexerTheme.createAttributes(
            JexerTheme.getSidebarTreeColor(),
            JexerTheme.getBackgroundColor()
        ))

        // Title
        def titleText = "📄 ${title}"
        for (int i = 0; i < titleText.length() && i + startX + 1 < width; i++) {
            char ch = titleText.charAt(i)
            setScreenCell(startX + i + 1 + windowX, y + windowY, ch, JexerTheme.createAttributes(
                JexerTheme.getTextColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Directory with tree
        def dirLine = "│"
        setScreenCell(startX + windowX, y + windowY, dirLine, JexerTheme.createAttributes(
            JexerTheme.getSidebarTreeColor(),
            JexerTheme.getBackgroundColor()
        ))

        def dirText = "📁 ${directory}"
        for (int i = 0; i < dirText.length() && i + startX + 1 < width; i++) {
            char ch = dirText.charAt(i)
            setScreenCell(startX + i + 1 + windowX, y + windowY, ch, JexerTheme.createAttributes(
                JexerTheme.getTextMutedColor(),
                JexerTheme.getBackgroundColor()
            ))
        }

        y++

        // Session ID with tree
        def idLine = "│"
        setScreenCell(startX + windowX, y + windowY, idLine, JexerTheme.createAttributes(
            JexerTheme.getSidebarTreeColor(),
            JexerTheme.getBackgroundColor()
        ))

        // Truncate ID if too long
        String shortId = sessionId.length() > 25 ? sessionId[0..22] + "..." : sessionId
        def idText = "🆔 ${shortId}"
        for (int i = 0; i < idText.length() && i + startX + 1 < width; i++) {
            char ch = idText.charAt(i)
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

    /**
     * Set screen cell at position.
     */
    private void setScreenCell(int x, int y, char ch, jexer.bits.CellAttributes attr) {
        // Get screen from desktop
        def screen = application.getScreen()
        if (screen != null) {
            if (x >= 0 && x < screen.getWidth() &&
                y >= 0 && y < screen.getHeight()) {
                screen.putChar(x, y, ch, attr)
            }
        }
    }
}
