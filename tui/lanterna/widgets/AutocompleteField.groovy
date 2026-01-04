package tui.lanterna.widgets

import jexer.TField
import jexer.TWidget
import jexer.TAction
import jexer.event.TKeypressEvent
import static jexer.TKeypress.*
import tui.shared.AutocompleteItem
import tui.shared.FileProvider
import tui.shared.CommandProvider

/**
 * Custom text field with @ file mentions and / slash command autocomplete.
 * Inspired by SST OpenCode's autocomplete implementation.
 *
 * Trigger behaviors:
 * - '@' triggers file/agent mention autocomplete (anywhere in text)
 * - '/' triggers command autocomplete (only at start of input)
 */
class AutocompleteField extends TField {

    private AutocompletePopup popup
    private AutocompleteCallback callback
    private int triggerPosition = -1
    private String triggerType = null  // '@' or '/'

    interface AutocompleteCallback {

        void onSubmit(String text, List<String> mentions)

    }

    AutocompleteField(TWidget parent, int x, int y, int width, boolean fixed,
                      String text = '', TAction enterAction = null, TAction updateAction = null) {
        super(parent, x, y, width, fixed, text, enterAction, updateAction)
                      }

    void setAutocompleteCallback(AutocompleteCallback callback) {
        this.callback = callback
    }

    void setPopup(AutocompletePopup popup) {
        this.popup = popup
    }

    @Override
    void onKeypress(TKeypressEvent keypress) {
        def key = keypress.getKey()

        // Handle popup navigation when visible
        if (popup?.isPopupVisible()) {
            if (keypress.equals(kbUp)) {
                popup.selectPrevious()
                return
            }
            if (keypress.equals(kbDown)) {
                popup.selectNext()
                return
            }
            if (keypress.equals(kbEnter) || keypress.equals(kbTab)) {
                String selected = popup.getSelectedItem()
                if (selected) {
                    insertAutocompleteSelection(selected)
                    hideAutocomplete()
                    return
                }
            }
            if (keypress.equals(kbEsc)) {
                hideAutocomplete()
                return
            }
        }

        // Check for @ mention trigger
        if (!key.isFnKey() && !key.isAlt() && !key.isCtrl() && key.getChar() == (char)'@') {
            // @ can trigger mid-text if preceded by space or at start
            String currentText = getText()
            int cursorPos = getCursor()
            boolean canTrigger = cursorPos == 0 ||
                                 currentText.isEmpty() ||
                                 cursorPos > currentText.length() ||
                                 Character.isWhitespace(currentText.charAt(Math.max(0, cursorPos - 1)))

            if (canTrigger) {
                super.onKeypress(keypress)
                triggerPosition = getCursor() - 1
                triggerType = '@'
                showMentionAutocomplete()
                return
            }
        }

        // Check for / slash command trigger (only at start)
        if (!key.isFnKey() && !key.isAlt() && !key.isCtrl() && key.getChar() == (char)'/') {
            if (getCursor() == 0 || getText().isEmpty()) {
                super.onKeypress(keypress)
                triggerPosition = 0
                triggerType = '/'
                showCommandAutocomplete()
                return
            }
        }

        // Regular typing - filter popup if open
        if (popup?.isPopupVisible() && !key.isFnKey() && !key.isAlt() && !key.isCtrl()) {
            super.onKeypress(keypress)
            updateFilter()
            return
        }

        // Backspace handling - might close popup
        if (keypress.equals(kbBackspace) || keypress.equals(kbBackspaceDel)) {
            super.onKeypress(keypress)
            if (popup?.isPopupVisible()) {
                if (getCursor() <= triggerPosition) {
                    hideAutocomplete()
                } else {
                    updateFilter()
                }
            }
            return
        }

        // Space handling - close popup
        if (!key.isFnKey() && !key.isAlt() && !key.isCtrl() && key.getChar() == (char)' ') {
            if (popup?.isPopupVisible()) {
                hideAutocomplete()
            }
            super.onKeypress(keypress)
            return
        }

        // Default handling
        super.onKeypress(keypress)
    }

    private void showMentionAutocomplete() {
        if (popup == null) return

        List<AutocompleteItem> items = []

        // Add file suggestions
        items.addAll(FileProvider.getFiles(getWorkingDirectory(), ''))

        popup.loadItems(items, '@')
        popup.setPopupVisible(true)
    }

    private void showCommandAutocomplete() {
        if (popup == null) return

        List<AutocompleteItem> items = CommandProvider.getCommands()
        popup.loadItems(items, '/')
        popup.setPopupVisible(true)
    }

    private void updateFilter() {
        if (popup == null || triggerPosition < 0) return

        String currentText = getText()
        int cursorPos = getCursor()

        if (cursorPos <= triggerPosition || cursorPos > currentText.length()) {
            hideAutocomplete()
            return
        }

        String filter = currentText.substring(triggerPosition + 1, cursorPos)

        // Check for space in filter (invalidates autocomplete)
        if (filter.contains(' ')) {
            hideAutocomplete()
            return
        }

        popup.filter(filter)
    }

    private void insertAutocompleteSelection(String selected) {
        if (triggerPosition < 0) return

        String currentText = getText()
        int cursorPos = getCursor()

        String before = triggerPosition > 0 ? currentText.substring(0, triggerPosition) : ''
        String after = cursorPos < currentText.length() ? currentText.substring(cursorPos) : ''

        // For @ mentions, include the @ symbol in the result
        // For / commands, include the / symbol
        String insertion = triggerType + selected + ' '

        String newText = before + insertion + after
        setText(newText)

        // Position cursor after the insertion
        int newPos = before.length() + insertion.length()
    // Note: TField doesn't expose setCursor, so we work with the text
    }

    private void hideAutocomplete() {
        if (popup != null) {
            popup.setPopupVisible(false)
        }
        triggerPosition = -1
        triggerType = null
    }

    private int getCursor() {
        // TField uses 'position' field (protected)
        try {
            def field = TField.class.getDeclaredField('position')
            field.setAccessible(true)
            return field.get(this) as int
        } catch (Exception e) {
            return 0
        }
    }

    private String getWorkingDirectory() {
        return System.getProperty('user.dir')
    }

    List<String> extractMentions() {
        List<String> mentions = []
        String text = getText()

        // Find all @mentions
        def matcher = text =~ /@([\w.\/\-]+)/
        while (matcher.find()) {
            mentions << matcher.group(1)
        }

        return mentions
    }

}
