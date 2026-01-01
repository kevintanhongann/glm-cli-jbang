package tui

/**
 * Represents an autocomplete suggestion item.
 */
enum AutocompleteItemType {
    FILE,
    DIRECTORY,
    COMMAND,
    AGENT
}

class AutocompleteItem {
    String label        // Display text
    String value        // Value to insert
    String description  // Optional description
    AutocompleteItemType type
    boolean disabled = false

    AutocompleteItem(String label, String value, AutocompleteItemType type, String description = null) {
        this.label = label
        this.value = value
        this.type = type
        this.description = description
    }

    static AutocompleteItem file(String path) {
        new AutocompleteItem(path, path, AutocompleteItemType.FILE)
    }

    static AutocompleteItem directory(String path) {
        new AutocompleteItem(path + '/', path, AutocompleteItemType.DIRECTORY)
    }

    static AutocompleteItem command(String name, String description = null) {
        new AutocompleteItem(name, name, AutocompleteItemType.COMMAND, description)
    }

    static AutocompleteItem agent(String name, String description = null) {
        new AutocompleteItem(name, name, AutocompleteItemType.AGENT, description)
    }
}
