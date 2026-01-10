# Two-Pane Editing Implementation Plan

This document outlines how to implement OpenCode-style two-pane editing in GLM-CLI's Lanterna TUI, based on analysis of the [SST OpenCode](https://github.com/sst/opencode) codebase.

## Overview

OpenCode's TUI uses a sophisticated two-pane layout with a main content area (flexible width) and a fixed-width sidebar. The key innovations include:

1. **Responsive layout** - Sidebar auto-hides on narrow terminals (< 120 chars)
2. **Split-pane diff viewer** - Shows before/after code side-by-side when space allows
3. **Context-based state sharing** - Panes communicate through shared state
4. **Smart scrolling** - Sticky-bottom scrolling with message navigation

## Current GLM-CLI Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  HeaderPanel (model, tokens, LSP status)                        │
├─────────────────────────────────────────────────┬───────────────┤
│                                                 │               │
│  ActivityLogPanel                               │  SidebarPanel │
│  (messages, tool outputs, diffs)                │  (42 width)   │
│                                                 │               │
├─────────────────────────────────────────────────┴───────────────┤
│  CommandInputPanel (prompt input)                               │
├─────────────────────────────────────────────────────────────────┤
│  FooterPanel (keybinds)                                         │
└─────────────────────────────────────────────────────────────────┘
```

**Existing Components:**
- [LanternaTUI.groovy](file:///home/kevintan/glm-cli-jbang/tui/LanternaTUI.groovy) - Main TUI orchestrator
- [ActivityLogPanel.groovy](file:///home/kevintan/glm-cli-jbang/tui/lanterna/widgets/ActivityLogPanel.groovy) - Message display area
- [SidebarPanel.groovy](file:///home/kevintan/glm-cli-jbang/tui/lanterna/widgets/SidebarPanel.groovy) - Fixed 42-char sidebar

## Target Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  HeaderPanel                                                    │
├─────────────────────────────────────────────────┬───────────────┤
│                                                 │               │
│  MainPane (flexGrow)                            │  SidebarPane  │
│  ┌───────────────────────────────────────────┐  │  (42 width)   │
│  │ ScrollBox (sticky-bottom)                 │  │               │
│  │   ├─ UserMessage                          │  │  • Session    │
│  │   ├─ AssistantMessage                     │  │  • Tokens     │
│  │   │    └─ DiffViewer (split/unified)      │  │  • LSP        │
│  │   ├─ ToolExecution                        │  │  • Files      │
│  │   └─ PermissionPrompt                     │  │  • Subagents  │
│  └───────────────────────────────────────────┘  │               │
├─────────────────────────────────────────────────┴───────────────┤
│  CommandInputPanel                                              │
├─────────────────────────────────────────────────────────────────┤
│  FooterPanel                                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Phases

### Phase 1: Responsive Layout Manager

Create a layout system that adapts to terminal width.

**New File:** `tui/lanterna/layout/ResponsiveLayoutManager.groovy`

```groovy
package tui.lanterna.layout

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*

class ResponsiveLayoutManager implements LayoutManager {
    
    private static final int SIDEBAR_WIDTH = 42
    private static final int SIDEBAR_THRESHOLD = 120
    private static final int MARGINS = 4
    
    private boolean sidebarVisible = true
    private String sidebarMode = "auto" // "auto", "show", "hide"
    
    boolean isSidebarVisible(int terminalWidth) {
        switch (sidebarMode) {
            case "show": return true
            case "hide": return false
            default: return terminalWidth >= SIDEBAR_THRESHOLD
        }
    }
    
    int getContentWidth(int terminalWidth) {
        return terminalWidth - (isSidebarVisible(terminalWidth) ? SIDEBAR_WIDTH : 0) - MARGINS
    }
    
    void setSidebarMode(String mode) {
        this.sidebarMode = mode
    }
    
    @Override
    TerminalSize getPreferredSize(List<Component> components) {
        // Calculate based on components
    }
    
