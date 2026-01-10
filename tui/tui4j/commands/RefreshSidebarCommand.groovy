package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import core.LspManager as SidebarLspManager

class RefreshSidebarCommand implements Command {

    private final String sessionId

    RefreshSidebarCommand(String sessionId) {
        this.sessionId = sessionId
    }

    @Override
    Message execute() {
        try {
            SidebarLspManager.instance.updateDiagnosticCounts(sessionId)
            return new StatusMessage("Sidebar refreshed")
        } catch (Exception e) {
            return new ErrorMessage("Refresh error: ${e.message}", e)
        }
    }
}
