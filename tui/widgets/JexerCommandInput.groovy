package tui.widgets

import jexer.TField
import jexer.TApplication
import jexer.TWindow
import jexer.event.TKeypressEvent
import tui.CommandProvider
import tui.FileProvider
import tui.AutocompleteItem
import jexer.bits.CellAttributes
import jexer.bits.Color
import tui.JexerTheme
import static jexer.TKeypress.*

/**
 * Enhanced input field with autocomplete for commands and file mentions.
 * Supports @file mentions and /command triggers.
 */
class JexerCommandInput extends TField {

    private JexerAutocompletePopup autocompletePopup
    private List<String> history = []
    private int historyIndex = -1
    private String currentInput = ''
    private int triggerPosition = -1
    private String triggerType = null
    private String currentCwd
    private Closure onSubmitCallback = null
    private TWindow parentWindow

    JexerCommandInput(TWindow parent, int width, String cwd = null) {
        super(width, '', false)
        this.parentWindow = parent
        this.currentCwd = cwd ?: System.getProperty('user.dir')
    }

    /**
     * Set autocomplete popup.
     */
    void setAutocompletePopup(JexerAutocompletePopup popup) {
        this.autocompletePopup = popup
    }

    /**
     * Set callback for input submission.
     */
    void setOnSubmit(Closure callback) {
        this.onSubmitCallback = callback
    }

    /**
     * Handle key press events for autocomplete and history.
     */
    @Override
    public void onKeypress(TKeypressEvent keypress) {
        // Let autocomplete handle keys when visible
        if (autocompletePopup != null && autocompletePopup.isPopupVisible()) {
            if (handleAutocompleteKeys(keypress)) {
                return
            }
        }

        // Check for trigger characters
        char ch = keypress.getKey().getChar()

        // @ trigger for file mentions
        if (ch == '@' && handleTrigger('@'.charAt(0))) {
            return
        }

        // / trigger for commands (only at start)
        if (ch == '/' && getText().isEmpty() && handleTrigger('/'.charAt(0))) {
            return
        }

        // Space closes autocomplete
        if (ch == ' ' && autocompletePopup != null) {
            autocompletePopup.showPopup(false)
        }

        // Backspace may close autocomplete
        if (keypress.getKey() == kbBackspace) {
            handleBackspace()
        }

        // Enter submits input (when popup not visible)
        if (keypress.getKey() == kbEnter) {
            if (autocompletePopup == null || !autocompletePopup.isPopupVisible()) {
                submitInput()
                return
            }
        }

        // Arrow keys for history navigation (when popup not visible)
        if (autocompletePopup == null || !autocompletePopup.isPopupVisible()) {
            if (keypress.getKey() == kbUp) {
                navigateHistory(-1)
                return
            }
            if (keypress.getKey() == kbDown) {
                navigateHistory(1)
                return
            }
        }

        // Escape clears input
        if (keypress.getKey() == kbEsc) {
            setText('')
            if (autocompletePopup != null) {
                autocompletePopup.showPopup(false)
            }
            return
        }

        // Let parent handle normal text input
        super.onKeypress(keypress)
    }

    /**
     * Handle autocomplete popup navigation.
     */
    private boolean handleAutocompleteKeys(TKeypressEvent keypress) {
        if (autocompletePopup == null) {
            return false
        }

        switch (keypress.getKey()) {
            case kbUp:
                autocompletePopup.selectPrevious()
                return true

            case kbDown:
                autocompletePopup.selectNext()
                return true

            case kbEnter:
                AutocompleteItem selected = autocompletePopup.getSelectedItem()
                if (selected != null) {
                    insertAutocompleteSelection(selected)
                    submitInput()
                }
                return true

            case kbTab:
                AutocompleteItem selected = autocompletePopup.getSelectedItem()
                if (selected != null) {
                    insertAutocompleteSelection(selected)
                }
                autocompletePopup.showPopup(false)
                return true

            case kbEsc:
                autocompletePopup.showPopup(false)
                return true

            default:
                // Filter on character input
                char ch = keypress.getKey().getChar()
                if (ch != 0) {
                    updateFilter(ch)
                }
                return false
        }
    }

    /**
     * Handle @ or / trigger.
     */
    private boolean handleTrigger(char triggerChar) {
        String currentText = getText()
        int cursorPos = getCursorPosition()

        boolean canTrigger = false
        if (triggerChar == '@') {
            // @ can trigger after space or at start
            canTrigger = cursorPos == 0 ||
                         currentText.isEmpty() ||
                         Character.isWhitespace(currentText.charAt(Math.max(0, cursorPos - 1)))
        } else if (triggerChar == '/') {
            // / only triggers at start
            canTrigger = cursorPos == 0 || currentText.isEmpty()
        }

        if (canTrigger && autocompletePopup != null) {
            triggerPosition = cursorPos
            triggerType = triggerChar.toString()

            if (triggerChar == '@') {
                showMentionAutocomplete()
            } else {
                showCommandAutocomplete()
            }

            return true
        }

        return false
    }

