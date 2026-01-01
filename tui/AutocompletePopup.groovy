package tui

import jexer.TWidget
import jexer.TWindow
import jexer.event.TKeypressEvent
import jexer.bits.CellAttributes
import jexer.bits.Color
import jexer.bits.BorderStyle
import static jexer.TKeypress.*

/**
 * Popup widget for displaying autocomplete suggestions.
 * Rendered as an overlay list below the input field.
 */
class AutocompletePopup extends TWidget {

    private List<AutocompleteItem> allItems = []
    private List<AutocompleteItem> filteredItems = []
    private int selectedIndex = 0
    private String prefix = ''
    private boolean visible = false
    private int maxVisibleItems = 8

    private static final int POPUP_WIDTH = 40
    private static final int POPUP_HEIGHT = 10
    private boolean popupVisible = false

    AutocompletePopup(TWidget parent, int x, int y) {
        super(parent, x, y, POPUP_WIDTH, POPUP_HEIGHT)
        popupVisible = false
    }

    void loadItems(List<AutocompleteItem> items, String prefix) {
        this.allItems = items ?: []
        this.filteredItems = new ArrayList<>(allItems)
        this.prefix = prefix
        this.selectedIndex = 0
    }

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
        
        // Reset selection if out of bounds
        if (selectedIndex >= filteredItems.size()) {
            selectedIndex = Math.max(0, filteredItems.size() - 1)
        }
        
        // Hide if no matches
        if (filteredItems.isEmpty()) {
            popupVisible = false
        }
    }

    void selectPrevious() {
        if (filteredItems.isEmpty()) return
        selectedIndex = (selectedIndex - 1 + filteredItems.size()) % filteredItems.size()
    }

    void selectNext() {
        if (filteredItems.isEmpty()) return
        selectedIndex = (selectedIndex + 1) % filteredItems.size()
    }

    String getSelectedItem() {
        if (filteredItems.isEmpty() || selectedIndex >= filteredItems.size()) {
            return null
        }
        return filteredItems[selectedIndex].value
    }

    AutocompleteItem getSelectedItemFull() {
        if (filteredItems.isEmpty() || selectedIndex >= filteredItems.size()) {
            return null
        }
        return filteredItems[selectedIndex]
    }

    void setPopupVisible(boolean visible) {
        this.popupVisible = visible
    }

    boolean isPopupVisible() {
        return this.popupVisible
    }

    @Override
    void draw() {
        if (!popupVisible || filteredItems.isEmpty()) return

        // Get theme colors
        CellAttributes normalAttr = new CellAttributes()
        normalAttr.setForeColor(Color.WHITE)
        normalAttr.setBackColor(Color.BLACK)

        CellAttributes selectedAttr = new CellAttributes()
        selectedAttr.setForeColor(Color.BLACK)
        selectedAttr.setBackColor(Color.CYAN)

        CellAttributes borderAttr = new CellAttributes()
        borderAttr.setForeColor(Color.CYAN)
        borderAttr.setBackColor(Color.BLACK)

        CellAttributes typeAttr = new CellAttributes()
        typeAttr.setForeColor(Color.YELLOW)
        typeAttr.setBackColor(Color.BLACK)

        int width = getWidth()
        int height = Math.min(filteredItems.size() + 2, maxVisibleItems + 2)

        // Draw border
        drawBox(0, 0, width, height, borderAttr, borderAttr, BorderStyle.SINGLE, false)

        // Draw items
        int startItem = 0
        if (selectedIndex >= maxVisibleItems) {
            startItem = selectedIndex - maxVisibleItems + 1
        }

        int displayCount = Math.min(filteredItems.size() - startItem, maxVisibleItems)
        
        for (int i = 0; i < displayCount; i++) {
            int itemIndex = startItem + i
            AutocompleteItem item = filteredItems[itemIndex]
            
            CellAttributes attr = (itemIndex == selectedIndex) ? selectedAttr : normalAttr
            
            // Clear line
            hLineXY(1, i + 1, width - 2, ' ', attr)
            
            // Draw icon/type indicator
            String icon = getIcon(item.type)
            putStringXY(1, i + 1, icon, 
                (itemIndex == selectedIndex) ? selectedAttr : typeAttr)
            
            // Draw label
            String label = truncate(item.label, width - 5)
            putStringXY(3, i + 1, label, attr)
        }

        // Draw scroll indicators if needed
        if (startItem > 0) {
            putStringXY(width - 2, 1, '↑', borderAttr)
        }
        if (startItem + displayCount < filteredItems.size()) {
            putStringXY(width - 2, height - 2, '↓', borderAttr)
        }
    }

    private String getIcon(AutocompleteItemType type) {
        switch (type) {
            case AutocompleteItemType.FILE:
                return '📄'
            case AutocompleteItemType.DIRECTORY:
                return '📁'
            case AutocompleteItemType.COMMAND:
                return '/'
            case AutocompleteItemType.AGENT:
                return '@'
            default:
                return ' '
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return ''
        if (s.length() <= maxLen) return s
        return s.substring(0, maxLen - 3) + '...'
    }
}
