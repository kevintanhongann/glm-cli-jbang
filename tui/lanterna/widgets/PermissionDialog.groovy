package tui.lanterna.widgets

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class PermissionDialog extends DialogWindow {

    enum PermissionType {
        WRITE,
        EXECUTE,
        DELETE,
        NETWORK
    }

    enum Decision {
        ALLOW,
        ALLOW_ALWAYS,
        DENY,
        VIEW
    }

    private PermissionType type
    private String description
    private String details
    private String diffContent
    private Closure<Decision> onDecision

    PermissionDialog(MultiWindowTextGUI textGUI) {
        super('Permission Required')
        this.textGUI = textGUI
    }

    Decision show(PermissionType type, String description, String details, String diffContent = null, Closure<Decision> callback = null) {
        this.type = type
        this.description = description
        this.details = details
        this.diffContent = diffContent
        this.onDecision = callback

        setHints(Arrays.asList(Window.Hint.CENTERED))

        Panel mainPanel = new Panel()
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        Panel headerPanel = new Panel()
        headerPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        String icon = getWarningIcon(type)
        Label iconLabel = new Label(icon)
        iconLabel.setForegroundColor(getWarningColor(type))
        iconLabel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning))
        headerPanel.addComponent(iconLabel)

        headerPanel.addComponent(new Label('  '))

        Label titleLabel = new Label('Permission Required')
        titleLabel.setForegroundColor(TextColor.ANSI.YELLOW)
        titleLabel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning))
        headerPanel.addComponent(titleLabel)

        mainPanel.addComponent(headerPanel)

        mainPanel.addComponent(new Label(''))

        Label descLabel = new Label(description)
        descLabel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill))
        mainPanel.addComponent(descLabel)

        mainPanel.addComponent(new Label(''))

        Panel detailsPanel = new Panel()
        detailsPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        Label detailsLabel = new Label(details)
        detailsLabel.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
        detailsPanel.addComponent(detailsLabel)

        if (diffContent) {
            detailsPanel.addComponent(new Label(''))
            Label diffLabel = new Label(truncateDiff(diffContent))
            diffLabel.setForegroundColor(TextColor.ANSI.GREEN)
            detailsPanel.addComponent(diffLabel)
        }

        mainPanel.addComponent(detailsPanel)

        mainPanel.addComponent(new Label(''))
        mainPanel.addComponent(new Label('─'.repeat(50)))

        Panel buttonPanel = new Panel()
        buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        Button allowButton = new Button('[Y] Allow', {
            close(Decision.ALLOW)
        })
        buttonPanel.addComponent(allowButton)

        buttonPanel.addComponent(new Label('  '))

        Button alwaysButton = new Button('[A] Always', {
            close(Decision.ALLOW_ALWAYS)
        })
        buttonPanel.addComponent(alwaysButton)

        buttonPanel.addComponent(new Label('  '))

        Button viewButton = null
        if (diffContent) {
            viewButton = new Button('[V] View', {
                close(Decision.VIEW)
            })
            buttonPanel.addComponent(viewButton)
            buttonPanel.addComponent(new Label('  '))
        }

        Button denyButton = new Button('[N] Deny', {
            close(Decision.DENY)
        })
        buttonPanel.addComponent(denyButton)

        mainPanel.addComponent(buttonPanel)

        setComponent(mainPanel)

        return textGUI.addWindowAndWaitFor(this)
    }

    private String getWarningIcon(PermissionType type) {
        switch (type) {
            case PermissionType.WRITE:
                return '⚠'
            case PermissionType.EXECUTE:
                return '⚡'
            case PermissionType.DELETE:
                return '🗑'
            case PermissionType.NETWORK:
                return '🌐'
            default:
                return '⚠'
        }
    }

    private TextColor getWarningColor(PermissionType type) {
        switch (type) {
            case PermissionType.DELETE:
                return TextColor.ANSI.RED
            case PermissionType.EXECUTE:
                return TextColor.ANSI.YELLOW
            default:
                return TextColor.ANSI.ORANGE
        }
    }

    private String truncateDiff(String diff) {
        if (!diff) return ''
        String[] lines = diff.split('\n')
        if (lines.length <= 5) {
            return diff
        }
        StringBuilder sb = new StringBuilder()
        sb.append(lines[0]).append('\n')
        sb.append('... ').append(lines.length - 4).append(' lines hidden ...\n')
        sb.append(lines[lines.length - 3])
        return sb.toString()
    }

    static String getDecisionText(Decision decision) {
        switch (decision) {
            case Decision.ALLOW:
                return 'Allowed'
            case Decision.ALLOW_ALWAYS:
                return 'Always Allowed'
            case Decision.DENY:
                return 'Denied'
            case Decision.VIEW:
                return 'View'
            default:
                return 'Unknown'
        }
    }
}
