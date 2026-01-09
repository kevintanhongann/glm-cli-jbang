package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalPosition
import tui.LanternaTUI
import tui.LanternaTheme
import tui.shared.AutocompleteItem
import tui.shared.FileProvider
import tui.shared.CommandProvider

class CommandInputPanel {

    private TextBox inputBox
    private LanternaTUI tui
    private MultiWindowTextGUI textGUI
    private LanternaAutocompletePopup autocompletePopup
    private List<String> history = []
    private int historyIndex = -1
    private String currentInput = ''
    private int triggerPosition = -1
    private String triggerType = null
    private String currentCwd

    CommandInputPanel(MultiWindowTextGUI textGUI, LanternaTUI tui, String cwd = null) {
        this.textGUI = textGUI
        this.tui = tui
        this.currentCwd = cwd ?: System.getProperty('user.dir')

        inputBox = new TextBox(
            new TerminalSize(80, 1),
            '',
            TextBox.Style.SINGLE_LINE
        )

        autocompletePopup = new LanternaAutocompletePopup(textGUI)

        LanternaTheme.applyToTextBox(inputBox)

        setupAutocompleteCallbacks()
        setupKeyBindings()
    }

    TextBox getTextBox() {
        return inputBox
    }

    private void setupAutocompleteCallbacks() {
        autocompletePopup.setOnSelection({
            AutocompleteItem selected = autocompletePopup.getSelectedItem()
            if (selected) {
                insertAutocompleteSelection(selected)
            }
        } as Runnable)
    }

    private void setupKeyBindings() {
        inputBox.setInputFilter((textBox, key) -> {
            KeyType keyType = key.getKeyType()

            // When popup is visible, let it handle most keys
            if (autocompletePopup.isVisible()) {
                if (keyType == KeyType.ArrowUp || keyType == KeyType.ArrowDown) {
                    return false  // Let popup handle navigation
                }
                if (keyType == KeyType.Escape) {
                    autocompletePopup.hide()
                    return false
                }
                if (keyType == KeyType.Enter) {
                    AutocompleteItem selected = autocompletePopup.getSelectedItem()
                    if (selected) {
                        // Build the completed text directly
                        String currentText = inputBox.getText()
                        int cursorPos = inputBox.getCaretPosition().getColumn()
                        String before = triggerPosition > 0 ? currentText.substring(0, triggerPosition) : ''
                        String after = cursorPos < currentText.length() ? currentText.substring(cursorPos) : ''
                        String completedText = (before + triggerType + selected.value + ' ' + after).trim()

                        // Set and submit directly
                        inputBox.setText(completedText)
                        submitInputDirect(completedText)
                    } else {
                        autocompletePopup.hide()
                    }
                    return false
                }
                if (keyType == KeyType.Tab) {
                    AutocompleteItem selected = autocompletePopup.getSelectedItem()
                    if (selected) {
                        insertAutocompleteSelection(selected)
                    }
                    autocompletePopup.hide()
                    return false
                }
                if (keyType == KeyType.Character) {
                    // Pre-calculate filter by including the new character
                    String newChar = key.getCharacter() as String
                    String currentText = inputBox.getText()
                    int cursorPos = inputBox.getCaretPosition().getColumn()

                    // Insert the new character at cursor position
                    String newText = new StringBuilder(currentText)
                        .insert(cursorPos, newChar)
                        .toString()

                    // Calculate filter based on new text
                    if (triggerPosition >= 0) {
                        int newCursorPos = cursorPos + 1

                        if (newCursorPos <= triggerPosition || newCursorPos > newText.length()) {
                            autocompletePopup.hide()
                        } else {
                            String filter = newText.substring(triggerPosition + 1, newCursorPos)

                            if (filter.contains(' ')) {
                                autocompletePopup.hide()
                            } else {
                                autocompletePopup.filter(filter)

                                if (autocompletePopup.isEmpty()) {
                                    autocompletePopup.hide()
                                }
                            }
                        }
                    }

                    return true
                }
                if (keyType == KeyType.Backspace) {
                    // Handle backspace: might close popup
                    return handleBackspace()
                }
            }

            // Regular Enter - submit input (only when popup not visible)
            if (keyType == KeyType.Enter) {
                submitInput()
                return false
            }

            // Tab to switch agent (only when autocomplete not visible)
            if (keyType == KeyType.Tab && !autocompletePopup.isVisible()) {
                if (key.isShiftDown()) {
                    tui.cycleAgent(-1)  // Reverse cycling
                } else {
                    tui.cycleAgent(1)   // Forward cycling
                }
                return false
            }

            // Ctrl+M to show model selection dialog
            if (!autocompletePopup.isVisible() &&
                key.isCtrlDown() && keyType == KeyType.Character && key.getCharacter() == 'm') {
                // Defer dialog creation to avoid blocking in input filter context
                Thread.start {
                    tui.showModelSelectionDialog()
                }
                return false
                }

            // Check for @ mention trigger
            if (!key.isAltDown() && !key.isCtrlDown() &&
                keyType == KeyType.Character && key.getCharacter() == '@') {
                return handleTrigger('@'.charAt(0))
                }

            // Check for / slash command trigger (only at start)
            if (!key.isAltDown() && !key.isCtrlDown() &&
                keyType == KeyType.Character && key.getCharacter() == '/') {
                return handleTrigger('/'.charAt(0))
                }

            // Backspace handling - might close popup
            if (keyType == KeyType.Backspace) {
                return handleBackspace()
            }

            // Space handling - close popup
            if (!key.isAltDown() && !key.isCtrlDown() &&
                keyType == KeyType.Character && key.getCharacter() == ' ') {
                autocompletePopup.hide()
                return true
                }

            // History navigation when popup is not visible
            if (!autocompletePopup.isVisible()) {
                if (keyType == KeyType.ArrowUp) {
                    navigateHistory(-1)
                    return false
                }
                if (keyType == KeyType.ArrowDown) {
                    navigateHistory(1)
                    return false
                }
            }

            // Escape - clear input
            if (keyType == KeyType.Escape) {
                inputBox.setText('')
                autocompletePopup.hide()
                return false
            }

            // Ctrl+C - clear input or exit
            if (!autocompletePopup.isVisible() &&
                key.isCtrlDown() && keyType == KeyType.Character && key.getCharacter() == 'c') {
                String currentText = inputBox.getText()
                if (!currentText.trim().isEmpty()) {
                    inputBox.setText('')
                    autocompletePopup.hide()
                } else {
                    tui.exit()
                }
                return false
            }

            return true
        })
    }

