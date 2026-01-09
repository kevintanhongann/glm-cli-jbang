package tui.shared

import com.googlecode.lanterna.input.KeyPattern
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.ConcurrentHashMap

class KeybindManager {

    private static final Map<String, List<KeyPattern>> DEFAULT_KEYBINDS = [
        'exit': [KeyPattern.fromString('ctrl-c')],
        'export': [KeyPattern.fromString('ctrl-s')],
        'clear': [KeyPattern.fromString('ctrl-l')],
        'model': [KeyPattern.fromString('ctrl-m')],
        'sidebar': [KeyPattern.fromString('ctrl-b')],
        'help': [KeyPattern.fromString('f1'), KeyPattern.fromString('ctrl-h')],
        'next_message': [KeyPattern.fromString('ctrl-down')],
        'prev_message': [KeyPattern.fromString('ctrl-up')],
        'home': [KeyPattern.fromString('ctrl-home')],
        'end': [KeyPattern.fromString('ctrl-end')],
        'page_up': [KeyPattern.fromString('page-up')],
        'page_down': [KeyPattern.fromString('page-down')],
        'switch_agent': [KeyPattern.fromString('tab'), KeyPattern.fromString('shift-tab')],
        'autocomplete': [KeyPattern.fromString('ctrl-space')],
        'cancel': [KeyPattern.fromString('escape')]
    ]

    private Map<String, List<KeyPattern>> keybinds = new ConcurrentHashMap<>()

    KeybindManager() {
        resetToDefaults()
    }

    void resetToDefaults() {
        keybinds.clear()
        keybinds.putAll(DEFAULT_KEYBINDS)
    }

    void bind(String action, String keyPattern) {
        bind(action, KeyPattern.fromString(keyPattern))
    }

    void bind(String action, KeyPattern pattern) {
        keybinds.computeIfAbsent(action, { new ArrayList<KeyPattern>() }).add(pattern)
    }

    void bind(String action, List<KeyPattern> patterns) {
        keybinds.put(action, patterns)
    }

    void unbind(String action) {
        keybinds.remove(action)
    }

    List<KeyPattern> getKeyPatterns(String action) {
        return keybinds.get(action) ?: []
    }

    String getAction(KeyPattern pattern) {
        for (entry in keybinds) {
            if (entry.value.contains(pattern)) {
                return entry.key
            }
        }
        return null
    }

    boolean matches(KeyPattern pattern, String action) {
        List<KeyPattern> patterns = keybinds.get(action)
        if (!patterns) return false
        return patterns.any { it == pattern }
    }

    Map<String, List<KeyPattern>> getAllKeybinds() {
        return new ConcurrentHashMap<>(keybinds)
    }

    List<String> getAllActions() {
        return new ArrayList<>(keybinds.keySet())
    }

    static Map<String, String> getDefaultShortcutsDisplay() {
        return [
            'exit': 'Ctrl+C',
            'export': 'Ctrl+S',
            'clear': 'Ctrl+L',
            'model': 'Ctrl+M',
            'sidebar': 'Ctrl+B',
            'help': 'F1',
            'next_message': 'Ctrl+↓',
            'prev_message': 'Ctrl+↑',
            'home': 'Ctrl+Home',
            'end': 'Ctrl+End',
            'page_up': 'PgUp',
            'page_down': 'PgDn',
            'switch_agent': 'Tab',
            'cancel': 'Esc'
        ]
    }

    static String getActionDescription(String action) {
        switch (action) {
            case 'exit':
                return 'Exit TUI'
            case 'export':
                return 'Export chat log'
            case 'clear':
                return 'Clear chat history'
            case 'model':
                return 'Open model selection'
            case 'sidebar':
                return 'Toggle sidebar'
            case 'help':
                return 'Show help'
            case 'next_message':
                return 'Jump to next message'
            case 'prev_message':
                return 'Jump to previous message'
            case 'home':
                return 'Jump to start'
            case 'end':
                return 'Jump to end'
            case 'page_up':
                return 'Page up'
            case 'page_down':
                return 'Page down'
            case 'switch_agent':
                return 'Switch agent'
            case 'autocomplete':
                return 'Show autocomplete'
            case 'cancel':
                return 'Cancel / Clear input'
            default:
                return action
        }
    }
}
