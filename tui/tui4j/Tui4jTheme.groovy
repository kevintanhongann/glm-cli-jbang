package tui.tui4j

import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.*
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.color.Color
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.border.StandardBorder

@Singleton
class Tui4jTheme {

    // Colors
    static final String BG_DARK = '#1a1a2e'
    static final String FG_WHITE = '#ffffff'
    static final String ACCENT_BLUE = '#4fc3f7'
    static final String ACCENT_GREEN = '#81c784'
    static final String ACCENT_RED = '#e57373'
    static final String ACCENT_YELLOW = '#fff176'
    static final String BORDER_COLOR = '#3a3a5e'

    // Styles
    Style headerStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_BLUE))
        .bold(true)
        .padding(0, 1)

    Style userStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_GREEN))
        .bold(true)

    Style assistantStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_BLUE))
        .bold(true)

    Style promptStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_GREEN))

    Style statusStyle = Style.newStyle()
        .foreground(Color.color('#888888'))
        .padding(0, 1)

    Style errorStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_RED))
        .bold(true)

    Style sidebarStyle = Style.newStyle()
        .width(30)
        .padding(1)
        .border(StandardBorder.RoundedBorder)
        .borderForeground(Color.color(BORDER_COLOR))

    Style codeBlockStyle = Style.newStyle()
        .background(Color.color('#2d2d4a'))
        .padding(1)
        .margin(0, 2)

}
