package core

import models.Session
import groovy.sql.Sql
import java.nio.file.Files

@Singleton(strict=false)
class SessionManager {
    private Sql database
    private static final String DB_PATH = System.getProperty("user.home") + "/.glm/sessions"
    private static boolean shutdownHookRegistered = false

    static {
        registerShutdownHook()
    }

    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            Runtime.runtime.addShutdownHook(new Thread({
                SessionManager.instance?.shutdown()
            }))
            shutdownHookRegistered = true
        }
    }

    SessionManager() {
        initializeDatabase()
    }

    private void initializeDatabase() {
        def dbDir = new File(DB_PATH).parentFile
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }

        database = Sql.newInstance(
            "jdbc:h2:${DB_PATH}",
            "sa",
            "",
            "org.h2.Driver"
        )

        runSchema()
    }

    private void runSchema() {
        database.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                id VARCHAR(64) PRIMARY KEY,
                project_hash VARCHAR(64) NOT NULL,
                directory VARCHAR(512) NOT NULL,
                title VARCHAR(256),
                agent_type VARCHAR(32),
                model VARCHAR(64),
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                is_archived BOOLEAN DEFAULT FALSE,
                metadata TEXT
            )
        """)

        database.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                id VARCHAR(64) PRIMARY KEY,
                session_id VARCHAR(64) NOT NULL,
                role VARCHAR(16) NOT NULL,
                content TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                parent_id VARCHAR(64),
                tokens_input INTEGER,
                tokens_output INTEGER,
                tokens_reasoning INTEGER,
                finish_reason VARCHAR(32),
                metadata TEXT
            )
        """)

        database.execute("""
            CREATE TABLE IF NOT EXISTS token_stats (
                session_id VARCHAR(64) PRIMARY KEY,
                total_tokens INTEGER DEFAULT 0,
                total_cost DECIMAL(10, 4) DEFAULT 0.0000,
                last_compaction TIMESTAMP
            )
        """)

        database.execute("CREATE INDEX IF NOT EXISTS idx_sessions_project ON sessions(project_hash)")
        database.execute("CREATE INDEX IF NOT EXISTS idx_sessions_updated ON sessions(updated_at DESC)")
        database.execute("CREATE INDEX IF NOT EXISTS idx_sessions_directory ON sessions(directory)")
        database.execute("CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id)")
        database.execute("CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at)")
        database.execute("CREATE INDEX IF NOT EXISTS idx_messages_parent ON messages(parent_id)")

        database.execute("""
            ALTER TABLE messages
            ADD CONSTRAINT IF NOT EXISTS fk_messages_session
            FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
        """)

        database.execute("""
            ALTER TABLE token_stats
            ADD CONSTRAINT IF NOT EXISTS fk_token_stats_session
            FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
        """)
    }

    String createSession(String directory, String agentType = "BUILD", String model = "glm-4.7") {
        def sessionId = generateSessionId()
        def projectHash = RootDetector.findGitRoot(directory) ?: "global"
        def now = new Date()

        database.execute(
            "INSERT INTO sessions (id, project_hash, directory, agent_type, model, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            [sessionId, projectHash, directory, agentType, model, now, now]
        )

        database.execute(
            "INSERT INTO token_stats (session_id, total_tokens, total_cost) VALUES (?, ?, ?)",
            [sessionId, 0, 0.0000]
        )

        return sessionId
    }

    Session getSession(String sessionId) {
        def row = database.firstRow(
            "SELECT * FROM sessions WHERE id = ?",
            [sessionId]
        )

        if (!row) return null

        return new Session(
            id: row.id,
            projectHash: row.project_hash,
            directory: row.directory,
            title: row.title,
            agentType: row.agent_type,
            model: row.model,
            createdAt: row.created_at,
            updatedAt: row.updated_at,
            isArchived: row.is_archived,
            metadata: row.metadata
        )
    }

    List<Session> listSessions(String projectHash = null, boolean includeArchived = false) {
        String sql = "SELECT * FROM sessions"
        def params = []
        def conditions = []

        if (projectHash != null) {
            conditions << "project_hash = ?"
            params << projectHash
        }

        if (!includeArchived) {
            conditions << "is_archived = FALSE"
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + conditions.join(" AND ")
        }

        sql += " ORDER BY updated_at DESC"

        def rows = database.rows(sql, params)

        return rows.collect { row ->
            new Session(
                id: row.id,
                projectHash: row.project_hash,
                directory: row.directory,
                title: row.title,
                agentType: row.agent_type,
                model: row.model,
                createdAt: row.created_at,
                updatedAt: row.updated_at,
                isArchived: row.is_archived,
                metadata: row.metadata
            )
        }
    }

    void touchSession(String sessionId) {
        database.execute(
            "UPDATE sessions SET updated_at = ? WHERE id = ?",
            [new Date(), sessionId]
        )
    }

    void updateSessionTitle(String sessionId, String title) {
        database.execute(
            "UPDATE sessions SET title = ? WHERE id = ?",
            [title, sessionId]
        )
    }

    void deleteSession(String sessionId) {
        database.execute("DELETE FROM sessions WHERE id = ?", [sessionId])
    }

    void archiveSession(String sessionId) {
        database.execute(
            "UPDATE sessions SET is_archived = TRUE WHERE id = ?",
            [sessionId]
        )
    }

    void unarchiveSession(String sessionId) {
        database.execute(
            "UPDATE sessions SET is_archived = FALSE WHERE id = ?",
            [sessionId]
        )
    }

    Sql getDatabase() {
        return database
    }

    void shutdown() {
        try {
            database?.close()
        } catch (Exception e) {
            System.err.println("Error closing database: ${e.message}")
        }
    }

    private String generateSessionId() {
        return "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }
}
