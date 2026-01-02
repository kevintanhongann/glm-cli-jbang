# Lanterna TUI File Mention Feature

## Overview

The Lanterna TUI now supports file/folder mentions and command autocomplete, similar to modern code editors and AI assistants like OpenCode and Cursor.

## Features

### File/Folder Mentions (`@`)

Trigger file and folder autocomplete by typing `@` anywhere in the input (after a space or at the beginning).

**Examples:**
- `@README.md` - Mention a file
- `@src/main.groovy` - Mention a file with path
- `@tui/` - Mention a folder (folders end with `/`)
- `@README.md#L10-L50` - Mention specific lines

**Supported Features:**
- Fuzzy matching (type partial names)
- Line range syntax (`#L10-L50`)
- Icons for files (📄) and folders (📁)
- Real-time filtering as you type

### Command Autocomplete (`/`)

Trigger command autocomplete by typing `/` at the start of input.

**Available Commands:**
- `/help` - Show available commands
- `/clear` - Clear chat history
- `/new` - Start new conversation
- `/exit` - Exit TUI
- `/model` - Show or change current model
- `/models` - List available models
- `/read` - Read a file
- `/ls` - List files in directory
- `/tools` - List available tools
- `/search` - Search web
- `/context` - Show current context
- `/cwd` - Show/change working directory
- `/config` - Show configuration
- `/theme` - Change color theme
- `/export` - Export conversation
- `/copy` - Copy last response to clipboard
- `/debug` - Toggle debug mode
- `/verbose` - Toggle verbose output

### Keyboard Navigation

**When Popup is Visible:**
- `↑/↓` - Navigate through suggestions
- `Enter` - Insert selected item
- `Tab` - Insert selected item
- `Esc` - Close popup
- `Space` - Close popup and insert space

**When Popup is Hidden:**
- `↑` - Previous history item
- `↓` - Next history item
- `Enter` - Submit message
- `Esc` - Clear input

## Usage Examples

### Example 1: File Mention
```
You: Read the @README.md file
```

### Example 2: Multiple File Mentions
```
You: Compare @src/main.groovy with @src/test.groovy
```

### Example 3: File with Line Range
```
You: Look at lines 10-50 in @glm.groovy#L10-L50
```

### Example 4: Folder Mention
```
You: What files are in the @tui/ folder?
```

### Example 5: Command
```
You: /read README.md
```

### Example 6: Mixed Usage
```
You: Check @README.md and @package.json /clear
```

## Implementation Details

### Files Created/Modified

**New Files:**
- `tui/LanternaAutocompletePopup.groovy` - Popup component for displaying suggestions

**Modified Files:**
- `tui/CommandInputPanel.groovy` - Enhanced with autocomplete support
- `tui/LanternaTUI.groovy` - Updated to pass working directory and mentions

**Reused Files:**
- `tui/AutocompleteItem.groovy` - Item structure for suggestions
- `tui/FileProvider.groovy` - File/folder suggestion logic
- `tui/CommandProvider.groovy` - Command definitions

### How It Works

1. **Trigger Detection:**
   - User types `@` → Shows file/folder autocomplete
   - User types `/` at start → Shows command autocomplete

2. **Popup Display:**
   - Popup appears below input field
   - Shows filtered suggestions with icons
   - Positioned to avoid terminal boundaries

3. **Filtering:**
   - As user types after trigger, suggestions are filtered
   - Supports fuzzy matching and partial paths
   - Real-time updates

4. **Selection:**
   - User navigates with arrow keys
   - Enter or Tab inserts selected item
   - Adds trailing space automatically

5. **Mention Extraction:**
   - Regex extracts `@filename` patterns from input
   - Supports line ranges (`#L10-L50`)
   - Passed to message processing

### Limitations

- **No caching**: File list is rescanned on each trigger (can be slow on large projects)
- **Cursor positioning**: Limited support for precise cursor positioning after insertion
- **Terminal resize**: Popup may need manual close if resized

### Future Enhancements

- [ ] Cache file lists for performance
- [ ] Support for line selection without line numbers
- [ ] Auto-scroll popup content
- [ ] Better cursor positioning after insertion
- [ ] Support for relative path hints (`../`, `./`)
- [ ] File preview in popup
- [ ] Highlight matching characters in suggestions

## Testing

To test the autocomplete feature:

1. Start the TUI:
   ```bash
   ./glm.groovy chat
   ```

2. Type `@` to see file/folder suggestions
3. Type partial names to filter (e.g., `@README`)
4. Use arrow keys to navigate, Enter to select
5. Type `/` at start to see command suggestions
6. Try multiple mentions in one message

## Troubleshooting

**Popup doesn't appear:**
- Ensure `@` is typed after a space or at the start
- Ensure `/` is typed at the very start of input (for commands)

**Popup shows wrong position:**
- Try scrolling the terminal
- Resize the terminal window

**Files not found:**
- Check working directory (shown in status bar)
- Ensure files are not in ignored directories (.git, node_modules, etc.)

**Performance issues:**
- The feature scans up to 5 directory levels deep
- Try typing more specific paths to narrow results
- Large projects may be slow (future caching planned)

## Related Files

- Jexer TUI: `tui/AutocompleteField.groovy` - Original Jexer implementation
- Jexer TUI: `tui/AutocompletePopup.groovy` - Original Jexer popup
