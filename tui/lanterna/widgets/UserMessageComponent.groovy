package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor

/**
 * Displays user messages in the chat
 */
class UserMessageComponent extends MessageComponent {
    
    UserMessageComponent(String messageId, String content) {
        super(messageId, "user", content)
    }
    
    @Override
    protected void setup() {
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        setBorder(Borders.singleLine("👤 You"))
        
        // Add timestamp if enabled
        if (context.isShowTimestamps()) {
            Label ts = new Label("[${formatTimestamp()}]")
            ts.setForegroundColor(TextColor.ANSI.BLACK)
            ts.setBackgroundColor(TextColor.ANSI.WHITE)
            addComponent(ts)
        }
        
        // Wrap content to available width
        def lines = wrapText(content, availableWidth - 4)
        lines.each { line ->
            Label label = new Label(line)
            label.setForegroundColor(TextColor.ANSI.WHITE)
            addComponent(label)
        }
    }
    
    private List<String> wrapText(String text, int maxWidth) {
        if (!text) return []
        
        List<String> lines = []
        def words = text.split(' ')
        String currentLine = ""
        
        words.each { word ->
            if ((currentLine + " " + word).length() > maxWidth) {
                if (currentLine) lines << currentLine
                currentLine = word
            } else {
                currentLine = currentLine ? currentLine + " " + word : word
            }
        }
        
        if (currentLine) lines << currentLine
        return lines
    }
}
