package tui.shared

/**
 * Provides slash command definitions.
 * Similar to SST OpenCode's command system.
 */
class CommandProvider {

    private static final List<CommandDefinition> COMMANDS = [
        // Session commands
        new CommandDefinition('help', 'Show available commands'),
        new CommandDefinition('clear', 'Clear chat history'),
        new CommandDefinition('new', 'Start new conversation'),
        new CommandDefinition('exit', 'Exit the TUI'),
        
        // Model commands
        new CommandDefinition('model', 'Show or change current model'),
        new CommandDefinition('models', 'List available models'),
        
        // File commands
        new CommandDefinition('read', 'Read a file', 'read <path>'),
        new CommandDefinition('ls', 'List files in directory', 'ls [path]'),
        
        // Tool commands
        new CommandDefinition('tools', 'List available tools'),
        new CommandDefinition('search', 'Search the web', 'search <query>'),
        
        // Context commands
        new CommandDefinition('context', 'Show current context'),
        new CommandDefinition('cwd', 'Show/change working directory'),
        
        // Configuration
        new CommandDefinition('config', 'Show configuration'),
        new CommandDefinition('theme', 'Change color theme'),
        
        // Undo/Redo
        new CommandDefinition('undo', 'Undo last action'),
        new CommandDefinition('redo', 'Redo last undone action'),
        
        // Export
        new CommandDefinition('export', 'Export conversation', 'export [format]'),
        new CommandDefinition('copy', 'Copy last response to clipboard'),
        
        // Debug
        new CommandDefinition('debug', 'Toggle debug mode'),
        new CommandDefinition('verbose', 'Toggle verbose output'),
    ]

    /**
     * Get all available commands.
     */
    static List<AutocompleteItem> getCommands() {
        COMMANDS.collect { cmd ->
            AutocompleteItem.command(cmd.name, cmd.description)
        }
    }

    /**
     * Get commands matching a query.
     */
    static List<AutocompleteItem> getCommands(String query) {
        if (!query || query.isEmpty()) {
            return getCommands()
        }
        
        String lowerQuery = query.toLowerCase()
        return COMMANDS
            .findAll { it.name.toLowerCase().startsWith(lowerQuery) }
            .collect { cmd -> AutocompleteItem.command(cmd.name, cmd.description) }
    }

    /**
     * Check if a command exists.
     */
    static boolean exists(String name) {
        COMMANDS.any { it.name == name }
    }

    /**
     * Get command definition by name.
     */
    static CommandDefinition get(String name) {
        COMMANDS.find { it.name == name }
    }

    /**
     * Parse a command string into name and arguments.
     */
    static Map<String, Object> parse(String input) {
        if (!input || !input.startsWith('/')) {
            return null
        }
        
        String trimmed = input.substring(1).trim()
        String[] parts = trimmed.split(/\s+/, 2)
        
        return [
            name: parts[0],
            arguments: parts.length > 1 ? parts[1] : ''
        ]
    }
}

class CommandDefinition {
    String name
    String description
    String usage
    boolean disabled = false

    CommandDefinition(String name, String description, String usage = null) {
        this.name = name
        this.description = description
        this.usage = usage ?: "/${name}"
    }
}
