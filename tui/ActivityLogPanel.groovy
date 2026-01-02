package tui

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalPosition

class ActivityLogPanel {
    private TextBox textBox
    private StringBuilder content = new StringBuilder()
    private MultiWindowTextGUI textGUI
    private String statusLine = null
    
    ActivityLogPanel(MultiWindowTextGUI textGUI) {
        this.textGUI = textGUI
        
        textBox = new TextBox(
            new TerminalSize(80, 20),
            "",
            TextBox.Style.MULTI_LINE
        )
        textBox.setReadOnly(true)
        textBox.setCaretWarp(true)
        
        LanternaTheme.applyToTextBox(textBox)
    }
    
    TextBox getTextBox() {
        return textBox
    }
    
    void updateDisplay() {
        synchronized (content) {
            textBox.setText(content.toString())
            try {
                String[] lines = content.toString().split('\n')
                if (lines.length > 0) {
                    textBox.setCaretPosition(new TerminalPosition(0, Math.max(0, lines.length - 1)))
                }
            } catch (Exception e) {
            }
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