    private boolean handleTrigger(char triggerChar) {
        String currentText = inputBox.getText()
        int cursorPos = inputBox.getCaretPosition().getColumn()

        // Check if trigger is allowed
        boolean canTrigger = false
        if (triggerChar == '@') {
            // @ can trigger after space or at start
            canTrigger = cursorPos == 0 ||
                         currentText.isEmpty() ||
                         cursorPos > currentText.length() ||
                         Character.isWhitespace(currentText.charAt(Math.max(0, cursorPos - 1)))
        } else if (triggerChar == '/') {
            // / only triggers at start of input
            canTrigger = cursorPos == 0 || currentText.isEmpty()
        }

        if (canTrigger) {
            triggerPosition = cursorPos
            triggerType = triggerChar.toString()

            if (triggerChar == '@') {
                showMentionAutocomplete()
            } else {
                showCommandAutocomplete()
            }

            return true
        }

        return true
    }

    private boolean handleBackspace() {
        if (autocompletePopup.isVisible()) {
            int cursorPos = inputBox.getCaretPosition().getColumn()

            if (cursorPos <= triggerPosition) {
                autocompletePopup.hide()
            } else {
                updateFilter()
            }
        }

        return true
    }

    private void showMentionAutocomplete() {
        List<AutocompleteItem> items = FileProvider.getFiles(currentCwd, '')
        autocompletePopup.loadItems(items, '@')

        TerminalPosition popupPos = calculatePopupPosition()
        autocompletePopup.setVisible(true, popupPos.getRow(), popupPos.getColumn())
    }

    private void showCommandAutocomplete() {
        List<AutocompleteItem> items = CommandProvider.getCommands()
        autocompletePopup.loadItems(items, '/')

        TerminalPosition popupPos = calculatePopupPosition()
        autocompletePopup.setVisible(true, popupPos.getRow(), popupPos.getColumn())
    }

    private void updateFilter() {
        if (triggerPosition < 0) return

        String currentText = inputBox.getText()
        int cursorPos = inputBox.getCaretPosition().getColumn()

        if (cursorPos <= triggerPosition || cursorPos > currentText.length()) {
            autocompletePopup.hide()
            return
        }

        String filter = currentText.substring(triggerPosition + 1, cursorPos)

        // Check for space in filter (invalidates autocomplete)
        if (filter.contains(' ')) {
            autocompletePopup.hide()
            return
        }

        autocompletePopup.filter(filter)

        if (autocompletePopup.isEmpty()) {
            autocompletePopup.hide()
        }
    }

    private void insertAutocompleteSelection(AutocompleteItem item) {
        if (triggerPosition < 0) return

        String currentText = inputBox.getText()
        int cursorPos = inputBox.getCaretPosition().getColumn()

        String before = triggerPosition > 0 ? currentText.substring(0, triggerPosition) : ''
        String after = cursorPos < currentText.length() ? currentText.substring(cursorPos) : ''

        String insertion = triggerType + item.value + ' '

        String newText = before + insertion + after
        inputBox.setText(newText)

        // Try to position cursor after insertion
        try {
            int newPos = before.length() + insertion.length()
            inputBox.setCaretPosition(inputBox.getCaretPosition().withColumn(newPos))
        } catch (Exception e) {
        // Cursor positioning might not work, but text insertion succeeded
        }

        autocompletePopup.hide()
        triggerPosition = -1
        triggerType = null
    }

    private TerminalPosition calculatePopupPosition() {
        TerminalPosition inputPosition = inputBox.getPosition()
        TerminalSize inputSize = inputBox.getSize()

        int popupRow = inputPosition.getRow() + inputSize.getRows() + 1
        int popupCol = inputPosition.getColumn()

        return new TerminalPosition(popupCol, popupRow)
    }

    private void submitInput() {
        String input = inputBox.getText().trim()

        if (!input.isEmpty()) {
            history.add(input)
            historyIndex = history.size()
            inputBox.setText('')
            autocompletePopup.hide()
            tui.processUserInput(input, extractMentions())
        }
    }

    private void submitInputDirect(String input) {
        if (!input.isEmpty()) {
            history.add(input)
            historyIndex = history.size()
            inputBox.setText('')
            autocompletePopup.hide()
            tui.processUserInput(input, extractMentions())
        }
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
            inputBox.setText('')
        }
    }

    /**
     * Extract @ mentions from input text.
     * Supports line ranges: @file#L10-L50
     */
    List<String> extractMentions() {
        List<String> mentions = []
        String text = inputBox.getText()

        // Find all @mentions
        def matcher = text =~ /@([\w.\/\-]+(?:#L?\d+(?:-L?\d+)?)?)/
        while (matcher.find()) {
            mentions << matcher.group(1)
        }

        return mentions
    }

    void setText(String text) {
        inputBox.setText(text)
    }

    void clear() {
        inputBox.setText('')
        autocompletePopup.hide()
    }

}
