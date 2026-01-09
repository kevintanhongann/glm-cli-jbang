package tui.lanterna.widgets

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import java.nio.file.Paths

class FooterPanel extends Panel {

    private Label directoryLabel
    private Label lspIndicator
    private Label mcpIndicator
    private Label agentLabel
    private Label versionLabel
    private Label modelLabel
    private Panel leftPanel
    private Panel centerPanel
    private Panel rightPanel

    FooterPanel(String currentDirectory, String agentName = 'BUILD', String version = 'GLM v1.0', String model = '') {
        setLayoutManager(new BorderLayout())

        leftPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
        addComponent(leftPanel, BorderLayout.Location.LEFT)

        centerPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
        addComponent(centerPanel, BorderLayout.Location.CENTER)

        rightPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
        addComponent(rightPanel, BorderLayout.Location.RIGHT)

        if (model) {
            modelLabel = new Label(model)
            modelLabel.setForegroundColor(TextColor.ANSI.YELLOW)
            leftPanel.addComponent(modelLabel)

            leftPanel.addComponent(new Label('  |  '))
        }

        String dirName = Paths.get(currentDirectory).fileName?.toString() ?: currentDirectory
        directoryLabel = new Label(dirName)
        directoryLabel.setForegroundColor(new TextColor.RGB(180, 180, 180))
        leftPanel.addComponent(directoryLabel)

        leftPanel.addComponent(new Label('  |  '))

        lspIndicator = new Label('')
        lspIndicator.setForegroundColor(TextColor.ANSI.GREEN)
        leftPanel.addComponent(lspIndicator)

        mcpIndicator = new Label('')
        mcpIndicator.setForegroundColor(TextColor.ANSI.GREEN)
        leftPanel.addComponent(mcpIndicator)

        leftPanel.addComponent(new Label('  |  '))

        agentLabel = new Label(agentName)
        agentLabel.setForegroundColor(TextColor.ANSI.CYAN)
        leftPanel.addComponent(agentLabel)

        leftPanel.addComponent(new Label(' (Tab/Shift+Tab to switch)'))

        centerPanel.addComponent(new Label('/help'))
        centerPanel.addComponent(new Label('  |  '))
        centerPanel.addComponent(new Label('/sidebar'))

        versionLabel = new Label(version)
        versionLabel.setForegroundColor(new TextColor.RGB(100, 100, 120))
        rightPanel.addComponent(versionLabel)
    }

    void updateDirectory(String directory) {
        Label label = directoryLabel
        if (label == null) return
        try {
            String dirName = Paths.get(directory).fileName?.toString() ?: directory
            label.setText(dirName)
        } catch (Exception ignored) {}
    }

    void updateLspStatus(int count, int errors = 0) {
        Label indicator = lspIndicator
        if (indicator == null) return
        String text = ''
        TextColor color = TextColor.ANSI.GREEN
        if (count > 0) {
            String statusIcon = errors > 0 ? '●' : '●'
            color = errors > 0 ? TextColor.ANSI.RED : TextColor.ANSI.GREEN
            text = "${statusIcon} ${count} LSP"
        }
        try {
            indicator.setText(text)
            indicator.setForegroundColor(color)
        } catch (Exception ignored) {}
    }

    void updateMcpStatus(int connected, int errors = 0) {
        Label indicator = mcpIndicator
        if (indicator == null) return
        String text = ''
        TextColor color = TextColor.ANSI.GREEN
        if (connected > 0 || errors > 0) {
            String statusIcon = errors > 0 ? '⊙' : '○'
            color = errors > 0 ? TextColor.ANSI.RED : TextColor.ANSI.GREEN
            text = "${statusIcon} ${connected} MCP"
        }
        try {
            indicator.setText(text)
            indicator.setForegroundColor(color)
        } catch (Exception ignored) {}
    }

    void updateAgent(String agentName, boolean isBuild) {
        Label label = agentLabel
        if (label == null) return
        try {
            label.setText(agentName)
            label.setForegroundColor(isBuild ? TextColor.ANSI.CYAN : TextColor.ANSI.YELLOW)
        } catch (Exception ignored) {}
    }

    void updateVersion(String version) {
        Label label = versionLabel
        if (label == null) return
        try {
            label.setText(version)
        } catch (Exception ignored) {}
    }

    void updateModel(String model) {
        Label label = modelLabel
        if (label == null) return
        try {
            label.setText(model)
        } catch (Exception ignored) {}
    }

    void updateShortcuts(List<String> shortcuts) {
        Panel panel = centerPanel
        if (panel == null) return
        try {
            panel.removeAllComponents()
            shortcuts.eachWithIndex { shortcut, index ->
                if (index > 0) {
                    panel.addComponent(new Label('  |  '))
                }
                panel.addComponent(new Label(shortcut))
            }
        } catch (Exception ignored) {}
    }

    void update(String directory, int lspCount, int lspErrors, int mcpCount, int mcpErrors, String agentName, boolean isBuild, String model = '') {
        updateDirectory(directory)
        updateLspStatus(lspCount, lspErrors)
        updateMcpStatus(mcpCount, mcpErrors)
        updateAgent(agentName, isBuild)
        updateModel(model)
    }

}
