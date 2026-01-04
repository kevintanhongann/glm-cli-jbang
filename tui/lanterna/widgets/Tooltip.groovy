package tui.lanterna.widgets

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor

/**
 * Simple tooltip component for displaying context-sensitive information.
 */
class Tooltip extends BasicWindow {
    private String message
    private TextColor bgColor
    private TextColor fgColor
    
    Tooltip(String message, TextColor bgColor = TextColor.ANSI.BLACK, TextColor fgColor = TextColor.ANSI.WHITE) {
        this.message = message
        this.bgColor = bgColor
        this.fgColor = fgColor
        setupWindow()
    }
    
    private void setupWindow() {
        setHints(Arrays.asList(Window.Hint.NO_FOCUS, Window.Hint.FLOATING))
        setComponent(createContent())
        setPosition(createPosition())
    }
    
    private Panel createContent() {
        def panel = new Panel()
        panel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def label = new Label(message)
        label.setForegroundColor(fgColor)
        panel.setBackgroundColor(bgColor)
        label.setBackgroundColor(bgColor)
        
        panel.addComponent(label)
        panel.setPadding(1)
        
        return panel
    }
    
    private void createPosition() {
        // Position tooltip near the current cursor or specified location
        // For now, just center it in the available space
        return null
    }
    
    void show(MultiWindowTextGUI gui) {
        gui.addWindow(this)
    }
    
    void close() {
        if (isVisible()) {
            this.close()
        }
    }
    
    void setMessage(String message) {
        this.message = message
        setComponent(createContent())
    }
}
