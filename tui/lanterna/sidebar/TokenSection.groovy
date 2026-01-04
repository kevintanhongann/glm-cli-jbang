package tui.lanterna.sidebar

import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TextColor
import core.SessionStatsManager
import core.TokenTracker

class TokenSection extends Panel {
    private String sessionId
    
    private Label tokensLabel
    private Label percentageLabel
    private Label costLabel
    private Panel headerPanel
    
    TokenSection(String sessionId) {
        this.sessionId = sessionId
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        buildUI()
    }
    
    private void buildUI() {
        removeAllComponents()
        
        // Get token stats
        def sessionStats = SessionStatsManager.instance.getStats(sessionId)
        def tokenStats = TokenTracker.instance.getInMemoryStats(sessionId)
        
        int totalTokens = sessionStats?.totalTokens ?: 0
        BigDecimal totalCost = sessionStats?.totalCost ?: 0.0000
        
        // Calculate percentage of context window (assuming 128k tokens for now)
        // TODO: Get actual context limit from model
        int contextLimit = 128000
        int percentage = contextLimit > 0 ? (int)((totalTokens / contextLimit) * 100) : 0
        
        // Build content panel
        def contentPanel = new Panel()
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        
        // Header with visual border
        headerPanel = new Panel()
        headerPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
        
        def headerLabel = new Label("━━ Context")
        headerLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
        headerPanel.addComponent(headerLabel)
        
        // Add padding
        contentPanel.addComponent(headerPanel)
        contentPanel.addComponent(new Label(""))
        
        // Token count
        tokensLabel = new Label("  ${totalTokens} tokens")
        tokensLabel.setForegroundColor(LanternaTheme.getTextColor())
        contentPanel.addComponent(tokensLabel)
        
        // Percentage with color coding
        percentageLabel = new Label("  ${percentage}% used")
        if (percentage < 50) {
            percentageLabel.setForegroundColor(TextColor.ANSI.GREEN)
        } else if (percentage < 80) {
            percentageLabel.setForegroundColor(TextColor.ANSI.YELLOW)
        } else {
            percentageLabel.setForegroundColor(TextColor.ANSI.RED)
        }
        contentPanel.addComponent(percentageLabel)
        
        // Cost
        String costStr = String.format("\$%.4f", totalCost)
        costLabel = new Label("  ${costStr} spent")
        costLabel.setForegroundColor(LanternaTheme.getTextMutedColor())
        contentPanel.addComponent(costLabel)
        
        // Separator
        contentPanel.addComponent(new Label(""))
        
        addComponent(contentPanel)
    }
    
    void refresh() {
        buildUI()
    }
}
