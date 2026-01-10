package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.SessionManager
import core.MessageStore
import core.TokenTracker
import core.RootDetector
import models.Session
import models.TokenStats

@Command(name = "session", description = "Manage conversation sessions", mixinStandardHelpOptions = true, subcommands = [
    SessionListCommand.class,
    SessionResumeCommand.class,
    SessionDeleteCommand.class,
    SessionInfoCommand.class,
    SessionArchiveCommand.class,
    SessionUnarchiveCommand.class
])
class SessionCommand implements Runnable {
    @Override
    void run() {
        new SessionListCommand().run()
    }
}

@Command(name = "list", description = "List all sessions")
class SessionListCommand implements Runnable {
    @Option(names = ["-a", "--all"], description = "Include archived sessions")
    boolean all = false

    @Option(names = ["-p", "--project"], description = "Filter by project path")
    String projectPath

    @Option(names = ["-g", "--global"], description = "List global sessions only")
    boolean global = false

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def projectHash = null

        if (global) {
            projectHash = "global"
        } else if (projectPath) {
            projectHash = core.RootDetector.findGitRoot(projectPath)
        } else {
            projectHash = core.RootDetector.findGitRoot(System.getProperty("user.dir"))
        }

        def sessions = sessionManager.listSessions(projectHash, all)

        if (sessions.isEmpty()) {
            println "No sessions found."
            if (projectHash && projectHash != "global") {
                println "Project hash: ${projectHash}"
            } else if (global) {
                println "Showing global sessions."
            } else {
                println "Run 'glm session list --global' to see global sessions."
            }
            return
        }

        println "\nSessions (${sessions.size()}):"
        println "─" * 80
        sessions.each { session ->
            println "ID:       ${session.id}"
            println "Title:    ${session.title ?: 'Untitled'}"
            println "Agent:    ${session.agentType}"
            println "Model:    ${session.model}"
            println "Updated:  ${session.updatedAt}"
            println "Directory: ${session.directory}"
            if (session.isArchived) {
                println "Status:   Archived"
            }
            println "─" * 80
        }
    }
}

@Command(name = "resume", description = "Resume a session")
class SessionResumeCommand implements Runnable {
    @Parameters(index = "0", description = "Session ID")
    String sessionId

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def session = sessionManager.getSession(sessionId)

        if (!session) {
            println "Error: Session '${sessionId}' not found."
            println "Run 'glm session list' to see available sessions."
            return
        }

        if (session.isArchived) {
            println "Error: Session is archived. Unarchive it first with 'glm session unarchive ${sessionId}'"
            return
        }

        println "Resuming session: ${session.title ?: sessionId}"
        println "Last updated: ${session.updatedAt}"
        println "Model: ${session.model}"
        println "Directory: ${session.directory}"
        println "\nStarting chat in session mode..."

        def chatCmd = new ChatCommand()
        chatCmd.model = session.model
        chatCmd.sessionId = sessionId
        chatCmd.run()
    }
}

@Command(name = "delete", description = "Delete a session")
class SessionDeleteCommand implements Runnable {
    @Parameters(index = "0", description = "Session ID")
    String sessionId

    @Option(names = ["-f", "--force"], description = "Skip confirmation")
    boolean force = false

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def session = sessionManager.getSession(sessionId)

        if (!session) {
            println "Error: Session '${sessionId}' not found."
            return
        }

        if (!force) {
            def console = System.console()
            if (console != null) {
                def confirm = console.readLine("Delete session '${session.title ?: sessionId}'? (y/N): ")
                if (!confirm.equalsIgnoreCase("y")) {
                    println "Cancelled."
                    return
                }
            } else {
                println "Warning: No console available. Use --force to delete without confirmation."
                return
            }
        }

        sessionManager.deleteSession(sessionId)
        println "Session '${session.title ?: sessionId}' deleted."
    }
}

@Command(name = "info", description = "Show session details")
class SessionInfoCommand implements Runnable {
    @Parameters(index = "0", description = "Session ID")
    String sessionId

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def session = sessionManager.getSession(sessionId)

        if (!session) {
            println "Error: Session '${sessionId}' not found."
            return
        }

        def messageStore = MessageStore.instance
        def tokenTracker = new TokenTracker()

        def messages = messageStore.getMessages(sessionId)
        def tokenStats = tokenTracker.getTokenStats(sessionId)

        println "\nSession Details:"
        println "─" * 80
        println "ID:          ${session.id}"
        println "Title:        ${session.title ?: 'Untitled'}"
        println "Agent:        ${session.agentType}"
        println "Model:        ${session.model}"
        println "Project:      ${session.projectHash}"
        println "Directory:    ${session.directory}"
        println "Created:      ${session.createdAt}"
        println "Updated:      ${session.updatedAt}"
        println "Status:       ${session.isArchived ? 'Archived' : 'Active'}"
        println "Messages:     ${messages.size()}"
        if (tokenStats) {
            println "Total Tokens: ${tokenStats.totalTokens}"
            println "Total Cost:   \$${String.format('%.4f', tokenStats.totalCost)}"
        }
        println "─" * 80
    }
}

@Command(name = "archive", description = "Archive a session")
class SessionArchiveCommand implements Runnable {
    @Parameters(index = "0", description = "Session ID")
    String sessionId

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def session = sessionManager.getSession(sessionId)

        if (!session) {
            println "Error: Session '${sessionId}' not found."
            return
        }

        sessionManager.archiveSession(sessionId)
        println "Session '${session.title ?: sessionId}' archived."
    }
}

@Command(name = "unarchive", description = "Unarchive a session")
class SessionUnarchiveCommand implements Runnable {
    @Parameters(index = "0", description = "Session ID")
    String sessionId

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def session = sessionManager.getSession(sessionId)

        if (!session) {
            println "Error: Session '${sessionId}' not found."
            return
        }

        sessionManager.unarchiveSession(sessionId)
        println "Session '${session.title ?: sessionId}' unarchived."
    }
}
