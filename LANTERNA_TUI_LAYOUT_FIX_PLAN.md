# Lanterna TUI Layout Fix Plan

## Problem Summary

The Lanterna TUI is not displaying properly - panels/widgets are not filling the terminal properly. Based on the screenshot:
- Only a small portion of the left side shows the TUI (blue area)
- The rest of the terminal is gray/empty
- Footer appears at the bottom but other panels don't expand to fill available space

## Root Causes

### 1. Missing Full-Screen Layout Configuration
The main layout doesn't properly expand to fill the terminal window:
- `contentPanel` and `leftPanel` don't have proper size hints
- Panels use `LinearLayout` without proper terminal-size constraints
- No explicit terminal size binding

### 2. Header Panel Layout Issues (HeaderPanel.groovy)
- Uses `LinearLayout.Alignment.Beginning` and `End` but these don't enforce proper spacing
- No preferred size or layout constraints to span full width

### 3. Footer Panel Layout Issues (FooterPanel.groovy)
- Same alignment issues as HeaderPanel
- Left/center/right sections may not distribute properly

### 4. Activity Log Panel Size (ActivityLogPanel.groovy)
- `handleResize()` uses hardcoded offsets (`terminalWidth - 40`, `terminalHeight - 6`)
- Initial TextBox size is hardcoded to `80x20`
- Doesn't respond to actual terminal dimensions on startup

### 5. Content Panel Horizontal Layout (LanternaTUI.groovy:299-336)
- `contentPanel` uses `LinearLayout(Direction.HORIZONTAL)` but doesn't allocate remaining space to `leftPanel`
- Sidebar takes fixed width but leftPanel doesn't expand to fill remaining space

### 6. Theme Not Filling Background
- `applyDarkTheme()` in LanternaTheme.groovy is empty - doesn't set window background
- Gray area is the default terminal background not covered by panels

## Fix Plan

### Phase 1: Fix Main Container Layout (LanternaTUI.groovy)

**File: `tui/LanternaTUI.groovy`**

1. **Use BorderLayout instead of LinearLayout for main container**:
   ```groovy
   private void setupUI() {
       Panel mainContainer = new Panel()
       mainContainer.setLayoutManager(new BorderLayout())
       
       // Header at TOP
       headerPanel = new HeaderPanel("GLM CLI - ${currentModel}")
       mainContainer.addComponent(headerPanel, BorderLayout.Location.TOP)
       
       // Footer at BOTTOM
       footerPanel = new FooterPanel(currentCwd, agentRegistry.getCurrentAgentName())
       mainContainer.addComponent(footerPanel, BorderLayout.Location.BOTTOM)
       
       // Content panel in CENTER (will expand to fill)
       Panel contentPanel = new Panel()
       contentPanel.setLayoutManager(new BorderLayout())
       
       // Left panel (activity log + input) as CENTER of content
       Panel leftPanel = new Panel()
       leftPanel.setLayoutManager(new BorderLayout())
       
       activityLogPanel = new ActivityLogPanel(textGUI)
       leftPanel.addComponent(activityLogPanel.getTextBox(), BorderLayout.Location.CENTER)
       
       commandInputPanel = new CommandInputPanel(textGUI, this, currentCwd)
       leftPanel.addComponent(commandInputPanel.getTextBox(), BorderLayout.Location.BOTTOM)
       
       contentPanel.addComponent(leftPanel, BorderLayout.Location.CENTER)
       
       // Sidebar on RIGHT
       setupSidebar(contentPanel)
       
       mainContainer.addComponent(contentPanel, BorderLayout.Location.CENTER)
       mainWindow.setComponent(mainContainer)
   }
   ```

2. **Set window hints for full-screen**:
   ```groovy
   mainWindow.setHints([
       Window.Hint.FULL_SCREEN,
       Window.Hint.NO_DECORATIONS,
       Window.Hint.FIT_TERMINAL_WINDOW  // Add this
   ])
   ```

### Phase 2: Fix Header Panel (HeaderPanel.groovy)

**File: `tui/lanterna/widgets/HeaderPanel.groovy`**

1. **Use BorderLayout for proper left/right distribution**:
   ```groovy
   HeaderPanel(String sessionTitle = 'GLM CLI') {
       setLayoutManager(new BorderLayout())
       
       leftPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
       addComponent(leftPanel, BorderLayout.Location.LEFT)
       
       rightPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
       addComponent(rightPanel, BorderLayout.Location.RIGHT)
       
       // ... add components to left/right panels
   }
   ```

2. **Add preferred height**:
   ```groovy
   setPreferredSize(new TerminalSize(TerminalSize.AUTOSIZE, 1))
   ```

### Phase 3: Fix Footer Panel (FooterPanel.groovy)

**File: `tui/lanterna/widgets/FooterPanel.groovy`**

1. **Use BorderLayout similar to HeaderPanel**:
   ```groovy
   FooterPanel(...) {
       setLayoutManager(new BorderLayout())
       
       addComponent(leftPanel, BorderLayout.Location.LEFT)
       addComponent(centerPanel, BorderLayout.Location.CENTER)
       addComponent(rightPanel, BorderLayout.Location.RIGHT)
   }
   ```

### Phase 4: Fix Activity Log Panel (ActivityLogPanel.groovy)

