package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.input.MouseAction
import com.googlecode.lanterna.input.MouseActionType
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalTextUtils
import tui.LanternaTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Paths as NioPaths
import java.util.Timer

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
        int visibleRows = getSize().getRows()
        if (lineCount > 0) {
            int targetRow = Math.max(0, lineCount - visibleRows)
            getRenderer().setViewTopLeft(com.googlecode.lanterna.TerminalPosition.TOP_LEFT_CORNER.withRow(targetRow))
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

    private String wrapText(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return text
        }
        def lines = text.split('\n')
        def wrapped = TerminalTextUtils.getWordWrappedText(maxWidth, lines)
        return wrapped.join('\n')
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
        if (textGUI != null && textGUI.getGUIThread() != null) {
            textGUI.getGUIThread().invokeLater(() -> {
                synchronized (content) {
                    int maxWidth = textBox.getSize().getColumns()
                    String wrappedContent = wrapText(content.toString(), maxWidth)
                    textBox.setText(wrappedContent)
                    textBox.scrollToBottom()
                    textBox.invalidate()
                }
            })
        } else {
            synchronized (content) {
                int maxWidth = textBox.getSize().getColumns()
                String wrappedContent = wrapText(content.toString(), maxWidth)
                textBox.setText(wrappedContent)
                textBox.scrollToBottom()
                textBox.invalidate()
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

    void appendSystemMessage(String message) {
        synchronized (content) {
            content.append("ℹ️  ${message}\n")
        }
        updateDisplay()
    }

    void appendSeparator() {
        synchronized (content) {
            content.append("\n")
        }
        updateDisplay()
    }

    // Streaming support methods
    private boolean streamingMode = false
    private StringBuilder currentStream = new StringBuilder()
    private Timer updateTimer
    private boolean updatePending = false
    private boolean insideThinkTag = false
    private String partialTagBuffer = ''  // Buffer for partial tags across chunks
    private Timer animationTimer
    private int animationIndex = 0
    private static final String[] ANIMATION_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']
    private Closure onStreamingStateChanged = null

    /**
     * Set callback for streaming state changes.
     * Callback receives (boolean isStreaming, String indicator)
     * Use this to update UI elements like status bar or window title.
     */
    void setOnStreamingStateChanged(Closure callback) {
        this.onStreamingStateChanged = callback
    }

    private void startStreamingAnimation() {
        if (animationTimer != null) {
            animationTimer.cancel()
        }
        animationIndex = 0
        animationTimer = new Timer()
        animationTimer.scheduleAtFixedRate({
            if (streamingMode && onStreamingStateChanged != null) {
                animationIndex = (animationIndex + 1) % ANIMATION_FRAMES.length
                String indicator = ANIMATION_FRAMES[animationIndex]
                if (textGUI != null && textGUI.getGUIThread() != null) {
                    textGUI.getGUIThread().invokeLater {
                        onStreamingStateChanged.call(true, indicator)
                    }
                }
            }
        } as java.util.TimerTask, 0, 100)
    }

    private void stopStreamingAnimation() {
        if (animationTimer != null) {
            animationTimer.cancel()
            animationTimer = null
        }
        if (onStreamingStateChanged != null) {
            if (textGUI != null && textGUI.getGUIThread() != null) {
                textGUI.getGUIThread().invokeLater {
                    onStreamingStateChanged.call(false, '')
                }
            } else {
                onStreamingStateChanged.call(false, '')
            }
        }
    }

    void startStreamingResponse() {
        synchronized (content) {
            streamingMode = true
            currentStream = new StringBuilder()
            insideThinkTag = false
            content.append('GLM> ')
        }
        startStreamingAnimation()
        updateDisplay()
    }

    void appendStreamChunk(String chunk) {
        synchronized (content) {
            // Filter out <think> tags and their content
            String filtered = filterThinkingTags(chunk)
            if (filtered) {
                currentStream.append(filtered)
                content.append(filtered)
            }
        }
        debouncedUpdateDisplay()
    }

    /**
     * Filter out <think>...</think> tags from streamed content.
     * Handles partial tags across chunk boundaries and case variations.
     */
    private String filterThinkingTags(String chunk) {
        // Prepend any buffered content from previous chunk
        String input = partialTagBuffer + chunk
        partialTagBuffer = ''

        StringBuilder result = new StringBuilder()
        int i = 0

        while (i < input.length()) {
            if (insideThinkTag) {
                // Look for closing </think> (case-insensitive)
                int closeIdx = input.toLowerCase().indexOf('</think>', i)
                if (closeIdx >= 0) {
                    insideThinkTag = false
                    i = closeIdx + 8 // Skip past </think>
                } else {
                    // Check if we might have a partial closing tag at the end
                    // Buffer the last 8 chars in case it's a partial </think>
                    if (input.length() - i <= 8) {
                        partialTagBuffer = input.substring(i)
                    }
                    // Still inside think tag, skip rest of input
                    break
                }
            } else {
                // Look for opening <think> (case-insensitive)
                int openIdx = input.toLowerCase().indexOf('<think>', i)
                // Look for orphan closing </think> (case-insensitive) - sometimes model outputs closing without opening or we missed it
                int orphanCloseIdx = input.toLowerCase().indexOf('</think>', i)

                if (openIdx >= 0) {
                    // Check if we have an orphan closing tag BEFORE the opening tag
                    if (orphanCloseIdx >= 0 && orphanCloseIdx < openIdx) {
                         // Append content before the orphan closing tag
                        result.append(input.substring(i, orphanCloseIdx))
                        i = orphanCloseIdx + 8 // Skip past </think>
                        continue
                    }

                    // Append content before <think>
                    result.append(input.substring(i, openIdx))
                    insideThinkTag = true
                    i = openIdx + 7 // Skip past <think>
                } else if (orphanCloseIdx >= 0) {
                     // We found a closing tag but no opening tag - assume it's an orphan and strip it
                    result.append(input.substring(i, orphanCloseIdx))
                    i = orphanCloseIdx + 8 // Skip past </think>
                } else {
                    // Check if chunk ends with partial tag like '<', '<t', '<th', etc.
                    String remaining = input.substring(i)
                    String potentialPartial = getPotentialPartialTag(remaining, '<think>')
                    if (potentialPartial) {
                        // Append everything except the potential partial tag
                        result.append(remaining.substring(0, remaining.length() - potentialPartial.length()))
                        partialTagBuffer = potentialPartial
                    } else {
                        // No partial tag, append rest
                        result.append(remaining)
                    }
                    break
                }
            }
        }

        return result.toString()
    }

    /**
     * Check if the string ends with a partial match of the given tag.
     * Returns the partial match if found, null otherwise.
     */
    private String getPotentialPartialTag(String str, String tag) {
        String lowerStr = str.toLowerCase()
        String lowerTag = tag.toLowerCase()

        // Check for partial matches at the end of the string
        for (int len = 1; len < tag.length(); len++) {
            String partial = lowerTag.substring(0, len)
            if (lowerStr.endsWith(partial)) {
                return str.substring(str.length() - len)
            }
        }
        return null
    }

    void finishStreamingResponse() {
        stopStreamingAnimation()
        synchronized (content) {
            streamingMode = false
            insideThinkTag = false
            partialTagBuffer = ''  // Clear any remaining partial tag buffer
            content.append('\n')
        }
        updateDisplay()
    }

    private void debouncedUpdateDisplay() {
        if (updatePending) return

        updatePending = true
        if (updateTimer != null) {
            updateTimer.cancel()
        }
        updateTimer = new Timer()
        updateTimer.schedule({
            synchronized (content) {
                updateDisplayInternal()
                updatePending = false
            }
        } as java.util.TimerTask, 50) // 50ms debounce for performance
    }

    private void updateDisplayInternal() {
        if (textGUI != null && textGUI.getGUIThread() != null) {
            textGUI.getGUIThread().invokeLater(() -> {
                synchronized (content) {
                    int maxWidth = textBox.getSize().getColumns()
                    String wrappedContent = wrapText(content.toString(), maxWidth)
                    textBox.setText(wrappedContent)
                    textBox.scrollToBottom()
                    textBox.invalidate()
                }
            })
        } else {
            synchronized (content) {
                int maxWidth = textBox.getSize().getColumns()
                String wrappedContent = wrapText(content.toString(), maxWidth)
                textBox.setText(wrappedContent)
                textBox.scrollToBottom()
                textBox.invalidate()
            }
        }
    }

    void clear() {
        synchronized (content) {
            content = new StringBuilder()
            insideThinkTag = false
        }
        updateDisplay()
    }

}
