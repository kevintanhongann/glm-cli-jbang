package tui.lanterna.sidebar

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize
import core.SubagentSessionManager

class SubagentSection extends CollapsibleSection {

    private Label statusLabel
    private ProgressBar progressBar
    private Map<String, AgentStatusLabel> agentLabels = [:]
    private SubagentSessionManager sessionManager

    SubagentSection() {
        super("Subagents")
        setExpanded(true)
        this.sessionManager = SubagentSessionManager.getInstance()
        buildContent()
    }

    private void buildContent() {
        def panel = getContentPanel()
        panel.removeAllComponents()

        statusLabel = new Label("  No active subagents")
        statusLabel.setForegroundColor(TextColor.ANSI.GREEN)
        panel.addComponent(statusLabel)

        progressBar = new ProgressBar()
        panel.addComponent(progressBar)

        panel.addComponent(new Label(""))
    }

    void refresh() {
        def sessions = sessionManager.getAllSessions()

        if (sessions.isEmpty()) {
            statusLabel.setText("  No active subagents")
            progressBar.setVisible(false)
            statusLabel.setForegroundColor(TextColor.ANSI.GREEN)

            agentLabels.values().each { getPanel().removeComponent(it.panel) }
            agentLabels.clear()
        } else {
            def active = sessions.findAll { it.status in ["pending", "running"] }
            def completed = sessions.findAll { it.status == "completed" }
            int total = sessions.size()

            statusLabel.setText("  ${active.size()} active / ${total} total")
            progressBar.setVisible(true)

            double progress = total > 0 ? completed.size() / total : 0
            progressBar.setProgress(progress)

            statusLabel.setForegroundColor(
                active.size() > 0 ? TextColor.ANSI.YELLOW : TextColor.ANSI.GREEN
            )

            sessions.each { session ->
                def agentLabel = agentLabels[session.sessionId]
                if (!agentLabel) {
                    agentLabel = new AgentStatusLabel(session.sessionId)
                    getPanel().addComponent(agentLabel.panel)
                    agentLabels[session.sessionId] = agentLabel
                }
                agentLabel.update(session)
            }

            def sessionIds = sessions*.sessionId
            agentLabels.keySet().findAll { !(it in sessionIds) }.each { sessionId ->
                def agentLabel = agentLabels[sessionId]
                getPanel().removeComponent(agentLabel.panel)
                agentLabels.remove(sessionId)
            }
        }

        invalidate()
    }

    void clear() {
        sessionManager.clearCompleted()
        refresh()
    }

    static class AgentStatusLabel {
        String sessionId
        Panel panel
        Label iconLabel
        Label nameLabel

        AgentStatusLabel(String sessionId) {
            this.sessionId = sessionId
            panel = new Panel()
            panel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

            iconLabel = new Label("⠋")
            iconLabel.setForegroundColor(TextColor.ANSI.YELLOW)
            panel.addComponent(iconLabel)

            nameLabel = new Label(" Agent")
            nameLabel.setForegroundColor(TextColor.ANSI.WHITE)
            panel.addComponent(nameLabel)
        }

        void update(SubagentSessionManager.SubagentSession session) {
            String icon = getStatusIcon(session.status)
            String status = session.status.capitalize()

            iconLabel.setText(icon)
            iconLabel.setForegroundColor(getStatusColor(session.status))

            nameLabel.setText(" ${session.agentType.toUpperCase()} (${status})")
        }

        private String getStatusIcon(String status) {
            switch (status) {
                case "pending": return "⏳"
                case "running": return "⠋"
                case "completed": return "✓"
                case "error": return "✗"
                default: return "•"
            }
        }

        private TextColor getStatusColor(String status) {
            switch (status) {
                case "pending": return TextColor.ANSI.YELLOW
                case "running": return TextColor.ANSI.YELLOW
                case "completed": return TextColor.ANSI.GREEN
                case "error": return TextColor.ANSI.RED
                default: return TextColor.ANSI.WHITE
            }
        }
    }

    class ProgressBar extends Panel {
        private double progress = 0.0

        ProgressBar() {
            setPreferredSize(new TerminalSize(30, 1))
        }

        void setProgress(double value) {
            this.progress = Math.max(0, Math.min(1, value))
            invalidate()
        }

        void drawSelf(com.googlecode.lanterna.graphics.TextGraphics graphics) {
            int width = getSize().getColumns()
            int filled = (int) (width * progress)

            String bar = "█" * filled + "░" * (width - filled)
            graphics.setForegroundColor(new TextColor.RGB(76, 175, 80))
            graphics.putString(0, 0, bar)
        }
    }
}
