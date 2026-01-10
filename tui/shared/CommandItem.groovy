package tui.shared

class CommandItem {
    String id
    String title
    String description
    String category
    String keybindAction
    String slashCommand
    boolean suggested = false
    boolean disabled = false
    Closure onSelect = null

    CommandItem(String id, String title, String description, String category, 
                String keybindAction = null, String slashCommand = null, 
                boolean suggested = false, boolean disabled = false, 
                Closure onSelect = null) {
        this.id = id
        this.title = title
        this.description = description
        this.category = category
        this.keybindAction = keybindAction
        this.slashCommand = slashCommand
        this.suggested = suggested
        this.disabled = disabled
        this.onSelect = onSelect
    }

    String getKeybindDisplay(KeybindManager keybindManager) {
        if (!keybindAction) return ''
        String keybind = keybindManager.getKeybind(keybindAction)
        if (!keybind || keybind == 'none') return ''
        return keybindManager.formatKeybindString(keybind)
    }
}
