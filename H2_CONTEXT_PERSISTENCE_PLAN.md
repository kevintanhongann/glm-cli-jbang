# H2 Database Context Persistence Implementation Plan

## Overview

Implement a robust H2-based persistence layer for storing conversation sessions and message history, enabling users to resume conversations and maintain context across CLI sessions.

## Goals

1. **Session Persistence**: Save and restore conversation sessions
2. **Message History**: Store complete conversation history with metadata
3. **Token Tracking**: Track token usage for cost management
4. **Project Context**: Associate sessions with project directories
5. **User Experience**: Intuitive session management (list, resume, delete)
6. **Future-Proof**: Schema designed for future enhancements (compaction, forking)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   glm-cli-jbang                         │
├─────────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────────────────────────────────────┐        │
│  │         SessionManager (NEW)                │        │
│  ├──────────────────────────────────────────────┤        │
│  │  • createSession()                        │        │
│  │  • loadSession()                          │        │
│  │  • listSessions()                         │        │
│  │  • deleteSession()                        │        │
│  │  • addMessage()                           │        │
│  │  • getMessages()                          │        │
│  └──────────────────────────────────────────────┘        │
│                        │                                │
│                        ▼                                │
│  ┌──────────────────────────────────────────────┐        │
│  │         H2 Database (sessions.mv.db)       │        │
│  ├──────────────────────────────────────────────┤        │
│  │  sessions table                           │        │
│  │  messages table                           │        │
│  │  message_parts table (future)              │        │
│  │  token_stats table                        │        │
│  └──────────────────────────────────────────────┘        │
│                        │                                │
│                        ▼                                │
│  Modified Components:                                    │
│  ├─ Agent.groovy (add persistence)                      │
│  ├─ ChatCommand.groovy (add session support)             │
│  ├─ LanternaTUI.groovy (add session support)           │
│  └─ GlmCli.groovy (add SessionCommand)                 │
│                                                           │
└─────────────────────────────────────────────────────────────┘
```

## Database Schema

### 1. Sessions Table

```sql
CREATE TABLE sessions (
    id VARCHAR(64) PRIMARY KEY,              -- Unique session ID (e.g., "ses_abc123")
    project_hash VARCHAR(64) NOT NULL,        -- Git root hash or "global"
    directory VARCHAR(512) NOT NULL,          -- Working directory path
    title VARCHAR(256),                      -- AI-generated or user-provided title
    agent_type VARCHAR(32),                  -- BUILD, PLAN, EXPLORE
    model VARCHAR(64),                       -- Model used (e.g., "glm-4.7")
    created_at TIMESTAMP NOT NULL,            -- Session creation time
    updated_at TIMESTAMP NOT NULL,            -- Last activity time
    is_archived BOOLEAN DEFAULT FALSE,        -- Archive status
    metadata TEXT                           -- JSON for future extensibility
);

CREATE INDEX idx_sessions_project ON sessions(project_hash);
CREATE INDEX idx_sessions_updated ON sessions(updated_at DESC);
CREATE INDEX idx_sessions_directory ON sessions(directory);
```

### 2. Messages Table

```sql
CREATE TABLE messages (
    id VARCHAR(64) PRIMARY KEY,              -- Unique message ID
    session_id VARCHAR(64) NOT NULL,         -- Foreign key to sessions
    role VARCHAR(16) NOT NULL,              -- 'user', 'assistant', 'system'
    content TEXT NOT NULL,                    -- Message content
    created_at TIMESTAMP NOT NULL,            -- Message timestamp
    parent_id VARCHAR(64),                   -- For threading (future)
    tokens_input INTEGER,                     -- Input tokens (assistant only)
    tokens_output INTEGER,                    -- Output tokens (assistant only)
    tokens_reasoning INTEGER,                 -- Reasoning tokens (future)
    finish_reason VARCHAR(32),               -- 'stop', 'tool_calls', etc.
    metadata TEXT                            -- JSON for tool calls, etc.
);

CREATE INDEX idx_messages_session ON messages(session_id);
CREATE INDEX idx_messages_created ON messages(created_at);
CREATE INDEX idx_messages_parent ON messages(parent_id);

