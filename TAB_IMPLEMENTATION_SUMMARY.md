# Tab Button Agent Switching Implementation

## Summary

Successfully implemented full Tab button agent switching functionality to match opencode's implementation, with both forward and reverse cycling support.

## Changes Made

### 1. Created `core/AgentRegistry.groovy`
- New centralized agent management class
- Implements circular cycling through visible agents
- Supports forward (direction=1) and backward (direction=-1) cycling
- Filters out hidden agents (EXPLORE, GENERAL)
- Key methods:
  - `cycleAgent(int direction)` - Circular agent cycling
  - `getCurrentAgent()` - Get current agent type
  - `getCurrentAgentConfig()` - Get agent configuration
  - `getVisibleAgents()` - List all visible agent types
  - `canSwitchTo(AgentType)` - Check if agent is accessible

### 2. Updated `core/AgentConfig.groovy`
Added helper methods for agent registry:
- `forType(AgentType)` - Get config by enum type
- `forName(String)` - Get config by name string
- `getVisibleAgentTypes()` - Get list of non-hidden agent types

### 3. Updated `tui/LanternaTUI.groovy`
- Replaced `AgentType currentAgentType` with `AgentRegistry agentRegistry`
- Renamed `switchAgent()` to `cycleAgent(int direction)` with direction parameter
- Updated agent indicator to use registry
- Updated visual hint: "(Tab to switch)" → "(Tab/Shift+Tab to switch)"
- Agent config retrieval now uses `agentRegistry.getCurrentAgentConfig()`

### 4. Updated `tui/CommandInputPanel.groovy`
- Enhanced Tab key handling to detect Shift modifier
- Tab alone → `tui.cycleAgent(1)` (forward)
- Shift+Tab → `tui.cycleAgent(-1)` (backward)
- Both only trigger when autocomplete popup is not visible

### 5. Updated `core/Config.groovy`
Added TUI keybinding configuration:
- `agent_cycle_key` (default: "tab")
- `agent_cycle_reverse_key` (default: "shift+tab")

## Implementation Details

### Agent Behavior
- **BUILD**: Full access agent (default)
- **PLAN**: Read-only agent for analysis
- **EXPLORE**: Fast codebase exploration (hidden)
- **GENERAL**: Multi-step task execution (hidden)

Only BUILD and PLAN are visible and cyclable in the current configuration.

### Circular Cycling Algorithm
```groovy
int nextIndex = (currentIndex + direction) % visibleAgents.size()
if (nextIndex < 0) {
    nextIndex = visibleAgents.size() - 1
}
```
This ensures seamless wraparound in both directions.

### User Experience
- Press `Tab` to cycle forward through visible agents
- Press `Shift+Tab` to cycle backward
- Current agent displayed with color coding in status bar
- Visual indicator shows "(Tab/Shift+Tab to switch)" hint

## Comparison with OpenCode

| Feature | OpenCode | GLM-CLI-Jbang | Status |
|---------|----------|---------------|--------|
| Forward cycling (Tab) | ✅ | ✅ | Implemented |
| Reverse cycling (Shift+Tab) | ✅ | ✅ | Implemented |
| Configurable keybindings | ✅ | ✅ | Implemented |
| Circular wraparound | ✅ | ✅ | Implemented |
| Hidden agent filtering | ✅ | ✅ | Implemented |
| Reactive state management | ✅ | Partial | Working (simpler architecture) |
| Visual feedback | ✅ | ✅ | Implemented |

## Testing

Created `test_agent_registry.groovy` which verifies:
1. Initial agent state
2. Visible agent filtering
3. Forward cycling
4. Backward cycling
5. Circular wraparound
6. Agent config retrieval
7. Switch validation

All tests passed successfully. ✅

## Future Enhancements

1. **More agents**: Add more agent types (e.g., CODE_REVIEW, TESTING)
2. **Agent selection dialog**: Allow direct agent selection via popup
3. **Model switching per agent**: Each agent can have its own default model
4. **Persistent agent preference**: Remember last used agent across sessions
5. **Agent-specific tool permissions**: Fine-grained tool control per agent

## Files Modified

- ✅ `core/AgentRegistry.groovy` (new)
- ✅ `core/AgentConfig.groovy` (modified)
- ✅ `core/Config.groovy` (modified)
- ✅ `tui/LanternaTUI.groovy` (modified)
- ✅ `tui/CommandInputPanel.groovy` (modified)
- ✅ `test_agent_registry.groovy` (new, for testing)
