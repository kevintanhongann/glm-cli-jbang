package tui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.graphics.SimpleTheme

class LanternaTheme {

    static void applyDarkTheme(MultiWindowTextGUI gui) {
        if (gui != null) {
            try {
                // Set default window background
                def theme = SimpleTheme.makeTheme(
                    true,                          // activeIsBold
                    getTextColor(),                // baseForeground
                    getBackgroundColor(),          // baseBackground
                    getAccentColor(),              // editableForeground
                    getBackgroundColor(),          // editableBackground
                    getAccentColor(),              // selectedForeground
                    getBackgroundElementColor(),   // selectedBackground
                    getBackgroundColor()           // guiBackground
                )
                gui.setTheme(theme)
            } catch (Exception e) {
            }
        }
    }

    static void applyToTextBox(TextBox textBox) {
        if (textBox != null) {
            try {
                textBox.setBackgroundColor(getBackgroundColor())
                textBox.setForegroundColor(getTextColor())
            } catch (Exception e) {
            }
        }
    }

    static void applyToPanel(Panel panel) {
        if (panel != null) {
            try {
                panel.setBackgroundColor(getBackgroundColor())
                panel.setForegroundColor(getTextColor())
            } catch (Exception e) {
            }
        }
    }

    static void applyToLabel(Label label) {
    }

    static TextColor getBackgroundColor() {
        return new TextColor.RGB(26, 26, 46)
    }

    static TextColor getBackgroundElementColor() {
        return new TextColor.RGB(35, 35, 55)
    }

    static TextColor getForegroundColor() {
        return TextColor.ANSI.WHITE
    }

    static TextColor getTextColor() {
        return TextColor.ANSI.WHITE
    }

    static TextColor getTextMutedColor() {
        return new TextColor.RGB(128, 128, 128)
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
