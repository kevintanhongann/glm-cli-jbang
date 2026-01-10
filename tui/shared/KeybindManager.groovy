package tui.shared

import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.ConcurrentHashMap

class KeybindManager {

    private static final Map<String, String> DEFAULT_KEYBINDS = [
        'command_palette': 'ctrl-p',
        'exit': 'ctrl-c',
        'export': 'ctrl-s',
        'clear': 'ctrl-l',
        'model': 'ctrl-m',
        'sidebar': 'ctrl-b',
        'help': 'f1',
        'next_message': 'ctrl-down',
        'prev_message': 'ctrl-up',
        'home': 'ctrl-home',
        'end': 'ctrl-end',
        'page_up': 'page-up',
        'page_down': 'page-down',
        'switch_agent': 'tab',
        'autocomplete': 'ctrl-space',
        'cancel': 'escape',
        'session_new': 'ctrl-n',
        'diff_toggle': 'ctrl-d',
        'diff_prev': '[',
        'diff_next': ']',
        'toggle_thinking': 'ctrl-t',
        'toggle_timestamps': 'ctrl-shift-t',
        'toggle_details': 'ctrl-shift-d'
    ]

    private Map<String, String> keybinds = new ConcurrentHashMap<>()

    KeybindManager() {
        resetToDefaults()
    }

    void resetToDefaults() {
        keybinds.clear()
        keybinds.putAll(DEFAULT_KEYBINDS)
    }

    void bind(String action, String keyPattern) {
        keybinds[action] = keyPattern
    }

    void unbind(String action) {
        keybinds.remove(action)
    }

    String getKeybind(String action) {
        return keybinds.get(action)
    }

    Map<String, String> getAllKeybinds() {
        return new ConcurrentHashMap<>(keybinds)
    }

    List<String> getAllActions() {
        return new ArrayList<>(keybinds.keySet())
    }

    static Map<String, String> getDefaultShortcutsDisplay() {
        return [
            'command_palette': 'Ctrl+P',
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
            case 'command_palette':
                return 'Open command palette'
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
                return 'Cancel / Clear input (Ctrl+C also clears, exits when empty)'
            case 'session_new':
                return 'Start new session'
            case 'diff_toggle':
                return 'Toggle diff view mode'
            case 'diff_prev':
                return 'Previous diff change'
            case 'diff_next':
                return 'Next diff change'
            case 'toggle_thinking':
                return 'Show/hide thinking sections'
            case 'toggle_timestamps':
                return 'Show/hide timestamps'
            case 'toggle_details':
                return 'Show/hide message details'
            default:
                return action
        }
    }

    String getDisplayForKeybind(String action) {
        String pattern = keybinds.get(action)
        if (!pattern) return ''
        return formatKeybindString(pattern)
    }

    private String formatKeybindString(String pattern) {
        if (!pattern || pattern == 'none') return ''

        List<String> parts = pattern.split('\\+')
        List<String> formatted = []

        parts.each { part ->
            switch (part.toLowerCase().trim()) {
                case 'ctrl':
                case 'control':
                    formatted.add('Ctrl')
                    break
                case 'alt':
                    formatted.add('Alt')
                    break
                case 'shift':
                    formatted.add('Shift')
                    break
                case 'escape':
                case 'esc':
                    formatted.add('Esc')
                    break
                case 'enter':
                    formatted.add('Enter')
                    break
                case 'tab':
                    formatted.add('Tab')
                    break
                case 'space':
                    formatted.add('Space')
                    break
                case 'backspace':
                    formatted.add('Backspace')
                    break
                case 'delete':
                    formatted.add('Delete')
                    break
                case 'home':
                    formatted.add('Home')
                    break
                case 'end':
                    formatted.add('End')
                    break
                case 'page-up':
                case 'pageup':
                    formatted.add('PgUp')
                    break
                case 'page-down':
                case 'pagedown':
                    formatted.add('PgDn')
                    break
                case 'arrow-up':
                case 'up':
                    formatted.add('↑')
                    break
                case 'arrow-down':
                case 'down':
                    formatted.add('↓')
                    break
                case 'arrow-left':
                case 'left':
                    formatted.add('←')
                    break
                case 'arrow-right':
                case 'right':
                    formatted.add('→')
                    break
                case 'f1':
                    formatted.add('F1')
                    break
                case 'f2':
                    formatted.add('F2')
                    break
                case 'f3':
                    formatted.add('F3')
                    break
                case 'f4':
                    formatted.add('F4')
                    break
                case 'f5':
                    formatted.add('F5')
                    break
                case 'f6':
                    formatted.add('F6')
                    break
                case 'f7':
                    formatted.add('F7')
                    break
                case 'f8':
                    formatted.add('F8')
                    break
                case 'f9':
                    formatted.add('F9')
                    break
                case 'f10':
                    formatted.add('F10')
                    break
                case 'f11':
                    formatted.add('F11')
                    break
                case 'f12':
                    formatted.add('F12')
                    break
                default:
                    if (part.length() == 1) {
                        formatted.add(part.toUpperCase())
                    } else {
                        formatted.add(part)
                    }
            }
        }

        return formatted.join('+')
    }
}
