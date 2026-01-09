package tui.lanterna.sidebar

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class TodoSection extends CollapsibleSection {

    private static final Map<String, String> STATUS_ICONS = [
        'completed': '☑',
        'in-progress': '◐',
        'todo': '☐',
        'pending': '☐',
        'done': '☑',
        'cancelled': '☒',
        'blocked': '⛔'
    ]

    private static final Map<String, TextColor> STATUS_COLORS = [
        'completed': TextColor.ANSI.GREEN,
        'in-progress': TextColor.ANSI.YELLOW,
        'todo': TextColor.ANSI.WHITE,
        'pending': TextColor.ANSI.WHITE,
        'done': TextColor.ANSI.GREEN,
        'cancelled': TextColor.ANSI.RED,
        'blocked': TextColor.ANSI.RED
    ]

    private static final Map<String, String> STATUS_NAMES = [
        'completed': 'Completed',
        'in-progress': 'In Progress',
        'todo': 'To Do',
        'pending': 'To Do',
        'done': 'Completed',
        'cancelled': 'Cancelled',
        'blocked': 'Blocked'
    ]

    TodoSection() {
        super('Todo')
    }

    void update(List<Map<String, Object>> todos) {
        clear()

        Panel content = getContentPanel()

        if (!todos || todos.isEmpty()) {
            Label emptyLabel = new Label('  No tasks')
            emptyLabel.setForegroundColor(TextColor.ANSI.GRAY)
            content.addComponent(emptyLabel)
            content.addComponent(new Label(''))
            return
        }

        int completedCount = todos.count { it.status in ['completed', 'done'] }
        int totalCount = todos.size()
        int percentage = totalCount > 0 ? (int)((completedCount / totalCount) * 100) : 0

        Label progressLabel = new Label("  ${completedCount}/${totalCount} (${percentage}%)")
        progressLabel.setForegroundColor(TextColor.ANSI.GREEN)
        content.addComponent(progressLabel)

        content.addComponent(new Label(''))

        todos.each { todo ->
            String status = todo.status?.toLowerCase() ?: 'todo'
            String icon = STATUS_ICONS[status] ?: '☐'
            TextColor color = STATUS_COLORS[status] ?: TextColor.ANSI.WHITE
            String statusName = STATUS_NAMES[status] ?: status

            Panel row = new Panel()
            row.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

            Label iconLabel = new Label(icon + ' ')
            iconLabel.setForegroundColor(color)
            row.addComponent(iconLabel)

            String contentText = todo.content ?: todo.text ?: todo.title ?: 'Untitled'
            Label contentLabel = new Label(truncate(contentText, 25))
            contentLabel.setForegroundColor(color)
            row.addComponent(contentLabel)

            content.addComponent(row)
        }

        content.addComponent(new Label(''))
    }

    void updateFromToolResult(String toolResult) {
        try {
            def parsed = new groovy.json.JsonSlurper().parseText(toolResult)
            if (parsed instanceof List) {
                update(parsed.collect { item ->
                    [
                        status: item.status ?: item.state ?: 'todo',
                        content: item.content ?: item.description ?: item.title ?: ''
                    ]
                })
            }
        } catch (Exception e) {
        }
    }

    private String truncate(String text, int maxLen) {
        if (!text) return ''
        if (text.length() <= maxLen) return text
        return text.substring(0, maxLen - 3) + '...'
    }
}