    @Override
    void doLayout(TerminalSize area, List<Component> components) {
        int width = area.getColumns()
        boolean showSidebar = isSidebarVisible(width)
        
        components.each { component ->
            if (component instanceof SidebarPanel) {
                if (showSidebar) {
                    component.setPosition(width - SIDEBAR_WIDTH, 0)
                    component.setSize(SIDEBAR_WIDTH, area.getRows())
                } else {
                    component.setVisible(false)
                }
            } else {
                // Main content area
                int contentWidth = showSidebar ? width - SIDEBAR_WIDTH : width
                component.setSize(contentWidth, area.getRows())
            }
        }
    }
}
```

**Tasks:**
- [ ] Create `ResponsiveLayoutManager` class
- [ ] Add terminal resize detection to `LanternaTUI`
- [ ] Update sidebar visibility based on width threshold
- [ ] Add keybind for manual sidebar toggle (Ctrl+B)

---

### Phase 2: Split-Pane Diff Viewer

Create a diff component that switches between split and unified views.

**New File:** `tui/lanterna/widgets/DiffViewer.groovy`

```groovy
package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor

class DiffViewer extends Panel {
    
    enum ViewMode { SPLIT, UNIFIED }
    
    private String originalContent
    private String modifiedContent
    private String filePath
    private ViewMode viewMode = ViewMode.UNIFIED
    private int availableWidth
    
    private static final int SPLIT_THRESHOLD = 80
    
    DiffViewer(String filePath, String original, String modified, int width) {
        this.filePath = filePath
        this.originalContent = original
        this.modifiedContent = modified
        this.availableWidth = width
        
        determineViewMode()
        render()
    }
    
    private void determineViewMode() {
        // Auto-select based on available width
        viewMode = availableWidth >= SPLIT_THRESHOLD ? ViewMode.SPLIT : ViewMode.UNIFIED
    }
    
    private void render() {
        removeAllComponents()
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Header
        addComponent(createHeader())
        
        if (viewMode == ViewMode.SPLIT) {
            renderSplitView()
        } else {
            renderUnifiedView()
        }
    }
    
    private void renderSplitView() {
        Panel splitPanel = new Panel()
        splitPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        int paneWidth = (availableWidth - 3) / 2 // -3 for separator
        
        // Left pane (original)
        Panel leftPane = createPane(originalContent, paneWidth, "Before")
        leftPane.setBorder(Borders.singleLine("Before"))
        
        // Separator
        Label separator = new Label("│")
        
        // Right pane (modified)  
        Panel rightPane = createPane(modifiedContent, paneWidth, "After")
        rightPane.setBorder(Borders.singleLine("After"))
        
        splitPanel.addComponent(leftPane)
        splitPanel.addComponent(separator)
        splitPanel.addComponent(rightPane)
        
        addComponent(splitPanel)
    }
    
    private void renderUnifiedView() {
        // Traditional unified diff format
        List<DiffLine> diffLines = computeUnifiedDiff()
        
        Panel diffPanel = new Panel()
        diffPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        diffLines.each { line ->
            Label label = new Label(line.text)
            switch (line.type) {
                case DiffLineType.ADDED:
                    label.setForegroundColor(TextColor.ANSI.GREEN)
                    break
                case DiffLineType.REMOVED:
                    label.setForegroundColor(TextColor.ANSI.RED)
                    break
                case DiffLineType.CONTEXT:
                    label.setForegroundColor(TextColor.ANSI.WHITE)
                    break
            }
            diffPanel.addComponent(label)
        }
        
        addComponent(diffPanel)
    }
    
    void setViewMode(ViewMode mode) {
        this.viewMode = mode
        render()
    }
    
    void updateWidth(int width) {
        this.availableWidth = width
        determineViewMode()
        render()
    }
}
```

**Tasks:**
- [ ] Create `DiffViewer` component with split/unified modes
- [ ] Implement unified diff algorithm (or use existing library)
- [ ] Implement side-by-side rendering for split mode
- [ ] Add syntax highlighting support (file type detection)
- [ ] Add line number display
- [ ] Add word-level diff highlighting in split mode

---

### Phase 3: Message Component System

Create structured message components instead of plain text.

**New File:** `tui/lanterna/widgets/MessagePanel.groovy`

```groovy
package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*

abstract class MessagePanel extends Panel {
    protected String messageId
    protected long timestamp
    
    abstract void render(int availableWidth)
}

class UserMessagePanel extends MessagePanel {
    private String content
    
    @Override
    void render(int availableWidth) {
        removeAllComponents()
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // User message with left border indicator
        Panel contentPanel = new Panel()
        contentPanel.setBorder(Borders.singleLineBevel())
        
        Label label = new Label(wrapText(content, availableWidth - 4))
        contentPanel.addComponent(label)
        
        addComponent(contentPanel)
    }
}

class AssistantMessagePanel extends MessagePanel {
    private String textContent
    private List<ToolCall> toolCalls = []
    private DiffViewer diffViewer
    
