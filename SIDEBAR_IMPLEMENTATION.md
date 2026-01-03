# Sidebar Implementation for Token and LSP Usage Tracking

## Overview

This implementation adds a sidebar to the GLM CLI TUI that displays:
- **Token usage**: Total tokens, percentage of context window, and cost
- **LSP status**: Active language servers with connection status
- **Modified files**: List of files modified with diff statistics
- **Session info**: Session title, directory, and ID

## Implementation Status: Phase 1-3 Complete ✅

### ✅ Phase 1: Data Tracking Enhancement

**Created Files:**
- `core/SessionStats.groovy` - Data model for session statistics
- `core/SessionStatsManager.groovy` - Singleton manager for tracking real-time session metrics
- `core/LspManager.groovy` - Singleton manager for tracking active LSP servers

**Enhanced Files:**
- `core/TokenTracker.groovy` - Added in-memory tracking for faster access
- `models/TokenStats.groovy` - Added input/output token breakdown

**Key Features:**
- Real-time in-memory tracking (no DB queries for display)
- LSP server registration and status tracking
- Modified file tracking with diff stats
- Session-level aggregation of metrics

### ✅ Phase 2: Sidebar UI Components

**Created Files:**
- `tui/SidebarPanel.groovy` - Main sidebar container
- `tui/sidebar/SessionInfoSection.groovy` - Session title and directory display
- `tui/sidebar/TokenSection.groovy` - Token usage with color coding
- `tui/sidebar/LspSection.groovy` - LSP server list with status indicators
- `tui/sidebar/ModifiedFilesSection.groovy` - Modified files with diff stats

**Key Features:**
- Fixed width sidebar (42 columns)
- Collapsible sections (expand/collapse)
- Color-coded indicators (green/yellow/red)
- Auto-collapse when content exceeds 2 items

### ✅ Phase 3: Integration with Main TUI

**Enhanced Files:**
- `tui/LanternaTUI.groovy` - Integrated sidebar into layout
- `tui/LanternaTheme.groovy` - Added sidebar-specific colors

**Key Features:**
- Horizontal layout: Content + Sidebar
- Auto-hide sidebar on small terminals (< 100 columns)
- Toggle sidebar with `/sidebar` command
- Real-time token tracking on responses
- Session ID generation for tracking

## Architecture

### Data Flow

```
Chat Request
    ↓
API Response (with token usage)
    ↓
TokenTracker.recordTokens() → Updates DB + In-memory
    ↓
SessionStatsManager.updateTokenCount() → Updates session stats
    ↓
Sidebar.refresh() → Updates UI display
```

### Component Hierarchy

```
LanternaTUI
└── mainContainer (Horizontal)
    ├── contentPanel (Vertical)
    │   ├── ActivityLogPanel
    │   ├── CommandInputPanel
    │   └── StatusBar
    └── SidebarPanel (Vertical)
        ├── SessionInfoSection
        ├── TokenSection
        ├── LspSection
        └── ModifiedFilesSection
```

### State Management

- **SessionStatsManager**: Singleton managing session-level stats
- **LspManager**: Singleton managing active LSP connections
- **TokenTracker**: Enhanced with in-memory caching

## Usage

### Toggle Sidebar

```
/sidebar      # Show/hide sidebar
```

### Sidebar Sections

1. **Session Info**: Displays current session title, working directory, and session ID
2. **Context**: Shows total tokens, percentage of context used, and total cost
3. **LSP**: Lists active language servers with status indicators:
   - `•` Green: Connected
   - `•` Red: Error
   - Shows server name and project root
4. **Modified Files**: Lists files modified in session with diff stats:
   - `+N` (green): Additions
   - `-N` (red): Deletions

## Color Scheme

- **Text**: White
- **Muted**: Gray (RGB: 128, 128, 128)
- **Success/Connected**: Green
- **Warning/Medium**: Yellow
- **Error**: Red
- **Diff Added**: Green (RGB: 76, 175, 80)
- **Diff Removed**: Red (RGB: 244, 67, 54)

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `/sidebar` | Toggle sidebar visibility |
| `Ctrl+C` | Exit |
| `Ctrl+S` | Save log |
| `Tab/Shift+Tab` | Switch agent |

## Next Steps (Phase 4-5)

### Phase 4: Enhanced LSP Integration
- [ ] Hook LSP client initialization into LspManager
- [ ] Update LSP status on diagnostics
- [ ] Track LSP server names and roots
- [ ] Handle LSP connection errors

### Phase 5: Visual Polish
- [ ] Add mouse support for section toggles
- [ ] Add scroll indicator for sidebar content
- [ ] Add smooth animations for collapse/expand
- [ ] Improve borders and spacing
- [ ] Add tooltips for LSP errors

### Future Enhancements
- [ ] MCP server support (like OpenCode)
- [ ] Todo list section
- [ ] Session diff visualization
- [ ] Configurable context limit per model
- [ ] Cost projection based on usage trends
- [ ] Token usage by model/provider breakdown

## File Structure

```
core/
├── SessionStats.groovy          # NEW - Session statistics model
├── SessionStatsManager.groovy    # NEW - Stats manager singleton
├── LspManager.groovy            # NEW - LSP manager singleton
└── TokenTracker.groovy          # ENHANCE - In-memory tracking

tui/
├── SidebarPanel.groovy          # NEW - Main sidebar
├── sidebar/                    # NEW - Sidebar components
│   ├── SessionInfoSection.groovy
│   ├── TokenSection.groovy
│   ├── LspSection.groovy
│   └── ModifiedFilesSection.groovy
└── LanternaTheme.groovy         # ENHANCE - Sidebar colors

models/
└── TokenStats.groovy            # ENHANCE - Input/output breakdown
```

## Testing

To test the sidebar implementation:

```bash
cd /home/kevintan/glm-cli-jbang
./glm.groovy chat
```

Then:
1. Send a message to see token tracking update
2. Type `/sidebar` to toggle sidebar visibility
3. Check token count, percentage, and cost display
4. Verify layout adapts to terminal size

## Limitations

1. **Context limit**: Fixed at 128k tokens (should be model-specific)
2. **LSP integration**: Basic structure in place, needs integration with existing LSPClient
3. **Modified files**: Manual tracking needed (not automatic)
4. **Cost calculation**: Basic implementation, needs per-model pricing

## Technical Notes

### Threading
- Sidebar refresh calls are UI operations
- All UI updates happen on the main thread
- Background threads update state, main thread refreshes UI

### Performance
- In-memory stats avoid DB queries during UI updates
- Sidebar refresh only called on state changes
- Component invalidation only for changed sections

### Compatibility
- Auto-hides on terminals < 100 columns
- Backward compatible with existing TUI features
- Can be disabled by removing from setupUI()

## References

- OpenCode sidebar implementation: `/home/kevintan/opencode/packages/opencode/src/cli/cmd/tui/routes/session/sidebar.tsx`
- Lanterna documentation: https://github.com/mabe02/lanterna
