package tui.lanterna.sidebar

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor
import core.SessionStatsManager
import core.SessionManager
import java.nio.file.Paths
import java.util.UUID

class SessionInfoSection extends Panel {
    private String sessionId
    
    private Label titleLabel
    private Label directoryLabel
    private Label sessionIdLabel
    
    SessionInfoSection(String sessionId) {
        this.sessionId = sessionId
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        buildUI()
    }
    
    private void buildUI() {
        removeAllComponents()
        
        // Get session info
        def sessionManager = SessionManager.instance
        def session = sessionManager.getSession(sessionId)
        String title = session?.title ?: "New Session"
        String cwd = System.getProperty("user.dir")
        String dirName = Paths.get(cwd).fileName?.toString() ?: cwd
        
        // Build content panel
        def contentPanel = new Panel()
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Session title with bold style
        def titlePanel = new Panel()
        titlePanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def titleDecor = new Label("┌")
        titleDecor.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        titlePanel.addComponent(titleDecor)
        
        titleLabel = new Label(" ${title}")
        titleLabel.setForegroundColor(LanternaTheme.getTextColor())
        titlePanel.addComponent(titleLabel)
        
        def titleEnd = new Label("┐")
        titleEnd.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        titlePanel.addComponent(titleEnd)
        
        contentPanel.addComponent(titlePanel)
        
        // Directory with tree structure
        def dirPanel = new Panel()
        dirPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def dirTree = new Label("│  ")
        dirTree.setForegroundColor(LanternaTheme.getSidebarTreeColor())
        dirPanel.addComponent(dirTree)
        
        def dirIcon = new Label("📁 ")
        dirPanel.addComponent(dirIcon)
        
        directoryLabel = new Label(dirName)
        directoryLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
        dirPanel.addComponent(directoryLabel)
        
        contentPanel.addComponent(dirPanel)
        
        // Session ID (truncated) with tree structure
        String shortId = sessionId.length() > 12 ? sessionId[0..11] + "..." : sessionId
        def idPanel = new Panel()
        idPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def idTree = new Label("│  ")
        idTree.setForegroundColor(LanternaTheme.getSidebarTreeColor())
        idPanel.addComponent(idTree)
        
        def idIcon = new Label("🆔 ")
        idPanel.addComponent(idIcon)
        
        sessionIdLabel = new Label(shortId)
        sessionIdLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
        idPanel.addComponent(sessionIdLabel)
        
        contentPanel.addComponent(idPanel)
        
        // Bottom border
        def bottomPanel = new Panel()
        bottomPanel.setLayout(new LinearLayout(Direction.HORIZONTAL))
        
        def bottomDecor = new Label("└")
        bottomDecor.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        bottomPanel.addComponent(bottomDecor)
        
        def bottomLine = new Label("─" * 38)
        bottomLine.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        bottomPanel.addComponent(bottomLine)
        
        def bottomEnd = new Label("┘")
        bottomEnd.setForegroundColor(LanternaTheme.getSidebarBorderColor())
        bottomPanel.addComponent(bottomEnd)
        
        contentPanel.addComponent(bottomPanel)
        
        // Separator
        contentPanel.addComponent(new Label(""))
        
        addComponent(contentPanel)
    }
    
    void refresh() {
        buildUI()
    }
}
