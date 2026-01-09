package tui.lanterna.widgets

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.*
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean
import tui.shared.AutocompleteItem
import tui.shared.AutocompleteItemType

/**
 * Popup window for displaying autocomplete suggestions in Lanterna TUI.
 * Shows file/folder suggestions with icons and navigation support.
 * 
 * This implementation uses an overlay panel approach instead of a separate window
 * to avoid focus management issues common with multi-window setups in Lanterna.
 */
class LanternaAutocompletePopup {

    private MultiWindowTextGUI textGUI
    private BasicWindow popupWindow
    private Panel contentPanel
    private List<AutocompleteItem> allItems = []
    private List<AutocompleteItem> filteredItems = []
    private int selectedIndex = 0
    private boolean visible = false
    private Runnable onSelectionCallback = null
    private Runnable onHideCallback = null

    private static final int MAX_VISIBLE_ITEMS = 8
    private static final int POPUP_WIDTH = 50
    private static final int POPUP_HEIGHT = MAX_VISIBLE_ITEMS + 2  // +2 for border

    // Scroll offset for when there are more items than visible
    private int scrollOffset = 0

    LanternaAutocompletePopup(MultiWindowTextGUI textGUI) {
        this.textGUI = textGUI
        createPopupWindow()
    }

    private void createPopupWindow() {
        popupWindow = new BasicWindow()
        popupWindow.setHints(Arrays.asList(
            Window.Hint.NO_DECORATIONS,
            Window.Hint.FIXED_POSITION,
            Window.Hint.NO_FOCUS
        ))

        contentPanel = new Panel()
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        contentPanel.setPreferredSize(new TerminalSize(POPUP_WIDTH, MAX_VISIBLE_ITEMS))

        // Create a bordered panel to wrap content
        Panel borderedPanel = new Panel()
        borderedPanel.setLayoutManager(new BorderLayout())
        borderedPanel.addComponent(contentPanel.withBorder(Borders.singleLine(" Suggestions ")), BorderLayout.Location.CENTER)

        popupWindow.setComponent(borderedPanel)

        SimpleTheme popupTheme = SimpleTheme.makeTheme(
            true,                           // activeIsBold
            TextColor.ANSI.WHITE,           // baseForeground
            TextColor.ANSI.BLUE,            // baseBackground
            TextColor.ANSI.WHITE,           // editableForeground
            TextColor.ANSI.BLUE,            // editableBackground
            TextColor.ANSI.BLACK,           // selectedForeground
            TextColor.ANSI.CYAN,            // selectedBackground
            TextColor.ANSI.BLUE             // guiBackground
        )
        popupWindow.setTheme(popupTheme)
    }

    /**
     * Load items into the popup.
     */
    void loadItems(List<AutocompleteItem> items, String prefix = '') {
        this.allItems = items ?: []
        this.filteredItems = new ArrayList<>(allItems)
        this.selectedIndex = 0
        this.scrollOffset = 0
        updateDisplay()
    }

    /**
     * Set callback for when an item is selected.
     */
    void setOnSelection(Runnable callback) {
        this.onSelectionCallback = callback
    }

    /**
     * Set callback for when popup is hidden.
     */
    void setOnHide(Runnable callback) {
        this.onHideCallback = callback
    }

    /**
     * Filter items based on query.
     */
    void filter(String query) {
        if (query == null || query.isEmpty()) {
            filteredItems = new ArrayList<>(allItems)
        } else {
            String lowerQuery = query.toLowerCase()
            filteredItems = allItems.findAll { item ->
                item.label.toLowerCase().contains(lowerQuery) ||
                item.value.toLowerCase().contains(lowerQuery)
            }
        }

        // Reset selection and scroll when filtering
        selectedIndex = 0
        scrollOffset = 0
        updateDisplay()
    }

    private void updateDisplay() {
        contentPanel.removeAllComponents()

        if (filteredItems.isEmpty()) {
            Label noResults = new Label("  No matches found  ")
            noResults.setForegroundColor(TextColor.ANSI.WHITE)
            contentPanel.addComponent(noResults)
            return
        }

        // Calculate visible range with scrolling
        int visibleStart = scrollOffset
        int visibleEnd = Math.min(scrollOffset + MAX_VISIBLE_ITEMS, filteredItems.size())

        for (int i = visibleStart; i < visibleEnd; i++) {
            AutocompleteItem item = filteredItems[i]
            String label = formatItemLabel(item, i == selectedIndex)
            Label itemLabel = new Label(label)

            if (i == selectedIndex) {
                itemLabel.setForegroundColor(TextColor.ANSI.BLACK)
                itemLabel.setBackgroundColor(TextColor.ANSI.CYAN)
            } else {
                itemLabel.setForegroundColor(TextColor.ANSI.WHITE)
                itemLabel.setBackgroundColor(TextColor.ANSI.BLUE)
            }

            contentPanel.addComponent(itemLabel)
        }

        // Show scroll indicators if needed
        if (scrollOffset > 0) {
            // Could add "↑ more" indicator
        }
        if (visibleEnd < filteredItems.size()) {
            Label moreLabel = new Label("  ↓ ${filteredItems.size() - visibleEnd} more...")
            moreLabel.setForegroundColor(TextColor.ANSI.YELLOW)
            contentPanel.addComponent(moreLabel)
        }
    }

