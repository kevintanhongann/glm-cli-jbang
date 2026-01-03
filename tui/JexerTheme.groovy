package tui

import jexer.TApplication
import jexer.bits.CellAttributes
import jexer.bits.Color
import jexer.bits.ColorTheme

/**
 * Jexer theme mapping from Lanterna theme colors.
 * Provides OpenCode-style dark theme for Jexer TUI.
 */
class JexerTheme {

    /**
     * Apply dark OpenCode-style theme to Jexer application.
     */
    static void applyDarkTheme(TApplication app) {
        ColorTheme theme = app.getTheme()

        // Helper to safely set colors
        def setColor = { String key, Color fg, Color bg, boolean bold = false ->
            try {
                CellAttributes attr = theme.getColor(key)
                if (attr != null) {
                    attr.setForeColor(fg)
                    attr.setBackColor(bg)
                    if (bold) attr.setBold(true)
                }
            } catch (Exception e) {
            }
        }

        // Desktop/background
        setColor('tdesktop.background', Color.WHITE, Color.BLACK)

        // Window colors
        setColor('twindow.border', Color.CYAN, Color.BLACK)
        setColor('twindow.background', Color.WHITE, Color.BLACK)
        setColor('twindow.border.inactive', Color.WHITE, Color.BLACK)
        setColor('twindow.border.modal', Color.CYAN, Color.BLACK)
        setColor('twindow.border.modal.inactive', Color.WHITE, Color.BLACK)

        // Text and labels
        setColor('ttext', Color.WHITE, Color.BLACK)
        setColor('tlabel', Color.CYAN, Color.BLACK, true)

        // Fields
        setColor('tfield.active', Color.WHITE, Color.BLACK)
        setColor('tfield.inactive', Color.WHITE, Color.BLACK)

        // Buttons
        setColor('tbutton.inactive', Color.WHITE, Color.BLACK)
        setColor('tbutton.active', Color.BLACK, Color.CYAN)
        setColor('tbutton.disabled', Color.WHITE, Color.BLACK)

        // Menus
        setColor('tmenu', Color.WHITE, Color.BLACK)
        setColor('tmenu.highlighted', Color.BLACK, Color.CYAN)
    }

    /**
     * Get background color - dark blue-black.
     */
    static Color getBackgroundColor() {
        return Color.BLACK
    }

    /**
     * Get foreground color.
     */
    static Color getForegroundColor() {
        return Color.WHITE
    }

    /**
     * Get text color.
     */
    static Color getTextColor() {
        return Color.WHITE
    }

    /**
     * Get accent color (cyan).
     */
    static Color getAccentColor() {
        return Color.CYAN
    }

    /**
     * Get error color (red).
     */
    static Color getErrorColor() {
        return Color.RED
    }

    /**
     * Get warning color (yellow).
     */
    static Color getWarningColor() {
        return Color.YELLOW
    }

    /**
     * Get success color (green).
     */
    static Color getSuccessColor() {
        return Color.GREEN
    }

    /**
     * Get user message color (green).
     */
    static Color getUserMessageColor() {
        return Color.GREEN
    }

    /**
     * Get AI response color (white).
     */
    static Color getAIResponseColor() {
        return Color.WHITE
    }

    /**
     * Get tool execution color (magenta).
     */
    static Color getToolColor() {
        return Color.MAGENTA
    }

    /**
     * Get agent BUILD color (cyan).
     */
    static Color getAgentBuildColor() {
        return Color.CYAN
    }

    /**
     * Get agent PLAN color (yellow).
     */
    static Color getAgentPlanColor() {
        return Color.YELLOW
    }

    /**
     * Get muted text color (gray).
     */
    static Color getTextMutedColor() {
        return Color.GRAY
    }

    /**
     * Get sidebar background color (dark blue-gray).
     */
    static Color getSidebarBackgroundColor() {
        return new Color(1, 1, 2)
    }

    /**
     * Get sidebar border color.
     */
    static Color getSidebarBorderColor() {
        return new Color(2, 2, 3)
    }

    /**
     * Get sidebar header color.
     */
    static Color getSidebarHeaderColor() {
        return new Color(3, 3, 4)
    }

    /**
     * Get sidebar tree color (for │, └, ├).
     */
    static Color getSidebarTreeColor() {
        return new Color(2, 2, 3)
    }

    /**
     * Get diff added color (green).
     */
    static Color getDiffAddedColor() {
        return Color.GREEN
    }

    /**
     * Get diff removed color (red).
     */
    static Color getDiffRemovedColor() {
        return Color.RED
    }

    /**
     * Create CellAttributes with foreground and background colors.
     */
    static CellAttributes createAttributes(Color fg, Color bg) {
        CellAttributes attr = new CellAttributes()
        attr.setForeColor(fg)
        attr.setBackColor(bg)
        return attr
    }

    /**
     * Create CellAttributes with foreground, background, and bold.
     */
    static CellAttributes createAttributes(Color fg, Color bg, boolean bold) {
        CellAttributes attr = new CellAttributes()
        attr.setForeColor(fg)
        attr.setBackColor(bg)
        attr.setBold(bold)
        return attr
    }
}
