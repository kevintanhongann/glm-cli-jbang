package tui.themes

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class LightTheme {

    static TextColor getBackgroundColor() {
        return new TextColor.RGB(250, 250, 252)
    }

    static TextColor getBackgroundPanelColor() {
        return new TextColor.RGB(245, 245, 248)
    }

    static TextColor getBackgroundElementColor() {
        return new TextColor.RGB(240, 240, 244)
    }

    static TextColor getForegroundColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getTextColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getTextMutedColor() {
        return new TextColor.RGB(100, 100, 100)
    }

    static TextColor getTextBoldColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getAccentColor() {
        return new TextColor.RGB(0, 122, 204)
    }

    static TextColor getErrorColor() {
        return new TextColor.RGB(197, 15, 31)
    }

    static TextColor getWarningColor() {
        return new TextColor.RGB(191, 90, 0)
    }

    static TextColor getSuccessColor() {
        return new TextColor.RGB(40, 167, 69)
    }

    static TextColor getInfoColor() {
        return new TextColor.RGB(0, 122, 204)
    }

    static TextColor getUserMessageColor() {
        return new TextColor.RGB(0, 134, 9)
    }

    static TextColor getAIResponseColor() {
        return TextColor.ANSI.BLACK
    }

    static TextColor getToolColor() {
        return new TextColor.RGB(136, 46, 224)
    }

    static TextColor getAgentBuildColor() {
        return new TextColor.RGB(0, 122, 204)
    }

    static TextColor getAgentPlanColor() {
        return new TextColor.RGB(191, 90, 0)
    }

    static TextColor getSidebarBackgroundColor() {
        return new TextColor.RGB(240, 240, 244)
    }

    static TextColor getSidebarBorderColor() {
        return new TextColor.RGB(200, 200, 200)
    }

    static TextColor getSidebarHeaderColor() {
        return new TextColor.RGB(80, 80, 80)
    }

    static TextColor getSidebarTreeColor() {
        return new TextColor.RGB(150, 150, 150)
    }

    static TextColor getSidebarHighlightColor() {
        return new TextColor.RGB(220, 220, 220)
    }

    static TextColor getDiffAddedColor() {
        return new TextColor.RGB(34, 139, 34)
    }

    static TextColor getDiffRemovedColor() {
        return new TextColor.RGB(197, 15, 31)
    }

    static TextColor getDiffAddedBackgroundColor() {
        return new TextColor.RGB(220, 252, 220)
    }

    static TextColor getDiffRemovedBackgroundColor() {
        return new TextColor.RGB(252, 220, 220)
    }

    static TextColor getDiffContextBackgroundColor() {
        return new TextColor.RGB(250, 250, 252)
    }

    static TextColor getToolReadColor() {
        return new TextColor.RGB(0, 122, 204)
    }

    static TextColor getToolWriteColor() {
        return new TextColor.RGB(40, 167, 69)
    }

    static TextColor getToolSearchColor() {
        return new TextColor.RGB(191, 90, 0)
    }

    static TextColor getToolExecuteColor() {
        return new TextColor.RGB(136, 46, 224)
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
                        return new ThemeStyle(getAccentColor(), TextColor.ANSI.WHITE, null)
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
