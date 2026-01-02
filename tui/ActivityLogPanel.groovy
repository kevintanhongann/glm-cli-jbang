package tui

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.input.MouseAction
import com.googlecode.lanterna.input.MouseActionType
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalPosition
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Paths as NioPaths

/**
 * Custom TextBox that extends standard TextBox with scroll callbacks and save support.
 * Uses standard setReadOnly() to control text editing while allowing navigation.
 */
class ScrollableTextBox extends TextBox {

    private Closure onScrollCallback = null
    private Closure onSaveCallback = null

    ScrollableTextBox(TerminalSize preferredSize, String initialContent, Style style) {
        super(preferredSize, initialContent, style)
    }

    void setOnScrollCallback(Closure callback) {
        this.onScrollCallback = callback
    }

    void setOnSaveCallback(Closure callback) {
        this.onSaveCallback = callback
    }

    private void notifyScroll() {
        if (onScrollCallback != null) {
            onScrollCallback.call()
        }
    }

    @Override
    synchronized Result handleKeyStroke(KeyStroke keyStroke) {
        KeyType keyType = keyStroke.getKeyType()

        // Handle Ctrl+S for save
        if (keyType == KeyType.Character && keyStroke.isCtrlDown()) {
            char c = keyStroke.getCharacter()
            if (c == 's' || c == 'S') {
                if (onSaveCallback != null) {
                    onSaveCallback.call()
                }
                return Result.HANDLED
            }
        }

        // Track scroll position changes for navigation keys
        switch (keyType) {
            case KeyType.Home:
                if (keyStroke.isCtrlDown()) {
                    notifyScroll()
                }
                break
            case KeyType.End:
                if (keyStroke.isCtrlDown()) {
                    notifyScroll()
                }
                break
            case KeyType.PageUp:
            case KeyType.PageDown:
            case KeyType.ArrowUp:
            case KeyType.ArrowDown:
                notifyScroll()
                break
        }

        // Let parent TextBox handle all other key strokes including mouse events
        Result result = super.handleKeyStroke(keyStroke)

        // Also notify after mouse events
        if (keyType == KeyType.MouseEvent) {
            notifyScroll()
        }

        return result
    }

    void scrollToBottom() {
        int lineCount = getLineCount()
        if (lineCount > 0) {
            setCaretPosition(0, lineCount - 1)
        }
    }

    /**
     * Get current scroll position info [currentLine, totalLines]
     */
    int[] getScrollPosition() {
        int lineCount = getLineCount()
        TerminalPosition viewTopLeft = getRenderer().getViewTopLeft()
        int currentLine = viewTopLeft.getRow() + 1
        return [currentLine, lineCount] as int[]
    }

}

class ActivityLogPanel {

    private ScrollableTextBox textBox
    private StringBuilder content = new StringBuilder()
    private MultiWindowTextGUI textGUI
    private String statusLine = null
    private boolean timestampsEnabled = true
    private Closure onScrollPositionChanged = null

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern('HH:mm:ss')

    ActivityLogPanel(MultiWindowTextGUI textGUI) {
        this.textGUI = textGUI

        textBox = new ScrollableTextBox(
            new TerminalSize(80, 20),
            '',
            TextBox.Style.MULTI_LINE
        )
        // Use read-only mode to allow navigation but block text editing
        textBox.setReadOnly(true)
        textBox.setCaretWarp(true)

        // Track scroll position for status bar display
        textBox.setOnScrollCallback {
            notifyScrollPositionChanged()
        }

        // Handle Ctrl+S to export log
        textBox.setOnSaveCallback {
            exportLog()
        }

        LanternaTheme.applyToTextBox(textBox)
    }

    private String timestamp() {
        if (timestampsEnabled) {
            return "[${LocalDateTime.now().format(TIME_FORMAT)}] "
        }
        return ''
    }

    private void notifyScrollPositionChanged() {
        if (onScrollPositionChanged != null) {
            int[] pos = textBox.getScrollPosition()
            onScrollPositionChanged.call(pos[0], pos[1])
        }
    }

    /**
     * Set callback for scroll position changes
     * Callback receives (currentLine, totalLines)
     */
    void setOnScrollPositionChanged(Closure callback) {
        this.onScrollPositionChanged = callback
    }

