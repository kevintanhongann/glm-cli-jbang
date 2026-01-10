package tui.lanterna.widgets

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import mcp.McpClientManager
import mcp.McpEventBus
import mcp.ServerState
import mcp.McpStatusChangeEvent

class McpStatusPanel extends Panel {

    private Label statusLabel
    private int connectedCount = 0
    private int errorCount = 0
    private Panel container

    McpStatusPanel() {
        setLayoutManager(new BorderLayout())

        container = new Panel(new LinearLayout(Direction.HORIZONTAL))
        addComponent(container, BorderLayout.Location.LEFT)

        statusLabel = new Label('')
        statusLabel.setForegroundColor(TextColor.ANSI.GREEN)
        container.addComponent(statusLabel)

        updateDisplay()

        McpEventBus.getStatusChangeEvents().subscribe { event ->
            updateFromEvent(event)
        }
    }

    void updateFromEvent(McpStatusChangeEvent event) {
        if (event.status.state == ServerState.CONNECTED) {
            connectedCount++
        } else if (event.status.state == ServerState.FAILED) {
            errorCount++
        } else if (event.status.state == ServerState.DISCONNECTED ||
                   event.status.state == ServerState.DISABLED) {
            if (connectedCount > 0) connectedCount--
        }

        updateDisplay()
    }

    void refresh() {
        McpClientManager.instance.initialize()
        def statuses = McpClientManager.instance.getStatuses()

        connectedCount = 0
        errorCount = 0

        statuses.each { name, status ->
            if (status.state == ServerState.CONNECTED) {
                connectedCount++
            } else if (status.state == ServerState.FAILED) {
                errorCount++
            }
        }

        updateDisplay()
    }

    private void updateDisplay() {
        try {
            if (connectedCount == 0 && errorCount == 0) {
                statusLabel.setText('')
            } else {
                String icon = errorCount > 0 ? '⊙' : '○'
                TextColor color = errorCount > 0 ? TextColor.ANSI.RED : TextColor.ANSI.GREEN
                statusLabel.setText("${icon} ${connectedCount} MCP")
                statusLabel.setForegroundColor(color)
            }
        } catch (Exception ignored) {}
    }

    int getConnectedCount() {
        return connectedCount
    }

    int getErrorCount() {
        return errorCount
    }

}
