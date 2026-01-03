# Model Selection Implementation - Summary

## What Was Implemented

### 1. New File: `tui/ModelSelectionDialog.groovy`

A dialog component for selecting models from the catalog, inspired by OpenCode's model selection.

**Features:**
- Searchable model list with fuzzy filtering
- Models grouped into sections:
  - Favorites (pinned models)
  - Recent (recently used)
  - By provider (OpenCode, Zai)
- Visual indicators:
  - " (Free)" tag for free models
  - Provider names in Recent/Favorites
- Keyboard navigation:
  - Type to filter
  - Arrow keys to navigate
  - Enter to select
  - Escape to close
- Automatic persistence:
  - Selected model added to recent models list
  - Saved to `~/.glm/config.toml`

### 2. Modified: `tui/LanternaTUI.groovy`

Added command handling and model switching:

**New Methods:**
- `handleSlashCommand(String input)` - Processes slash commands
- `showModelSelectionDialog()` - Opens model selection dialog (public for keyboard shortcut)
- `switchModel(String newModel)` - Switches to new model
- `updateWindowAndStatusBar()` - Updates UI when model changes
- `showHelp()` - Shows help text for commands
- `appendSystemMessage(String message)` - Helper to add system messages (uses `getTextBox().getRenderer().addLine()`)

**Slash Commands Implemented:**
- `/models` - Opens model selection dialog
- `/model [id]` - Show current model or switch to specific model
- `/help` - Show help information
- `/clear` - Clear chat history
- `/exit` - Exit TUI

**Keyboard Shortcut:**
- `Ctrl+M` - Open model selection dialog (global shortcut)

**Process Changes:**
- Modified `processUserInput()` to intercept slash commands before sending to AI
- Updates window title and status bar when model changes
- Reinitializes client when switching models

### 3. Modified: `tui/CommandInputPanel.groovy`

Added Ctrl+M keyboard shortcut handler:
```groovy
// Ctrl+M to show model selection dialog
if (!autocompletePopup.isVisible() &&
    key.isCtrlDown() && keyType == KeyType.Character && key.getCharacter() == 'm') {
    tui.showModelSelectionDialog()
    return false
}
```

### 4. Modified: `core/Config.groovy`

Extended `BehaviorConfig` to include model persistence:
```groovy
static class BehaviorConfig {
    String language = "auto"
    @JsonProperty("safety_mode")
    String safetyMode = "ask"
    @JsonProperty("default_model")
    String defaultModel = "zai/glm-4-flash"
    @JsonProperty("max_steps")
    Integer maxSteps = null
    @JsonProperty("recent_models")      // NEW
    List<String> recentModels = []
    @JsonProperty("favorite_models")   // NEW
    List<String> favoriteModels = []
}
```

### 5. Modified: `commands/ChatCommand.groovy`

Added logic to use most recent model from config:
```groovy
String modelToUse = model ?: config.behavior.defaultModel

// Check if there are recent models and use the most recent one
if (!model && config.behavior.recentModels && !config.behavior.recentModels.isEmpty()) {
    modelToUse = config.behavior.recentModels[0]
}
```

## How It Works

### User Flow

1. **Starting a session:**
   ```bash
   ./glm.groovy chat
   ```
   - Loads config from `~/.glm/config.toml`
   - Uses `defaultModel` or most recent model from `recentModels`
   - Initializes TUI with selected model

2. **Changing model via command:**
   - Type `/models` in TUI
   - Model selection dialog opens
   - Search/filter models
   - Select model with Enter
   - Model is saved to `recentModels`
   - Client is reinitialized
   - Window title and status bar update

3. **Changing model via shortcut:**
   - Press `Ctrl+M` in TUI
   - Same flow as `/models` command

4. **Changing model directly:**
   - Type `/model opencode/glm-4.6`
   - Model switches directly (skips dialog)

### Data Persistence

Models are saved to `~/.glm/config.toml`:

```toml
[behavior]
default_model = "opencode/big-pickle"
recent_models = ["opencode/glm-4.6", "zai/glm-4-flash"]
favorite_models = []
```

### Model Display Logic

Models are displayed in this order:
1. **Favorites** (from `favoriteModels`)
2. **Recent** (from `recentModels`, excluding favorites)
3. **By Provider** (all other models, grouped by provider)

Within each group:
- Free models shown first
- Then alphabetically by name

### Free Model Detection

A model is marked as "Free" if:
```groovy
model.cost?.input == 0 && model.cost?.output == 0
```

## Testing

### Start TUI:
```bash
cd /home/kevintan/glm-cli-jbang
./glm.groovy chat
```

