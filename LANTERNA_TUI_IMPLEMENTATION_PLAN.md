# Lanterna TUI Implementation Plan

This document outlines the plan to integrate a Lanterna-based TUI into GLM-CLI, replacing/supplementing the existing Jexer-based TUI.

## Design Goals

Based on the screenshot reference, the TUI should feature:
1. **Activity Log Window** - Large scrollable area covering most of the screen for conversation history
2. **Text Input** - Fixed input field at the bottom for user commands
3. **Status Bar** - Optional status information (model, working directory, etc.)
4. **Clean Dark Theme** - Similar to the Amp/OpenCode aesthetic

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│                         GLM CLI TUI                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│                      Activity Log Window                       │
│                    (TextBox - MULTI_LINE)                      │
│                       - Read-only                              │
│                       - Auto-scrolling                         │
│                       - Displays conversation history          │
│                                                                │
├────────────────────────────────────────────────────────────────┤
│  Command Input (TextBox - SINGLE_LINE)          [Status: ✓]   │
└────────────────────────────────────────────────────────────────┘
```

## Dependencies

Add Lanterna 3.x to `glm.groovy`:

```groovy
//DEPS com.googlecode.lanterna:lanterna:3.1.2
```

## File Structure

```
tui/
├── AnsiColors.groovy           # (existing - keep for fallback)
├── DiffRenderer.groovy         # (existing - keep)
├── OutputFormatter.groovy      # (existing - keep)
├── ProgressIndicator.groovy    # (existing - keep)
├── JexerTUI.groovy            # (existing - keep as fallback)
├── LanternaTUI.groovy         # NEW - Main Lanterna TUI class
├── LanternaTheme.groovy       # NEW - Custom dark theme
├── ActivityLogPanel.groovy    # NEW - Scrollable log component
└── CommandInputPanel.groovy   # NEW - Input field with history
```

## Implementation Tasks

### Phase 1: Core Setup

#### 1.1 Add Lanterna Dependency
- Add `//DEPS com.googlecode.lanterna:lanterna:3.1.2` to `glm.groovy`
- Add source file references for new TUI components

#### 1.2 Create LanternaTUI.groovy
Main application class that:
- Initializes the Lanterna Screen and Terminal
- Creates MultiWindowTextGUI
- Sets up the main window layout
- Handles keyboard shortcuts (Ctrl+C to exit)

```groovy
package tui

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory

class LanternaTUI {
    private Screen screen
    private MultiWindowTextGUI textGUI
    private BasicWindow mainWindow
    private TextBox activityLog
    private TextBox commandInput
    // ... GLM client integration
}
```

