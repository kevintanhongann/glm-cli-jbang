package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize
import tui.shared.TuiContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Container for message components with scrolling and sticky-bottom behavior
 */
class ScrollableMessageList extends Panel implements TuiContext.WidthChangeListener {
    
    private ScrollingContainer scrollingContainer
    private Panel messagesPanel
    private List<MessageComponent> messages = new CopyOnWriteArrayList<>()
    private TuiContext context
    private boolean stickyBottom = true
    private int scrollPosition = 0
    
    ScrollableMessageList() {
        this.context = TuiContext.getInstance()
        context.onWidthChange(this)
        setup()
    }
    
    private void setup() {
        setLayoutManager(new BorderLayout())
        
        // Create messages container
        messagesPanel = new Panel()
        messagesPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Wrap in scrolling container
        scrollingContainer = new ScrollingContainer(messagesPanel)
        addComponent(scrollingContainer, BorderLayout.Location.CENTER)
    }
    
    void addMessage(MessageComponent message) {
        messages.add(message)
        messagesPanel.addComponent(message)
        
        if (stickyBottom) {
            scrollToBottom()
        }
    }
    
    void removeMessage(String messageId) {
        def msg = messages.find { it.messageId == messageId }
        if (msg) {
            messages.remove(msg)
            messagesPanel.removeComponent(msg)
        }
    }
    
    void scrollToBottom() {
        if (scrollingContainer) {
            try {
                scrollingContainer.setVerticalScrollbarPosition(Integer.MAX_VALUE)
            } catch (Exception e) {
                // Scrolling might not be available yet
            }
        }
    }
    
    void scrollToTop() {
        if (scrollingContainer) {
            scrollingContainer.setVerticalScrollbarPosition(0)
        }
    }
    
    void scrollUp(int lines = 3) {
        scrollPosition = Math.max(0, scrollPosition - lines)
        updateScroll()
    }
    
    void scrollDown(int lines = 3) {
        scrollPosition += lines
        updateScroll()
    }
    
    private void updateScroll() {
        if (scrollingContainer) {
            try {
                scrollingContainer.setVerticalScrollbarPosition(scrollPosition)
            } catch (Exception e) {
            }
        }
    }
    
    @Override
    void onWidthChange(int width, int height) {
        // Messages will self-update on width change via their own listeners
    }
    
    List<MessageComponent> getMessages() {
        return new ArrayList<>(messages)
    }
    
    MessageComponent getMessage(String messageId) {
        return messages.find { it.messageId == messageId }
    }
    
    int getMessageCount() {
        return messages.size()
    }
    
    void clear() {
        messages.clear()
        messagesPanel.removeAllComponents()
        scrollPosition = 0
    }
    
    void setStickyBottom(boolean sticky) {
        this.stickyBottom = sticky
        if (sticky) scrollToBottom()
    }
    
    boolean isStickyBottom() {
        return stickyBottom
    }
}