ALTER TABLE messages
ADD CONSTRAINT fk_messages_session
FOREIGN KEY (session_id) REFERENCES sessions(id)
ON DELETE CASCADE;
```

### 3. Message_Parts Table (Future - Phase 2)

```sql
CREATE TABLE message_parts (
    id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,              -- 'text', 'tool', 'snapshot', 'reasoning'
    data TEXT NOT NULL,                      -- JSON-encoded part data
    created_at TIMESTAMP NOT NULL,
    is_compacted BOOLEAN DEFAULT FALSE,       -- Compaction tracking
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);
```

### 4. Token_Stats Table

```sql
CREATE TABLE token_stats (
    session_id VARCHAR(64) PRIMARY KEY,
    total_tokens INTEGER DEFAULT 0,
    total_cost DECIMAL(10, 4) DEFAULT 0.0000,
    last_compaction TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);
```

## File Structure

```
core/
├── SessionManager.groovy          # NEW - Database operations
├── Session.groovy                # NEW - Session model
├── MessageStore.groovy           # NEW - Message persistence
└── TokenTracker.groovy          # NEW - Token usage tracking

models/
├── Session.groovy                # NEW - Session POJO
├── Message.groovy               # MODIFY - Extend with DB fields
└── TokenStats.groovy            # NEW - Token stats model

commands/
├── SessionCommand.groovy         # NEW - Session management CLI
├── AgentCommand.groovy          # MODIFY - Add persistence
├── ChatCommand.groovy           # MODIFY - Add session support
└── GlmCli.groovy               # MODIFY - Add SessionCommand subcommand

config/
├── schema.sql                   # NEW - Database schema
└── migrations/                  # NEW - Migration files
    ├── 001_initial_schema.sql
    ├── 002_add_message_parts.sql
    └── 003_add_token_stats.sql
```

## Implementation Phases

### Phase 1: Core Persistence (Week 1-2)

**Objective**: Basic session and message storage

#### 1.1 Database Setup

**File**: `core/SessionManager.groovy`

```groovy
package core

import java.sql.*
import groovy.sql.Sql

@Singleton
class SessionManager {
    private Sql database
    private static final String DB_PATH = "~/.glm/sessions.mv.db"

    SessionManager() {
        initializeDatabase()
    }

    private void initializeDatabase() {
        def dbPath = System.getProperty("user.home") + "/.glm/sessions.mv.db"
        new File(dbPath).parentFile.mkdirs()

        database = Sql.newInstance(
            "jdbc:h2:~/glm/sessions",
            "sa",
            "",
            "org.h2.Driver"
        )

        // Create tables if not exists
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

        // Create indexes
        database.execute("CREATE INDEX IF NOT EXISTS idx_sessions_project ON sessions(project_hash)")
        database.execute("CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id)")
    }

    void shutdown() {
        database?.close()
    }
}
```

#### 1.2 Session Model

**File**: `models/Session.groovy`

```groovy
package models

import groovy.transform.Canonical

@Canonical
class Session {
    String id
    String projectHash
    String directory
    String title
    String agentType
    String model
    Date createdAt
    Date updatedAt
    boolean isArchived = false
    String metadata
}
```

#### 1.3 Session CRUD Operations

**Add to `SessionManager.groovy`**

```groovy
// Create new session
String createSession(String directory, String agentType = "BUILD", String model = "glm-4.7") {
    def sessionId = generateSessionId()
    def projectHash = RootDetector.findGitRoot(directory) ?: "global"
    def now = new Date()

    database.execute(
        "INSERT INTO sessions (id, project_hash, directory, agent_type, model, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        [sessionId, projectHash, directory, agentType, model, now, now]
    )

    return sessionId
}

// Load session
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

// List sessions for project
List<Session> listSessions(String projectHash) {
    def rows = database.rows(
        "SELECT * FROM sessions WHERE project_hash = ? AND is_archived = FALSE ORDER BY updated_at DESC",
        [projectHash]
    )

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
            isArchived: row.is_archived
        )
    }
}

// Update session timestamp
void touchSession(String sessionId) {
    database.execute(
        "UPDATE sessions SET updated_at = ? WHERE id = ?",
        [new Date(), sessionId]
    )
}

// Delete session
void deleteSession(String sessionId) {
    database.execute("DELETE FROM sessions WHERE id = ?", [sessionId])
    // Messages cascade deleted automatically
}