#### 1.3 Create LanternaTheme.groovy
Custom theme matching the dark aesthetic:
- Dark background (#1a1a2e or similar)
- Light text (white/gray)
- Accent colors for prompts, responses, errors
- Border styling

### Phase 2: UI Components

#### 2.1 ActivityLogPanel
Scrollable multiline TextBox for conversation history:
- Read-only mode
- Auto-scroll to bottom on new messages
- Syntax highlighting for code blocks (via ANSI or custom rendering)
- Support for:
  - User prompts (prefixed with `>`)
  - AI responses
  - Tool call outputs
  - Error messages

```groovy
class ActivityLogPanel {
    private TextBox textBox
    private StringBuilder content = new StringBuilder()
    
    void appendUserMessage(String message) {
        content.append("\n> ").append(message).append("\n")
        textBox.setText(content.toString())
    }
    
    void appendAIResponse(String response) {
        content.append("\n").append(response).append("\n")
        textBox.setText(content.toString())
    }
    
    void appendToolOutput(String toolName, String output) {
        content.append("\n[${toolName}]\n").append(output).append("\n")
        textBox.setText(content.toString())
    }
}
```

#### 2.2 CommandInputPanel
Single-line input with features:
- Command history (up/down arrows)
- Tab completion for files/commands
- Enter to submit
- Esc to clear

```groovy
class CommandInputPanel {
    private TextBox inputBox
    private List<String> history = []
    private int historyIndex = -1
    
    void setupKeyBindings() {
        // Handle up/down for history
        // Handle tab for completion
        // Handle enter for submission
    }
}
```

### Phase 3: GLM Integration

#### 3.1 Connect to GlmClient
- Initialize API client with config
- Set up tool registration
- Handle streaming responses

#### 3.2 Async Message Handling
- Process user input in background thread
- Update activity log as responses stream in
- Handle tool calls and display results

```groovy
void processUserInput(String input) {
    activityLog.appendUserMessage(input)
    
    Thread.start {
        try {
            // Build request with conversation history
            def response = client.chat(request)
            
            // Handle streaming or full response
            activityLog.appendAIResponse(response.content)
            
            // Process tool calls if any
            if (response.toolCalls) {
                for (toolCall in response.toolCalls) {
                    String result = executeTool(toolCall)
                    activityLog.appendToolOutput(toolCall.name, result)
                }
            }
        } catch (Exception e) {
            activityLog.appendError(e.message)
        }
    }
}
```

### Phase 4: Advanced Features

#### 4.1 Status Bar
Bottom status panel showing:
- Current model name
- Working directory
- Connection status
- Token count (optional)

#### 4.2 Welcome Screen
Initial splash with:
- ASCII art logo
- Quick help (Ctrl+O for help, etc.)
- Version info

#### 4.3 Help Window
Popup window (Ctrl+O) showing:
- Available commands
- Keyboard shortcuts
- Tool descriptions

#### 4.4 Markdown Rendering
Basic markdown support in activity log:
- Code blocks with syntax highlighting
- Bold/italic text
- Lists
- Links

### Phase 5: Integration & Testing

#### 5.1 Update GlmCli.groovy
Modify to support Lanterna TUI:

```groovy
@Override
void run() {
    if (simpleMode) {
        CommandLine.usage(this, System.out)
    } else {
        try {
            // Try Lanterna first
            LanternaTUI tui = new LanternaTUI()
            tui.start(model, System.getProperty("user.dir"))
        } catch (Exception e) {
            // Fallback to Jexer
            try {
                JexerTUI tui = new JexerTUI()
                tui.start(model, System.getProperty("user.dir"))
            } catch (Exception e2) {
                System.err.println("TUI failed: ${e2.message}")
                CommandLine.usage(this, System.out)
            }
        }
    }
}
```

#### 5.2 Add CLI Flags
New options:
- `--tui=lanterna|jexer` - Choose TUI backend
- `--simple` - No TUI (existing)

## Code Examples from Lanterna

### Basic Screen Setup
```groovy
Screen screen = new DefaultTerminalFactory().createScreen()
screen.startScreen()

try {
    MultiWindowTextGUI textGUI = new MultiWindowTextGUI(screen)
    textGUI.setBlockingIO(false)
    textGUI.setEOFWhenNoWindows(true)
    
    // Create main window
    BasicWindow window = new BasicWindow("GLM CLI")
    // ... setup panels
    
    textGUI.addWindow(window)
    
    AsynchronousTextGUIThread guiThread = 
        (AsynchronousTextGUIThread)textGUI.getGUIThread()
    guiThread.start()
    guiThread.waitForStop()
} finally {
    screen.stopScreen()
}
```

### Layout Pattern
```groovy
Panel mainPanel = new Panel()
mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

// Activity log - grows to fill space
TextBox activityLog = new TextBox(
    new TerminalSize(120, 25),
    "Welcome to GLM CLI\n",
    TextBox.Style.MULTI_LINE
)
activityLog.setReadOnly(true)
activityLog.setLayoutData(
    LinearLayout.createLayoutData(
        LinearLayout.Alignment.FILL,
        LinearLayout.Alignment.FILL,
        true, true
    )
)
mainPanel.addComponent(activityLog.withBorder(Borders.singleLine()))

// Command input - fixed height
TextBox commandInput = new TextBox(
    new TerminalSize(120, 1),
    "",
    TextBox.Style.SINGLE_LINE
)
commandInput.setLayoutData(
    LinearLayout.createLayoutData(
        LinearLayout.Alignment.FILL,
        LinearLayout.Alignment.CENTER,
        true, false
    )
)
mainPanel.addComponent(commandInput.withBorder(Borders.singleLine(">")))
```

## Timeline Estimate

| Phase | Description | Effort |
|-------|-------------|--------|
| Phase 1 | Core Setup | 2-3 hours |
| Phase 2 | UI Components | 3-4 hours |
| Phase 3 | GLM Integration | 4-5 hours |
| Phase 4 | Advanced Features | 4-6 hours |
| Phase 5 | Integration & Testing | 2-3 hours |
| **Total** | | **15-21 hours** |

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Lanterna terminal compatibility | Keep Jexer as fallback; test on multiple terminals |
| Performance with large logs | Implement log truncation (keep last N lines) |
| Thread safety issues | Use synchronized access to UI components |
| Missing Jexer features | Implement equivalents incrementally |

## Success Criteria

1. ✅ TUI launches with `glm` or `./glm.groovy` command
2. ✅ Activity log displays conversation history with scrolling
3. ✅ Command input accepts user prompts
4. ✅ AI responses stream to activity log
5. ✅ Tool calls are executed and displayed
6. ✅ Ctrl+C cleanly exits the application
7. ✅ Dark theme matches reference screenshot aesthetic

## References

- [Lanterna GitHub Repository](https://github.com/mabe02/lanterna)
- [Lanterna 3.x API Documentation](https://mabe02.github.io/lanterna/apidocs/3.1/)
- [TextBox Examples](https://github.com/mabe02/lanterna/blob/master/src/test/java/com/googlecode/lanterna/gui2/TextBoxTest.java)
- [Panel/Layout Examples](https://github.com/mabe02/lanterna/blob/master/src/test/java/com/googlecode/lanterna/gui2/MiscComponentTest.java)
