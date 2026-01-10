package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor
import tui.shared.TuiContext

/**
 * Base class for message display components
 */
abstract class MessageComponent extends Panel implements TuiContext.WidthChangeListener {
    
    protected String messageId
    protected String role // "user", "assistant", "tool", "system"
    protected String content
    protected long timestamp = System.currentTimeMillis()
    protected TuiContext context
    protected int availableWidth
    
    MessageComponent(String messageId, String role, String content) {
        this.messageId = messageId
        this.role = role
        this.content = content
        this.context = TuiContext.getInstance()
        this.availableWidth = context.getTerminalWidth()
        
        context.onWidthChange(this)
        setup()
    }
    
    protected abstract void setup()
    
    @Override
    void onWidthChange(int width, int height) {
        this.availableWidth = width
        invalidate()
    }
    
    protected String formatTimestamp() {
        new java.text.SimpleDateFormat("HH:mm:ss").format(new Date(timestamp))
    }
    
    protected String truncate(String text, int maxLen) {
        if (!text || text.length() <= maxLen) return text ?: ''
        return text.substring(0, maxLen - 3) + '...'
    }
    
    String getMessageId() { messageId }
    String getRole() { role }
    String getContent() { content }
    long getTimestamp() { timestamp }
}