private String generateSessionId() {
    return "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
}
```

#### 1.4 Message Storage

**File**: `core/MessageStore.groovy`

```groovy
package core

import models.Message as ModelMessage
import java.sql.*

@Singleton
class MessageStore {
    private Sql database

    MessageStore() {
        this.database = SessionManager.instance.getDatabase()
    }

    // Save message
    String saveMessage(String sessionId, ModelMessage message) {
        def messageId = generateMessageId()

        database.execute("""
            INSERT INTO messages
            (id, session_id, role, content, created_at, parent_id, tokens_input, tokens_output, finish_reason, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                messageId,
                sessionId,
                message.role,
                message.content,
                new Date(),
                null,  // parent_id (future)
                null,  // tokens_input
                null,  // tokens_output
                null,  // finish_reason
                null   // metadata
            ]
        )

        return messageId
    }

    // Load messages for session
    List<ModelMessage> getMessages(String sessionId, int limit = 0) {
        String sql = "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at ASC"
        def params = [sessionId]

        if (limit > 0) {
            sql += " LIMIT ?"
            params << limit
        }

        def rows = database.rows(sql, params)

        return rows.collect { row ->
            new ModelMessage(
                role: row.role,
                content: row.content
            )
        }
    }

    private String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }
}
```

#### 1.5 Session CLI Command

**File**: `commands/SessionCommand.groovy`

```groovy
package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.SessionManager
import core.MessageStore
import models.Session
import tui.OutputFormatter

@Command(name = "session", description = "Manage conversation sessions", mixinStandardHelpOptions = true, subcommands = [
    SessionListCommand.class,
    SessionResumeCommand.class,
    SessionDeleteCommand.class,
    SessionInfoCommand.class
])
class SessionCommand implements Runnable {
    @Override
    void run() {
        // Default to list
        new SessionListCommand().run()
    }
}

@Command(name = "list", description = "List all sessions")
class SessionListCommand implements Runnable {
    @Option(names = ["-a", "--all"], description = "Include archived sessions")
    boolean all = false

    @Option(names = ["-p", "--project"], description = "Filter by project path")
    String projectPath

    @Override
    void run() {
        def sessionManager = SessionManager.instance
        def projectHash = projectPath ?
            RootDetector.findGitRoot(projectPath) :
            RootDetector.findGitRoot(System.getProperty("user.dir"))

        def sessions = sessionManager.listSessions(projectHash ?: "global")

        if (sessions.isEmpty()) {
            println "No sessions found."
            return
        }

        println "\nSessions:"
        println "─" * 80
        sessions.each { session ->
            println "ID:      ${session.id}"
            println "Title:    ${session.title ?: 'Untitled'}"
            println "Agent:    ${session.agentType}"
            println "Model:    ${session.model}"
            println "Updated:  ${session.updatedAt}"
            println "Directory: ${session.directory}"
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

        println "Resuming session: ${session.title ?: sessionId}"
        println "Last updated: ${session.updatedAt}"
        println "\nStarting TUI in session mode..."

        // Launch TUI with session context
        def tui = new LanternaTUI()
        tui.start(session.model, session.directory, sessionId)
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
        if (!force) {
            def confirm = System.console().readLine("Delete session '${sessionId}'? (y/N): ")
            if (!confirm.equalsIgnoreCase("y")) {
                println "Cancelled."
                return
            }
        }

        def sessionManager = SessionManager.instance
        sessionManager.deleteSession(sessionId)
        println "Session '${sessionId}' deleted."
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

        def messageStore = new MessageStore()
        def messages = messageStore.getMessages(sessionId)

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
        println "Messages:     ${messages.size()}"
        println "─" * 80
    }
}
```

#### 1.6 Integration Points

**Modify `Agent.groovy`**

```groovy
class Agent {
    // ... existing fields ...
    private SessionManager sessionManager
    private MessageStore messageStore
    private String currentSessionId

    Agent(String apiKey, String model, String sessionId = null) {
        // ... existing initialization ...

        this.sessionManager = SessionManager.instance
        this.messageStore = new MessageStore()
        this.currentSessionId = sessionId ?: sessionManager.createSession(
            System.getProperty("user.dir"),
            "BUILD",
            model
        )
    }

    void run(String prompt) {
        // Load existing messages if session exists
        if (currentSessionId) {
            def existingMessages = messageStore.getMessages(currentSessionId)
            history.addAll(existingMessages)
        }

        // ... rest of existing run() logic ...

        // Save user message
        def userMsgId = messageStore.saveMessage(currentSessionId,
            new Message("user", prompt))
        sessionManager.touchSession(currentSessionId)

        // After LLM response, save assistant message
        // ... in the response handling code ...
        def assistantMsgId = messageStore.saveMessage(currentSessionId,
            new Message("assistant", responseContent))
        sessionManager.touchSession(currentSessionId)
    }
}
```

**Modify `ChatCommand.groovy`**

```groovy
@Command(name = "chat", description = "Start a chat session", mixinStandardHelpOptions = true)
class ChatCommand implements Runnable {
    @Option(names = ["-s", "--session"], description = "Resume existing session")
    String sessionId

    // ... existing fields ...

    @Override
    void run() {
        // ... existing config setup ...

        def sessionManager = SessionManager.instance
        def messageStore = new MessageStore()

        // Create or resume session
        String currentSessionId = sessionId ?: sessionManager.createSession(
            System.getProperty("user.dir"),
            "CHAT",
            modelToUse
        )

        // Load existing messages
        def messages = messageStore.getMessages(currentSessionId)
        println "Session: ${currentSessionId} (${messages.size()} messages)"
        println "Type 'exit' or 'quit' to stop\n"

        Scanner scanner = new Scanner(System.in)
        while (true) {
            print "> "
            if (!scanner.hasNextLine()) break
            String input = scanner.nextLine().trim()

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break
            }
            if (input.isEmpty()) continue

            // Save and process
            messageStore.saveMessage(currentSessionId, new Message("user", input))
            sessionManager.touchSession(currentSessionId)

            // ... existing processing logic ...

            // Save assistant response
            messageStore.saveMessage(currentSessionId, new Message("assistant", fullResponse))
            sessionManager.touchSession(currentSessionId)
        }
    }
}
```

**Modify `GlmCli.groovy`**

```groovy
@Command(name = "glm", mixinStandardHelpOptions = true, version = "glm-cli 1.0.0",
        description = "GLM-4 based AI coding agent",
        subcommands = [ChatCommand.class, AgentCommand.class, AuthCommand.class,
                       InitCommand.class, SessionCommand.class])  // Add SessionCommand
class GlmCli implements Runnable {
    // ... existing code ...
}
```

---

### Phase 2: Enhanced Message Storage (Week 3)

**Objective**: Add message parts for detailed tracking (tool calls, reasoning, etc.)

#### 2.1 Message Parts Table

```sql
CREATE TABLE message_parts (
    id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    data TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    is_compacted BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);
```

#### 2.2 Message Part Types

```groovy
enum MessagePartType {
    TEXT,           // Regular text content
    TOOL_CALL,      // Tool invocation
    TOOL_OUTPUT,    // Tool result
    SNAPSHOT,       // Git/file snapshot
    REASONING,      // Thinking process
    SUBTASK         // Subagent invocation
}
```

#### 2.3 Save Message Parts

```groovy
void saveMessagePart(String messageId, MessagePartType type, Map<String, Object> data) {
    database.execute("""
        INSERT INTO message_parts (id, message_id, type, data, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        [
            generateMessagePartId(),
            messageId,
            type.name(),
            new ObjectMapper().writeValueAsString(data),
            new Date()
        ]
    )
}
```

---

### Phase 3: Token Tracking (Week 4)

**Objective**: Track token usage and costs

#### 3.1 Token Stats Table

```groovy
class TokenTracker {
    private Sql database

    void recordTokens(String sessionId, int inputTokens, int outputTokens) {
        database.execute("""
            INSERT INTO token_stats (session_id, total_tokens)
            VALUES (?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
            total_tokens = total_tokens + ?
            """,
            [sessionId, inputTokens + outputTokens, inputTokens + outputTokens]
        )
    }

    TokenStats getTokenStats(String sessionId) {
        def row = database.firstRow(
            "SELECT * FROM token_stats WHERE session_id = ?",
            [sessionId]
        )

        return row ? new TokenStats(
            sessionId: row.session_id,
            totalTokens: row.total_tokens,
            totalCost: row.total_cost
        ) : null
    }
}
```

#### 3.2 Cost Calculation

```groovy
// GLM-4 pricing (example)
BigDecimal calculateCost(int inputTokens, int outputTokens) {
    def inputCost = (inputTokens / 1000) * 0.012  // $0.012/1K input
    def outputCost = (outputTokens / 1000) * 0.012 // $0.012/1K output
    return inputCost + outputCost
}
```

---

### Phase 4: Session Features (Week 5)

**Objective**: Add forking, archiving, export

#### 4.1 Fork Session

```groovy
String forkSession(String originalSessionId, String messageId = null) {
    def original = getSession(originalSessionId)

    // Get messages up to fork point
    def messages = messageId ?
        getMessagesBefore(originalSessionId, messageId) :
        getMessages(originalSessionId)

    // Create new session
    def newSessionId = createSession(
        original.directory,
        original.agentType,
        original.model
    )

    // Copy messages
    messages.each { msg ->
        saveMessage(newSessionId, msg)
    }

    // Update title
    database.execute(
        "UPDATE sessions SET title = ? WHERE id = ?",
        ["Fork of ${original.title ?: originalSessionId}", newSessionId]
    )

    return newSessionId
}
```

#### 4.2 Archive Session

```groovy
void archiveSession(String sessionId) {
    database.execute(
        "UPDATE sessions SET is_archived = TRUE WHERE id = ?",
        [sessionId]
    )
}
```

#### 4.3 Export Session

```groovy
void exportSession(String sessionId, String outputPath) {
    def session = getSession(sessionId)
    def messages = getMessages(sessionId)

    def exportData = [
        session: session,
        messages: messages
    ]

    def jsonOutput = new ObjectMapper().writeValueAsString(exportData)
    new File(outputPath).text = jsonOutput
}
```

---

### Phase 5: Advanced Features (Week 6-8)

**Objective**: Compaction, search, analytics

#### 5.1 Session Compaction

```groovy
void compactSession(String sessionId, GlmClient client) {
    def messages = getMessages(sessionId)

    // If under threshold, skip
    if (messages.size() < 20) return

    // Generate summary using LLM
    def prompt = """
    Summarize the following conversation in a concise way.
    Focus on: What was accomplished, what decisions were made, what remains to be done.

    Conversation:
    ${messages.collect { "${it.role}: ${it.content}" }.join('\n')}
    """

    def summaryResponse = client.sendMessage(new ChatRequest(
        model: "glm-4.7",
        messages: [new Message("user", prompt)]
    ))

    def summary = summaryResponse.choices[0].message.content

    // Create summary message
    def summaryId = saveMessage(sessionId,
        new Message("system", "[COMPACTED] ${summary}"))

    // Mark old messages as compacted
    database.execute("""
        UPDATE message_parts SET is_compacted = TRUE
        WHERE message_id IN (
            SELECT id FROM messages WHERE session_id = ? AND id < ?
        )
        """,
        [sessionId, summaryId]
    )
}
```

#### 5.2 Search Messages

```groovy
List<Session> searchSessions(String query, String projectHash = null) {
    String sql = """
        SELECT DISTINCT s.*
        FROM sessions s
        INNER JOIN messages m ON s.id = m.session_id
        WHERE m.content LIKE ?
    """

    def params = ["%${query}%"]

    if (projectHash) {
        sql += " AND s.project_hash = ?"
        params << projectHash
    }

    sql += " ORDER BY s.updated_at DESC"

    def rows = database.rows(sql, params)

    return rows.collect { row ->
        new Session(
            id: row.id,
            title: row.title,
            updatedAt: row.updated_at
        )
    }
}
```

#### 5.3 Session Analytics

```groovy
Map<String, Object> getSessionStats(String sessionId) {
    def session = getSession(sessionId)
    def messages = getMessages(sessionId)
    def tokenStats = tokenTracker.getTokenStats(sessionId)

    def userMessages = messages.findAll { it.role == "user" }
    def assistantMessages = messages.findAll { it.role == "assistant" }

    return [
        sessionId: sessionId,
        messageCount: messages.size(),
        userMessages: userMessages.size(),
        assistantMessages: assistantMessages.size(),
        totalTokens: tokenStats?.totalTokens ?: 0,
        avgTokensPerMessage: tokenStats?.totalTokens ? tokenStats.totalTokens / messages.size() : 0,
        sessionDuration: (session.updatedAt.time - session.createdAt.time) / 1000 / 60, // minutes
        lastActivity: session.updatedAt
    ]
}
```

---

## Testing Strategy

### Unit Tests

**File**: `tests/SessionManagerTest.groovy`

```groovy
import core.SessionManager
import models.Session
import org.junit.jupiter.api.*

class SessionManagerTest {
    SessionManager sessionManager

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager()
        // Use test database
        sessionManager.setDatabase(Sql.newInstance("jdbc:h2:mem:test"))
    }

    @Test
    void testCreateSession() {
        def sessionId = sessionManager.createSession("/test/dir", "BUILD", "glm-4.7")

        assert sessionId != null
        assert sessionId.startsWith("ses_")

        def session = sessionManager.getSession(sessionId)
        assert session != null
        assert session.agentType == "BUILD"
        assert session.model == "glm-4.7"
    }

    @Test
    void testListSessions() {
        sessionManager.createSession("/test/dir1", "BUILD", "glm-4.7")
        sessionManager.createSession("/test/dir1", "PLAN", "glm-4.7")

        def sessions = sessionManager.listSessions(RootDetector.findGitRoot("/test/dir1"))

        assert sessions.size() == 2
    }

    @Test
    void testDeleteSession() {
        def sessionId = sessionManager.createSession("/test/dir", "BUILD", "glm-4.7")

        sessionManager.deleteSession(sessionId)

        def session = sessionManager.getSession(sessionId)
        assert session == null
    }
}
```

### Integration Tests

**File**: `tests/SessionIntegrationTest.groovy`

```groovy
class SessionIntegrationTest {
    @Test
    void testFullSessionLifecycle() {
        def sessionManager = SessionManager.instance

        // Create session
        def sessionId = sessionManager.createSession(System.getProperty("user.dir"))

        // Send messages
        def messageStore = new MessageStore()
        messageStore.saveMessage(sessionId, new Message("user", "Hello"))
        messageStore.saveMessage(sessionId, new Message("assistant", "Hi there!"))

        // Retrieve
        def messages = messageStore.getMessages(sessionId)

        assert messages.size() == 2
        assert messages[0].content == "Hello"
        assert messages[1].content == "Hi there!"

        // Cleanup
        sessionManager.deleteSession(sessionId)
    }
}
```

---

## Migration Path

### From In-Memory to H2

1. **Phase 1**: Implement H2 alongside in-memory
2. **Phase 2**: Add optional persistence flag in config
3. **Phase 3**: Default to persistence, in-memory as fallback
4. **Phase 4**: Remove in-memory code

### Database Migrations

**File**: `config/migrations/001_initial_schema.sql`

```sql
-- Migration 001: Initial schema
CREATE TABLE IF NOT EXISTS sessions (...);
CREATE TABLE IF NOT EXISTS messages (...);
```

**Migration Runner**

```groovy
class MigrationRunner {
    void runMigrations(Sql database) {
        def currentVersion = getDatabaseVersion(database)

        migrations.each { migration ->
            if (migration.version > currentVersion) {
                println "Running migration: ${migration.name}"
                migration.execute(database)
                updateDatabaseVersion(database, migration.version)
            }
        }
    }
}
```

---

## Configuration

**File**: `core/Config.groovy` (add to existing)

```groovy
@JsonProperty("persistence")
PersistenceConfig persistence = new PersistenceConfig()

static class PersistenceConfig {
    @JsonProperty("enabled")
    Boolean enabled = true

