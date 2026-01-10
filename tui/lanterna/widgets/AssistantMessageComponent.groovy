package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor

/**
 * Displays assistant messages with optional diff viewer for tool outputs
 */
class AssistantMessageComponent extends MessageComponent {
    
    private DiffViewer diffViewer
    private String diffOriginal
    private String diffModified
    private String diffPath
    
    AssistantMessageComponent(String messageId, String content) {
        super(messageId, "assistant", content)
    }
    
    void setDiff(String filePath, String original, String modified) {
        this.diffPath = filePath
        this.diffOriginal = original
        this.diffModified = modified
        this.diffViewer = new DiffViewer(filePath, original, modified, availableWidth - 4)
        invalidate()
    }
    
    @Override
    protected void setup() {
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        setBorder(Borders.singleLine("🤖 Assistant"))
        
        // Add timestamp if enabled
        if (context.isShowTimestamps()) {
            Label ts = new Label("[${formatTimestamp()}]")
            ts.setForegroundColor(TextColor.ANSI.BLACK)
            ts.setBackgroundColor(TextColor.ANSI.CYAN)
            addComponent(ts)
        }
        
        // Wrap content to available width
        def lines = wrapText(content, availableWidth - 4)
        lines.each { line ->
            Label label = new Label(line)
            label.setForegroundColor(TextColor.ANSI.CYAN)
            addComponent(label)
        }
        
        // Add diff viewer if present
        if (diffViewer) {
            addComponent(diffViewer)
        }
    }
    
    @Override
    void onWidthChange(int width, int height) {
        super.onWidthChange(width, height)
        if (diffViewer) {
            diffViewer.updateWidth(width - 4)
        }
    }
    
    private List<String> wrapText(String text, int maxWidth) {
        if (!text) return []
        
        List<String> lines = []
        def paragraphs = text.split('\n')
        
        paragraphs.each { para ->
            if (!para) {
                lines << ""
            } else {
                def words = para.split(' ')
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
            }
        }
        
        return lines
    }
}
