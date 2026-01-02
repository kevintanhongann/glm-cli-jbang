package tui

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalPosition

class CommandInputPanel {
    private TextBox inputBox
    private LanternaTUI tui
    private MultiWindowTextGUI textGUI
    private List<String> history = []
    private int historyIndex = -1
    private String currentInput = ""
    
    CommandInputPanel(MultiWindowTextGUI textGUI, LanternaTUI tui) {
        this.textGUI = textGUI
        this.tui = tui
        
        inputBox = new TextBox(
            new TerminalSize(80, 1),
            "",
            TextBox.Style.SINGLE_LINE
        )
        
        LanternaTheme.applyToTextBox(inputBox)
        
        setupKeyBindings()
    }
    
    TextBox getTextBox() {
        return inputBox
    }
    
    private void setupKeyBindings() {
        inputBox.setInputFilter((textBox, key) -> {
            if (key.getKeyType() == KeyType.Enter) {
                String input = inputBox.getText().trim()
                if (!input.isEmpty()) {
                    history.add(input)
                    historyIndex = history.size()
                    inputBox.setText("")
                    tui.processUserInput(input)
                }
                return false
            } else if (key.getKeyType() == KeyType.ArrowUp) {
                navigateHistory(-1)
                return false
            } else if (key.getKeyType() == KeyType.ArrowDown) {
                navigateHistory(1)
                return false
            } else if (key.getKeyType() == KeyType.Escape) {
                inputBox.setText("")
                return false
            }
            return true
        })
    }
    
    private void navigateHistory(int direction) {
        if (history.isEmpty()) {
            return
        }
        
        if (direction < 0) {
            if (historyIndex > 0) {
                historyIndex--
            }
        } else {
            if (historyIndex < history.size()) {
                historyIndex++
            }
        }
        
        if (historyIndex >= 0 && historyIndex < history.size()) {
            inputBox.setText(history.get(historyIndex))
        } else {
            inputBox.setText("")
        }
    }
    
    void setText(String text) {
        inputBox.setText(text)
    }
    
    void clear() {
        inputBox.setText("")
    }
}
