# TUI Implementation Plan - Amp-Style Welcome Screen

This document outlines the implementation plan for redesigning the GLM CLI TUI to match modern CLI tools like Amp, with a polished, immersive terminal interface.

## ✅ Implemented with Lanterna 3.1.3

The TUI now uses **Lanterna** for:

| Feature | Description |
|---------|-------------|
| **Automatic resize handling** | Terminal resize events are detected and UI redraws automatically |
| **Double-buffered screen** | No flickering, smooth rendering |
| **Panel layouts** | `BorderLayout` and `LinearLayout` managers |
| **TextBox input** | Proper cursor handling and text editing |
| **ActionListBox** | Scrollable chat history |
| **Fallback mode** | Use `--simple` flag for basic console mode |

### Files Created

| File | Description |
|------|-------------|
| `tui/LanternaScreen.groovy` | Low-level screen wrapper with drawing methods |
| `tui/LanternaGUI.groovy` | High-level GUI with panels, text boxes, layouts |

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

## Design Goals

Create a terminal interface that features:
- **Full-screen welcome experience** on startup
- **ASCII art branding** for visual identity
- **Clear help hints** for discoverability
- **Bottom-anchored input** for familiar chat UX
- **Status bar** for context awareness

---

## ASCII Art Logo Design

The logo will use a gradient dot pattern similar to Amp's style:

```
                    .:::::..
              .::+*#%%@@@@%%#*+::.
          .:+#%@@@@@@@@@@@@@@@@%#+:.
        :+#@@@@@@@@@@@@@@@@@@@@@@%#*:.
      :*%@@@@@@@@@@@@@@@@@@@@@@@@@@%*:
     :*%@@@@@@@@%%%####%%%@@@@@@@@@@%*:
    :*%@@@@@%*+:..      ..:+*%@@@@@@@%*:
   .*%@@@%*:.                .:*%@@@@@%*.
   :*%@@#+.    .:*****+:.      .+#@@@@%*:
   :*%@%+.    :*%@@@@@@@%*:     .+%@@@%*:
   :*%@%+.   :*%@@@@@@@@@%*:    .+%@@@%*:
   :*%@@#:.  .*%@@@@@@@@@%*.   .:*%@@@%*:
   .*%@@@%+:. .:+*#%%%#*+:. .:+%@@@@@%*.
    :*%@@@@@%*+:..    ..:+*%@@@@@@@%*:
     :*%@@@@@@@@@%####%@@@@@@@@@@@%*:
      :*%@@@@@@@@@@@@@@@@@@@@@@@@%*:          ╔════════════════════════════════════╗
        :+#%@@@@@@@@@@@@@@@@@@%#+:           ║       Welcome to GLM CLI           ║
          .:+*#%@@@@@@@@@@%#*+:.             ╠════════════════════════════════════╣
              .::+*##%%#*+::.                ║  Ctrl+C  for help                  ║
                    ....                     ║                                    ║
                                             ║  "Ask me about your codebase"      ║
                                             ║                                    ║
                                             ║  Model: glm-4.7                    ║
                                             ╚════════════════════════════════════╝
```

---

## Component Architecture

### 1. WelcomeScreen.groovy (NEW)

Renders the full-screen welcome display.

