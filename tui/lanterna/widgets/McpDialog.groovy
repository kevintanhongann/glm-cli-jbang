package tui.lanterna.widgets

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import mcp.McpClientManager
import mcp.McpConfig
import mcp.McpServerConfig
import mcp.ServerState

class McpDialog extends DialogWindow {

    private Panel mainPanel
    private DefaultWindow window
    private List<ServerListItem> serverItems = []
    private int selectedIndex = 0
    private Runnable onClose

    McpDialog(String title = 'MCP Servers') {
        super(title)

        window = new DefaultWindow(title)
        mainPanel = new Panel(new BorderLayout())
        window.setComponent(mainPanel)

        buildContent()
        loadServers()
    }

    private void buildContent() {
        Panel listPanel = new Panel(new LinearLayout(Direction.VERTICAL))

        serverItems.each { item ->
            ServerListItemRow row = new ServerListItemRow(item)
            listPanel.addComponent(row)
        }

        if (serverItems.empty) {
            Label emptyLabel = new Label('No MCP servers configured.\n\nUse "glm mcp add" to add a server.')
            emptyLabel.setForegroundColor(TextColor.ANSI.YELLOW)
            listPanel.addComponent(emptyLabel)
        }

        Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
        buttonPanel.addComponent(new Label('[Space] Toggle  '))
        buttonPanel.addComponent(new Label('[Enter] Details  '))
        buttonPanel.addComponent(new Label('[c] Connect  '))
        buttonPanel.addComponent(new Label('[d] Disconnect  '))
        buttonPanel.addComponent(new Label('[Esc] Close'))

        Panel contentPanel = new Panel(new BorderLayout())
        contentPanel.addComponent(listPanel, BorderLayout.Location.CENTER)
        contentPanel.addComponent(buttonPanel, BorderLayout.Location.BOTTOM)

        mainPanel.addComponent(contentPanel)
    }

    private void loadServers() {
        McpClientManager.instance.initialize()
        McpConfig config = McpConfig.load()

        serverItems.clear()

        config.mcpServers?.each { name, serverConfig ->
            def status = McpClientManager.instance.getStatus(name)
            ServerListItem item = new ServerListItem(
                name: name,
                config: serverConfig,
                status: status
            )
            serverItems.add(item)
        }

        rebuildList()
    }

    private void rebuildList() {
        Panel listPanel = mainPanel.getComponent(0).getComponent(0) as Panel
        listPanel.removeAllComponents()

        serverItems.eachWithIndex { item, index ->
            ServerListItemRow row = new ServerListItemRow(item, index == selectedIndex)
            listPanel.addComponent(row)
        }

        if (serverItems.empty) {
            Label emptyLabel = new Label('No MCP servers configured.\n\nUse "glm mcp add" to add a server.')
            emptyLabel.setForegroundColor(TextColor.ANSI.YELLOW)
            listPanel.addComponent(emptyLabel)
        }
    }

    void show(Window basedOn) {
        basedOn.addWindow(window)
        window.setFocusedInteractable(mainPanel)
    }

    void setOnClose(Runnable onClose) {
        this.onClose = onClose
    }

    private void handleKeyStroke(KeyStroke key) {
        switch (key.keyType) {
            case KeyType.ArrowUp:
                if (selectedIndex > 0) {
                    selectedIndex--
                    rebuildList()
                }
                break
            case KeyType.ArrowDown:
                if (selectedIndex < serverItems.size() - 1) {
                    selectedIndex++
                    rebuildList()
                }
                break
            case KeyType.Character:
                char c = key.character
                if (c == ' ' as char) {
                    toggleServer(selectedIndex)
                } else if (c == 'c' as char) {
                    connectServer(selectedIndex)
                } else if (c == 'd' as char) {
                    disconnectServer(selectedIndex)
                } else if (c == '\n' as char) {
                    showDetails(selectedIndex)
                }
                break
            case KeyType.Enter:
                showDetails(selectedIndex)
                break
            case KeyType.Escape:
                close()
                break
        }
    }

