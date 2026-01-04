package tui.lanterna.sidebar

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor
import core.ModifiedFile
import core.SessionStatsManager

class ModifiedFilesSection extends Panel {
    private String sessionId
    
    private Label headerLabel
    private Panel contentPanel
    
    private boolean expanded = true
    
    ModifiedFilesSection(String sessionId) {
        this.sessionId = sessionId
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        buildUI()
    }
    
    private void buildUI() {
        removeAllComponents()
        
        // Get modified files
        def sessionStats = SessionStatsManager.instance.getStats(sessionId)
        def modifiedFiles = sessionStats?.modifiedFiles ?: []
        
        // Build header with visual style
        def headerPanel = new Panel()
        headerPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def expandLabel = new Label(expanded ? "▼" : "▶")
        expandLabel.setForegroundColor(LanternaTheme.getTextColor())
        headerPanel.addComponent(expandLabel)
        
        headerLabel = new Label(" Modified Files")
        headerLabel.setForegroundColor(LanternaTheme.getTextColor())
        headerPanel.addComponent(headerLabel)
        
        // Show count if collapsed
        if (!expanded || modifiedFiles.size() > 2) {
            def countLabel = new Label(" (${modifiedFiles.size()})")
            countLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
            headerPanel.addComponent(countLabel)
        }
        
        addComponent(headerPanel)
        
        // Add visual separator
        def separator = new Label("─${"─" * 35}")
        separator.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        addComponent(separator)
        addComponent(new Label(""))
        
        // Content
        if (expanded) {
            if (modifiedFiles.isEmpty()) {
                def emptyLabel = new Label("  └─ No modified files")
                emptyLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
                addComponent(emptyLabel)
            } else {
                for (file in modifiedFiles) {
                    addModifiedFileItem(file)
                }
            }
        }
        
        // Separator
        addComponent(new Label(""))
    }
    
    private void addModifiedFileItem(ModifiedFile file) {
        def filePanel = new Panel()
        filePanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // File name with tree structure
        def fileLinePanel = new Panel()
        fileLinePanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def treeLabel = new Label("│  └─ ")
        treeLabel.setForegroundColor(LanternaTheme.getSidebarTreeColor())
        fileLinePanel.addComponent(treeLabel)
        
        // File name (truncated)
        String fileName = getShortFileName(file.filePath)
        def fileLabel = new Label(fileName)
        fileLabel.setForegroundColor(LanternaTheme.getTextColor())
        fileLinePanel.addComponent(fileLabel)
        
        filePanel.addComponent(fileLinePanel)
        
        // Diff stats with color coding on separate line
        if (file.additions > 0 || file.deletions > 0) {
            def statsPanel = new Panel()
            statsPanel.setLayout(new LinearLayout(Direction.HORIZONTAL))
            
            def statsTree = new Label("│     ")
            statsTree.setForegroundColor(LanternaTheme.getSidebarTreeColor())
            statsPanel.addComponent(statsTree)
            
            if (file.additions > 0) {
                def addLabel = new Label("└─ +${file.additions}")
                addLabel.setForegroundColor(LanternaTheme.getDiffAddedColor())
                statsPanel.addComponent(addLabel)
            }
            
            if (file.deletions > 0) {
                if (file.additions > 0) {
                    statsPanel.addComponent(new Label(" "))
                }
                def delLabel = new Label("└─ -${file.deletions}")
                delLabel.setForegroundColor(LanternaTheme.getDiffRemovedColor())
                statsPanel.addComponent(delLabel)
            }
            
            filePanel.addComponent(statsPanel)
        }
        
        addComponent(filePanel)
    }

    private String getShortFileName(String filePath) {
        // Extract just the filename, or truncate the full path
        def parts = filePath.split("/")
        if (parts.length > 0) {
            return parts[-1]
        }
        return filePath
    }
    
    void toggleExpanded() {
        expanded = !expanded
        buildUI()
    }
    
    void refresh() {
        buildUI()
    }
}
