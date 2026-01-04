package tui.jexer.widgets

import jexer.TWidget
import jexer.TText
import jexer.TApplication
import jexer.bits.CellAttributes
import jexer.bits.Color
import tui.JexerTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Paths as NioPaths

/**
 * Scrollable activity log widget with message formatting.
 * Supports timestamps, different message types, and scroll tracking.
 */
class JexerActivityLog extends TText {

    private StringBuilder content = new StringBuilder()
    private String statusLine = null
    private boolean timestampsEnabled = true
    private Closure onScrollPositionChanged = null
    private int scrollPosition = 0

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern('HH:mm:ss')
    private static final int MAX_DISPLAY_LINES = 1000

    JexerActivityLog(TWidget parent, int width, int height) {
        super(parent, '', 0, 0, width, height)
    }

    /**
     * Get current timestamp string.
     */
    private String timestamp() {
        if (timestampsEnabled) {
            return "[${LocalDateTime.now().format(TIME_FORMAT)}] "
        }
        return ''
    }

    /**
     * Set callback for scroll position changes.
     * Callback receives (currentLine, totalLines)
     */
    void setOnScrollPositionChanged(Closure callback) {
        this.onScrollPositionChanged = callback
    }

    /**
     * Enable or disable timestamps on log entries.
     */
    void setTimestampsEnabled(boolean enabled) {
        this.timestampsEnabled = enabled
    }

    /**
     * Get current scroll position [currentLine, totalLines].
     */
    int[] getScrollPosition() {
        String text = getText()
        if (!text) {
            return [0, 0]
        }
        int totalLines = text.split('\n').size()
        return [scrollPosition, totalLines]
    }

    /**
     * Update display with wrapped text.
     */
    private void updateDisplay() {
        String currentText = getText()
        int maxWidth = getWidth()

        if (currentText && maxWidth > 0) {
            // Simple word wrapping for display
            String wrappedText = wrapText(content.toString(), maxWidth)
            setText(wrappedText)
        }

        // Scroll to bottom by default
        scrollToBottom()
    }

    /**
     * Scroll text widget to bottom.
     */
    void scrollToBottom() {
        String text = getText()
        if (!text) return

        int totalLines = text.split('\n').size()
        int visibleLines = getHeight()

        if (totalLines > visibleLines) {
            scrollPosition = totalLines - visibleLines
        } else {
            scrollPosition = 0
        }

        notifyScrollPositionChanged()
    }

    /**
     * Notify scroll position callback.
     */
    private void notifyScrollPositionChanged() {
        if (onScrollPositionChanged != null) {
            int[] pos = getScrollPosition()
            onScrollPositionChanged.call(pos[0], pos[1])
        }
    }

    /**
     * Simple text wrapping.
     */
    private String wrapText(String text, int maxWidth) {
        if (maxWidth <= 0 || !text) {
            return text ?: ''
        }

        StringBuilder wrapped = new StringBuilder()
        String[] lines = text.split('\n')

        lines.each { line ->
            if (line.length() <= maxWidth) {
                wrapped.append(line).append('\n')
            } else {
                int start = 0
                while (start < line.length()) {
                    int end = Math.min(start + maxWidth, line.length())
                    wrapped.append(line.substring(start, end)).append('\n')
                    start = end
                }
            }
        }

        return wrapped.toString()
    }

    /**
     * Export log content to a file.
     */
    String exportLog(String filePath = null) {
        if (filePath == null) {
            def logDir = NioPaths.get(System.getProperty('user.home'), '.glm', 'logs')
            Files.createDirectories(logDir)
            def ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyyMMdd_HHmmss'))
            filePath = logDir.resolve("activity_${ts}.log").toString()
        }

        Files.writeString(NioPaths.get(filePath), content.toString())
        appendStatus("Log exported to: ${filePath}")
        Thread.start {
            Thread.sleep(2000)
            removeStatus()
        }

        return filePath
    }

    /**
     * Append welcome message.
     */
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

    /**
     * Append user message.
     */
    void appendUserMessage(String message) {
        synchronized (content) {
            content.append(timestamp())
            content.append("You> ${message}\n")
            content.append("\n")
        }
        updateDisplay()
    }

    /**
     * Append AI response.
     */
    void appendAIResponse(String response) {
        synchronized (content) {
            content.append("GLM> ${response}\n")
        }
        updateDisplay()
    }

    /**
     * Append status message (temporary).
     */
    void appendStatus(String status) {
        synchronized (content) {
            statusLine = status
            content.append("... ${status}\n")
        }
        updateDisplay()
    }

    /**
     * Remove temporary status message.
     */
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

    /**
     * Append tool execution message.
     */
    void appendToolExecution(String toolCall) {
        synchronized (content) {
            content.append("  → ${toolCall}\n")
        }
        updateDisplay()
    }

    /**
     * Append tool result message.
     */
    void appendToolResult(String result) {
        synchronized (content) {
            content.append("    ✓ ${result}\n")
        }
        updateDisplay()
    }

    /**
     * Append tool error message.
     */
    void appendToolError(String error) {
        synchronized (content) {
            content.append("    ✗ Error: ${error}\n")
        }
        updateDisplay()
    }

    /**
     * Append error message.
     */
    void appendError(String error) {
        synchronized (content) {
            content.append("❌ Error: ${error}\n")
            content.append("\n")
        }
        updateDisplay()
    }

    /**
     * Append warning message.
     */
    void appendWarning(String warning) {
        synchronized (content) {
            content.append("⚠️  ${warning}\n")
            content.append("\n")
        }
        updateDisplay()
    }

    /**
     * Append system info message.
     */
    void appendSystemMessage(String message) {
        synchronized (content) {
            content.append("ℹ️  ${message}\n")
        }
        updateDisplay()
    }

    /**
     * Append separator.
     */
    void appendSeparator() {
        synchronized (content) {
            content.append("\n")
        }
        updateDisplay()
    }

    /**
     * Clear all log content.
     */
    void clear() {
        synchronized (content) {
            content = new StringBuilder()
        }
        updateDisplay()
    }

}