    @JsonProperty("database_path")
    String databasePath = "~/.glm/sessions.mv.db"

    @JsonProperty("auto_compact")
    Boolean autoCompact = true

    @JsonProperty("compact_threshold")
    Integer compactThreshold = 100000  // tokens

    @JsonProperty("retention_days")
    Integer retentionDays = 30
}
```

**Config File Example**: `~/.glm/config.toml`

```toml
[persistence]
enabled = true
database_path = "~/.glm/sessions.mv.db"
auto_compact = true
compact_threshold = 100000  # Auto-compact after 100K tokens
retention_days = 30  # Archive sessions older than 30 days
```

---

## Performance Considerations

### Indexes

- **Project index**: Fast session lookup by project
- **Created index**: Sorted by time for recent lists
- **Session index**: Fast message retrieval

### Connection Pooling

```groovy
import org.h2.jdbcx.JdbcConnectionPool

// Use connection pool for concurrent access
def pool = new JdbcConnectionPool(
    "jdbc:h2:~/glm/sessions",
    "sa", ""
)
```

### Batch Inserts

```groovy
// For bulk message loading
database.withBatch(100) { stmt ->
    messages.each { msg ->
        stmt.addBatch(insertSql, [msg.id, msg.content, ...])
    }
}
```

---

## Security & Privacy

### Data Encryption (Optional - Future)

```groovy
import javax.crypto.Cipher

def encrypt(String data, String key) {
    def cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, createSecretKey(key))
    return cipher.doFinal(data.bytes)
}
```

### Sensitive Data Handling

- Never store API keys in database
- Sanitize file paths before storage
- Use parameterized queries (already done with Groovy SQL)

---

## Documentation

### User Documentation

**File**: `docs/SESSION_MANAGEMENT.md`

```markdown
# Session Management

