package tui.themes

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class HighContrastTheme {

    static TextColor getBackgroundColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getBackgroundPanelColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getBackgroundElementColor() {
        return new TextColor.RGB(40, 40, 40)
    }

    static TextColor getForegroundColor() {
        return TextColor.ANSI.WHITE
    }

    static TextColor getTextColor() {
        return TextColor.ANSI.WHITE
    }

    static TextColor getTextMutedColor() {
        return TextColor.ANSI.WHITE_BRIGHT
    }

    static TextColor getTextBoldColor() {
        return TextColor.ANSI.WHITE_BRIGHT
    }

    static TextColor getAccentColor() {
        return TextColor.ANSI.WHITE_BRIGHT
    }

    static TextColor getErrorColor() {
        return TextColor.ANSI.RED
    }

    static TextColor getWarningColor() {
        return TextColor.ANSI.YELLOW
    }

    static TextColor getSuccessColor() {
        return TextColor.ANSI.GREEN
    }

    static TextColor getInfoColor() {
        return TextColor.ANSI.CYAN
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
        return new TextColor.RGB(30, 30, 30)
    }

    static TextColor getSidebarBorderColor() {
        return TextColor.ANSI.WHITE
    }

    static TextColor getSidebarHeaderColor() {
        return TextColor.ANSI.WHITE_BRIGHT
    }

    static TextColor getSidebarTreeColor() {
        return TextColor.ANSI.WHITE
    }

    static TextColor getSidebarHighlightColor() {
        return new TextColor.RGB(60, 60, 60)
    }

    static TextColor getDiffAddedColor() {
        return TextColor.ANSI.GREEN
    }

    static TextColor getDiffRemovedColor() {
        return TextColor.ANSI.RED
    }

    static TextColor getDiffAddedBackgroundColor() {
        return new TextColor.RGB(0, 60, 0)
    }

    static TextColor getDiffRemovedBackgroundColor() {
        return new TextColor.RGB(60, 0, 0)
    }

    static TextColor getDiffContextBackgroundColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getToolReadColor() {
        return TextColor.ANSI.CYAN
    }

    static TextColor getToolWriteColor() {
        return TextColor.ANSI.GREEN
    }

    static TextColor getToolSearchColor() {
        return TextColor.ANSI.YELLOW
    }

    static TextColor getToolExecuteColor() {
        return TextColor.ANSI.MAGENTA
    }

    static void apply(MultiWindowTextGUI gui) {
        gui.setTheme(new Theme() {
            @Override
            ThemeDefinition getDefinition(String name) {
                return new ThemeDefinition() {
                    @Override
                    public ThemeStyle getNormal() {
                        return new ThemeStyle(getBackgroundColor(), getTextColor(), null)
                    }
                    @Override
                    public ThemeStyle getPreLight() {
                        return new ThemeStyle(getBackgroundColor(), getTextColor(), null)
                    }
                    @Override
                    public ThemeStyle getSelection() {
                        return new ThemeStyle(getAccentColor(), getBackgroundColor(), null)
                    }
                    @Override
                    public ThemeStyle getActive() {
                        return new ThemeStyle(getBackgroundElementColor(), getTextColor(), null)
                    }
                    @Override
                    public ThemeStyle getInsensitive() {
                        return new ThemeStyle(getBackgroundColor(), getTextMutedColor(), null)
                    }
                    @Override
                    public ThemeStyle getBorder() {
                        return new ThemeStyle(getBackgroundColor(), getSidebarBorderColor(), null)
                    }
                }
            }
        })
    }
}