    /**
     * Enable or disable timestamps on log entries
     */
    void setTimestampsEnabled(boolean enabled) {
        this.timestampsEnabled = enabled
    }

    /**
     * Get current scroll position [currentLine, totalLines]
     */
    int[] getScrollPosition() {
        return textBox.getScrollPosition()
    }

    /**
     * Export log content to a file
     */
    String exportLog(String filePath = null) {
        if (filePath == null) {
            // Default to ~/.glm/logs/activity_TIMESTAMP.log
            def logDir = NioPaths.get(System.getProperty('user.home'), '.glm', 'logs')
            Files.createDirectories(logDir)
            def timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss'))
            filePath = logDir.resolve("activity_${timestamp}.log").toString()
        }

        synchronized (content) {
            Files.writeString(NioPaths.get(filePath), content.toString())
        }

        appendStatus("Log exported to: ${filePath}")
        Thread.start {
            Thread.sleep(2000)
            removeStatus()
        }

        return filePath
    }

    TextBox getTextBox() {
        return textBox
    }

    void updateDisplay() {
        synchronized (content) {
            textBox.setText(content.toString())

            // Always scroll to bottom when content is updated
            textBox.scrollToBottom()
        }

        if (textGUI != null && textGUI.getGUIThread() != null) {
            try {
                textGUI.getGUIThread().invokeLater(() -> {
                    textBox.invalidate()
                })
            } catch (Exception e) {
            }
        }
    }

    /**
     * Check if the activity log panel has focus
     */
    boolean isFocused() {
        return textBox.isFocused()
    }

    /**
     * Request focus for the activity log panel (for manual scrolling)
     */
    void takeFocus() {
        textBox.takeFocus()
    }

    void appendWelcomeMessage(String model) {
        synchronized (content) {
            content.append("╔═════════════════════════════════════════════════════════════════╗\n")
            content.append("║                    GLM CLI TUI                                    ║\n")
            content.append("╚═════════════════════════════════════════════════════════════════╝\n")
            content.append("\n")
            content.append("Model: ${model}\n")
            content.append("\n")
            content.append("Type your message below and press Enter to send.\n")
            content.append("Press Ctrl+C to exit.\n")
            content.append("\n")
            content.append("───────────────────────────────────────────────────────────────────\n")
            content.append("\n")
        }
        updateDisplay()
    }

    void appendUserMessage(String message) {
        synchronized (content) {
            content.append("You> ${message}\n")
            content.append("\n")
        }
        updateDisplay()
    }

    void appendAIResponse(String response) {
        synchronized (content) {
            content.append("GLM> ${response}\n")
        }
        updateDisplay()
    }

    void appendStatus(String status) {
        synchronized (content) {
            statusLine = status
            content.append("... ${status}\n")
        }
        updateDisplay()
    }

    void removeStatus() {
        if (statusLine != null) {
            synchronized (content) {
                String current = content.toString()
                int idx = current.lastIndexOf("... ${statusLine}")
                if (idx >= 0) {
                    String before = current.substring(0, idx)
                    String after = current.substring(idx + statusLine.length() + 5)
                    content = new StringBuilder(before)
                    content.append(after)
                }
                statusLine = null
            }
            updateDisplay()
        }
    }

    void appendToolExecution(String toolCall) {
        synchronized (content) {
            content.append("  → ${toolCall}\n")
        }
        updateDisplay()
    }

    void appendToolResult(String result) {
        synchronized (content) {
            content.append("    ✓ ${result}\n")
        }
        updateDisplay()
    }

    void appendToolError(String error) {
        synchronized (content) {
            content.append("    ✗ Error: ${error}\n")
        }
        updateDisplay()
    }

    void appendError(String error) {
        synchronized (content) {
            content.append("❌ Error: ${error}\n")
            content.append("\n")
        }
        updateDisplay()
    }

    void appendWarning(String warning) {
        synchronized (content) {
            content.append("⚠️  ${warning}\n")
            content.append("\n")
        }
        updateDisplay()
    }

    void appendSeparator() {
        synchronized (content) {
            content.append("\n")
        }
        updateDisplay()
    }

    void clear() {
        synchronized (content) {
            content = new StringBuilder()
        }
        updateDisplay()
    }

}
