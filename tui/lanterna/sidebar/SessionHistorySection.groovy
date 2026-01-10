package tui.lanterna.sidebar

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import core.SessionManager
import models.Session
import core.RootDetector

class SessionHistorySection extends CollapsibleSection {

    private String currentCwd
    private SessionManager sessionManager
    private String currentSessionId
    private Closure onSessionSelected

    SessionHistorySection(String currentCwd, String currentSessionId = null) {
        super('Session History')
        this.currentCwd = currentCwd
        this.sessionManager = SessionManager.instance
        this.currentSessionId = currentSessionId
        refresh()
    }

    void setOnSessionSelected(Closure callback) {
        this.onSessionSelected = callback
    }

    void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId
        refresh()
    }

    void refresh() {
        clear()
        loadSessions()
    }

    private void loadSessions() {
        String projectHash = RootDetector.findGitRoot(currentCwd)
        List<Session> sessions = sessionManager.listSessions(projectHash, false)

        if (sessions.isEmpty()) {
            Label noSessionsLabel = new Label('  No sessions')
            noSessionsLabel.setForegroundColor(TextColor.ANSI.GRAY)
            addToContent(noSessionsLabel)
            return
        }

        int displayCount = 0
        int maxDisplay = 10

        sessions.take(maxDisplay).each { session ->
            boolean isCurrent = (session.id == currentSessionId)
            String prefix = isCurrent ? '● ' : '○ '
            String title = session.title ?: 'Untitled'

            Label sessionLabel = new Label("  ${prefix}${title}")
            if (isCurrent) {
                sessionLabel.setForegroundColor(TextColor.ANSI.GREEN)
            } else {
                sessionLabel.setForegroundColor(TextColor.ANSI.WHITE)
            }

            def currentSession = session
            sessionLabel.addMouseListener(new MouseListener() {
                @Override
                void onMouseClick(Component component, MouseEvent mouseEvent) {
                    if (mouseEvent.isAction()) {
                        if (currentSessionId != currentSession.id && onSessionSelected) {
                            onSessionSelected.call(currentSession)
                        }
                    }
                }
            })

            addToContent(sessionLabel)
            displayCount++
        }

        if (sessions.size() > maxDisplay) {
            Label moreLabel = new Label("  ... ${sessions.size() - maxDisplay} more")
            moreLabel.setForegroundColor(TextColor.ANSI.GRAY)
            addToContent(moreLabel)
        }
    }

    private String formatDate(Date date) {
        if (!date) return ''

        long diffMillis = System.currentTimeMillis() - date.getTime()
        long diffDays = diffMillis / (24 * 60 * 60 * 1000)

        if (diffDays < 1) {
            long diffHours = diffMillis / (60 * 60 * 1000)
            if (diffHours < 1) {
                long diffMinutes = diffMillis / (60 * 1000)
                return "${diffMinutes}m"
            }
            return "${diffHours}h"
        } else if (diffDays == 1) {
            return '1d'
        } else if (diffDays < 7) {
            return "${diffDays}d"
        } else {
            return date.format('MMM d')
        }
    }
}