    private String formatItemLabel(AutocompleteItem item, boolean selected) {
        String icon = getIcon(item.type)
        String prefix = selected ? "▸ " : "  "
        String label = item.label

        // Truncate if too long
        int maxLabelLength = POPUP_WIDTH - 6  // Account for icon and prefix
        if (label.length() > maxLabelLength) {
            label = "..." + label.substring(label.length() - maxLabelLength + 3)
        }

        return "${prefix}${icon} ${label}"
    }

    private String getIcon(AutocompleteItemType type) {
        switch (type) {
            case AutocompleteItemType.FILE:
                return '▫'  // Use simple ASCII for better compatibility
            case AutocompleteItemType.DIRECTORY:
                return '▪'
            case AutocompleteItemType.COMMAND:
                return '/'
            case AutocompleteItemType.AGENT:
                return '@'
            default:
                return ' '
        }
    }

    /**
     * Get currently selected item.
     */
    AutocompleteItem getSelectedItem() {
        if (selectedIndex >= 0 && selectedIndex < filteredItems.size()) {
            return filteredItems[selectedIndex]
        }
        return null
    }

    /**
     * Move selection up.
     */
    void selectPrevious() {
        if (filteredItems.isEmpty()) return

        if (selectedIndex > 0) {
            selectedIndex--

            // Adjust scroll if needed
            if (selectedIndex < scrollOffset) {
                scrollOffset = selectedIndex
            }
        } else {
            // Wrap to bottom
            selectedIndex = filteredItems.size() - 1
            scrollOffset = Math.max(0, filteredItems.size() - MAX_VISIBLE_ITEMS)
        }

        updateDisplay()
        refreshUI()
    }

    /**
     * Move selection down.
     */
    void selectNext() {
        if (filteredItems.isEmpty()) return

        if (selectedIndex < filteredItems.size() - 1) {
            selectedIndex++

            // Adjust scroll if needed
            if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
                scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1
            }
        } else {
            // Wrap to top
            selectedIndex = 0
            scrollOffset = 0
        }

        updateDisplay()
        refreshUI()
    }

    /**
     * Handle key input for the popup.
     * Returns true if the key was handled, false otherwise.
     */
    boolean handleKeyInput(KeyStroke keyStroke) {
        if (!visible) return false

        KeyType keyType = keyStroke.getKeyType()

        switch (keyType) {
            case KeyType.ArrowUp:
                selectPrevious()
                return true

            case KeyType.ArrowDown:
                selectNext()
                return true

            case KeyType.Enter:
            case KeyType.Tab:
                if (onSelectionCallback != null && getSelectedItem() != null) {
                    onSelectionCallback.run()
                }
                hide()
                return true

            case KeyType.Escape:
                hide()
                return true

            case KeyType.PageUp:
                // Jump up by visible items
                for (int i = 0; i < MAX_VISIBLE_ITEMS && selectedIndex > 0; i++) {
                    selectPrevious()
                }
                return true

            case KeyType.PageDown:
                // Jump down by visible items
                for (int i = 0; i < MAX_VISIBLE_ITEMS && selectedIndex < filteredItems.size() - 1; i++) {
                    selectNext()
                }
                return true

            default:
                return false
        }
    }

    /**
     * Show popup at specified screen position.
     */
    void setVisible(boolean visible, int row = 0, int col = 0) {
        this.visible = visible

        if (visible && !filteredItems.isEmpty()) {
            // Position popup above the input if near bottom of screen
            TerminalSize screenSize = textGUI.getScreen().getTerminalSize()
            int adjustedRow = row
            int adjustedCol = col

            // If popup would go off bottom, show above the cursor
            if (row + POPUP_HEIGHT + 2 > screenSize.getRows()) {
                adjustedRow = Math.max(0, row - POPUP_HEIGHT - 2)
            }

            // Ensure popup doesn't go off right edge
            if (col + POPUP_WIDTH > screenSize.getColumns()) {
                adjustedCol = Math.max(0, screenSize.getColumns() - POPUP_WIDTH - 1)
            }

            popupWindow.setPosition(new TerminalPosition(adjustedCol, adjustedRow))

            if (!textGUI.getWindows().contains(popupWindow)) {
                textGUI.addWindow(popupWindow)
            }

            updateDisplay()
            refreshUI()
        } else {
            hide()
        }
    }

    /**
     * Hide popup.
     */
    void hide() {
        if (!visible) return

        visible = false
        selectedIndex = 0
        scrollOffset = 0

        if (textGUI.getWindows().contains(popupWindow)) {
            textGUI.removeWindow(popupWindow)
        }

        if (onHideCallback != null) {
            onHideCallback.run()
        }

        refreshUI()
    }

    /**
     * Refresh the GUI to show updates.
     */
    private void refreshUI() {
        try {
            // Use updateScreen() for thread-safe refresh
            textGUI.updateScreen()
        } catch (Exception e) {
            // Ignore refresh errors
        }
    }

    /**
     * Check if popup is visible.
     */
    boolean isVisible() {
        return visible && textGUI.getWindows().contains(popupWindow)
    }

    /**
     * Check if popup has no items.
     */
    boolean isEmpty() {
        return filteredItems.isEmpty()
    }

    /**
     * Get the number of filtered items.
     */
    int getItemCount() {
        return filteredItems.size()
    }

}
