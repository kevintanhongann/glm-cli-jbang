package tui.tui4j.components

import com.williamcallahan.tui4j.compat.bubbletea.*
import com.williamcallahan.tui4j.compat.bubbletea.message.*
import tui.tui4j.Tui4jTheme
import tui.shared.OutputFormatter

class ConversationView implements Model {

    private List<Map> messages = []
    private int scrollOffset = 0
    private int visibleHeight = 20
    private final Tui4jTheme theme = Tui4jTheme.instance

    void setMessages(List<Map> msgs) {
        this.messages = msgs
    }

    void scrollUp() {
        scrollOffset = Math.max(0, scrollOffset - 1)
    }

    void scrollDown() {
        scrollOffset = Math.min(messages.size() - 1, scrollOffset + 1)
    }

    @Override
    Command init() { null }

    @Override
    UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPressMessage) {
            switch (((KeyPressMessage) msg).key()) {
                case 'up': scrollUp(); break
                case 'down': scrollDown(); break
            }
        }
        return UpdateResult.from(this)
    }

    @Override
    String view() {
        def sb = new StringBuilder()

        for (m in messages) {
            if (m.role == 'user') {
                sb.append(theme.userStyle.render('You: '))
                sb.append(m.content)
            } else if (m.role == 'assistant') {
                sb.append(theme.assistantStyle.render('Assistant: '))
                sb.append(m.content)
            }
            sb.append("\n\n")
        }

        return sb.toString()
    }

}
