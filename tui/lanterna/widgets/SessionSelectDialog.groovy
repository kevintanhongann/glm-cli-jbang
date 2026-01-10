package tui.lanterna.widgets

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType
import core.RootDetector
import core.SessionManager
import models.Session

class SessionSelectDialog {

    private MultiWindowTextGUI textGUI
    private BasicWindow dialogWindow
    private ActionListBox sessionListBox
    private String currentCwd
    private Session selectedSession = null
    private Map<Integer, Session> listboxIndexToSession = [:]
    private SessionManager sessionManager

    SessionSelectDialog(MultiWindowTextGUI textGUI, String cwd) {
        this.textGUI = textGUI
        this.currentCwd = cwd
        this.sessionManager = SessionManager.instance
    }

    String show() {
        dialogWindow = new BasicWindow('Select Session')
        dialogWindow.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.MODAL))

        Panel mainPanel = new Panel()
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        String projectInfo = RootDetector.findGitRoot(currentCwd) ?: "Global"
        mainPanel.addComponent(new Label("Project: ${new File(currentCwd).name} (${projectInfo})"))
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE))

        sessionListBox = new ActionListBox()
        updateSessionList()

        mainPanel.addComponent(sessionListBox.withBorder(Borders.singleLine('Sessions')))
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE))

        Panel hintPanel = new Panel()
        hintPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        hintPanel.addComponent(new Label('↑↓: Navigate  Enter: Select  Esc: Close'))
        mainPanel.addComponent(hintPanel)

        dialogWindow.setComponent(mainPanel)

        setupKeyHandler()

        textGUI.addWindow(dialogWindow)
        dialogWindow.waitUntilClosed()

        return selectedSession?.id
    }

    private void setupKeyHandler() {
        sessionListBox.setInputFilter({ listBox, key ->
            KeyType keyType = key.getKeyType()

            if (keyType == KeyType.Escape) {
                close()
                return false
            }

            return true
        })
    }

    private void updateSessionList() {
        sessionListBox.clearItems()
        listboxIndexToSession.clear()

        String projectHash = RootDetector.findGitRoot(currentCwd)

        List<Session> sessions = sessionManager.listSessions(projectHash, false)

        sessionListBox.addItem('[ New Session ]', { ->
            this.selectedSession = null
            close()
        })

        if (!sessions.isEmpty()) {
            sessionListBox.addItem('', { -> })
            sessionListBox.addItem('── Recent Sessions ──', { -> })
        }

        int listboxIndex = 2
        sessions.each { session ->
            String title = session.title ?: 'Untitled'
            String modelShort = session.model?.split('/')?.last() ?: 'unknown'
            String dateStr = formatDate(session.updatedAt)

            String displayString = "${title}"
            int msgCount = getMessageCount(session.id)
            if (msgCount > 0) {
                displayString += " (${msgCount} msgs)"
            }
            displayString += " - ${dateStr}"

            def currentSession = session
            sessionListBox.addItem(displayString, { ->
                this.selectedSession = currentSession
                close()
            })
            listboxIndexToSession[listboxIndex] = session
            listboxIndex++
        }

        if (sessions.isEmpty()) {
            sessionListBox.addItem('', { -> })
            sessionListBox.addItem('(No recent sessions)', { -> })
        }
    }

    private int getMessageCount(String sessionId) {
        try {
            return sessionManager.getDatabase().firstRow(
                "SELECT COUNT(*) as count FROM messages WHERE session_id = ?",
                [sessionId]
            ).count
        } catch (Exception e) {
            return 0
        }
    }

    private String formatDate(Date date) {
        if (!date) return ''
        
        java.util.Date utilDate = date instanceof java.sql.Timestamp 
            ? new java.util.Date(date.getTime()) 
            : date
        
        long diffMillis = System.currentTimeMillis() - utilDate.getTime()
        long diffDays = diffMillis / (24 * 60 * 60 * 1000)

        if (diffDays < 1) {
            long diffHours = diffMillis / (60 * 60 * 1000)
            if (diffHours < 1) {
                long diffMinutes = diffMillis / (60 * 1000)
                return "${diffMinutes}m ago"
            }
            return "${diffHours}h ago"
        } else if (diffDays == 1) {
            return '1d ago'
        } else if (diffDays < 7) {
            return "${diffDays}d ago"
        } else {
            return new java.text.SimpleDateFormat('MMM d').format(utilDate)
        }
    }

    void close() {
        if (dialogWindow != null) {
            dialogWindow.close()
            dialogWindow = null
        }
    }
}