    /**
     * Handle backspace - may close popup.
     */
    private void handleBackspace() {
        if (autocompletePopup != null && autocompletePopup.isPopupVisible()) {
            int cursorPos = getCursorPosition()
            if (cursorPos <= triggerPosition) {
                autocompletePopup.showPopup(false)
            } else {
                updateFilter()
            }
        }
    }

    /**
     * Show file mention autocomplete.
     */
    private void showMentionAutocomplete() {
        if (autocompletePopup == null) return

        List<AutocompleteItem> items = FileProvider.getFiles(currentCwd, '')
        autocompletePopup.loadItems(items, '@')

        int popupX = getAbsoluteX()
        int popupY = getAbsoluteY() + 10
        autocompletePopup.showPopup(true, popupY, popupX)
    }

    /**
     * Show command autocomplete.
     */
    private void showCommandAutocomplete() {
        if (autocompletePopup == null) return

        List<AutocompleteItem> items = CommandProvider.getCommands()
        autocompletePopup.loadItems(items, '/')

        int popupX = getAbsoluteX()
        int popupY = getAbsoluteY() + 10
        autocompletePopup.showPopup(true, popupY, popupX)
    }

    /**
     * Update autocomplete filter based on new character.
     */
    private void updateFilter(char newChar) {
        if (triggerPosition < 0 || autocompletePopup == null) return

        String currentText = getText()
        int cursorPos = getCursorPosition()

        if (cursorPos <= triggerPosition || cursorPos > currentText.length()) {
            autocompletePopup.showPopup(false)
            return
        }

        String filter = currentText.substring(triggerPosition + 1, cursorPos)

        // Check for space (invalidates autocomplete)
        if (filter.contains(' ')) {
            autocompletePopup.showPopup(false)
            return
        }

        autocompletePopup.filter(filter)

        if (autocompletePopup.isEmpty()) {
            autocompletePopup.showPopup(false)
        }
    }

    /**
     * Insert selected autocomplete item.
     */
    private void insertAutocompleteSelection(AutocompleteItem item) {
        if (triggerPosition < 0) return

        String currentText = getText()
        int cursorPos = getCursorPosition()

        String before = triggerPosition > 0 ? currentText.substring(0, triggerPosition) : ''
        String after = cursorPos < currentText.length() ? currentText.substring(cursorPos) : ''

        String insertion = triggerType + item.value + ' '
        String newText = before + insertion + after

        setText(newText)

        // Reset trigger state
        triggerPosition = -1
        triggerType = null

        if (autocompletePopup != null) {
            autocompletePopup.showPopup(false)
        }
    }

    /**
     * Navigate command history.
     */
    private void navigateHistory(int direction) {
        if (history.isEmpty()) {
            return
        }

        if (direction < 0) {
            // Previous
            if (historyIndex > 0) {
                historyIndex--
            } else if (historyIndex == -1 && history.size() > 0) {
                historyIndex = history.size() - 1
            }
        } else {
            // Next
            if (historyIndex < history.size() - 1) {
                historyIndex++
            } else {
                historyIndex = -1
            }
        }

        if (historyIndex >= 0 && historyIndex < history.size()) {
            setText(history.get(historyIndex))
        } else {
            setText('')
        }
    }

    /**
     * Submit current input.
     */
    private void submitInput() {
        String input = getText().trim()

        if (!input.isEmpty()) {
            // Add to history
            history.add(input)
            historyIndex = history.size()

            // Clear input
            setText('')

            // Reset trigger state
            triggerPosition = -1
            triggerType = null

            // Hide popup
            if (autocompletePopup != null) {
                autocompletePopup.showPopup(false)
            }

            // Call callback
            if (onSubmitCallback != null) {
                onSubmitCallback.call(input, extractMentions())
            }
        }
    }

    /**
     * Extract @mentions from input.
     */
    List<String> extractMentions() {
        List<String> mentions = []
        String text = getText()

        // Find all @mentions
        def matcher = text =~ /@([\w.\/\-]+(?:#L?\d+(?:-L?\d+)?)?)/
        while (matcher.find()) {
            mentions << matcher.group(1)
        }

        return mentions
    }

    /**
     * Clear input.
     */
    void clear() {
        setText('')
        triggerPosition = -1
        triggerType = null
        if (autocompletePopup != null) {
            autocompletePopup.showPopup(false)
        }
    }

}