**File: `tui/lanterna/widgets/ActivityLogPanel.groovy`**

1. **Remove hardcoded size, let layout manager handle it**:
   ```groovy
   ActivityLogPanel(MultiWindowTextGUI textGUI) {
       this.textGUI = textGUI
       
       textBox = new ScrollableTextBox(
           new TerminalSize(TerminalSize.AUTOSIZE, TerminalSize.AUTOSIZE),  // Dynamic size
           '',
           TextBox.Style.MULTI_LINE
       )
       // ...
   }
   ```

2. **Update handleResize to be more adaptive**:
   ```groovy
   void handleResize(int terminalWidth, int terminalHeight) {
       // Let BorderLayout handle sizing - just invalidate
       textBox.invalidate()
   }
   ```

### Phase 5: Fix Sidebar Setup (LanternaTUI.groovy)

**File: `tui/LanternaTUI.groovy`**

1. **Add sidebar to BorderLayout.RIGHT**:
   ```groovy
   private void setupSidebar(Panel contentPanel) {
       try {
           TerminalSize size = screen.getTerminalSize()
           int terminalWidth = size.getColumns()
           
           if (sidebarEnabled && terminalWidth >= 80) {
               int sidebarWidth = Math.min(30, terminalWidth / 4)
               sidebarPanel = new SidebarPanel(textGUI, sessionId, sidebarWidth)
               sidebarPanel.setPreferredSize(new TerminalSize(sidebarWidth, TerminalSize.AUTOSIZE))
               
               contentPanel.addComponent(sidebarPanel, BorderLayout.Location.RIGHT)
           }
       } catch (Exception e) {
           // Log error
       }
   }
   ```

### Phase 6: Fix Theme Background (LanternaTheme.groovy)

**File: `tui/LanternaTheme.groovy`**

1. **Implement applyDarkTheme to set proper background**:
   ```groovy
   static void applyDarkTheme(MultiWindowTextGUI gui) {
       if (gui != null) {
           try {
               // Set default window background
               def theme = SimpleTheme.makeTheme(
                   true,                          // ANSI colors
                   getTextColor(),                // foreground
                   getBackgroundColor(),          // background
                   getAccentColor(),              // selected foreground
                   getBackgroundElementColor(),   // selected background
                   getAccentColor(),              // edit foreground
                   getBackgroundColor(),          // edit background
                   getAccentColor()               // gui background
               )
               gui.setTheme(theme)
           } catch (Exception e) {
               // Fallback - set basic colors
           }
       }
   }
   ```

### Phase 7: Add Terminal Resize Handler (LanternaTUI.groovy)

1. **Listen for terminal resize events**:
   ```groovy
   private void setupResizeListener() {
       screen.doResizeIfNecessary()
       // Poll for resize or use callback if available
       Thread.start {
           while (running) {
               try {
                   TerminalSize newSize = screen.doResizeIfNecessary()
                   if (newSize != null) {
                       handleResize(newSize)
                   }
                   Thread.sleep(100)
               } catch (Exception e) {
                   break
               }
           }
       }
   }
   ```

## Implementation Order

1. **Phase 1** - Fix main container layout (highest impact)
2. **Phase 6** - Fix theme background (eliminates gray area)
3. **Phase 4** - Fix activity log sizing
4. **Phase 2 & 3** - Fix header/footer layouts
5. **Phase 5** - Fix sidebar integration
6. **Phase 7** - Add resize handler

## Testing

1. Launch TUI: `./glm.groovy tui`
2. Verify full terminal is filled with dark background
3. Verify header spans full width
4. Verify activity log fills available space
5. Verify footer spans full width
6. Toggle sidebar with `/sidebar` command
7. Resize terminal and verify layout adapts

## Alternative Approach: GridLayout

If BorderLayout proves problematic, consider using `GridLayout`:

```groovy
Panel mainContainer = new Panel()
mainContainer.setLayoutManager(new GridLayout(1))  // 1 column
mainContainer.addComponent(headerPanel)
mainContainer.addComponent(contentPanel.withLayoutData(GridLayout.createLayoutData(
    GridLayout.Alignment.FILL,
    GridLayout.Alignment.FILL,
    true,  // grabExcessHorizontalSpace
    true   // grabExcessVerticalSpace
)))
mainContainer.addComponent(footerPanel)
```

## Files to Modify

| File | Changes |
|------|---------|
| `tui/LanternaTUI.groovy` | Main layout using BorderLayout, resize handling |
| `tui/LanternaTheme.groovy` | Implement `applyDarkTheme()` |
| `tui/lanterna/widgets/HeaderPanel.groovy` | Use BorderLayout, set preferred height |
| `tui/lanterna/widgets/FooterPanel.groovy` | Use BorderLayout |
| `tui/lanterna/widgets/ActivityLogPanel.groovy` | Remove hardcoded sizes |
| `tui/lanterna/widgets/SidebarPanel.groovy` | Ensure proper width constraints |

## Estimated Effort

- Phase 1: 30 minutes
- Phase 2-3: 20 minutes
- Phase 4: 15 minutes
- Phase 5: 15 minutes
- Phase 6: 20 minutes
- Phase 7: 20 minutes
- Testing: 30 minutes

**Total: ~2.5 hours**
