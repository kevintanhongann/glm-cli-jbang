package tui.jexer.widgets

import jexer.TWindow
import jexer.TApplication
import jexer.TField
import jexer.TLabel
import jexer.TList
import jexer.event.TKeypressEvent
import jexer.bits.CellAttributes
import jexer.bits.Color
import tui.shared.CommandProvider
import tui.shared.CommandItem
import tui.shared.KeybindManager
import tui.JexerTheme
import static jexer.TKeypress.*

class JexerCommandPalette extends TWindow {

    private TApplication app
    private TField searchField
    private TList commandList
    private List<CommandItem> allCommands = []
    private List<CommandItem> filteredCommands = []
    private Map<Integer, CommandItem> listIndexToCommand = [:]
    private CommandItem selectedCommand = null
    private KeybindManager keybindManager
    private Closure onSelectCallback

    JexerCommandPalette(TApplication app, KeybindManager keybindManager, Closure onSelect = null) {
        super(app, 'Command Palette', 0, 0, 60, 25, RESIZABLE)
        
        this.app = app
        this.keybindManager = keybindManager
        this.onSelectCallback = onSelect
        this.allCommands = CommandProvider.getAllCommands()
        this.filteredCommands = allCommands
        
        initializeComponents()
        updateCommandList('')
        
        setFocus(searchField)
    }

    private void initializeComponents() {
        int row = 2
        
        new TLabel(this, 2, row, 'Search:')
        searchField = new TField(this, 9, row, 48, false)
        searchField.onKeypress = { TKeypressEvent keypress ->
            if (handleSearchKeypress(keypress)) {
                return
            }
            super.onKeypress(keypress)
        }
        searchField.onChange = {
            String filter = searchField.getText()
            updateCommandList(filter)
        }
        
        row += 2
        commandList = new TList(this, 2, row, 56, 18)
        commandList.onKeypress = { TKeypressEvent keypress ->
            handleListKeypress(keypress)
        }
        
        int hintRow = row + 19
        new TLabel(this, 2, hintRow, '↑↓: Navigate  Enter: Select  Esc: Close',
                    new CellAttributes(Color.WHITE, Color.BLACK))
    }

    private boolean handleSearchKeypress(TKeypressEvent keypress) {
        if (keypress.getKey().equals(kbEsc)) {
            close()
            return true
        }
        
        if (keypress.getKey().equals(kbEnter)) {
            CommandItem selected = getSelectedCommand()
            if (selected && !selected.disabled) {
                selectCommand(selected)
            }
            return true
        }
        
        if (keypress.getKey().equals(kbDown)) {
            commandList.focus()
            commandList.selectNext()
            return true
        }
        
        return false
    }

    private void handleListKeypress(TKeypressEvent keypress) {
        if (keypress.getKey().equals(kbEsc)) {
            close()
        } else if (keypress.getKey().equals(kbEnter)) {
            CommandItem selected = getSelectedCommand()
            if (selected && !selected.disabled) {
                selectCommand(selected)
            }
        } else if (keypress.getKey().equals(kbTab)) {
            searchField.focus()
        }
    }

    private void updateCommandList(String filter) {
        commandList.clear()
        listIndexToCommand.clear()
        
        if (filter.isEmpty()) {
            filteredCommands = allCommands.findAll { !it.disabled }
            
            List<CommandItem> suggested = CommandProvider.getSuggestedCommands()
            suggested = suggested.findAll { !it.disabled }
            
            if (!suggested.isEmpty()) {
                addCategoryHeader('Suggested')
                suggested.each { cmd ->
                    addCommandItem(cmd)
                }
                commandList.addItem('')
            }
            
            Map<String, List<CommandItem>> grouped = CommandProvider.getCommandsGroupedByCategory()
            grouped.keySet().sort().each { category ->
                if (category == 'Suggested') return
                List<CommandItem> categoryCommands = grouped[category]
                if (categoryCommands.isEmpty()) return
                
                addCategoryHeader(category)
                categoryCommands.sort { a, b -> a.title <=> b.title }.each { cmd ->
                    addCommandItem(cmd)
                }
                commandList.addItem('')
            }
        } else {
            filteredCommands = CommandProvider.filterCommands(filter)
            
            if (filteredCommands.isEmpty()) {
                commandList.addItem('No matching commands found')
                return
            }
            
            filteredCommands.each { cmd ->
                addCommandItem(cmd)
            }
        }
    }

    private void addCategoryHeader(String category) {
        String header = "── ${category} ──"
        commandList.addItem(header)
    }

    private void addCommandItem(CommandItem cmd) {
        int index = commandList.getItemCount()
        String keybindDisplay = cmd.getKeybindDisplay(keybindManager)
        String label = cmd.title
        
        if (keybindDisplay) {
            int padding = 35 - cmd.title.length()
            if (padding < 0) padding = 0
            label = cmd.title + ' ' * padding + '[' + keybindDisplay + ']'
        }
        
        commandList.addItem(label)
        listIndexToCommand[index] = cmd
    }

    private CommandItem getSelectedCommand() {
        int selectedIndex = commandList.getSelectedIndex()
        return listIndexToCommand[selectedIndex]
    }

    private void selectCommand(CommandItem cmd) {
        this.selectedCommand = cmd
        close()
        
        if (onSelectCallback != null) {
            onSelectCallback.call(cmd)
        }
    }

    @Override
    public void onKeypress(TKeypressEvent keypress) {
        if (keypress.getKey().equals(kbEsc)) {
            close()
            return
        }
        
        if (keypress.getKey().equals(kbCtrlP)) {
            close()
            return
        }
        
        super.onKeypress(keypress)
    }

    void close() {
        if (app != null) {
            removeWindow(this)
            app = null
        }
    }
}