    @Override
    void render(int availableWidth) {
        removeAllComponents()
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Text content
        if (textContent) {
            Label textLabel = new Label(wrapText(textContent, availableWidth))
            addComponent(textLabel)
        }
        
        // Tool calls with results
        toolCalls.each { toolCall ->
            addComponent(new ToolCallPanel(toolCall, availableWidth))
        }
        
        // Embedded diff viewer if file was modified
        if (diffViewer) {
            diffViewer.updateWidth(availableWidth)
            addComponent(diffViewer)
        }
    }
}

class ToolCallPanel extends Panel {
    private ToolCall toolCall
    private int width
    
    ToolCallPanel(ToolCall toolCall, int width) {
        this.toolCall = toolCall
        this.width = width
        render()
    }
    
    private void render() {
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Tool header
        Label header = new Label("⚡ ${toolCall.name}")
        header.setForegroundColor(LanternaTheme.getToolCallColor())
        addComponent(header)
        
        // Arguments (collapsed by default)
        // Result
        if (toolCall.result) {
            Label result = new Label(truncate(toolCall.result, 200))
            addComponent(result)
        }
    }
}
```

**Tasks:**
- [ ] Create abstract `MessagePanel` base class
- [ ] Implement `UserMessagePanel`
- [ ] Implement `AssistantMessagePanel` with tool call support
- [ ] Implement `ToolCallPanel` with collapsible arguments
- [ ] Integrate `DiffViewer` into tool result display
- [ ] Add thinking/reasoning section (collapsible)

---

### Phase 4: Scrollable Message List

Replace `ActivityLogPanel` with a proper scrollable message list.

**New File:** `tui/lanterna/widgets/MessageListPanel.groovy`

```groovy
package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize

class MessageListPanel extends Panel {
    
    private List<MessagePanel> messages = []
    private int scrollOffset = 0
    private boolean stickyScroll = true
    private int availableWidth
    private int availableHeight
    
    private Closure onScrollChanged
    
    MessageListPanel() {
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
    }
    
    void addMessage(MessagePanel message) {
        messages.add(message)
        message.render(availableWidth)
        addComponent(message)
        
        if (stickyScroll) {
            scrollToBottom()
        }
        
        invalidate()
    }
    
    void scrollToBottom() {
        // Calculate scroll offset to show last message
        int totalHeight = messages.sum { it.getPreferredSize().getRows() } ?: 0
        scrollOffset = Math.max(0, totalHeight - availableHeight)
        applyScroll()
    }
    
    void scrollToMessage(String messageId) {
        int targetOffset = 0
        for (MessagePanel msg : messages) {
            if (msg.messageId == messageId) {
                scrollOffset = targetOffset
                applyScroll()
                return
            }
            targetOffset += msg.getPreferredSize().getRows()
        }
    }
    
    void scrollBy(int delta) {
        int totalHeight = messages.sum { it.getPreferredSize().getRows() } ?: 0
        scrollOffset = Math.max(0, Math.min(totalHeight - availableHeight, scrollOffset + delta))
        stickyScroll = (scrollOffset >= totalHeight - availableHeight - 1)
        applyScroll()
    }
    
    private void applyScroll() {
        // Update view offset
        if (onScrollChanged) {
            int totalLines = messages.sum { it.getPreferredSize().getRows() } ?: 0
            onScrollChanged.call(scrollOffset, totalLines)
        }
    }
    
    void updateDimensions(int width, int height) {
        boolean widthChanged = (this.availableWidth != width)
        this.availableWidth = width
        this.availableHeight = height
        
        if (widthChanged) {
            // Re-render all messages with new width
            messages.each { msg ->
                msg.render(width)
            }
        }
    }
    
    void setOnScrollChanged(Closure callback) {
        this.onScrollChanged = callback
    }
    
    // Navigation helpers
    String findNextMessage(String direction) {
        // Find next/prev visible message
    }
}
```

**Tasks:**
- [ ] Create `MessageListPanel` with sticky-scroll behavior
- [ ] Implement scroll position tracking
- [ ] Add message navigation (next/prev keybinds)
- [ ] Add scroll acceleration (configurable speed)
- [ ] Integrate with `ActivityLogPanel` or replace it

---

### Phase 5: Pane Communication Context

Create a shared context for coordinated state between panes.

**New File:** `tui/lanterna/context/TuiContext.groovy`

```groovy
package tui.lanterna.context

import java.util.concurrent.CopyOnWriteArrayList

