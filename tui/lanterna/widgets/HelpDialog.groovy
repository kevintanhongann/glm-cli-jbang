package tui.lanterna.widgets

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class HelpDialog extends BasicWindow {

    HelpDialog(MultiWindowTextGUI textGUI) {
        super('Help - Keyboard Shortcuts & Commands')
        this.textGUI = textGUI
    }

    void show() {
        setHints(Arrays.asList(Window.Hint.CENTERED))

        Panel mainPanel = new Panel()
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        addSection(mainPanel, 'Navigation', [
            ['PgUp / PgDn', 'Scroll activity log'],
            ['Ctrl+Home / Ctrl+End', 'Jump to start/end'],
            ['Ctrl+↑ / Ctrl+↓', 'Jump to prev/next message'],
            ['Tab', 'Switch agent (BUILD/PLAN)']
        ])

        addSection(mainPanel, 'Commands', [
            ['/models', 'Open model selection dialog'],
            ['/model [name]', 'Show/switch model'],
            ['/sidebar', 'Toggle sidebar'],
            ['/help', 'Show this help'],
            ['/clear', 'Clear chat history'],
            ['/skill [name]', 'List/show skills'],
            ['/exit', 'Exit TUI']
        ])

        addSection(mainPanel, 'Keyboard Shortcuts', [
            ['Ctrl+P', 'Open command palette'],
            ['Ctrl+M', 'Open model selection dialog'],
            ['Ctrl+S', 'Export chat log'],
            ['Ctrl+L', 'Clear chat history'],
            ['Ctrl+B', 'Toggle sidebar'],
            ['Ctrl+C', 'Exit TUI'],
            ['F1', 'Show this help'],
            ['Esc', 'Clear input / close dialog']
        ])

        addSection(mainPanel, 'Autocomplete', [
            ['@', 'Mention files in current directory'],
            ['/', 'Show available commands'],
            ['↑/↓', 'Navigate autocomplete suggestions'],
            ['Enter', 'Select suggestion'],
            ['Esc', 'Close autocomplete']
        ])

        mainPanel.addComponent(new Label(''))
        mainPanel.addComponent(new Label('─'.repeat(50)))
        mainPanel.addComponent(new Label(''))

        Panel buttonPanel = new Panel()
        buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        Button closeButton = new Button('[Enter] Close', {
            close()
        })
        buttonPanel.addComponent(closeButton)

        mainPanel.addComponent(buttonPanel)

        setComponent(mainPanel)

        textGUI.addWindowAndWaitFor(this)
    }

    private void addSection(Panel parent, String title, List<List<String>> items) {
        Label titleLabel = new Label(title)
        titleLabel.setForegroundColor(TextColor.ANSI.CYAN)
        parent.addComponent(titleLabel)

        parent.addComponent(new Label(''))

        int maxKeyLength = items.collect { it[0].length() }.max() ?: 0

        items.each { item ->
            String key = item[0]
            String desc = item[1]

            Panel row = new Panel()
            row.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

            Label keyLabel = new Label(key.padEnd(maxKeyLength + 2))
            keyLabel.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
            row.addComponent(keyLabel)

            Label descLabel = new Label(desc)
            row.addComponent(descLabel)

            parent.addComponent(row)
        }

        parent.addComponent(new Label(''))
    }
}