## Overview

GLM-CLI now supports persistent sessions, allowing you to resume conversations and maintain context across CLI sessions.

## Usage

### List Sessions
```bash
glm session list
glm session list --project /path/to/project
```

### Resume Session
```bash
glm session resume ses_abc123
```

### Delete Session
```bash
glm session delete ses_abc123
glm session delete ses_abc123 --force
```

### Session Info
```bash
glm session info ses_abc123
```

### Integration with Commands

Sessions work automatically with:
- `glm agent` - Creates session for each agent run
- `glm chat` - Maintains chat history
- `glm` (TUI) - Resumable sessions

### Configuration

Configure session persistence in `~/.glm/config.toml`:

```toml
[persistence]
enabled = true
database_path = "~/.glm/sessions.mv.db"
auto_compact = true
compact_threshold = 100000
retention_days = 30
```
```

---

## Rollback Plan

If issues arise:

1. **Disable persistence** in config:
   ```toml
   [persistence]
   enabled = false
   ```

2. **Clear database**:
   ```bash
   rm ~/.glm/sessions.mv.db
   ```

3. **Backup database** before upgrades:
   ```bash
   cp ~/.glm/sessions.mv.db ~/.glm/sessions.mv.db.backup
   ```

---

## Success Criteria

- ✅ Session creation and retrieval works
- ✅ Message storage and retrieval works
- ✅ Session CLI commands functional
- ✅ Integration with Agent/Chat/TUI works
- ✅ Database persists across restarts
- ✅ Performance is acceptable (< 100ms queries)
- ✅ Test coverage > 80%
- ✅ Documentation complete
- ✅ Backward compatibility maintained

---

## Timeline

| Week | Tasks | Deliverables |
|-------|--------|--------------|
| 1 | Database setup, SessionManager, Session model | Working persistence |
| 2 | MessageStore, SessionCommand, CLI integration | Session commands work |
| 3 | Message parts, token tracking | Detailed tracking |
| 4 | Testing, bug fixes, documentation | Stable release |
| 5+ | Advanced features (compaction, search) | Full-featured |

**Total**: 4 weeks for MVP, 6-8 weeks for full features

---

## Open Questions

1. **Should sessions be project-scoped or global?**
   - Current: Project-scoped by git root
   - Alternative: Global with project filter

2. **What's the default session title strategy?**
   - AI-generated from first message?
   - User-provided via prompt?
   - Timestamp-based?

3. **Should we support session sharing?**
   - Export/import functionality?
   - Collaborative features?

4. **How to handle database upgrades?**
   - Auto-migration on startup?
   - Manual migration command?
   - Version compatibility matrix?

---

## References

- [H2 Database Documentation](https://www.h2database.com/html/main.html)
- [Groovy SQL Documentation](http://docs.groovy-lang.org/latest/html/documentation/#sql)
- [LangChain4j Session Management](https://docs.langchain4j.dev/)
- [OpenCode Persistence Analysis](../opencode_analysis.md)