class TuiContext {
    
    // Observable properties
    private int contentWidth = 0
    private String sessionId
    private boolean showThinking = false
    private boolean showTimestamps = true
    private boolean showDetails = false
    private String diffWrapMode = "word" // "word" or "none"
    private String diffStyle = "auto" // "auto", "split", "stacked"
    
    // Listeners
    private List<Closure> widthListeners = new CopyOnWriteArrayList<>()
    private List<Closure> settingsListeners = new CopyOnWriteArrayList<>()
    
    // Singleton
    private static TuiContext instance
    
    static TuiContext getInstance() {
        if (instance == null) {
            instance = new TuiContext()
        }
        return instance
    }
    
    void setContentWidth(int width) {
        if (this.contentWidth != width) {
            this.contentWidth = width
            notifyWidthListeners()
        }
    }
    
    int getContentWidth() {
        return contentWidth
    }
    
    void addWidthListener(Closure listener) {
        widthListeners.add(listener)
    }
    
    void removeWidthListener(Closure listener) {
        widthListeners.remove(listener)
    }
    
    private void notifyWidthListeners() {
        widthListeners.each { it.call(contentWidth) }
    }
    
    // Settings accessors
    void setShowThinking(boolean show) {
        this.showThinking = show
        notifySettingsListeners()
    }
    
    void setDiffStyle(String style) {
        this.diffStyle = style
        notifySettingsListeners()
    }
    
    boolean shouldUseSplitDiff() {
        if (diffStyle == "stacked") return false
        if (diffStyle == "split") return true
        // auto mode
        return contentWidth >= 120
    }
    
    private void notifySettingsListeners() {
        settingsListeners.each { it.call() }
    }
}
```

**Tasks:**
- [ ] Create `TuiContext` singleton for shared state
- [ ] Add width change notifications
- [ ] Add settings change notifications
- [ ] Wire context to all pane components
- [ ] Add configuration persistence (config.toml integration)

---

### Phase 6: Permission Prompt Integration

Create inline permission prompts with diff preview.

**New File:** `tui/lanterna/widgets/PermissionPromptPanel.groovy`

```groovy
package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*

class PermissionPromptPanel extends Panel {
    
    enum Action { ALLOW, DENY, ALLOW_ALL }
    
    private String toolName
    private Map<String, Object> arguments
    private DiffViewer diffViewer
    private Closure<Action> onResponse
    
    PermissionPromptPanel(String toolName, Map args, String diff, Closure<Action> callback) {
        this.toolName = toolName
        this.arguments = args
        this.onResponse = callback
        
        if (diff) {
            this.diffViewer = new DiffViewer("", "", diff, 80)
        }
        
        render()
    }
    
    private void render() {
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Header
        Label header = new Label("⚠️ Permission Required: ${toolName}")
        header.setForegroundColor(LanternaTheme.getWarningColor())
        addComponent(header)
        
        // Arguments display
        arguments.each { key, value ->
            addComponent(new Label("  ${key}: ${truncate(value.toString(), 60)}"))
        }
        
        // Diff preview if available
        if (diffViewer) {
            addComponent(new Label("Preview:"))
            addComponent(diffViewer)
        }
        
        // Action buttons
        Panel buttons = new Panel()
        buttons.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        Button allowBtn = new Button("Allow (y)", { onResponse.call(Action.ALLOW) })
        Button denyBtn = new Button("Deny (n)", { onResponse.call(Action.DENY) })
        Button allowAllBtn = new Button("Allow All (!)", { onResponse.call(Action.ALLOW_ALL) })
        
        buttons.addComponent(allowBtn)
        buttons.addComponent(denyBtn)
        buttons.addComponent(allowAllBtn)
        
        addComponent(buttons)
    }
}
```

**Tasks:**
- [ ] Create `PermissionPromptPanel` with diff preview
- [ ] Integrate with `TuiPermissionPromptHandler`
- [ ] Add keyboard shortcuts (y/n/!)
- [ ] Add inline display within message flow

---

### Phase 7: Keyboard Navigation

Enhance keyboard navigation for two-pane editing.

**Update:** `tui/shared/KeybindManager.groovy`

```groovy
// Add new keybindings
static final Map<String, String> DEFAULT_KEYBINDS = [
    // Existing
    'command_palette': 'Ctrl+P',
    'cancel': 'Escape',
    
    // Sidebar
    'sidebar_toggle': 'Ctrl+B',
    
    // Message navigation
    'message_next': 'Ctrl+N',
    'message_prev': 'Ctrl+Shift+N',
    'scroll_up': 'PageUp',
    'scroll_down': 'PageDown',
    'scroll_top': 'Ctrl+Home',
    'scroll_bottom': 'Ctrl+End',
    
    // Diff navigation
    'diff_toggle_mode': 'Ctrl+D',
    'diff_next_change': ']',
    'diff_prev_change': '[',
    
    // View options
    'toggle_thinking': 'Ctrl+T',
    'toggle_timestamps': 'Ctrl+Shift+T',
    'toggle_details': 'Ctrl+Shift+D'
]
```

**Tasks:**
- [ ] Add sidebar toggle keybind (Ctrl+B)
- [ ] Add message navigation keybinds
- [ ] Add diff view mode toggle (Ctrl+D)
- [ ] Add diff change navigation ([ and ])
- [ ] Add view option toggles
- [ ] Update footer to show context-sensitive keybinds

---

### Phase 8: Mobile/Overlay Mode

Add overlay sidebar for narrow terminals.

**Update:** `tui/lanterna/widgets/SidebarPanel.groovy`

```groovy
class SidebarPanel extends Panel {
    
