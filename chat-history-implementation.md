# Chat History & Memory Implementation Plan for glm-cli-jbang

## Executive Summary

The glm-cli-jbang project currently has complete infrastructure for chat history (SessionManager, MessageStore, H2 database) and a working implementation in ChatCommand (CLI mode). However, neither TUI implementation (LanternaTUI nor Tui4jTUI) uses this infrastructure - messages are stored only in memory and lost on exit.

This plan adapts OpenCode's chat history architecture to work within glm-cli-jbang's existing Groovy/H2 ecosystem, focusing on Lanterna TUI as primary implementation target.

---

## Architecture Comparison

### OpenCode's Approach (TypeScript/File-based)

**Key Components:**
- **Storage**: JSON files in `~/.local/share/opencode/storage/`
- **Structure**: Session → Message → Part (hierarchical, streaming-friendly)
- **Sync**: Event-driven in-memory store with background loading
- **Compaction**: AI-generated summaries with "continue" markers
- **Features**: Message parts (text, reasoning, tools, files), forking, diffs

**Data Flow:**
```
User Input → MessageProcessor → Stream → EventBus → InMemoryStore → TUI Display
               ↓
         MessageStore (JSON parts) → Background Sync
```

### glm-cli-jbang Current State (Groovy/H2)

**Existing Infrastructure:**
- ✅ **SessionManager**: Creates/retrieves sessions from H2 database
- ✅ **MessageStore**: Saves/retrieves messages to H2 database
- ✅ **Database Schema**: sessions, messages, token_stats tables
- ✅ **ChatCommand**: Full persistence implementation (CLI mode only)

**TUI Gaps:**
- ❌ LanternaTUI: Creates session but never loads/saves messages
- ❌ Tui4jTUI: No session management, only in-memory `List<Map>`
- ❌ No session selection/switching in TUI
- ❌ No history browsing UI
- ❌ Missing compaction, pruning, context management

---

## Implementation Strategy

### Phase 1: Basic Persistence (Foundation)
**Goal**: Enable message saving/loading in Lanterna TUI

**Files to Create/Modify:**
1. `tui/LanternaTUI.groovy` - Integrate MessageStore
2. `tui/lanterna/widgets/SessionHistoryPanel.groovy` - NEW: Session browser widget
3. `tui/lanterna/widgets/SessionSelectDialog.groovy` - NEW: Session selection dialog

**Key Changes in LanternaTUI:**
```groovy
import core.MessageStore
import core.SessionManager

class LanternaTUI {
    private MessageStore messageStore
    private SessionManager sessionManager
    private List<models.Message> loadedHistory = []

    void start(String model, String cwd = null) {
        this.sessionManager = SessionManager.instance
        this.messageStore = new MessageStore()
        
        // Show session selection dialog
        def dialog = new SessionSelectDialog(currentCwd) { session ->
            if (session == null) {
                this.sessionId = sessionManager.createSession(currentCwd, 'BUILD', model)
                loadedHistory = []
            } else {
                this.sessionId = session.id
                loadSessionHistory(sessionId)
            }
            initializeTUI()
        }
        textGUI.addWindowAndWait(dialog)
    }

    private void loadSessionHistory(String sessionId) {
        loadedHistory = messageStore.getMessages(sessionId)
        activityLogPanel.clear()
        loadedHistory.each { msg ->
            if (msg.role == 'user') {
                activityLogPanel.appendUserMessage(msg.content)
            } else if (msg.role == 'assistant') {
                activityLogPanel.appendAIResponse(msg.content)
            }
        }
    }

    private void saveMessage(models.Message message) {
        messageStore.saveMessage(sessionId, message)
        sessionManager.touchSession(sessionId)
        loadedHistory << message
    }
}
```

### Phase 2: Session Management UI
**Goal**: Add session browsing and switching

**New Widgets:**
- `SessionHistoryPanel` - Displays list of sessions with message counts
- `SessionSelectDialog` - Modal dialog for session selection
- `/session` command - Switch sessions during runtime

### Phase 3: Tui4jTUI Parity
**Goal**: Add basic session support to Tui4jTUI

Replace `List<Map> conversationHistory` with `List<models.Message>` and integrate MessageStore.

### Phase 4: Sidebar Session Browser
**Goal**: Add session history to existing sidebar

Create `JexerSessionHistorySection` that displays recent sessions for the current project.

### Phase 5: Context Management & Compaction (Advanced)
**Goal**: Implement context window management

**New Components:**
- `HistoryPruner` - Token-based history pruning
- `TokenCounter` - Token counting utility
- `SessionCompactor` - Session compaction logic
- `MemoryConfig` section in Config

