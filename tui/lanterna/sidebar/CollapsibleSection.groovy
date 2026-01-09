package tui.lanterna.sidebar

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class CollapsibleSection extends Panel {

    protected String title
    protected boolean expanded = true
    protected Panel headerPanel
    protected Panel contentPanel
    protected Label arrowLabel
    protected Runnable onToggleCallback

    CollapsibleSection(String title) {
        this.title = title
        setLayoutManager(new LinearLayout(Direction.VERTICAL))
        buildHeader()
        buildContent()
    }

    private void buildHeader() {
        headerPanel = new Panel()
        headerPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        arrowLabel = new Label(expanded ? '▼' : '▶')
        arrowLabel.setForegroundColor(TextColor.ANSI.BLUE)
        headerPanel.addComponent(arrowLabel)

        headerPanel.addComponent(new Label(' '))

        Label titleLabel = new Label(title)
        titleLabel.setForegroundColor(TextColor.ANSI.CYAN)
        headerPanel.addComponent(titleLabel)

        addComponent(headerPanel)
    }

    private void buildContent() {
        contentPanel = new Panel()
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))
        addComponent(contentPanel)
    }

    void toggle() {
        expanded = !expanded
        arrowLabel.setText(expanded ? '▼' : '▶')
        if (expanded) {
            contentPanel.setVisible(true)
        } else {
            contentPanel.setVisible(false)
        }
        if (onToggleCallback) {
            onToggleCallback.run()
        }
    }

    boolean isExpanded() {
        return expanded
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded
        arrowLabel.setText(expanded ? '▼' : '▶')
        contentPanel.setVisible(expanded)
    }

    Panel getContentPanel() {
        return contentPanel
    }

    void setOnToggle(Runnable callback) {
        this.onToggleCallback = callback
    }

    void setTitle(String title) {
        this.title = title
        headerPanel.removeAllComponents()
        headerPanel.addComponent(arrowLabel)
        headerPanel.addComponent(new Label(' '))
        Label titleLabel = new Label(title)
        titleLabel.setForegroundColor(TextColor.ANSI.CYAN)
        headerPanel.addComponent(titleLabel)
    }

    void clear() {
        contentPanel.removeAllComponents()
    }

    void addComponent(Component component) {
        contentPanel.addComponent(component)
    }

    void addSeparator() {
        contentPanel.addComponent(new Label('  '))
    }
}
