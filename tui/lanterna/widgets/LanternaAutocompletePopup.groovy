package tui.lanterna.widgets

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean
import tui.shared.AutocompleteItem
import tui.shared.AutocompleteItemType

/**
 * Popup window for displaying autocomplete suggestions in Lanterna TUI.
 * Shows file/folder suggestions with icons and navigation support.
 */
class LanternaAutocompletePopup {

    private MultiWindowTextGUI textGUI
    private BasicWindow popupWindow
    private ActionListBox actionListBox
    private List<AutocompleteItem> allItems = []
    private List<AutocompleteItem> filteredItems = []
    private AutocompleteItem selectedCallback = null
    private boolean visible = false
    private Runnable onSelectionCallback = null

    private static final int MAX_VISIBLE_ITEMS = 8
    private static final int POPUP_WIDTH = 60
    private static final int POPUP_HEIGHT = 10

    LanternaAutocompletePopup(MultiWindowTextGUI textGUI) {
        this.textGUI = textGUI
        createPopupWindow()
    }

    private void createPopupWindow() {
        popupWindow = new BasicWindow()
        popupWindow.setHints(Arrays.asList(
            Window.Hint.NO_DECORATIONS,
            Window.Hint.FIXED_POSITION
        ))

        Panel panel = new Panel()
        panel.setLayoutManager(new BorderLayout())

        actionListBox = new ActionListBox(new TerminalSize(POPUP_WIDTH - 2, MAX_VISIBLE_ITEMS))

        panel.addComponent(actionListBox, BorderLayout.Location.CENTER)
        popupWindow.setComponent(panel)

        setupKeyboardHandling()
    }

    private void setupKeyboardHandling() {
        popupWindow.addWindowListener(new WindowListenerAdapter() {

            @Override
            void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                KeyType keyType = keyStroke.getKeyType()

                if (keyType == KeyType.Escape) {
                    hide()
                    hasBeenHandled.set(true)
                } else if (keyType == KeyType.Tab) {
                    if (onSelectionCallback != null) {
                        onSelectionCallback.run()
                    }
                    hide()
                    hasBeenHandled.set(true)
                } else if (keyType == KeyType.Character && !keyStroke.isAltDown() && !keyStroke.isCtrlDown()) {
                    // Any other character key - hide popup and let it pass through
                    hide()
                    hasBeenHandled.set(false)
                } else if (keyType == KeyType.Backspace) {
                    hide()
                    hasBeenHandled.set(false)
                }
            }

        })
    }

    /**
     * Load items into the popup.
     */
    void loadItems(List<AutocompleteItem> items, String prefix = '') {
        this.allItems = items ?: []
        this.filteredItems = new ArrayList<>(allItems)
        updateActionList()
    }

    /**
     * Set callback for when an item is selected.
     */
    void setOnSelection(Runnable callback) {
        this.onSelectionCallback = callback
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

        updateActionList()
    }

    private void updateActionList() {
        actionListBox.clearItems()

        filteredItems.each { item ->
            String label = formatItemLabel(item)
            actionListBox.addItem(label, {
                // When an item is selected via Enter/Space
                if (onSelectionCallback != null) {
                    onSelectionCallback.run()
                }
            } as Runnable)
        }

        if (!filteredItems.isEmpty()) {
            actionListBox.setSelectedIndex(0)
        }
    }

    private String formatItemLabel(AutocompleteItem item) {
        String icon = getIcon(item.type)
        return "${icon} ${item.label}"
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

    /**
     * Get currently selected item.
     */
    AutocompleteItem getSelectedItem() {
        int selectedIndex = actionListBox.getSelectedIndex()
        if (selectedIndex >= 0 && selectedIndex < filteredItems.size()) {
            return filteredItems[selectedIndex]
        }
        return null
    }

    /**
     * Move selection up.
     */
    void selectPrevious() {
        int currentIndex = actionListBox.getSelectedIndex()
        if (currentIndex > 0) {
            actionListBox.setSelectedIndex(currentIndex - 1)
        }
    }

    /**
     * Move selection down.
     */
    void selectNext() {
        int currentIndex = actionListBox.getSelectedIndex()
        int maxIndex = filteredItems.size() - 1
        if (currentIndex < maxIndex) {
            actionListBox.setSelectedIndex(currentIndex + 1)
        }
    }

    /**
     * Show popup at specified screen position.
     */
    void setVisible(boolean visible, int row = 0, int col = 0) {
        this.visible = visible

        if (visible && !filteredItems.isEmpty()) {
            popupWindow.setPosition(new TerminalPosition(col, row))
            popupWindow.setSize(new TerminalSize(POPUP_WIDTH, POPUP_HEIGHT))

            if (!textGUI.getWindows().contains(popupWindow)) {
                textGUI.addWindow(popupWindow)
            }
        } else {
            hide()
        }
    }

    /**
     * Hide popup.
     */
    void hide() {
        visible = false
        if (textGUI.getWindows().contains(popupWindow)) {
            textGUI.removeWindow(popupWindow)
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

}
