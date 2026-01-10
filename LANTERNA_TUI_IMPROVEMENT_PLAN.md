# Lanterna TUI Improvement Plan

> Inspired by OpenCode's TUI architecture (SolidJS + OpenTUI) adapted for Lanterna/Groovy

## Executive Summary

This document outlines a comprehensive plan to improve the GLM-CLI Lanterna TUI by adopting design patterns and UX principles from [OpenCode](https://github.com/sst/opencode). While OpenCode uses SolidJS with `@opentui/solid` for terminal rendering, we can translate their architectural patterns to Lanterna's component model.

---

## 1. Current State Analysis

### GLM-CLI Lanterna TUI Structure
```
tui/
├── LanternaTUI.groovy              # Main TUI orchestrator
├── LanternaTheme.groovy            # Theme/colors
├── lanterna/
│   ├── widgets/
│   │   ├── ActivityLogPanel.groovy      # Scrollable conversation log
│   │   ├── CommandInputPanel.groovy     # User input with autocomplete
│   │   ├── SidebarPanel.groovy          # Right sidebar
│   │   ├── AutocompletePopup.groovy     # @ mentions and / commands
│   │   ├── ModelSelectionDialog.groovy  # Model picker
│   │   └── Tooltip.groovy               # Hover tooltips
│   └── sidebar/
│       ├── SessionInfoSection.groovy
│       ├── TokenSection.groovy
│       ├── LspSection.groovy
│       └── ModifiedFilesSection.groovy
```

### Current Strengths
- ✅ Working sidebar with responsive width
- ✅ Autocomplete for `@` mentions and `/` commands  
- ✅ Streaming response display with animation
- ✅ Agent switching (BUILD/PLAN)
- ✅ Token tracking and LSP integration
- ✅ Export log functionality (Ctrl+S)

### Current Weaknesses
- ❌ No diff rendering for file edits
- ❌ No inline tool status indicators
- ❌ Basic text-only activity log
- ❌ No permission prompts for write operations
- ❌ No todo list visualization
- ❌ Limited keyboard shortcuts
- ❌ No message navigation (jump to next/prev)
- ❌ No timeline/history fork support
- ❌ No theming beyond basic dark mode

---

## 2. OpenCode Design Patterns to Adopt

### 2.1 Component Architecture

OpenCode uses a clear separation of concerns:

| OpenCode Component | Purpose | GLM-CLI Equivalent |
|-------------------|---------|-------------------|
| `session/index.tsx` | Main session view with scroll container | `LanternaTUI.groovy` |
| `session/sidebar.tsx` | Right sidebar with context info | `SidebarPanel.groovy` |
| `session/header.tsx` | Session title + token/cost display | Status bar (partial) |
| `session/footer.tsx` | Directory + status indicators | Status bar (partial) |
| `session/permission.tsx` | Permission prompts for writes | **NEW** |
| `component/prompt/` | Input with history + autocomplete | `CommandInputPanel.groovy` |

**Action Items:**
1. Split status bar into distinct Header and Footer components
2. Create dedicated PermissionDialog component
3. Implement proper Header with session title + context percentage

### 2.2 Message Rendering Patterns

OpenCode renders messages with rich formatting:

```
┌─────────────────────────────────────────┐
│ > User message                          │
│                                         │
│ Assistant response with:                │
│ - Inline tool indicators: → Read file   │
│ - Block tool output: ┌ Edit file.ts     │
│                      │ diff content     │
│                      └──────────────────│
│ - Todo checkboxes: ☑ Task completed     │
└─────────────────────────────────────────┘
```

**Action Items:**
1. Implement `InlineTool` rendering for quick tool calls
2. Implement `BlockTool` rendering for diffs/writes
3. Add spinning indicators for pending operations
4. Support markdown rendering in activity log

### 2.3 Tool Visualization

OpenCode distinguishes between inline and block tool displays:

#### Inline Tools (Single line)
```
→ Read src/main.groovy
% Grep "pattern" (45 matches)
◇ Glob **/*.ts (12 files)
```

#### Block Tools (Multi-line with content)
```
┌ ← Edit src/config.groovy
│ @@ -10,3 +10,5 @@
│  existing line
│ +new line added
│ -removed line
└────────────────────────────
```

**Action Items:**
1. Create `InlineToolRenderer` class
2. Create `BlockToolRenderer` class with diff support
3. Add color-coded prefixes (→, ←, %, ◇, ◉)

---

## 3. Detailed Implementation Plan

### Phase 1: Enhanced Layout (2-3 days)

#### 1.1 New Header Component

Create `tui/lanterna/widgets/HeaderPanel.groovy`:

```groovy
class HeaderPanel extends Panel {
    private Label titleLabel
    private Label contextLabel
    private Label costLabel
    
    void updateContext(int tokens, int percentage, BigDecimal cost) {
        contextLabel.setText("${tokens.toLocaleString()} tokens • ${percentage}%")
        costLabel.setText("\$${String.format('%.4f', cost)}")
    }
}
```

**Features:**
- Session title (left-aligned)
- Token count + percentage of context (right-aligned)
- Cost display
- Responsive layout based on terminal width

#### 1.2 New Footer Component

Create `tui/lanterna/widgets/FooterPanel.groovy`:

```groovy
class FooterPanel extends Panel {
    private Label directoryLabel
    private Label lspIndicator
    private Label mcpIndicator
    
    void updateLspStatus(int count, boolean hasErrors)
    void updateMcpStatus(int connected, int errors)
}
```

**Features:**
- Current working directory
- LSP status: `• 3 LSP` (green dot if active)
- MCP status: `⊙ 2 MCP` (if implemented)
- Keyboard shortcut hints

#### 1.3 Improved Main Layout

```
┌──────────────────────────────────────────────────────────┬────────────────────┐
│ # Session Title                          12,345 • 15%   │ Context            │
├──────────────────────────────────────────────────────────┤ 12,345 tokens      │
│                                                          │ 15% used           │
│                     Activity Log                         │ $0.0123 spent      │
│                                                          │                    │
│ > User prompt                                            │ LSP                │
│                                                          │ • groovy /project  │
│ GLM> Response with tool calls...                         │                    │
│ → Read README.md                                         │ Modified Files     │
│ ← Edit config.groovy                                     │ +12 -3 config.groovy│
│   +new line                                              │ +45 -0 NewFile.groovy│
│   -old line                                              │                    │
│                                                          │ Todo               │
│                                                          │ ☑ Read files       │
│                                                          │ ☐ Implement feature│
├──────────────────────────────────────────────────────────┤                    │
│ > Enter prompt...                                        │                    │
├──────────────────────────────────────────────────────────┤────────────────────│
│ /home/user/project              • 1 LSP  /status        │ OpenCode v0.1.0    │
└──────────────────────────────────────────────────────────┴────────────────────┘
```

### Phase 2: Rich Message Rendering (3-4 days)

#### 2.1 Message Types

Create structured message rendering with distinct visual styles:

```groovy
// tui/lanterna/rendering/MessageRenderer.groovy
class MessageRenderer {
    
    String renderUserMessage(String content) {
        return "> ${content}"
    }
    
    String renderAssistantMessage(String content, List<ToolCall> tools) {
        StringBuilder sb = new StringBuilder()
        sb.append("GLM> ")
        sb.append(content)
        
        tools.each { tool ->
            if (tool.hasBlockOutput()) {
                sb.append(renderBlockTool(tool))
            } else {
                sb.append(renderInlineTool(tool))
            }
        }
        
        return sb.toString()
    }
    
    private String renderInlineTool(ToolCall tool) {
        def icon = TOOL_ICONS[tool.name] ?: '⚙'
        def status = tool.pending ? '...' : ''
        return "${icon} ${tool.summary}${status}"
    }
    
    private String renderBlockTool(ToolCall tool) {
        // Render with borders and diff content
    }
}
```

#### 2.2 Diff Rendering

Create `tui/lanterna/rendering/DiffRenderer.groovy`:

```groovy
class DiffRenderer {
    
    String render(String diff, int maxWidth, String mode = 'unified') {
        def lines = parseDiff(diff)
        StringBuilder sb = new StringBuilder()
        
        lines.each { line ->
            switch (line.type) {
                case 'add':
                    sb.append("+ ${line.content}\n")
                    break
                case 'remove':
                    sb.append("- ${line.content}\n")
                    break
                case 'context':
                    sb.append("  ${line.content}\n")
                    break
                case 'header':
                    sb.append("@@ ${line.content} @@\n")
                    break
            }
        }
        
        return sb.toString()
    }
}
```

**Features:**
- Unified diff view (default for narrow terminals)
- Side-by-side diff view (for wide terminals >120 cols)
- Line numbers
- Syntax highlighting (via ANSI colors)
- Word-level diff highlighting

#### 2.3 Todo List Rendering

Visualize todo items from `TodoWriteTool`:

```groovy
class TodoRenderer {
    
    static final Map<String, String> STATUS_ICONS = [
        'completed': '☑',
        'in-progress': '◐', 
        'todo': '☐'
    ]
    
    String render(List<TodoItem> items) {
        items.collect { item ->
            def icon = STATUS_ICONS[item.status]
            def color = item.status == 'completed' ? 'dim' : 'normal'
            "${icon} ${item.content}"
        }.join('\n')
    }
}
```

### Phase 3: Permission System (2-3 days)

#### 3.1 Permission Dialog

Create `tui/lanterna/widgets/PermissionDialog.groovy`:

```groovy
class PermissionDialog extends DialogWindow {
    
    enum PermissionType {
        WRITE,      // File write/create
        EXECUTE,    // Bash command
        DELETE,     // File deletion
        NETWORK     // External API calls
    }
    
    private PermissionType type
    private String description
    private String details
    private Closure<Boolean> onDecision
    
    void show(PermissionType type, String desc, String details, Closure<Boolean> callback) {
        this.type = type
        this.description = desc
        this.details = details
        this.onDecision = callback
        
        buildUI()
        setVisible(true)
    }
    
    private void buildUI() {
        // Warning icon + description
        // Details (file path, command, etc.)
        // [Allow] [Allow Always] [Deny] buttons
        // Keyboard shortcuts: Y/N/A
    }
}
```

**Dialog Layout:**
```
┌─────────────────────────────────────────────┐
│ ⚠ Permission Required                       │
├─────────────────────────────────────────────┤
│                                             │
│ GLM wants to write to a file:               │
│                                             │
│   src/config.groovy                         │
│                                             │
│   +10 lines, -3 lines                       │
│                                             │
│ [Y]Allow  [A]Always  [N]Deny  [V]View Diff  │
└─────────────────────────────────────────────┘
```

#### 3.2 Permission Configuration

Add to `~/.glm/config.toml`:

```toml
[permissions]
# Modes: ask (default), allow_all, deny_all
write_mode = "ask"
execute_mode = "ask"
delete_mode = "ask"

# Auto-allow patterns
auto_allow_paths = [
    "*.md",
    "docs/**",
    "test/**"
]
```

### Phase 4: Enhanced Sidebar (2-3 days)

#### 4.1 Collapsible Sections

Improve sidebar sections with expand/collapse:

```groovy
class CollapsibleSection extends Panel {
    private boolean expanded = true
    private String title
    private Panel contentPanel
    
    void toggle() {
        expanded = !expanded
        buildUI()
    }
    
    private void buildUI() {
        removeAllComponents()
        
        def header = new Panel()
        header.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def arrow = new Label(expanded ? '▼' : '▶')
        def titleLabel = new Label(title)
        titleLabel.addStyle(SGR.BOLD)
        
        header.addComponent(arrow)
        header.addComponent(titleLabel)
        
        addComponent(header)
        
        if (expanded) {
            addComponent(contentPanel)
        }
    }
}
```

#### 4.2 Enhanced Context Display

Show more detailed context information:

```groovy
class ContextSection extends CollapsibleSection {
    
    void update(SessionStats stats) {
        def content = new Panel()
        content.addComponent(new Label("${stats.inputTokens} input"))
        content.addComponent(new Label("${stats.outputTokens} output"))
        content.addComponent(new Label("${stats.cacheHits} cached"))
        content.addComponent(new Label("${stats.percentUsed}% of context"))
        content.addComponent(new Label("\$${stats.cost} spent"))
        
        setContent(content)
    }
}
```

#### 4.3 MCP Server Status (Future)

Prepare for MCP integration:

```groovy
class McpSection extends CollapsibleSection {
    
    void update(List<McpServer> servers) {
        def content = new Panel()
        
        servers.each { server ->
            def status = server.connected ? '•' : '○'
            def color = server.connected ? TextColor.ANSI.GREEN : TextColor.ANSI.RED
            
            def row = new Panel()
            row.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
            
            def statusLabel = new Label(status)
            statusLabel.setForegroundColor(color)
            
            def nameLabel = new Label(server.name)
            
            row.addComponent(statusLabel)
            row.addComponent(nameLabel)
            
            content.addComponent(row)
        }
        
        setContent(content)
    }
}
```

### Phase 5: Navigation & Keyboard Shortcuts (2-3 days)

#### 5.1 Message Navigation

Implement jump-to-message functionality:

```groovy
// In ActivityLogPanel
void jumpToNextMessage(int direction = 1) {
    def messages = findMessageBoundaries()
    def currentLine = getScrollPosition()[0]
    
    if (direction > 0) {
        // Find next message after current position
        def next = messages.find { it > currentLine + 5 }
        if (next) scrollToLine(next)
    } else {
        // Find previous message before current position
        def prev = messages.reverse().find { it < currentLine - 5 }
        if (prev) scrollToLine(prev)
    }
}

private List<Integer> findMessageBoundaries() {
    def lines = content.toString().split('\n')
    def boundaries = []
    
    lines.eachWithIndex { line, idx ->
        if (line.startsWith('> ') || line.startsWith('GLM> ')) {
            boundaries << idx
        }
    }
    
    return boundaries
}
```

#### 5.2 Keyboard Shortcuts

Implement comprehensive keybindings:

| Shortcut | Action | OpenCode Equivalent |
|----------|--------|---------------------|
| `Ctrl+C` | Exit | ✓ |
| `Ctrl+S` | Export log | ✓ |
| `Ctrl+L` | Clear log | NEW |
| `Ctrl+M` | Model selection | ✓ (partial) |
| `Ctrl+N` | New session | NEW |
| `Ctrl+B` | Toggle sidebar | `/sidebar` |
| `Tab` | Cycle agent | ✓ |
| `↑/↓` | History (in input) | ✓ |
| `PgUp/PgDn` | Scroll activity log | ✓ |
| `Ctrl+↑/↓` | Jump to prev/next message | NEW |
| `Ctrl+Home/End` | Jump to start/end | ✓ |
| `F1` | Help dialog | NEW |
| `Esc` | Clear input / close dialog | ✓ |

#### 5.3 Keybind Configuration

Allow customization in config:

```toml
[keybinds]
exit = "ctrl+c"
export = "ctrl+s"
clear = "ctrl+l"
model = "ctrl+m"
sidebar = "ctrl+b"
help = "f1"
next_message = "ctrl+down"
prev_message = "ctrl+up"
```

### Phase 6: Theming System (1-2 days)

#### 6.1 Theme Structure

Create comprehensive theme support:

```groovy
// tui/themes/Theme.groovy
class Theme {
    // Backgrounds
    TextColor background
    TextColor backgroundPanel
    TextColor backgroundElement
    
    // Text
    TextColor text
    TextColor textMuted
    TextColor textBold
    
    // Borders
    TextColor border
    TextColor borderActive
    
    // Semantic colors
    TextColor success
    TextColor warning
    TextColor error
    TextColor info
    
    // Diff colors
    TextColor diffAdded
    TextColor diffRemoved
    TextColor diffAddedBg
    TextColor diffRemovedBg
    TextColor diffContextBg
    
    // Tool icons
    TextColor toolRead
    TextColor toolWrite
    TextColor toolSearch
    TextColor toolExecute
}
```

#### 6.2 Built-in Themes

Implement theme presets:

```groovy
class Themes {
    
    static Theme DARK = new Theme(
        background: new TextColor.RGB(26, 26, 46),
        backgroundPanel: new TextColor.RGB(30, 30, 50),
        text: TextColor.ANSI.WHITE,
        textMuted: new TextColor.RGB(128, 128, 140),
        success: new TextColor.RGB(76, 175, 80),
        error: new TextColor.RGB(244, 67, 54),
        warning: new TextColor.RGB(255, 152, 0),
        diffAdded: new TextColor.RGB(46, 160, 67),
        diffRemoved: new TextColor.RGB(248, 81, 73)
        // ...
    )
    
    static Theme LIGHT = new Theme(
        background: new TextColor.RGB(250, 250, 252),
        backgroundPanel: new TextColor.RGB(245, 245, 248),
        text: TextColor.ANSI.BLACK,
        // ...
    )
    
    static Theme HIGH_CONTRAST = new Theme(
        // ...
    )
}
```

### Phase 7: Advanced Features (3-4 days)

#### 7.1 Session Timeline/Fork

Enable forking from previous points:

```groovy
class TimelineDialog extends DialogWindow {
    
    private List<Message> messages
    private int selectedIndex
    
    void show(List<Message> history) {
        this.messages = history
        buildUI()
    }
    
    void forkFromSelected() {
        def selectedMessage = messages[selectedIndex]
        // Create new session with messages up to selected point
        // Navigate to new session
    }
}
```

#### 7.2 Session Management

Add session switching:

```groovy
class SessionSwitcher {
    
    List<Session> getSessions() {
        return SessionManager.instance.listSessions()
    }
    
    void switchToSession(String sessionId) {
        // Save current session state
        // Load selected session
        // Update TUI
    }
    
    void createNewSession() {
        def newSession = SessionManager.instance.createSession(
            currentCwd,
            agentRegistry.getCurrentAgent().name(),
            currentModel
        )
        switchToSession(newSession.id)
    }
}
```

#### 7.3 Export Options

Enhanced export dialog:

```groovy
class ExportDialog extends DialogWindow {
    
    enum ExportFormat {
        PLAIN_TEXT,
        MARKDOWN,
        JSON,
        HTML
    }
    
    void export(ExportFormat format, String path) {
        switch (format) {
            case PLAIN_TEXT:
                exportPlainText(path)
                break
            case MARKDOWN:
                exportMarkdown(path)
                break
            case JSON:
                exportJson(path)
                break
            case HTML:
                exportHtml(path)
                break
        }
    }
}
```

---

## 4. File Structure After Implementation

```
tui/
├── LanternaTUI.groovy                    # Main orchestrator (refactored)
├── LanternaTheme.groovy                  # Theme management
├── lanterna/
│   ├── widgets/
│   │   ├── ActivityLogPanel.groovy       # Enhanced with message rendering
│   │   ├── CommandInputPanel.groovy      # Enhanced autocomplete
│   │   ├── HeaderPanel.groovy            # NEW: Session title + context
│   │   ├── FooterPanel.groovy            # NEW: Status indicators
│   │   ├── SidebarPanel.groovy           # Enhanced collapsible sections
│   │   ├── PermissionDialog.groovy       # NEW: Write permissions
│   │   ├── HelpDialog.groovy             # NEW: Keyboard shortcuts help
│   │   ├── TimelineDialog.groovy         # NEW: Session history fork
│   │   ├── ExportDialog.groovy           # NEW: Export options
│   │   ├── ModelSelectionDialog.groovy   # Existing
│   │   ├── AutocompletePopup.groovy      # Existing
│   │   └── Tooltip.groovy                # Existing
│   ├── sidebar/
│   │   ├── CollapsibleSection.groovy     # NEW: Base class
│   │   ├── SessionInfoSection.groovy     # Enhanced
│   │   ├── ContextSection.groovy         # NEW: Token/cost details
│   │   ├── TokenSection.groovy           # Existing
│   │   ├── LspSection.groovy             # Enhanced
│   │   ├── McpSection.groovy             # NEW: Future MCP support
│   │   ├── TodoSection.groovy            # NEW: Todo visualization
│   │   └── ModifiedFilesSection.groovy   # Enhanced with diffs
│   └── rendering/
│       ├── MessageRenderer.groovy        # NEW: Message formatting
│       ├── DiffRenderer.groovy           # NEW: Diff visualization
│       ├── ToolRenderer.groovy           # NEW: Tool call display
│       ├── TodoRenderer.groovy           # NEW: Todo checkboxes
│       └── MarkdownRenderer.groovy       # NEW: Basic MD support
├── themes/
│   ├── Theme.groovy                      # NEW: Theme structure
│   ├── DarkTheme.groovy                  # NEW: Dark theme preset
│   ├── LightTheme.groovy                 # NEW: Light theme preset
│   └── HighContrastTheme.groovy          # NEW: Accessibility theme
└── shared/
    ├── AutocompleteItem.groovy           # Existing
    ├── CommandProvider.groovy            # Existing
    ├── FileProvider.groovy               # Existing
    └── KeybindManager.groovy             # NEW: Keybind configuration
```

---

## 5. Implementation Timeline

| Phase | Description | Estimated Effort | Priority |
|-------|-------------|------------------|----------|
| Phase 1 | Enhanced Layout (Header/Footer) | 2-3 days | HIGH |
| Phase 2 | Rich Message Rendering | 3-4 days | HIGH |
| Phase 3 | Permission System | 2-3 days | HIGH |
| Phase 4 | Enhanced Sidebar | 2-3 days | MEDIUM |
| Phase 5 | Navigation & Keyboard Shortcuts | 2-3 days | MEDIUM |
| Phase 6 | Theming System | 1-2 days | LOW |
| Phase 7 | Advanced Features | 3-4 days | LOW |
| **Total** | | **15-22 days** | |

---

## 6. Testing Strategy

### 6.1 Unit Tests

```groovy
// tests/tui/DiffRendererTest.groovy
class DiffRendererTest {
    
    @Test
    void testUnifiedDiff() {
        def diff = """+new line
-old line
 context line"""
        
        def renderer = new DiffRenderer()
        def result = renderer.render(diff, 80, 'unified')
        
        assert result.contains('+ new line')
        assert result.contains('- old line')
    }
}
```

### 6.2 Integration Tests

```groovy
// tests/tui/LanternaTUIIntegrationTest.groovy
class LanternaTUIIntegrationTest {
    
    @Test
    void testSidebarToggle() {
        def tui = new LanternaTUI()
        assert tui.sidebarPanel.getExpanded()
        
        tui.toggleSidebar()
        assert !tui.sidebarPanel.getExpanded()
    }
}
```

### 6.3 Visual Testing

Create test scripts for manual verification:

```groovy
// test-tui-components.groovy
def tui = new LanternaTUI()

// Test diff rendering
tui.activityLogPanel.appendDiff("test.groovy", """
+new line 1
+new line 2
-old line
 context
""")

// Test permission dialog
tui.showPermissionDialog(
    PermissionType.WRITE,
    "Write to config.groovy",
    "+10 -3 lines"
)

// Test todo rendering
tui.sidebar.updateTodos([
    [status: 'completed', content: 'Read files'],
    [status: 'in-progress', content: 'Implement feature'],
    [status: 'todo', content: 'Write tests']
])
```

---

## 7. Migration Notes

### 7.1 Backward Compatibility

- Existing `/sidebar` command continues to work
- All current keyboard shortcuts preserved
- Config file additions are optional with sensible defaults

### 7.2 Breaking Changes

- Status bar split into Header + Footer (visual change only)
- Activity log format changes (tool calls rendered differently)

### 7.3 Deprecations

- None planned

---

## 8. References

- [OpenCode Repository](https://github.com/sst/opencode)
- [OpenCode TUI Components](https://github.com/sst/opencode/tree/dev/packages/opencode/src/cli/cmd/tui)
- [Lanterna 3.x Documentation](https://github.com/mabe02/lanterna/wiki)
- [Lanterna Examples](https://github.com/mabe02/lanterna/tree/master/src/test/java/com/googlecode/lanterna)

---

## 9. Success Metrics

1. **Usability**: Users can understand tool execution status at a glance
2. **Discoverability**: New users can find features via help dialog
3. **Performance**: UI remains responsive with large conversation histories
4. **Accessibility**: High-contrast theme works for visually impaired users
5. **Feature Parity**: Core features match OpenCode TUI experience

---

*Document Version: 1.0*  
*Last Updated: 2026-01-09*  
*Author: GLM-CLI Team*