    private void toggleServer(int index) {
        if (index >= serverItems.size()) return

        McpConfig config = McpConfig.load()
        def item = serverItems.get(index)
        def serverConfig = config.getServer(item.name)

        if (serverConfig) {
            serverConfig.enabled = !serverConfig.enabled
            config.save()

            if (!serverConfig.enabled && McpClientManager.instance.hasConnection(item.name)) {
                McpClientManager.instance.disconnect(item.name)
            }

            loadServers()
        }
    }

    private void connectServer(int index) {
        if (index >= serverItems.size()) return

        def item = serverItems.get(index)
        McpClientManager.instance.connect(item.name)
        loadServers()
    }

    private void disconnectServer(int index) {
        if (index >= serverItems.size()) return

        def item = serverItems.get(index)
        McpClientManager.instance.disconnect(item.name)
        loadServers()
    }

    private void showDetails(int index) {
        if (index >= serverItems.size()) return

        def item = serverItems.get(index)
        StringBuilder details = new StringBuilder()
        details.append("Server: ${item.name}\n")
        details.append("Type: ${item.config?.type}\n")
        details.append("Enabled: ${item.config?.enabled}\n")
        details.append("Status: ${item.status?.state}\n")

        if (item.config?.isLocal()) {
            details.append("Command: ${item.config?.command?.join(' ')}\n")
        } else if (item.config?.isRemote()) {
            details.append("URL: ${item.config?.url}\n")
        }

        if (item.status?.errorMessage) {
            details.append("\nError: ${item.status?.errorMessage}")
        }

        TextBox textBox = new TextBox(new TerminalSize(50, 15), details.toString(), TextBox.Style.MULTI_LINE)
        textBox.setReadOnly(true)

        Panel panel = new Panel(new BorderLayout())
        panel.addComponent(textBox, BorderLayout.CENTER)
        panel.addComponent(new Label('Press any key to close...'), BorderLayout.BOTTOM)

        Panel container = new Panel(new BorderLayout())
        container.addComponent(panel)

        Window detailWindow = new BasicWindow('Server Details')
        detailWindow.setComponent(container)

        // Handle close
        detailWindow.setCloseHandler({
            mainPanel.gui.removeWindow(detailWindow)
        })

        mainPanel.gui.addWindowAndWait(detailWindow)
    }

    private void close() {
        window.close()
        if (onClose) {
            onClose.run()
        }
    }

    private static class ServerListItem {
        String name
        McpServerConfig config
        mcp.ServerStatus status
    }

    private class ServerListItemRow extends Panel {
        ServerListItemRow(ServerListItem item, boolean selected = false) {
            super(new LinearLayout(Direction.HORIZONTAL))
            setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning))

            String icon = getStatusIcon(item)
            String line = "${icon} ${item.name.padRight(25)} ${getStatusText(item)}"

            Label label = new Label(line)
            if (selected) {
                label.setForegroundColor(TextColor.ANSI.CYAN)
                setBackgroundColor(TextColor.ANSI.BLUE)
            } else {
                label.setForegroundColor(getStatusColor(item))
            }

            addComponent(label)
        }

        private String getStatusIcon(ServerListItem item) {
            if (item.status?.state == ServerState.CONNECTED) return '●'
            if (item.status?.state == ServerState.CONNECTING) return '○'
            if (item.status?.state == ServerState.FAILED) return '⊗'
            if (item.config?.enabled == false) return '⊘'
            return '○'
        }

        private String getStatusText(ServerListItem item) {
            if (item.status?.state == ServerState.CONNECTED) return 'Connected'
            if (item.status?.state == ServerState.CONNECTING) return 'Connecting...'
            if (item.status?.state == ServerState.FAILED) return 'Failed'
            if (item.config?.enabled == false) return 'Disabled'
            if (item.status?.state == ServerState.DISCONNECTED) return 'Disconnected'
            return 'Unknown'
        }

        private TextColor getStatusColor(ServerListItem item) {
            if (item.status?.state == ServerState.CONNECTED) return TextColor.ANSI.GREEN
            if (item.status?.state == ServerState.FAILED) return TextColor.ANSI.RED
            if (item.config?.enabled == false) return TextColor.ANSI.YELLOW
            return TextColor.ANSI.WHITE
        }
    }

}