    enum DisplayMode { INLINE, OVERLAY, HIDDEN }
    
    private DisplayMode displayMode = DisplayMode.INLINE
    private Panel overlayBackdrop
    
    void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode
        
        if (mode == DisplayMode.OVERLAY) {
            // Create semi-transparent backdrop
            // Position sidebar over content
            enableOverlayMode()
        } else if (mode == DisplayMode.INLINE) {
            disableOverlayMode()
        }
        
        buildUI()
    }
    
    private void enableOverlayMode() {
        // Set absolute positioning
        // Add backdrop click handler to dismiss
    }
}
```

**Tasks:**
- [ ] Add overlay mode for sidebar on narrow terminals
- [ ] Create semi-transparent backdrop
- [ ] Add click-to-dismiss behavior
- [ ] Animate slide-in/out (if possible in Lanterna)

---

## Configuration

Add new TUI settings to `~/.glm/config.toml`:

```toml
[tui]
# Sidebar behavior: "auto", "show", "hide"
sidebar = "auto"

# Sidebar width (default 42)
sidebar_width = 42

# Sidebar threshold for auto-hide (default 120)
sidebar_threshold = 120

# Diff style: "auto", "split", "stacked"
diff_style = "auto"

# Scroll speed multiplier
scroll_speed = 3

# Scroll acceleration
scroll_acceleration = true

# Show thinking sections
show_thinking = false

# Show timestamps
show_timestamps = true
```

---

## Migration Path

1. **Phase 1-2**: Can be implemented independently without breaking existing UI
2. **Phase 3-4**: Requires refactoring `ActivityLogPanel` - implement new components alongside, then switch
3. **Phase 5-6**: Context and permissions can be added incrementally
4. **Phase 7-8**: Keyboard and overlay are pure additions

## Testing Strategy

- [ ] Unit tests for `DiffViewer` with various content sizes
- [ ] Unit tests for `ResponsiveLayoutManager` width calculations
- [ ] Integration tests for sidebar toggle behavior
- [ ] Manual testing at various terminal widths (80, 100, 120, 150+ cols)
- [ ] Test resize behavior during active session

## Dependencies

No new dependencies required. All implementations use existing Lanterna library features.

## Estimated Effort

| Phase | Description | Effort |
|-------|-------------|--------|
| 1 | Responsive Layout | 2-3 days |
| 2 | Split-Pane Diff | 3-4 days |
| 3 | Message Components | 2-3 days |
| 4 | Scrollable List | 2-3 days |
| 5 | Pane Context | 1-2 days |
| 6 | Permission Prompts | 1-2 days |
| 7 | Keyboard Navigation | 1 day |
| 8 | Overlay Mode | 1-2 days |
| **Total** | | **13-20 days** |

## References

- [SST OpenCode TUI](https://github.com/sst/opencode/tree/main/packages/opencode/src/cli/cmd/tui)
- [OpenCode Session Route](https://github.com/sst/opencode/blob/main/packages/opencode/src/cli/cmd/tui/routes/session/index.tsx)
- [Lanterna GUI2 Documentation](https://github.com/mabe02/lanterna/wiki/GUI2)
- [GLM-CLI Existing TUI](file:///home/kevintan/glm-cli-jbang/tui/LanternaTUI.groovy)