```groovy
package tui

class WelcomeScreen {
    
    static final String[] GLM_LOGO = [
        "        .::+*##*+::.        ",
        "     .:*%@@@@@@@@@@%*:.     ",
        "   .+%@@@@@@@@@@@@@@@@%+.   ",
        "  :*@@@@@@#*+::+*#@@@@@@*:  ",
        " .+%@@@@*:.      .:*@@@@%+. ",
        " :*@@@%+.          .+%@@@*: ",
        " :*@@@*.    ::::    .*@@@*: ",
        " :*@@@*.   :@@@@:   .*@@@*: ",
        " :*@@@%+.  :@@@@:  .+%@@@*: ",
        " .+%@@@@*:..::::.:*@@@@%+.  ",
        "  :*@@@@@@%#**#%@@@@@@*:    ",
        "   .+%@@@@@@@@@@@@@@%+.     ",
        "     .:*%@@@@@@@@%*:.       ",
        "        .::+**+::.          "
    ]
    
    static final String[] TIPS = [
        '"Ask me about your codebase"',
        '"Use agent mode for file editing"',
        '"Type /help for commands"',
        '"Ctrl+C to exit anytime"'
    ]
    
    static void render(String model, String cwd) {
        // Clear screen
        AnsiColors.clearScreen()
        
        // Get terminal dimensions
        def (rows, cols) = AnsiColors.getTerminalSize()
        
        // Calculate center position
        int logoStartRow = (rows / 3) as int
        int logoStartCol = ((cols - 30) / 2) as int
        
        // Render logo
        GLM_LOGO.eachWithIndex { line, i ->
            AnsiColors.moveCursor(logoStartRow + i, logoStartCol)
            print colorizeGradient(line)
        }
        
        // Render welcome box
        int boxCol = logoStartCol + 35
        int boxRow = logoStartRow + 3
        renderWelcomeBox(boxRow, boxCol, model)
        
        // Render tip
        int tipRow = logoStartRow + GLM_LOGO.size() + 2
        AnsiColors.moveCursor(tipRow, logoStartCol)
        println AnsiColors.dim(TIPS[new Random().nextInt(TIPS.size())])
    }
    
    private static String colorizeGradient(String line) {
        // Apply cyan/blue gradient to ASCII art
        line.collect { ch ->
            if (ch in ['@', '%', '#']) {
                AnsiColors.cyan(ch)
            } else if (ch in ['*', '+']) {
                AnsiColors.blue(ch)
            } else if (ch in [':', '.']) {
                AnsiColors.dim(ch)
            } else {
                ch
            }
        }.join('')
    }
    
    private static void renderWelcomeBox(int row, int col, String model) {
        def lines = [
            "╔════════════════════════════════════╗",
            "║       ${AnsiColors.bold('Welcome to GLM CLI')}           ║",
            "╠════════════════════════════════════╣",
            "║  ${AnsiColors.cyan('Ctrl+C')}  for help                  ║",
            "║                                    ║",
            "║  Model: ${AnsiColors.green(model.padRight(25))}  ║",
            "╚════════════════════════════════════╝"
        ]
        
        lines.eachWithIndex { line, i ->
            AnsiColors.moveCursor(row + i, col)
            print line
        }
    }
}
```

---

### 2. StatusBar.groovy (NEW)

Renders the bottom status bar.

```groovy
package tui

class StatusBar {
    
    static void render(Map context) {
        def (rows, cols) = AnsiColors.getTerminalSize()
        
        // Position at bottom
        AnsiColors.moveCursor(rows, 1)
        
        // Build status line
        String left = buildLeftStatus(context)
        String right = buildRightStatus(context)
        
        int padding = cols - stripAnsi(left).length() - stripAnsi(right).length()
        
        // Render with background
        print AnsiColors.bgBlue(" ${left}${' ' * padding}${right} ")
    }
    
    private static String buildLeftStatus(Map context) {
        def parts = []
        
        if (context.mcpStatus) {
            String color = context.mcpConnected ? 'green' : 'yellow'
            parts << "${AnsiColors."$color"('MCP')} ${context.mcpStatus}"
        }
        
        parts.join(' │ ')
    }
    
    private static String buildRightStatus(Map context) {
        def parts = []
        
        if (context.gitBranch) {
            parts << "${AnsiColors.magenta('⎇')} ${context.gitBranch}"
        }
        
        if (context.cwd) {
            parts << context.cwd
        }
        
        parts.join(' │ ')
    }
}
```

---

### 3. InputPane.groovy (NEW)

Bottom input area with multi-line support.

```groovy
package tui

class InputPane {
    
    private int inputRow
    private String prompt = "> "
    private StringBuilder buffer = new StringBuilder()
    
    InputPane() {
        def (rows, cols) = AnsiColors.getTerminalSize()
        // Reserve last 3 rows for input (1 border + 1 input + 1 status)
        inputRow = rows - 2
    }
    
    void render() {
        def (rows, cols) = AnsiColors.getTerminalSize()
        
        // Draw top border of input area
        AnsiColors.moveCursor(inputRow - 1, 1)
        print AnsiColors.dim("─" * cols)
        
        // Draw prompt
        AnsiColors.moveCursor(inputRow, 1)
        print AnsiColors.cyan(prompt)
    }
    
    String readLine() {
        render()
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        return reader.readLine()?.trim()
    }
}
```

