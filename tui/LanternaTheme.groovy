package tui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class LanternaTheme {
    
    static void applyDarkTheme(MultiWindowTextGUI gui) {
    }
    
    static void applyToTextBox(TextBox textBox) {
    }
    
    static void applyToPanel(Panel panel) {
    }
    
    static void applyToLabel(Label label) {
    }
    
    static TextColor getBackgroundColor() {
        return new TextColor.RGB(26, 26, 46)
    }
    
    static TextColor getForegroundColor() {
        return TextColor.ANSI.WHITE
    }
    
    static TextColor getAccentColor() {
        return TextColor.ANSI.CYAN
    }
    
    static TextColor getErrorColor() {
        return TextColor.ANSI.RED
    }
    
    static TextColor getWarningColor() {
        return TextColor.ANSI.YELLOW
    }
    
    static TextColor getUserMessageColor() {
        return TextColor.ANSI.GREEN
    }
    
    static TextColor getAIResponseColor() {
        return TextColor.ANSI.WHITE
    }
    
    static TextColor getToolColor() {
        return TextColor.ANSI.MAGENTA
    }
}
