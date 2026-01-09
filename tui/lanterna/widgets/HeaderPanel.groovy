package tui.lanterna.widgets

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.TerminalSize

class HeaderPanel extends Panel {

    private Label titleLabel
    private Label contextLabel
    private Label costLabel
    private Label lspStatusLabel
    private Panel leftPanel
    private Panel rightPanel

    HeaderPanel(String sessionTitle = 'GLM CLI') {
        setLayoutManager(new BorderLayout())
        setPreferredSize(new TerminalSize(0, 1))

        leftPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
        addComponent(leftPanel, BorderLayout.Location.LEFT)

        rightPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
        addComponent(rightPanel, BorderLayout.Location.RIGHT)

        titleLabel = new Label(sessionTitle)
        titleLabel.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
        leftPanel.addComponent(titleLabel)

        contextLabel = new Label('')
        contextLabel.setForegroundColor(new TextColor.RGB(128, 128, 140))
        rightPanel.addComponent(contextLabel)

        rightPanel.addComponent(new Label('  '))

        costLabel = new Label('')
        costLabel.setForegroundColor(TextColor.ANSI.GREEN)
        rightPanel.addComponent(costLabel)

        rightPanel.addComponent(new Label('  '))

        lspStatusLabel = new Label('')
        lspStatusLabel.setForegroundColor(TextColor.ANSI.GREEN)
        rightPanel.addComponent(lspStatusLabel)
    }

    void updateContext(int inputTokens, int outputTokens, int percentage, BigDecimal cost) {
        String totalTokens = String.format('%,d', inputTokens + outputTokens)
        contextLabel.setText("${totalTokens} tokens • ${percentage}%")

        if (cost != null && cost > 0) {
            costLabel.setText("\$${String.format('%.4f', cost)}")
        } else {
            costLabel.setText('$0.0000')
        }
    }

    void updateLspStatus(int count, int errorCount = 0) {
        if (count > 0) {
            String indicator = errorCount > 0 ? '●' : '●'
            TextColor color = errorCount > 0 ? TextColor.ANSI.RED : TextColor.ANSI.GREEN
            lspStatusLabel.setText("${indicator} ${count} LSP")
            lspStatusLabel.setForegroundColor(color)
        } else {
            lspStatusLabel.setText('')
        }
    }

    void updateTitle(String title) {
        titleLabel.setText(title)
    }

    void setSessionTitle(String sessionTitle) {
        titleLabel.setText(sessionTitle)
    }

    void update(int inputTokens, int outputTokens, int percentage, BigDecimal cost, int lspCount = 0, int lspErrors = 0) {
        updateContext(inputTokens, outputTokens, percentage, cost)
        updateLspStatus(lspCount, lspErrors)
    }

}
