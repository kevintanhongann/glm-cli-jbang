package tui.shared

class CommandProvider {

    private static final List<CommandItem> DEFAULT_COMMANDS = [
        new CommandItem('session.new', 'New session', 'Start a new conversation', 'Session', 
                        'session_new', '/new', false, false, null),
        new CommandItem('session.clear', 'Clear history', 'Clear chat history', 'Session',
                        'clear', '/clear', false, false, null),
        new CommandItem('session.export', 'Export chat', 'Export conversation to file', 'Session',
                        'export', '/export', false, false, null),
        new CommandItem('session.rename', 'Rename session', 'Rename current session', 'Session',
                        null, '/rename', false, false, null),
        new CommandItem('session.share', 'Share session', 'Share session URL', 'Session',
                        null, '/share', true, false, null),
        
        new CommandItem('model.select', 'Switch model', 'Open model selection dialog', 'Model',
                        'model', '/models', true, false, null),
        new CommandItem('model.show', 'Show model', 'Display current model', 'Model',
                        null, '/model', false, false, null),
        new CommandItem('model.info', 'Model info', 'Show model details', 'Model',
                        null, null, false, false, null),
        
        new CommandItem('tools.list', 'List tools', 'Show available tools', 'Tools',
                        null, '/tools', false, false, null),
        new CommandItem('tools.search', 'Search web', 'Search the web', 'Tools',
                        null, '/search', true, false, null),
        new CommandItem('tools.skills', 'List skills', 'Show available skills', 'Tools',
                        null, '/skill', false, false, null),
        new CommandItem('tools.context', 'Show context', 'Display current context', 'Tools',
                        null, '/context', false, false, null),
        
        new CommandItem('nav.sidebar', 'Toggle sidebar', 'Show/hide sidebar', 'Navigation',
                        'sidebar', '/sidebar', true, false, null),
        new CommandItem('nav.home', 'Jump to start', 'Scroll to top', 'Navigation',
                        'home', '/home', false, false, null),
        new CommandItem('nav.end', 'Jump to end', 'Scroll to bottom', 'Navigation',
                        'end', '/end', false, false, null),
        
        new CommandItem('system.help', 'Show help', 'Display help dialog', 'System',
                        'help', '/help', true, false, null),
        new CommandItem('system.config', 'Show config', 'Display configuration', 'System',
                        null, '/config', false, false, null),
        new CommandItem('system.exit', 'Exit TUI', 'Exit the application', 'System',
                        'exit', '/exit', false, false, null),
        new CommandItem('system.debug', 'Toggle debug', 'Toggle debug mode', 'System',
                        null, '/debug', false, false, null),
    ]

    private static final List<CommandItem> customCommands = []
    private static final Map<String, CommandItem> commandMap = [:]

    static {
        rebuildCommandMap()
    }

    static void rebuildCommandMap() {
        commandMap.clear()
        DEFAULT_COMMANDS.each { cmd ->
            commandMap[cmd.id] = cmd
        }
        customCommands.each { cmd ->
            commandMap[cmd.id] = cmd
        }
    }

    static List<CommandItem> getAllCommands() {
        return DEFAULT_COMMANDS + customCommands
    }

    static List<CommandItem> getCommandsByCategory(String category) {
        return getAllCommands().findAll { it.category == category }
    }

    static List<CommandItem> getSuggestedCommands() {
        return getAllCommands().findAll { it.suggested && !it.disabled }
    }

    static List<CommandItem> filterCommands(String query) {
        if (!query || query.isEmpty()) {
            return getAllCommands().findAll { !it.disabled }
        }
        
        String lowerQuery = query.toLowerCase()
        return getAllCommands().findAll { 
            !it.disabled && (
                it.title.toLowerCase().contains(lowerQuery) ||
                it.description.toLowerCase().contains(lowerQuery) ||
                it.category.toLowerCase().contains(lowerQuery) ||
                it.slashCommand?.toLowerCase()?.contains(lowerQuery)
            )
        }
    }

    static CommandItem getById(String id) {
        return commandMap[id]
    }

    static CommandItem getBySlashCommand(String slashCommand) {
        return getAllCommands().find { it.slashCommand == slashCommand }
    }

    static void registerCommand(CommandItem command) {
        customCommands << command
        rebuildCommandMap()
    }

    static void unregisterCommand(String id) {
        customCommands.removeIf { it.id == id }
        rebuildCommandMap()
    }

    static Map<String, List<CommandItem>> getCommandsGroupedByCategory() {
        Map<String, List<CommandItem>> grouped = [:]
        getAllCommands().findAll { !it.disabled }.each { cmd ->
            if (!grouped[cmd.category]) {
                grouped[cmd.category] = []
            }
            grouped[cmd.category] << cmd
        }
        return grouped
    }

    static List<String> getAllCategories() {
        return getAllCommands()*.category.unique().sort()
    }

    static Map<String, Object> parseSlashCommand(String input) {
        if (!input || !input.startsWith('/')) {
            return null
        }
        
        String trimmed = input.substring(1).trim()
        String[] parts = trimmed.split(/\s+/, 2)
        
        return [
            command: parts[0],
            arguments: parts.length > 1 ? parts[1] : ''
        ]
    }

    static boolean existsSlashCommand(String commandName) {
        return getAllCommands().any { it.slashCommand == "/${commandName}" || it.slashCommand == commandName }
    }
}
