package tui.lanterna.widgets

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor
import java.util.concurrent.atomic.AtomicReference

/**
 * Inline permission prompt with diff preview
 */
class PermissionPromptPanel extends Panel {
    
    enum Action { ALLOW, DENY, ALLOW_ALL }
    
    private String toolName
    private Map<String, Object> arguments
    private DiffViewer diffViewer
    private AtomicReference<Action> responseRef = new AtomicReference<>(null)
    
    PermissionPromptPanel(String toolName, Map args, String diffPath = null, String original = null, String modified = null) {
        this.toolName = toolName
        this.arguments = args
        
        if (diffPath && original && modified) {
            this.diffViewer = new DiffViewer(diffPath, original, modified, 70)
        }
        
        setup()
    }
    
    private void setup() {
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        setBorder(Borders.singleLine("⚠️ PERMISSION REQUIRED"))
        
        // Header
        Label header = new Label("Tool: ${toolName}")
        header.setForegroundColor(TextColor.ANSI.YELLOW)
        addComponent(header)
        
        // Arguments display
        if (arguments && !arguments.isEmpty()) {
            addComponent(new Label("Arguments:"))
            arguments.each { key, value ->
                String valueStr = value.toString()
                if (valueStr.length() > 50) {
                    valueStr = valueStr.substring(0, 47) + "..."
                }
                addComponent(new Label("  ${key}: ${valueStr}"))
            }
        }
        
        // Diff preview if available
        if (diffViewer) {
            addComponent(new Label(""))
            addComponent(new Label("Preview:"))
            addComponent(diffViewer)
        }
        
        // Action buttons
        addComponent(new Label(""))
        Panel buttons = new Panel()
        buttons.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        Button allowBtn = new Button("Allow (y)", { 
            responseRef.set(Action.ALLOW) 
        })
        Button denyBtn = new Button("Deny (n)", { 
            responseRef.set(Action.DENY) 
        })
        Button allowAllBtn = new Button("Allow All (!)", { 
            responseRef.set(Action.ALLOW_ALL) 
        })
        
        buttons.addComponent(allowBtn)
        buttons.addComponent(new Label(" "))
        buttons.addComponent(denyBtn)
        buttons.addComponent(new Label(" "))
        buttons.addComponent(allowAllBtn)
        
        addComponent(buttons)
    }
    
    Action getResponse() {
        return responseRef.get()
    }
    
    boolean hasResponse() {
        return responseRef.get() != null
    }
}
