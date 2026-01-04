package tui.jexer.widgets

import jexer.TApplication
import jexer.TWindow
import jexer.TLabel
import jexer.TAction
import tui.shared.AutocompleteItem
import tui.JexerTheme

/**
 * Autocomplete popup widget for Jexer.
 * Displays filtered suggestions and allows selection.
 */
class JexerAutocompletePopup extends TWindow {

    private List<AutocompleteItem> items = []
    private int selectedIndex = 0
    private String filter = ''
    private String triggerType = '@'

    private static final int ITEM_HEIGHT = 1
    private static final int MAX_VISIBLE_ITEMS = 8
    private static final int PADDING = 1

    private Closure onSelectionCallback = null

    JexerAutocompletePopup(TApplication app) {
        super(app, '', 20, 10, TWindow.NOCLOSEBOX | TWindow.HIDEONCLOSE)
        setHidden(true)
    }

    /**
     * Set hidden state.
     */
    void setHidden(boolean hidden) {
        if (hidden) {
            hide()
        } else {
            show()
        }
    }

    /**
     * Load autocomplete items.
     */
    void loadItems(List<AutocompleteItem> newItems, String type) {
        this.items = newItems
        this.triggerType = type
        this.selectedIndex = 0
        this.filter = ''
        rebuildWindow()
    }

    /**
     * Filter items by query.
     */
    void filter(String query) {
        this.filter = query.toLowerCase()
        rebuildWindow()
    }

    /**
     * Rebuild window content based on current filter.
     */
    private void rebuildWindow() {
        // Filter items
        List<AutocompleteItem> filteredItems = []
        if (filter.isEmpty()) {
            filteredItems = items
        } else {
            filteredItems = items.findAll {
                it.value.toLowerCase().startsWith(filter)
            }
        }

        // Check if empty
        if (filteredItems.isEmpty()) {
            setHidden(true)
            return
        }

        // Calculate dimensions
        int maxItemLength = filteredItems.collect { it.value.length() + it.type.length() + 2 }.max() ?: 20
        int width = Math.max(20, Math.min(60, maxItemLength + PADDING * 2))
        int visibleCount = Math.min(MAX_VISIBLE_ITEMS, filteredItems.size())
        int height = visibleCount * ITEM_HEIGHT + 2

        // Reset window if dimensions changed significantly
        if (Math.abs(getWidth() - width) > 5 || Math.abs(getHeight() - height) > 5) {
            getChildren().clear()
        }

        setDimensions(width, height)
        setTitle(triggerType + filter)

        // Build items
        getChildren().clear()
        filteredItems.eachWithIndex { AutocompleteItem item, int index ->
            int y = PADDING + index * ITEM_HEIGHT

            // Highlight selected item
            if (index == selectedIndex) {
                addBox(y, PADDING, width - 2 * PADDING, 1,
                         JexerTheme.createAttributes(
                             JexerTheme.getBackgroundColor(),
                             JexerTheme.getAccentColor()
                         ))
            }

            // Item label
            String label = item.value
            if (item.type == 'file') {
                label = "📄 ${item.value}"
            } else if (item.type == 'directory') {
                label = "📁 ${item.value}"
            } else if (item.type == 'command') {
                label = "▸ ${item.value}"
            }

            def labelWidget = new TLabel(this, label, PADDING + 1, y)

            // Set color based on selection
            if (index == selectedIndex) {
                // labelWidget.getScreenCellAttributes().setForeColor(
                //     JexerTheme.getBackgroundColor()
                // )
                // labelWidget.getScreenCellAttributes().setBold(true)
            } else {
                // labelWidget.getScreenCellAttributes().setForeColor(
                //     JexerTheme.getTextColor()
                // )
            }
        }

        setHidden(false)
    }

    /**
     * Show popup at specified position.
     */
    void showPopup(boolean visible, int row = -1, int col = -1) {
        if (!visible) {
            setHidden(true)
            return
        }

        rebuildWindow()

        if (row >= 0 && col >= 0) {
            setPosition(col, row)
        }

        setHidden(false)
        activate()
    }

    /**
     * Check if popup is visible.
     */
    boolean isPopupVisible() {
        return !isHidden()
    }

    /**
     * Check if popup has any items.
     */
    boolean isEmpty() {
        return items.isEmpty()
    }

    /**
     * Get selected item.
     */
    AutocompleteItem getSelectedItem() {
        List<AutocompleteItem> filteredItems = getFilteredItems()
        if (filteredItems.isEmpty() || selectedIndex >= filteredItems.size()) {
            return null
        }
        return filteredItems.get(selectedIndex)
    }

    /**
     * Get filtered items list.
     */
    private List<AutocompleteItem> getFilteredItems() {
        if (filter.isEmpty()) {
            return items
        }
        return items.findAll {
            it.value.toLowerCase().startsWith(filter)
        }
    }

    /**
     * Select next item.
     */
    void selectNext() {
        List<AutocompleteItem> filtered = getFilteredItems()
        if (filtered.isEmpty()) {
            return
        }
        selectedIndex = (selectedIndex + 1) % filtered.size()
        rebuildWindow()
    }

    /**
     * Select previous item.
     */
    void selectPrevious() {
        List<AutocompleteItem> filtered = getFilteredItems()
        if (filtered.isEmpty()) {
            return
        }
        selectedIndex = (selectedIndex - 1 + filtered.size()) % filtered.size()
        rebuildWindow()
    }

    /**
     * Set selection callback.
     */
    void setOnSelection(Closure callback) {
        this.onSelectionCallback = callback
    }

    /**
     * Handle keypress for selection.
     */
    @Override
    public void onKeypress(jexer.event.TKeypressEvent keypress) {
        if (keypress.getKey().equals(jexer.TKeypress.kbEnter)) {
            if (onSelectionCallback != null) {
                onSelectionCallback.call()
            }
            return
        }
        if (keypress.getKey().equals(jexer.TKeypress.kbEsc)) {
            showPopup(false)
            return
        }
        super.onKeypress(keypress)
    }

    /**
     * Add a colored box for highlighting.
     */
    private void addBox(int x, int y, int width, int height,
                      jexer.bits.CellAttributes attr) {
        // Top border
        for (int i = 0; i < width; i++) {
            setScreenCell(x + i, y, ' ', attr)
        }
        // Bottom border
        for (int i = 0; i < width; i++) {
            setScreenCell(x + i, y + height - 1, ' ', attr)
        }
        // Left border
        for (int i = 0; i < height; i++) {
            setScreenCell(x, y + i, ' ', attr)
        }
        // Right border
        for (int i = 0; i < height; i++) {
            setScreenCell(x + width - 1, y + i, ' ', attr)
        }
    }

    /**
     * Set screen cell at position.
     */
    private void setScreenCell(int x, int y, char ch, jexer.bits.CellAttributes attr) {
        if (getApplication()?.getScreen() != null) {
            getApplication().getScreen().putChar(getX() + x, getY() + y, ch, attr)
        }
    }

}