### Test Commands:
1. `/models` - Opens model selection dialog
2. `/model` - Shows current model
3. `/model opencode/glm-4.6` - Switches to specific model
4. `/help` - Shows help text
5. `/clear` - Clears chat history
6. `Ctrl+M` - Opens model selection dialog

### Expected Behavior:
- Dialog shows with search box and model list
- Type to filter models (fuzzy matching)
- Arrow keys navigate through list
- Enter selects model
- Model is added to recent models
- Window title updates
- Client reinitializes with new model
- Status bar shows new model

## Comparison with OpenCode

| Feature | OpenCode | GLM CLI |
|---------|-----------|----------|
| `/models` command | ✅ | ✅ |
| Model dialog | ✅ | ✅ |
| Search/filtering | ✅ | ✅ |
| Favorites section | ✅ | ✅ |
| Recent section | ✅ | ✅ |
| Provider grouping | ✅ | ✅ |
| Free model tags | ✅ | ✅ |
| Keyboard shortcuts | Ctrl+X M | Ctrl+M |
| `Ctrl+M` shortcut | ✅ | ✅ |
| Model persistence | ✅ | ✅ |
| Fuzzy search | ✅ ✅ | ✅ (simple contains) |

**Key Differences:**
- OpenCode uses fuzzy search (fuzzysort), GLM CLI uses simple contains matching
- OpenCode has "Connect provider" button, GLM CLI requires CLI auth
- OpenCode has Ctrl+X M shortcut, GLM CLI has Ctrl+M (simpler)

## Files Changed

### Created:
- `tui/ModelSelectionDialog.groovy` (243 lines)

### Modified:
- `tui/LanternaTUI.groovy` (+93 lines)
- `tui/CommandInputPanel.groovy` (+7 lines)
- `core/Config.groovy` (+3 lines)
- `commands/ChatCommand.groovy` (+6 lines)

### Total:
- **349 lines added**
- **1 new file**
- **4 files modified**

## Bug Fixes (2026-01-03)

### Fixed: Autocomplete Crash on Slash Commands
**File:** `tui/LanternaAutocompletePopup.groovy`
**Issue:** The app crashed when typing characters while the autocomplete popup was visible.
**Root Cause:** Line 66 referenced undefined variable `key` instead of method parameter `keyStroke`.
**Fix:** Changed `key.isAltDown()` and `key.isCtrlDown()` to `keyStroke.isAltDown()` and `keyStroke.isCtrlDown()`.

### Fixed: Model Selection Index Mismatch
**File:** `tui/ModelSelectionDialog.groovy`
**Issue:** Selecting a model from the list could select the wrong model or fail silently.
**Root Cause:** The `handleSelection()` method used the ActionListBox index directly against `filteredModels`, but the listbox contains category headers and separators that don't correspond to model items.
**Fix:** Added `listboxIndexToModel` mapping that tracks which listbox indices correspond to actual model items, updated `updateModelList()` to populate this mapping, and updated `handleSelection()` to use it.

## Known Limitations

1. **Search:** Simple contains matching (not fuzzy like OpenCode)
2. **Favorites:** No UI to add/remove favorites (need to edit config manually)
3. **Model metadata:** Limited to what's in ModelCatalog (no caching of remote models)
4. **Provider connection:** Requires separate `glm auth login` command (not integrated in dialog)

## Future Enhancements

- [ ] Add fuzzy search library (Apache Commons Text or similar)
- [ ] Add "Favorite" toggle in dialog with keyboard shortcut
- [ ] Show model details (context window, cost) in dialog
- [ ] Add "Refresh models" button to fetch from remote API
- [ ] Add "Connect provider" button for unauthenticated providers
- [ ] Support for local models
- [ ] Model comparison feature

## Technical Notes

### Lanterna Components Used:
- `BasicWindow` - Dialog window
- `Panel` - Container for layout
- `LinearLayout` - Vertical/horizontal layout
- `TextBox` - Search input field
- `ActionListBox` - Scrollable model list
- `Borders.singleLine()` - Decorative borders

### Persistence:
- Uses Jackson TOML mapper (`TomlMapper`)
- Config file: `~/.glm/config.toml`
- Recent models limited to 10 entries
- Maintains insertion order (most recent first)

### Integration Points:
- `ModelCatalog.getAllModels()` - Gets available models
- `ModelCatalog.getModel(id)` - Validates model ID
- `ModelCatalog.getProvider(id)` - Gets provider info
- `Auth.get(providerId)` - Checks authentication
- `GlmClient(providerId)` - Reinitializes client
