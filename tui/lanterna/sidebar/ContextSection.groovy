package tui.lanterna.sidebar

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import core.SessionStatsManager
import core.TokenTracker

class ContextSection extends CollapsibleSection {

    private String sessionId

    ContextSection(String sessionId) {
        super('Context')
        this.sessionId = sessionId
    }

    @Override
    void refresh() {
        clear()

        def sessionStats = SessionStatsManager.instance.getStats(sessionId)
        def tokenStats = TokenTracker.instance.getInMemoryStats(sessionId)

        int inputTokens = sessionStats?.inputTokens ?: 0
        int outputTokens = sessionStats?.outputTokens ?: 0
        int totalTokens = inputTokens + outputTokens
        BigDecimal totalCost = sessionStats?.totalCost ?: 0.0000

        int contextLimit = 128000
        int percentage = contextLimit > 0 ? Math.min((int)((totalTokens / contextLimit) * 100), 100) : 0

        Panel content = getContentPanel()

        Label tokensHeader = new Label('━━ Context')
        tokensHeader.setForegroundColor(new TextColor.RGB(100, 100, 120))
        content.addComponent(tokensHeader)

        content.addComponent(new Label(''))

        Label inputLabel = new Label("  ${String.format('%,d', inputTokens)} input")
        inputLabel.setForegroundColor(TextColor.ANSI.CYAN)
        content.addComponent(inputLabel)

        Label outputLabel = new Label("  ${String.format('%,d', outputTokens)} output")
        outputLabel.setForegroundColor(TextColor.ANSI.MAGENTA)
        content.addComponent(outputLabel)

        Label totalLabel = new Label("  ${String.format('%,d', totalTokens)} total")
        totalLabel.setForegroundColor(TextColor.ANSI.WHITE)
        content.addComponent(totalLabel)

        content.addComponent(new Label(''))

        Label percentLabel = new Label("  ${percentage}% used")
        if (percentage < 50) {
            percentLabel.setForegroundColor(TextColor.ANSI.GREEN)
        } else if (percentage < 80) {
            percentLabel.setForegroundColor(TextColor.ANSI.YELLOW)
        } else {
            percentLabel.setForegroundColor(TextColor.ANSI.RED)
        }
        content.addComponent(percentLabel)

        content.addComponent(new Label(''))

        String costStr = String.format('$%.4f', totalCost)
        Label costLabel = new Label("  ${costStr} spent")
        costLabel.setForegroundColor(TextColor.ANSI.GREEN)
        content.addComponent(costLabel)

        content.addComponent(new Label(''))
    }

}
