package tui.tui4j.components

import com.williamcallahan.tui4j.compat.bubbletea.*
import tui.tui4j.Tui4jTheme
import core.SessionStatsManager
import core.LspManager

class SidebarView implements Model {

    private final String sessionId
    private final Tui4jTheme theme = Tui4jTheme.instance
    private int tokenCount = 0
    private String modelInfo = ''
    private int lspCount = 0

    SidebarView(String sessionId) {
        this.sessionId = sessionId
        refresh()
    }

    void refresh() {
        try {
            def stats = SessionStatsManager.instance.getSessionStats(sessionId)
            if (stats) {
                this.tokenCount = stats.inputTokens + stats.outputTokens
                this.modelInfo = "Tokens: ${this.tokenCount}"
            }

            LspManager.instance.updateDiagnosticCounts(sessionId)
            this.lspCount = LspManager.instance.getConnectedLspCount(sessionId)

        } catch (Exception e) {
        }
    }

    @Override
    Command init() {
        refresh()
        return null
    }

    @Override
    UpdateResult<? extends Model> update(Message msg) {
        return UpdateResult.from(this)
    }

    @Override
    String view() {
        def sb = new StringBuilder()
        sb.append("│ Session\n")
        sb.append("│ ───────\n")
        sb.append("│ ID: ${sessionId.substring(0, 8)}...\n")
        sb.append("│ ${modelInfo}\n")
        sb.append("│\n")
        sb.append("│ LSP Status\n")
        sb.append("│ ───────\n")
        if (lspCount > 0) {
            sb.append("│ ✓ ${lspCount} server(s) connected\n")
        } else {
            sb.append("│ No LSP servers\n")
        }
        sb.append("│\n")
        sb.append("│ Working Dir\n")
        sb.append("│ ───────\n")
        sb.append("│ ${System.getProperty('user.dir')}\n")

        return theme.sidebarStyle.render(sb.toString())
    }

}
