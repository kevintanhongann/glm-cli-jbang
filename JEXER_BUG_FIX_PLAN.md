# Jexer TUI Bug Fix Plan

This document outlines bugs in the GLM-CLI Jexer TUI implementation and provides fixes based on the [Jexer GitLab repository](https://gitlab.com/AutumnMeowMeow/jexer) best practices.

---

## Summary of Issues

| # | File | Issue | Severity |
|---|------|-------|----------|
| 1 | JexerSidebar.groovy | Using `getChildren().clear()` directly | **Critical** |
| 2 | JexerAutocompletePopup.groovy | Using `getChildren().clear()` directly | **Critical** |
| 3 | JexerStatusBar.groovy | Using `getChildren().clear()` directly | **Critical** |
| 4 | JexerActivityLog.groovy | Extends `TText` but calls `setText()` with custom wrapping | **High** |
| 5 | Sidebar sections (all) | Using `setScreenCell()` with invalid coordinates | **High** |
| 6 | JexerTheme.groovy | Creating `new Color(r, g, b)` which expects palette index | **Medium** |
| 7 | JexerCommandInput.groovy | `getCursorPosition()` method doesn't exist on TField | **High** |
| 8 | JexerSidebar.groovy | `setDimensions()` called improperly | **Medium** |
| 9 | JexerAutocompletePopup.groovy | `addBox()` method uses incorrect coordinate logic | **Medium** |
| 10 | All widgets | Commented-out `getScreenCellAttributes()` calls | **Low** |

---

## Bug #1: Improper Child Management with `getChildren().clear()`

### Affected Files
- `tui/jexer/widgets/JexerSidebar.groovy` (lines 90, 97)
- `tui/jexer/widgets/JexerAutocompletePopup.groovy` (lines 90, 97)
- `tui/jexer/widgets/JexerStatusBar.groovy` (line 39)

### Problem
Jexer's `TWidget.getChildren()` returns the internal list of child widgets. Calling `clear()` on it bypasses proper widget lifecycle management (focus cleanup, screen invalidation, parent references). This can cause:
- Orphaned widgets still receiving events
- Memory leaks
- Screen artifacts / rendering glitches
- NullPointerException when Jexer tries to draw removed children

### Jexer Expected Pattern
Jexer expects widgets to be:
1. Created with a parent (auto-added via constructor)
2. Removed via `parent.remove(child)` or let go out of scope
3. Windows closed via `closeWindow()`

### Fix

**Option A: Recreate the window instead of clearing children**
```groovy
// Instead of:
getChildren().clear()
// new TLabel(this, ...)

// Do:
close()
// Create new window
```

**Option B: Track and properly remove children**
```groovy
// Keep references to children
private List<TWidget> managedChildren = []

void rebuildContent() {
    // Remove existing children properly
    managedChildren.each { child ->
        remove(child)
    }
    managedChildren.clear()
    
    // Add new children
    def label = new TLabel(this, "text", x, y)
    managedChildren << label
}
```

**Option C: Use Jexer's built-in methods**
```groovy
// Use addLabel, addButton, etc. which return the widget
// Store references and remove them properly
def label = addLabel("text", x, y)
// Later:
remove(label)
```

---

## Bug #2: TText setText() with Custom Wrapping

### Affected File
- `tui/jexer/widgets/JexerActivityLog.groovy` (line 79-81)

### Problem
```groovy
class JexerActivityLog extends TText {
    private void updateDisplay() {
        String wrappedText = wrapText(content.toString(), maxWidth)
        setText(wrappedText)  // Calls TText.setText()
    }
}
```

`TText.setText()` internally calls `reflow()` which **already wraps text** to the widget width. Double-wrapping causes:
- Incorrect line breaks
- Content truncation
- Scroll position issues

### Jexer Expected Pattern
```java
// TText handles wrapping internally
textWidget.setText("Plain text without manual wrapping");
// TText.reflow() wraps based on getWidth()
```

### Fix
```groovy
private void updateDisplay() {
    // Don't pre-wrap - let TText handle it
    setText(content.toString())
    
    // TText automatically reflows
    // Just need to scroll to bottom
    // Note: TText uses internal scroll, may need to use reflection or override draw()
}
```

If custom scrolling is needed, consider:
```groovy
// Override draw() instead of wrapping manually
@Override
void draw() {
    // Custom drawing logic with proper screen coordinates
    super.draw()
}
```

---

## Bug #3: Sidebar Sections Using Invalid Screen Coordinates

### Affected Files
- `tui/jexer/sidebar/JexerTokenSection.groovy`
- `tui/jexer/sidebar/JexerLspSection.groovy`
- `tui/jexer/sidebar/JexerModifiedFilesSection.groovy`
- `tui/jexer/sidebar/JexerSessionInfoSection.groovy`

### Problem
```groovy
private void setScreenCell(int x, int y, char ch, CellAttributes attr) {
    def screen = application.getScreen()
    int screenX = getX() + x  // getX() always returns 0!
    int screenY = getY() + y  // getY() always returns 0!
    screen.putChar(screenX, screenY, ch, attr)
}

int getX() { return 0 }  // Never overridden with actual position
int getY() { return 0 }
```

These sections are **not TWidgets** - they're plain Groovy classes. They have stub `getX()/getY()` methods that always return 0. Drawing goes to wrong screen positions.

### Jexer Expected Pattern
Either:
1. Make sections extend `TWidget` and use relative coordinates
2. Pass parent window coordinates when calling `render()`

### Fix

**Option A: Pass parent window position**
```groovy
// In JexerSidebar.groovy
int render(int startY, int startX, int windowX, int windowY) {
    // ...
    setScreenCell(startX + windowX, y + windowY, ch, attr)
}

// Call with:
currentY = tokenSection.render(currentY, 1, getX(), getY())
```

**Option B: Make sections TWidgets**
```groovy
class TokenSection extends TWidget {
    TokenSection(TWidget parent, String sessionId, int x, int y, int width, int height) {
        super(parent, x, y, width, height)
        // ...
    }
    
    @Override
    void draw() {
        // Use getScreen() with relative coordinates (0,0 = top-left of widget)
        getScreen().putCharXY(0, 0, '📊', myAttributes)
    }
}
```

**Option C: Store parent reference and get position**
```groovy
class TokenSection {
    private TWindow parentWindow
    
    TokenSection(TApplication app, TWindow parent, String sessionId, int width) {
        this.parentWindow = parent
        // ...
    }
    
    int getX() { return parentWindow?.getX() ?: 0 }
    int getY() { return parentWindow?.getY() ?: 0 }
}
```

---

## Bug #4: Invalid Color Constructor Usage

### Affected File
- `tui/JexerTheme.groovy` (lines 156-178)

### Problem
```groovy
static Color getSidebarBackgroundColor() {
    return new Color(1, 1, 2)  // WRONG: expects palette index, not RGB
}

static Color getSidebarBorderColor() {
    return new Color(2, 2, 3)  // WRONG
}
```

Jexer's `Color` class uses **palette indices** (0-255 for 256-color terminals), not RGB values. The constructor `Color(int r, int g, int b)` doesn't exist in standard Jexer.

### Jexer Expected Pattern
```java
// Use predefined Color constants
Color.BLACK
Color.RED
Color.GREEN
Color.YELLOW
Color.BLUE
Color.MAGENTA
Color.CYAN
Color.WHITE

// Or create from palette index
new Color(index)  // Single int for 256-color palette
```

### Fix
```groovy
static Color getSidebarBackgroundColor() {
    return Color.BLACK  // Or Color.BLUE for dark blue
}

static Color getSidebarBorderColor() {
    return Color.WHITE  // Or Color.CYAN for accent
}

static Color getSidebarTreeColor() {
    return Color.CYAN
}
```

For true RGB support (if terminal supports it):
```groovy
// Check if Jexer version supports RGB
// Some backends support: new Color(r, g, b) 
// But standard TUI terminals use 256-color palette
```

---

## Bug #5: TField.getCursorPosition() Doesn't Exist

### Affected File
- `tui/jexer/widgets/JexerCommandInput.groovy` (lines 171, 178, 206, 249, 278)

### Problem
```groovy
int cursorPos = getCursorPosition()  // Method doesn't exist!
```

`TField` doesn't have `getCursorPosition()`. It has:
- `position` (private field for cursor position within text)
- `getCursorX()` / `getCursorY()` (for screen cursor position)

### Jexer Expected Pattern
`TField` manages cursor internally. To get text position:
```java
// Access internal position (may need reflection or subclass)
// Or track position manually
```

### Fix

**Option A: Use getText().length() as approximation**
```groovy
// Cursor is typically at end after typing
int cursorPos = getText().length()
```

**Option B: Override to track position**
```groovy
class JexerCommandInput extends TField {
    private int textPosition = 0
    
    @Override
    void onKeypress(TKeypressEvent keypress) {
        super.onKeypress(keypress)
        // Update textPosition based on key
        textPosition = Math.min(textPosition, getText().length())
    }
    
    int getCursorPosition() {
        return textPosition
    }
}
```

**Option C: Use reflection (not recommended)**
```groovy
int getCursorPosition() {
    try {
        def field = TField.class.getDeclaredField('position')
        field.setAccessible(true)
        return field.get(this) as int
    } catch (Exception e) {
        return getText().length()
    }
}
```

---

## Bug #6: TWindow.setDimensions() Usage

### Affected File
- `tui/jexer/widgets/JexerAutocompletePopup.groovy` (line 93)

### Problem
```groovy
setDimensions(width, height)  // May not exist or have different signature
```

### Jexer Expected Pattern
`TWindow` uses `setWidth()` / `setHeight()` or constructor parameters:
```java
// In constructor
super(app, title, x, y, width, height, flags)

// Or after construction
setWidth(newWidth);
setHeight(newHeight);
```

### Fix
```groovy
void rebuildWindow() {
    // Set dimensions properly
    setWidth(width)
    setHeight(height)
    
    // Or: create a new popup window instead of resizing
}
```

---

## Bug #7: Commented-Out Attribute Setters

### Affected Files
- Multiple files have commented code like:
```groovy
// labelWidget.getScreenCellAttributes().setForeColor(...)
```

### Problem
`TLabel` (and most widgets) don't expose `getScreenCellAttributes()` directly. The commented code indicates attempted but failed approaches.

### Jexer Expected Pattern
Use the `ColorTheme` and widget's theme key:
```java
// Widgets draw themselves using theme colors
// Override draw() to customize

@Override
public void draw() {
    CellAttributes attr = getTheme().getColor("tlabel");
    attr.setForeColor(Color.CYAN);
    getScreen().putStringXY(0, 0, getText(), attr);
}
```

Or set during construction via custom theme keys.

### Fix

**Option A: Override draw() in custom widgets**
```groovy
class ColoredLabel extends TLabel {
    private Color foreground
    
    ColoredLabel(TWidget parent, String text, int x, int y, Color fg) {
        super(parent, text, x, y)
        this.foreground = fg
    }
    
    @Override
    void draw() {
        CellAttributes attr = new CellAttributes()
        attr.setForeColor(foreground)
        attr.setBackColor(JexerTheme.getBackgroundColor())
        getScreen().putStringXY(0, 0, getLabel(), attr)
    }
}
```

**Option B: Use theme customization**
```groovy
// In JexerTheme.applyDarkTheme()
theme.setColor("custom.label", new CellAttributes())
theme.getColor("custom.label").setForeColor(Color.CYAN)

// In widget draw()
CellAttributes attr = getTheme().getColor("custom.label")
```

---

## Bug #8: addBox() Coordinate Confusion

### Affected File
- `tui/jexer/widgets/JexerAutocompletePopup.groovy` (lines 103-108, 246-264)

### Problem
```groovy
private void addBox(int x, int y, int width, int height, CellAttributes attr) {
    // Top border - uses x as row?
    for (int i = 0; i < width; i++) {
        setScreenCell(x + i, y, ' ', attr)  // x + i for horizontal
    }
    // Left border
    for (int i = 0; i < height; i++) {
        setScreenCell(x, y + i, ' ', attr)  // y + i for vertical
    }
}
```

The parameters suggest `(x, y, width, height)` but the implementation draws incorrectly:
- First loop: `x + i, y` - horizontal line (correct if x is column)
- But the usage `addBox(y, PADDING, width - 2 * PADDING, 1, ...)` passes `y` as first param

### Fix
```groovy
// Clarify parameter order: col, row
private void drawHighlightBox(int col, int row, int width, int height, CellAttributes attr) {
    // Fill the box area
    for (int dy = 0; dy < height; dy++) {
        for (int dx = 0; dx < width; dx++) {
            setScreenCell(col + dx, row + dy, ' ', attr)
        }
    }
}

// Call with correct order:
drawHighlightBox(PADDING, y, width - 2 * PADDING, ITEM_HEIGHT, highlightAttr)
```

---

## Bug #9: JexerActivityLog Scroll Position Tracking

### Affected File
- `tui/jexer/widgets/JexerActivityLog.groovy`

### Problem
`TText` manages its own scroll state. The custom `scrollPosition` field conflicts with internal state:
```groovy
private int scrollPosition = 0  // Custom tracking

void scrollToBottom() {
    // ... custom calculation
    scrollPosition = totalLines - visibleLines
}
```

### Jexer Expected Pattern
`TText` has internal scroll management. Use inherited methods or override `draw()`.

### Fix
```groovy
// Use TText's internal scrolling
void scrollToBottom() {
    // TText should have scroll methods or override draw()
    // May need to use toEnd() if available or simulate End key
}

// Or override draw() completely for full control
@Override
void draw() {
    // Calculate visible portion based on scroll
    // Draw lines manually with getScreen().putStringXY()
}
```

---

## Bug #10: Event Handling in JexerCommandInput

### Affected File
- `tui/jexer/widgets/JexerCommandInput.groovy` (line 82)

### Problem
```groovy
if (keypress.getKey() == kbBackspace) {  // Object comparison, not equals()
```

Should use `.equals()` for key comparison.

### Fix
```groovy
if (keypress.getKey().equals(kbBackspace)) {
    handleBackspace()
}
```

---

## Implementation Priority

### Phase 1: Critical Fixes (Breaking Issues)
1. **Fix `getChildren().clear()` pattern** - Replace with proper widget lifecycle
2. **Fix `getCursorPosition()`** - Implement or track cursor manually
3. **Fix Color constructor** - Use palette-based colors

### Phase 2: High Priority (Rendering Issues)
4. **Fix sidebar section coordinates** - Pass parent window position
5. **Fix TText wrapping** - Remove manual wrapping, use TText internal

### Phase 3: Medium Priority (Functionality)
6. **Fix setDimensions()** - Use setWidth/setHeight
7. **Fix addBox() coordinates** - Clarify row/col order
8. **Fix key comparison** - Use `.equals()`

### Phase 4: Cleanup
9. **Remove commented code** - Replace with working solutions
10. **Add proper draw() overrides** - For custom coloring

---

## Testing Checklist

After implementing fixes:

- [ ] TUI starts without exceptions
- [ ] Sidebar sections render at correct positions
- [ ] Autocomplete popup appears at cursor position
- [ ] Command history (Up/Down arrows) works
- [ ] @ mentions trigger autocomplete
- [ ] / commands trigger autocomplete
- [ ] Tab cycles agents (status bar updates)
- [ ] Ctrl+C exits cleanly
- [ ] Long messages wrap correctly in activity log
- [ ] Scroll position updates in status bar
- [ ] Token counts update after API calls
- [ ] No visual artifacts when resizing

---

## References

- [Jexer GitLab Repository](https://gitlab.com/AutumnMeowMeow/jexer)
- [Jexer TWidget Source](https://gitlab.com/AutumnMeowMeow/jexer/-/blob/master/src/jexer/TWidget.java)
- [Jexer TWindow Source](https://gitlab.com/AutumnMeowMeow/jexer/-/blob/master/src/jexer/TWindow.java)
- [Jexer TText Source](https://gitlab.com/AutumnMeowMeow/jexer/-/blob/master/src/jexer/TText.java)
- [Jexer TField Source](https://gitlab.com/AutumnMeowMeow/jexer/-/blob/master/src/jexer/TField.java)
- [Jexer Color Source](https://gitlab.com/AutumnMeowMeow/jexer/-/blob/master/src/jexer/bits/Color.java)
