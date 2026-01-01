# TUI Implementation Plan - OpenCode-Inspired Design

This document outlines the implementation plan for redesigning the GLM CLI TUI to match the modern, polished terminal interface of [SST OpenCode](https://github.com/sst/opencode).

## Design Philosophy

OpenCode's TUI exemplifies modern terminal design with:
- **Clean, minimalist aesthetic** with carefully chosen ASCII art branding
- **Provider-based architecture** for clean state management
- **Modular component system** for maintainability
- **Context-aware UI** that adapts to application state
- **Keyboard-first interaction** with intuitive shortcuts
- **Theme support** with dark/light mode auto-detection

## ✅ Implementation Status

The TUI uses **Lanterna 3.1.3** for terminal rendering:

| Feature | Description | Status |
|---------|-------------|--------|
| **Automatic resize handling** | Terminal resize events detected and UI redraws automatically | ✅ Implemented |
| **Double-buffered screen** | No flickering, smooth rendering | ✅ Implemented |
| **Panel layouts** | `BorderLayout` and `LinearLayout` managers | ✅ Implemented |
| **TextBox input** | Proper cursor handling and text editing | ✅ Implemented |
| **ActionListBox** | Scrollable chat history | ✅ Implemented |
| **Fallback mode** | Use `--simple` flag for basic console mode | ✅ Implemented |

### Files Created

| File | Description |
|------|-------------|
| `tui/LanternaScreen.groovy` | Low-level screen wrapper with drawing methods |
| `tui/LanternaGUI.groovy` | High-level GUI with panels, text boxes, layouts |
| `tui/WelcomeScreen.groovy` | Welcome screen with logo and prompts |
| `tui/StatusBar.groovy` | Bottom status bar with context |
| `tui/InputPane.groovy` | Bottom input area with multi-line support |

### Usage

```bash
# Default: Lanterna TUI mode
glm

# Simple console mode (no TUI)
glm --simple

# With initial message
glm "Hello, world!"
```

---

## OpenCode Design Analysis

### Architecture Insights

From studying OpenCode's codebase, we identified these key patterns:

#### 1. Component-Based Structure
OpenCode uses a modular, component-based architecture:
- **Logo Component**: Two-column ASCII art with muted and bold text
- **Prompt Component**: Central input area with hints
- **Status Bar**: Bottom bar showing context (directory, MCP status, version)
- **Routes**: Separate views for Home (welcome) and Session (active chat)

#### 2. Provider Pattern
OpenCode uses providers for state management:
- **RouteProvider**: Navigation state
- **ThemeProvider**: Color scheme management
- **SDKProvider**: API connection
- **SyncProvider**: Session synchronization
- **LocalProvider**: Local state (model, agent settings)
- **KVProvider**: Key-value storage
- **ToastProvider**: Notification system
- **CommandProvider**: Command palette (Ctrl+X)

#### 3. Keyboard-First Design
Intuitive keyboard shortcuts:
- `Ctrl+X`: Command palette
- `Ctrl+C`: Copy selection / Exit
- `Ctrl+S`: Suspend terminal
- `Tab`: Switch between agents
- Navigation keys for list scrolling

#### 4. Context-Aware UI
The UI adapts based on:
- First-time user vs returning user
- Connected MCP servers
- Current route (Home/Session)
- Error states
- Terminal capabilities

#### 5. Visual Hierarchy
- **Primary action area**: Centered prompt input
- **Secondary info**: Status bar (bottom)
- **Tertiary info**: Hints, tips (minimal)
- **Notifications**: Toasts overlay

### Visual Design Elements

#### Logo Design (OpenCode Style)
```
                    █▀▀▀ █▀▀█ █▀▀█ █▀▀█
       █▀▀█ █▀▀█ █▀▀▄  █░░░ █░░█ █░░█ █▀▀▀
       █░░█ █░░█ █░░█  ▀▀▀▀ ▀▀▀▀ ▀▀▀▀ ▀▀▀▀
       ▀▀▀▀ █▀▀▀ ▀  ▀
```
- Two-column design: muted left, bold right
- Block characters for visual weight
- Subtle spacing for readability
- Grayscaled color scheme

#### Status Bar Layout
```
[Directory]    [MCP: 3]     v1.0.223
```
- Left: Working directory
- Center: MCP server count / status
- Right: Version info
- Uses muted colors for subtlety

#### Welcome Screen Components
1. **Logo**: Centered, minimal
2. **Prompt**: Below logo, takes focus
3. **Hints**: Minimal text hints (e.g., "ctrl+x for commands")
4. **Status**: Only shown if not first-time user

---

## GLM CLI Adaptation

### Key Adaptations for Lanterna

Since OpenCode uses OpenTUI (React/SolidJS-based) and GLM CLI uses Lanterna (Java library), we adapt patterns, not implementations:

#### 1. Component Mapping

| OpenCode (OpenTUI) | GLM CLI (Lanterna) |
|-------------------|-------------------|
| `<Logo />` | `LogoComponent` class |
| `<Prompt />` | `InputPane` class |
| Status bar | `StatusBar` class |
| Routes | Screen classes |
| Providers | Singleton managers |
| Toasts | Overlay notifications |

#### 2. Architecture Patterns

We'll implement Lanterna equivalents of OpenCode's patterns:

**State Management:**
```groovy
// OpenCode: useLocal(), useTheme()
// GLM CLI: Singleton classes

class ThemeManager {
    static ThemeManager instance = new ThemeManager()
    String mode = "dark"
    Map colors = [:]
}

class LocalStateManager {
    static LocalStateManager instance = new LocalStateManager()
    String currentModel = "glm-4"
    String currentAgent = "build"
}
```

**Navigation:**
```groovy
// OpenCode: route.navigate({ type: "home" })
// GLM CLI: Screen switching

class UIManager {
    void switchToHome() {
        screen.addComponent(new HomeScreen())
    }
    
    void switchToSession(String sessionId) {
        screen.addComponent(new SessionScreen(sessionId))
    }
}
```

**Commands:**
```groovy
// OpenCode: Ctrl+X command palette
// GLM CLI: Ctrl+X or ? for command menu

class CommandPalette {
    Map<String, Closure> commands = [
        "model.list": { -> showModelList() },
        "agent.list": { -> showAgentList() },
        "session.new": { -> newSession() },
    ]
}
```

---

## Component Architecture

### 1. LogoComponent (NEW)

Implements OpenCode's two-column logo design.

```groovy
package tui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.TextGraphics

class LogoComponent {
    
    private static final String[] LOGO_LEFT = [
        "                   ",
        "█▀▀█ █▀▀█ █▀▀█ █▀▀▄",
        "█░░█ █░░█ █▀▀▀ █░░█",
        "▀▀▀▀ █▀▀▀ ▀▀▀▀ ▀  ▀"
    ]
    
    private static final String[] LOGO_RIGHT = [
        "             ▄     ",
        "█▀▀▀ █▀▀█ █▀▀█ █▀▀█",
        "█░░░ █░░█ █░░█ █▀▀▀",
        "▀▀▀▀ ▀▀▀▀ ▀▀▀▀ ▀▀▀▀"
    ]
    
    void render(TextGraphics tg, int startRow, int startCol, Theme theme) {
        for (int i = 0; i < LOGO_LEFT.size(); i++) {
            // Left column - muted
            tg.setForegroundColor(theme.textMuted)
            tg.putString(startCol, startRow + i, LOGO_LEFT[i])
            
            // Right column - bold equivalent
            tg.setForegroundColor(theme.text)
            tg.putString(startCol + LOGO_LEFT[i].length() + 1, startRow + i, LOGO_RIGHT[i])
        }
    }
}
```

---

### 2. Theme System (NEW)

Auto-detects terminal background like OpenCode.

```groovy
package tui

import com.googlecode.lanterna.TextColor

class Theme {
    String mode // "dark" or "light"
    
    // Dark theme (default)
    TextColor background = TextColor.ANSI.BLACK
    TextColor text = TextColor.ANSI.WHITE
    TextColor textMuted = new TextColor.RGB(128, 128, 128)
    TextColor primary = new TextColor.RGB(250, 178, 131) // Orange-ish like OpenCode
    TextColor success = TextColor.ANSI.GREEN
    TextColor error = TextColor.ANSI.RED
    TextColor warning = TextColor.ANSI.YELLOW
    TextColor info = TextColor.ANSI.BLUE
    
    static Theme detectFromTerminal() {
        Theme theme = new Theme()
        // Try to detect terminal background color
        // For now, default to dark mode
        theme.mode = "dark"
        return theme
    }
    
    void setLightMode() {
        mode = "light"
        background = TextColor.ANSI.WHITE
        text = TextColor.ANSI.BLACK
        textMuted = new TextColor.RGB(128, 128, 128)
        primary = new TextColor.RGB(59, 125, 216)
    }
}
```

---

### 3. StatusBar (ENHANCED)

Status bar showing directory, MCP status, version - like OpenCode.

```groovy
package tui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.graphics.TextGraphics

class StatusBar {
    
    Theme theme = Theme.detectFromTerminal()
    String directory = System.getProperty("user.dir")
    String version = "1.0.0"
    int mcpCount = 0
    boolean mcpError = false
    
    void render(TextGraphics tg, TerminalSize size) {
        int row = size.getRows() - 1
        int col = 0
        
        // Background
        tg.setBackgroundColor(theme.background)
        tg.fillRectangle(col, row, size.getColumns(), 1, ' ')
        
        // Left: Directory
        tg.setForegroundColor(theme.textMuted)
        String dirText = directory.take(Math.min(30, directory.length()))
        if (directory.length() > 30) dirText = "..." + dirText[-27..-1]
        tg.putString(col, row, dirText)
        col += dirText.length() + 2
        
        // Center: MCP status (if applicable)
        if (mcpCount > 0) {
            String mcpText = mcpError ? "⊙ MCP Error" : "⊙ ${mcpCount} MCP"
            tg.setForegroundColor(mcpError ? theme.error : theme.success)
            tg.putString(col, row, mcpText)
            col += mcpText.length() + 2
        }
        
        // Right: Version
        String versionText = "v${version}"
        int versionCol = size.getColumns() - versionText.length() - 2
        tg.setForegroundColor(theme.textMuted)
        tg.putString(versionCol, row, versionText)
    }
    
    void updateMcpStatus(int count, boolean error) {
        this.mcpCount = count
        this.mcpError = error
    }
}
```

---

### 4. InputPane (ENHANCED)

Input area with hints - similar to OpenCode's Prompt component.

```groovy
package tui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class InputPane {
    
    Theme theme = Theme.detectFromTerminal()
    String prompt = "> "
    StringBuilder buffer = new StringBuilder()
    int cursorPosition = 0
    List<String> hints = []
    int currentHintIndex = 0
    
    void render(TextGraphics tg, int row, int col, TerminalSize size) {
        // Clear input line
        tg.setBackgroundColor(theme.background)
        tg.fillRectangle(col, row, size.getColumns(), 1, ' ')
        
        // Draw prompt
        tg.setForegroundColor(theme.primary)
        tg.putString(col, row, prompt)
        
        // Draw input
        tg.setForegroundColor(theme.text)
        tg.putString(col + prompt.length(), row, buffer.toString())
        
        // Draw hint (if any)
        if (hints && !hints.isEmpty()) {
            tg.setForegroundColor(theme.textMuted)
            String hint = hints[currentHintIndex]
            int hintCol = col + prompt.length() + buffer.toString().length() + 2
            if (hintCol + hint.length() < size.getColumns()) {
                tg.putString(hintCol, row, hint)
            }
        }
    }
    
    KeyStroke handleInput(KeyStroke key) {
        switch (key.getKeyType()) {
            case KeyType.Enter:
                String result = buffer.toString()
                buffer = new StringBuilder()
                cursorPosition = 0
                return result.isEmpty() ? null : result
            
            case KeyType.Backspace:
                if (cursorPosition > 0) {
                    buffer.deleteCharAt(cursorPosition - 1)
                    cursorPosition--
                }
                break
            
            case KeyType.Character:
                buffer.insert(cursorPosition, key.getCharacter())
                cursorPosition++
                break
            
            default:
                if (key.getKeyType() == KeyType.Character && 
                    key.getCharacter() == '\t' && hints && !hints.isEmpty()) {
                    // Cycle hints on Tab
                    currentHintIndex = (currentHintIndex + 1) % hints.size()
                }
        }
        return null
    }
    
    void setHints(List<String> hintList) {
        this.hints = hintList
        this.currentHintIndex = 0
    }
}
```

---

### 5. WelcomeScreen (ENHANCED)

Main welcome screen with logo, input, and status.

```groovy
package tui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.graphics.TextGraphics

class WelcomeScreen {
    
    Theme theme = Theme.detectFromTerminal()
    LogoComponent logo = new LogoComponent()
    InputPane input = new InputPane()
    StatusBar status = new StatusBar()
    
    private List<String> hints = [
        "ctrl+x for commands",
        "type /help for help",
        "ctrl+c to exit"
    ]
    
    WelcomeScreen() {
        input.setHints(hints)
    }
    
    void render(TextGraphics tg, TerminalSize size) {
        // Clear screen
        tg.setBackgroundColor(theme.background)
        tg.fillRectangle(0, 0, size.getColumns(), size.getRows(), ' ')
        
        // Calculate center positions
        int logoRow = size.getRows() / 4
        int logoCol = (size.getColumns() - 40) / 2
        
        // Render logo
        logo.render(tg, logoRow, logoCol, theme)
        
        // Render input below logo
        int inputRow = logoRow + 5
        input.render(tg, inputRow, logoCol, size)
        
        // Render status bar at bottom
        status.render(tg, size)
    }
    
    String handleInput(KeyStroke key) {
        // Check for command palette shortcut
        if (key.getKeyType() == KeyType.Character && key.getCharacter() == '\u0018') {
            // Ctrl+X - show command palette
            return "COMMANDS"
        }
        
        // Handle regular input
        return input.handleInput(key)
    }
}
```

---

### 6. CommandPalette (NEW)

Ctrl+X command palette - OpenCode style.

```groovy
package tui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class CommandPalette {
    
    Theme theme = Theme.detectFromTerminal()
    
    private static final List<Map> COMMANDS = [
        [
            title: "Switch model",
            key: "m",
            action: { -> "model.list" }
        ],
        [
            title: "Switch agent",
            key: "a",
            action: { -> "agent.list" }
        ],
        [
            title: "New session",
            key: "n",
            action: { -> "session.new" }
        ],
        [
            title: "Toggle theme",
            key: "t",
            action: { -> "theme.toggle" }
        ],
        [
            title: "Help",
            key: "?",
            action: { -> "help.show" }
        ],
        [
            title: "Exit",
            key: "q",
            action: { -> "app.exit" }
        ],
    ]
    
    int selectedIndex = 0
    StringBuilder filter = new StringBuilder()
    
    void render(TextGraphics tg, TerminalSize size) {
        int height = Math.min(COMMANDS.size(), 12)
        int width = Math.min(60, size.getColumns() - 4)
        int row = (size.getRows() - height) / 2
        int col = (size.getColumns() - width) / 2
        
        // Draw box
        tg.setBackgroundColor(theme.text)
        tg.setForegroundColor(theme.background)
        
        // Header
        tg.putString(col, row, "─" * width)
        tg.putString(col, row + 1, "│ Commands (${filter.toString()})" + " " * (width - 15 - filter.length()))
        tg.putString(col, row + 2, "─" * width)
        
        // Commands
        for (int i = 0; i < height; i++) {
            int cmdIndex = i
            if (selectedIndex == cmdIndex) {
                tg.setBackgroundColor(theme.primary)
                tg.setForegroundColor(theme.background)
            } else {
                tg.setBackgroundColor(theme.background)
                tg.setForegroundColor(theme.text)
            }
            
            String line = "│ "
            if (cmdIndex < COMMANDS.size()) {
                Map cmd = COMMANDS[cmdIndex]
                line += "[${cmd.key}] ${cmd.title}"
            }
            line += " " * (width - line.length() - 1) + "│"
            tg.putString(col, row + 3 + i, line)
        }
        
        // Footer
        tg.setBackgroundColor(theme.background)
        tg.setForegroundColor(theme.textMuted)
        tg.putString(col, row + 3 + height, "─" * width)
        tg.putString(col, row + 4 + height, "│ [↑↓] Navigate  [Enter] Select  [Esc] Close" + " " * (width - 42) + "│")
        tg.putString(col, row + 5 + height, "─" * width)
    }
    
    String handleInput(KeyStroke key) {
        switch (key.getKeyType()) {
            case KeyType.Enter:
                if (selectedIndex < COMMANDS.size()) {
                    return COMMANDS[selectedIndex].action()
                }
                break
            
            case KeyType.Escape:
                return "CLOSE"
            
            case KeyType.ArrowUp:
                if (selectedIndex > 0) selectedIndex--
                break
            
            case KeyType.ArrowDown:
                if (selectedIndex < COMMANDS.size() - 1) selectedIndex++
                break
            
            case KeyType.Character:
                // Filter by key
                char ch = key.getCharacter().toLowerCase()
                def match = COMMANDS.find { it.key == ch }
                if (match) {
                    return match.action()
                }
                break
        }
        return null
    }
}
```

---

### 7. ToastNotification (NEW)

Toast notifications for events - OpenCode style.

```groovy
package tui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.graphics.TextGraphics

class ToastNotification {
    
    Theme theme = Theme.detectFromTerminal()
    
    String message
    String variant = "info" // info, success, warning, error
    int duration = 3000
    long startTime
    
    ToastNotification(String message, String variant = "info", int duration = 3000) {
        this.message = message
        this.variant = variant
        this.duration = duration
        this.startTime = System.currentTimeMillis()
    }
    
    boolean isExpired() {
        return System.currentTimeMillis() - startTime > duration
    }
    
    void render(TextGraphics tg, TerminalSize size) {
        int width = Math.min(message.length() + 4, 60)
        int col = (size.getColumns() - width) / 2
        int row = 2
        
        TextColor bgColor = theme.info
        switch (variant) {
            case "success": bgColor = theme.success; break
            case "warning": bgColor = theme.warning; break
            case "error": bgColor = theme.error; break
        }
        
        // Draw toast box
        tg.setBackgroundColor(bgColor)
        tg.setForegroundColor(theme.background)
        
        String padding = " " * ((width - message.length()) / 2)
        tg.putString(col, row, "─" * width)
        tg.putString(col, row + 1, "│${padding}${message}${padding}" + "─" * (width - 2 - padding.length() * 2 - message.length()) + "│")
        tg.putString(col, row + 2, "─" * width)
    }
}

class ToastManager {
    
    List<ToastNotification> toasts = []
    
    void show(String message, String variant = "info", int duration = 3000) {
        toasts << new ToastNotification(message, variant, duration)
    }
    
    void render(TextGraphics tg, TerminalSize size) {
        // Remove expired toasts
        toasts.removeAll { it.isExpired() }
        
        // Show only the most recent toast
        if (!toasts.isEmpty()) {
            toasts.last().render(tg, size)
        }
    }
}
```

---

## File Structure

```
tui/
├── LanternaScreen.groovy       # Low-level screen wrapper
├── LanternaGUI.groovy           # High-level GUI manager
├── Theme.groovy                 # Theme system
├── LogoComponent.groovy         # ASCII art logo
├── WelcomeScreen.groovy         # Welcome screen
├── InputPane.groovy             # Input area
├── StatusBar.groovy             # Status bar
├── CommandPalette.groovy        # Command palette (Ctrl+X)
├── ToastNotification.groovy     # Toast notifications
├── ToastManager.groovy          # Toast manager
└── AnsiColors.groovy            # ANSI color utilities
```

---

## Implementation Steps

### Phase 1: Core Components (Week 1)

1. **Implement Theme System** (`tui/Theme.groovy`)
   - Dark/light mode detection
   - Color scheme definitions
   - Theme switching

2. **Create Logo Component** (`tui/LogoComponent.groovy`)
   - Two-column ASCII art
   - Muted and bold styling
   - Positioning logic

3. **Update Input Pane** (`tui/InputPane.groovy`)
   - Add hint system
   - Improve cursor handling
   - Add keyboard shortcuts

4. **Enhance Status Bar** (`tui/StatusBar.groovy`)
   - Add directory display
   - Add MCP status
   - Add version info
   - Improve layout

### Phase 2: Advanced Features (Week 2)

5. **Create Command Palette** (`tui/CommandPalette.groovy`)
   - Command registry
   - Filter/search
   - Keyboard navigation
   - Bind to Ctrl+X

6. **Implement Toast Notifications** (`tui/ToastNotification.groovy`, `tui/ToastManager.groovy`)
   - Multiple variants (info, success, warning, error)
   - Auto-dismiss
   - Stack management

7. **Refactor Welcome Screen** (`tui/WelcomeScreen.groovy`)
   - Integrate all components
   - Improve layout
   - Add keyboard shortcuts

### Phase 3: Integration (Week 3)

8. **Update LanternaGUI** (`tui/LanternaGUI.groovy`)
   - Integrate command palette
   - Integrate toast notifications
   - Handle keyboard shortcuts globally

9. **Update ChatCommand** (`commands/ChatCommand.groovy`)
   - Use new components
   - Handle command palette actions
   - Show toasts for events

10. **Testing & Refinement**
    - Test keyboard shortcuts
    - Verify theme switching
    - Test on different terminal sizes
    - Performance optimization

---

## Keyboard Shortcuts

Adapted from OpenCode:

| Shortcut | Action |
|----------|--------|
| `Ctrl+X` | Command palette |
| `Ctrl+C` | Exit / Cancel |
| `Ctrl+L` | Clear screen |
| `Ctrl+S` | Suspend terminal |
| `Tab` | Cycle hints / Switch agent |
| `↑/↓` | Navigate lists |
| `Enter` | Select / Submit |
| `Esc` | Close dialog |
| `?` | Help |

---

## Comparison: OpenCode vs GLM CLI TUI

| Feature | OpenCode (OpenTUI) | GLM CLI (Lanterna) | Status |
|---------|-------------------|-------------------|--------|
| **Framework** | React/SolidJS (OpenTUI) | Java (Lanterna) | ✅ Different stack, similar design |
| **ASCII Logo** | Two-column, muted/bold | Multi-line gradient | 🔄 Adapt to two-column |
| **Input Area** | Centered with hints | Bottom with prompt | ✅ Matches |
| **Status Bar** | Directory, MCP, version | Basic status | 🔄 Enhance |
| **Command Palette** | Ctrl+X, full commands | Basic | 🔄 Implement |
| **Toast Notifications** | Overlay, auto-dismiss | None | 🔄 Add |
| **Theme System** | Dark/light auto-detect | Fixed colors | 🔄 Add detection |
| **Keyboard Shortcuts** | Comprehensive | Basic | 🔄 Expand |
| **Navigation** | Routes (Home/Session) | Screens | ✅ Similar pattern |
| **State Management** | Providers | Singletons | 🔄 Implement |

---

## Verification Checklist

- [ ] Theme auto-detection works correctly
- [ ] Logo renders with two-column styling
- [ ] Status bar shows directory, MCP status, version
- [ ] Command palette opens on Ctrl+X
- [ ] Toast notifications appear and auto-dismiss
- [ ] Keyboard shortcuts work as expected
- [ ] Theme switches between dark/light mode
- [ ] Screen resize handling works
- [ ] Input area shows hints correctly
- [ ] Navigation between screens works smoothly

---

## References

- [OpenCode Repository](https://github.com/sst/opencode) - Inspiration for design patterns
- [OpenCode AGENTS.md](https://github.com/sst/opencode/blob/dev/AGENTS.md) - Agent guidelines
- [Lanterna Documentation](https://github.com/mabe02/lanterna) - Terminal UI framework
- [OpenTUI](https://github.com/opentui/core) - Terminal UI framework (OpenCode's choice)
