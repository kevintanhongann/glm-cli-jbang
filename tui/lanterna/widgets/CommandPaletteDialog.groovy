package tui.lanterna.widgets

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType
import tui.shared.CommandProvider
import tui.shared.CommandItem
import tui.shared.KeybindManager

class CommandPaletteDialog {

    private MultiWindowTextGUI textGUI
    private BasicWindow dialogWindow
    private TextBox searchBox
    private ActionListBox commandListBox
    private List<CommandItem> allCommands = []
    private List<CommandItem> filteredCommands = []
    private Map<Integer, CommandItem> listboxIndexToCommand = [:]
    private CommandItem selectedCommand = null
    private KeybindManager keybindManager
    private Closure onSelectCallback

    CommandPaletteDialog(MultiWindowTextGUI textGUI, KeybindManager keybindManager, Closure onSelect = null) {
        this.textGUI = textGUI
        this.keybindManager = keybindManager
        this.onSelectCallback = onSelect
        this.allCommands = CommandProvider.getAllCommands()
        this.filteredCommands = allCommands
    }

    CommandItem show() {
        dialogWindow = new BasicWindow('Command Palette')
        dialogWindow.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.MODAL))

        Panel mainPanel = new Panel()
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        Panel searchPanel = new Panel()
        searchPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        searchPanel.addComponent(new Label('Search: '))
        searchBox = new TextBox(new TerminalSize(50, 1), '', TextBox.Style.SINGLE_LINE)
        searchPanel.addComponent(searchBox)

        mainPanel.addComponent(searchPanel)
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE))

        commandListBox = new ActionListBox()
        updateCommandList('')

        mainPanel.addComponent(commandListBox.withBorder(Borders.singleLine('Commands')))
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE))

        Panel hintPanel = new Panel()
        hintPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        hintPanel.addComponent(new Label('↑↓: Navigate  Enter: Select  Esc: Close'))
        mainPanel.addComponent(hintPanel)

        dialogWindow.setComponent(mainPanel)

        setupSearchHandler()

        textGUI.addWindow(dialogWindow)
        dialogWindow.waitUntilClosed()

        return selectedCommand
    }

    private void setupSearchHandler() {
        searchBox.setInputFilter({ textBox, key ->
            KeyType keyType = key.getKeyType()

            if (keyType == KeyType.Escape) {
                close()
                return false
            }

            if (keyType == KeyType.Enter) {
                CommandItem selected = getSelectedCommand()
                if (selected && !selected.disabled) {
                    selectCommand(selected)
                }
                return false
            }

            if (keyType == KeyType.ArrowUp) {
                commandListBox.selectPreviousItem()
                return false
            }

            if (keyType == KeyType.ArrowDown) {
                commandListBox.selectNextItem()
                return false
            }

            if (keyType == KeyType.Backspace || keyType == KeyType.Character) {
                String filter = searchBox.getText()
                updateCommandList(filter)
            }

            return true
        })
    }

    private void updateCommandList(String filter) {
        commandListBox.clearItems()
        listboxIndexToCommand.clear()

        if (filter.isEmpty()) {
            filteredCommands = allCommands.findAll { !it.disabled }
            
            List<CommandItem> suggested = CommandProvider.getSuggestedCommands()
            suggested = suggested.findAll { !it.disabled }
            
            if (!suggested.isEmpty()) {
                addCategoryHeader('Suggested')
                suggested.each { cmd ->
                    addCommandItem(cmd, 'Suggested')
                }
                commandListBox.addItem('')
                addSeparator()
            }
            
            Map<String, List<CommandItem>> grouped = CommandProvider.getCommandsGroupedByCategory()
            grouped.keySet().sort().each { category ->
                if (category == 'Suggested') return
                List<CommandItem> categoryCommands = grouped[category]
                if (categoryCommands.isEmpty()) return
                
                addCategoryHeader(category)
                categoryCommands.sort { a, b -> a.title <=> b.title }.each { cmd ->
                    addCommandItem(cmd, category)
                }
                commandListBox.addItem('')
                addSeparator()
            }
        } else {
            filteredCommands = CommandProvider.filterCommands(filter)
            
            if (filteredCommands.isEmpty()) {
                commandListBox.addItem('No matching commands found', { -> })
                return
            }
            
            filteredCommands.each { cmd ->
                addCommandItem(cmd, cmd.category)
            }
        }
    }

    private void addCategoryHeader(String category) {
        String header = "── ${category} ──"
        commandListBox.addItem(header, { -> })
    }

    private void addSeparator() {
        commandListBox.addItem('', { -> })
    }

    private void addCommandItem(CommandItem cmd, String category) {
        int listboxIndex = 0
        try {
            listboxIndex = commandListBox.getNumberOfItems()
        } catch (Exception e) {
            
        }
        
        String keybindDisplay = cmd.getKeybindDisplay(keybindManager)
        String label = cmd.title
        if (keybindDisplay) {
            int padding = 40 - cmd.title.length()
            if (padding < 0) padding = 0
            label = cmd.title + ' ' * padding + keybindDisplay
        }
        
        commandListBox.addItem(label, { ->
            if (!cmd.disabled) {
                selectCommand(cmd)
            }
        })
        listboxIndexToCommand[listboxIndex] = cmd
    }

    private CommandItem getSelectedCommand() {
        int selectedIndex = commandListBox.getSelectedIndex()
        return listboxIndexToCommand[selectedIndex]
    }

    private void selectCommand(CommandItem cmd) {
        this.selectedCommand = cmd
        close()
        
        if (onSelectCallback != null) {
            onSelectCallback.call(cmd)
        }
    }

    void close() {
        if (dialogWindow != null) {
            dialogWindow.close()
            dialogWindow = null
        }
    }
}