---

### 4. AnsiColors.groovy (MODIFY)

Add screen control utilities.

```groovy
// Add these methods to AnsiColors.groovy

static void clearScreen() {
    print "\033[2J"     // Clear screen
    print "\033[H"      // Move cursor to home
    System.out.flush()
}

static void moveCursor(int row, int col) {
    print "\033[${row};${col}H"
    System.out.flush()
}

static Tuple2<Integer, Integer> getTerminalSize() {
    // Try to get from environment or default
    int rows = 24
    int cols = 80
    
    try {
        String rowsEnv = System.getenv("LINES")
        String colsEnv = System.getenv("COLUMNS")
        if (rowsEnv) rows = Integer.parseInt(rowsEnv)
        if (colsEnv) cols = Integer.parseInt(colsEnv)
    } catch (Exception e) {
        // Use defaults
    }
    
    // Try stty as fallback
    try {
        def process = Runtime.runtime.exec(['sh', '-c', 'stty size < /dev/tty'] as String[])
        def output = process.inputStream.text.trim()
        if (output) {
            def parts = output.split(' ')
            rows = Integer.parseInt(parts[0])
            cols = Integer.parseInt(parts[1])
        }
    } catch (Exception e) {
        // Use defaults
    }
    
    return new Tuple2<>(rows, cols)
}

static String bgBlue(String text) {
    "\033[44m${text}\033[0m"
}

static String bgCyan(String text) {
    "\033[46m${text}\033[0m"
}
```

---

### 5. ChatCommand.groovy (MODIFY)

Update to use the new TUI components.

```groovy
// In ChatCommand.run():

@Override
void run() {
    // Initialize ANSI
    AnsiColors.install()
    
    // ... existing config loading ...
    
    // Show welcome screen
    WelcomeScreen.render(model, System.getProperty("user.dir"))
    
    // Create input pane
    InputPane inputPane = new InputPane()
    
    // Main loop
    while (true) {
        String input = inputPane.readLine()
        
        if (input?.equalsIgnoreCase("exit") || input?.equalsIgnoreCase("quit")) {
            break
        }
        if (input?.isEmpty()) continue
        
        processInput(input)
        
        // Redraw status bar after response
        StatusBar.render([
            cwd: System.getProperty("user.dir"),
            gitBranch: getGitBranch()
        ])
    }
    
    // Cleanup
    AnsiColors.clearScreen()
    AnsiColors.uninstall()
}
```

---

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `tui/WelcomeScreen.groovy` | NEW | Welcome screen with ASCII logo |
| `tui/StatusBar.groovy` | NEW | Bottom status bar |
| `tui/InputPane.groovy` | NEW | Input area component |
| `tui/AnsiColors.groovy` | MODIFY | Add screen control utilities |
| `commands/ChatCommand.groovy` | MODIFY | Integrate new TUI components |
| `glm.groovy` | MODIFY | Add new //SOURCES entries |

---

## Implementation Steps

1. **Add terminal utilities to `AnsiColors.groovy`**
   - `clearScreen()`
   - `moveCursor(row, col)`
   - `getTerminalSize()`
   - Background color methods

2. **Create `WelcomeScreen.groovy`**
   - ASCII art logo
   - Welcome box
   - Tip display

3. **Create `StatusBar.groovy`**
   - Context information display
   - Git branch detection

4. **Create `InputPane.groovy`**
   - Bottom input area
   - Border styling

5. **Update `ChatCommand.groovy`**
   - Initialize TUI on startup
   - Use new components in main loop
   - Handle screen refresh

6. **Update `glm.groovy`**
   - Add `//SOURCES` for new files

---

## Verification

1. Run `glm` and verify welcome screen appears
2. Check ASCII logo renders correctly with colors
3. Verify input works at the bottom
4. Test status bar shows correct info
5. Test exit/quit commands work
6. Verify screen clears on exit
