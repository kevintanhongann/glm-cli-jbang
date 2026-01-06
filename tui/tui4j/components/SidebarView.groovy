package tui.tui4j.components

import com.williamcallahan.tui4j.compat.bubbletea.*
import tui.tui4j.Tui4jTheme

class SidebarView implements Model {

    private final Tui4jTheme theme = Tui4jTheme.instance

    @Override
    Command init() { null }

    @Override
    UpdateResult<? extends Model> update(Message msg) {
        return UpdateResult.from(this)
    }

    @Override
    String view() {
        return theme.sidebarStyle.render('''
│ Files
│ ───────
│ src/
│   main.groovy
│
│ Diagnostics
│ ───────
│ ✓ No issues
''')
    }

}