**HistoryPruner Logic:**
- Keep all system messages
- Keep last N recent messages (configurable, default: 10)
- Prune middle messages until token limit satisfied
- Maximum pruning: 40k tokens

### Phase 6: Compaction
**Goal**: Automatically compact old messages when context window fills

**Compaction Process:**
1. Check if total tokens exceed threshold (default: 80k)
2. Generate summary of old messages
3. Add compaction marker message with summary
4. Update token_stats.last_compaction
5. Add manual `/compact` command

### Phase 7: Additional Features
- Auto-generated session titles
- Prompt history navigation (up/down arrows, stored in JSONL)
- Session export to Markdown
- Message threading (optional)
- Git diff tracking (optional)

---

## File Structure Summary

### New Files to Create
```
glm-cli-jbang/
├── core/
│   ├── HistoryPruner.groovy
│   ├── TokenCounter.groovy
│   └── SessionCompactor.groovy
├── tui/
│   └── lanterna/widgets/
│       ├── SessionHistoryPanel.groovy
│       └── SessionSelectDialog.groovy
└── models/
    └── PromptHistory.groovy
```

### Existing Files to Modify
```
glm-cli-jbang/
├── tui/
│   ├── LanternaTUI.groovy
│   ├── Tui4jTUI.groovy
│   └── lanterna/widgets/
│       └── SidebarPanel.groovy
└── core/
    └── Config.groovy
```

---

## Database Schema

### Existing Tables (No Changes Needed)
```sql
sessions (id, project_hash, directory, title, agent_type, model, created_at, updated_at, is_archived, metadata)
messages (id, session_id, role, content, created_at, parent_id, tokens_input, tokens_output, tokens_reasoning, finish_reason, metadata)
token_stats (session_id, total_tokens, total_cost, last_compaction)
```

### New Tables (Optional)
```sql
prompt_history (id, input, created_at, parts)
session_diff (session_id, diffs, updated_at)
```

---

## Implementation Phases

| Phase | Description | Estimated Time |
|-------|-------------|----------------|
| 1 | Basic Persistence (MessageStore integration) | 2-3 days |
| 2 | Session Management UI (dialogs, commands) | 2-3 days |
| 3 | Tui4jTUI Parity | 1-2 days |
| 4 | Sidebar Integration | 1 day |
| 5 | Context Management (pruning, token counting) | 2-3 days |
| 6 | Compaction | 2-3 days |
| 7 | Advanced Features | 3-4 days |
| **Total** | | **13-19 days** |

---

## Configuration

Add to `~/.glm/config.json`:
```json
{
  "memory": {
    "maxContextTokens": 128000,
    "autoCompact": true,
    "compactThreshold": 80000,
    "keepRecentMessages": 10
  }
}
```

---

## User Experience Flow

### New User
1. Run `glm tui`
2. See session selection dialog
3. Choose "New Session"
4. Start chatting (auto-saved)
5. Exit and restart → history preserved

### Existing User
1. Run `glm tui`
2. See list of existing sessions with message counts
3. Select session to resume
4. Continue chatting in context

### Power User
- Configure memory limits in config
- Use up/down for prompt history
- Export sessions with `/export`
- Manual compaction with `/compact`

---

## Success Criteria

### Must-Have (MVP)
- [ ] Messages persist across TUI restarts
- [ ] Can resume existing sessions
- [ ] Can switch between sessions
- [ ] Basic context management (pruning)

### Should-Have
- [ ] Auto-compaction
- [ ] Prompt history navigation
- [ ] Session export to Markdown
- [ ] Session browser in sidebar

### Nice-to-Have
- [ ] AI-generated summaries
- [ ] Message threading
- [ ] Git diff tracking
- [ ] Auto-generated titles

---

## Questions for Team

1. Should we prioritize LanternaTUI or implement both TUIs in parallel?
2. Is AI-generated compaction summaries important for MVP?
3. Do we need message threading/forking support?
4. What's the target token limit for compaction? (Plan uses 80,000)
5. Should we integrate model-specific tokenizers immediately?

---

## References

### OpenCode Files
- `/home/kevintan/opencode/packages/opencode/src/session/index.ts`
- `/home/kevintan/opencode/packages/opencode/src/session/message-v2.ts`
- `/home/kevintan/opencode/packages/opencode/src/storage/storage.ts`
- `/home/kevintan/opencode/packages/opencode/src/cli/cmd/tui/routes/session/index.tsx`

### glm-cli-jbang Files
- `/home/kevintan/glm-cli-jbang/core/SessionManager.groovy`
- `/home/kevintan/glm-cli-jbang/core/MessageStore.groovy`
- `/home/kevintan/glm-cli-jbang/commands/ChatCommand.groovy`
- `/home/kevintan/glm-cli-jbang/tui/LanternaTUI.groovy`
