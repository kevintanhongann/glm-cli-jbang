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
    
    static TextColor getTextColor() {
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

    static TextColor getAgentBuildColor() {
        return TextColor.ANSI.CYAN
    }

    static TextColor getAgentPlanColor() {
        return TextColor.ANSI.YELLOW
    }
    
    // Sidebar colors
    static TextColor getTextMutedColor() {
        return new TextColor.RGB(128, 128, 128)
    }
    
    static TextColor getSidebarBackgroundColor() {
        return new TextColor.RGB(30, 30, 50)
    }
    
    static TextColor getSidebarBorderColor() {
        return new TextColor.RGB(70, 70, 90)
    }
    
    static TextColor getSuccessColor() {
        return TextColor.ANSI.GREEN
    }
    
    static TextColor getDiffAddedColor() {
        return new TextColor.RGB(76, 175, 80)
    }
    
    static TextColor getDiffRemovedColor() {
        return new TextColor.RGB(244, 67, 54)
    }
    
    // Enhanced colors
    static TextColor getSidebarHeaderColor() {
        return new TextColor.RGB(100, 100, 120)
    }
    
    static TextColor getSidebarTreeColor() {
        return new TextColor.RGB(90, 90, 110)
    }
    
    static TextColor getSidebarHighlightColor() {
        return new TextColor.RGB(60, 60, 80)
    }
}
