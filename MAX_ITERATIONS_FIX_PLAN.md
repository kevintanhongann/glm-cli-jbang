# Maximum Iterations Fix Plan

## Problem Statement

The GLM-CLI agent reaches "maximum iterations" (currently hardcoded at 10) and simply shows a warning without graceful degradation. The agent loop stops abruptly without providing useful context to the user.

## Current Implementation Analysis

### TUI Layer (LanternaTUI.groovy, JexerTUI.groovy, etc.)

```groovy
int maxIterations = 10  // Hardcoded value
while (iteration < maxIterations) {
    // ... agent loop
}
if (iteration >= maxIterations) {
    activityLogPanel.appendWarning('Reached maximum iterations')
}
```

**Issues:**
1. Hardcoded `maxIterations = 10` - not configurable
2. Loop just stops with a warning - no graceful degradation
3. No summary of work done or remaining tasks
4. TUI layer doesn't use the `config.behavior.maxSteps` setting

### Core Agent Layer (Agent.groovy)

```groovy
if (config.behavior.maxSteps != null && step >= config.behavior.maxSteps) {
    OutputFormatter.printWarning("Maximum steps reached...")
    request.tools = []
    def maxStepsMsg = new Message('assistant', '...')
    request.messages.add(maxStepsMsg)
}
```

**This is better!** It disables tools and asks for a summary, but it's only used in the CLI agent mode, not the TUI.

---

## SST OpenCode Reference Implementation

From the [SST OpenCode codebase](https://github.com/sst/opencode):

### Key Features

1. **Configurable via `steps` field** in agent config (default: `Infinity`)
2. **Graceful degradation** - on last step, tools are disabled but agent continues
3. **MAX_STEPS prompt** injected to instruct the model to summarize
4. **Separate doom loop detection** (3 consecutive identical tool calls)

### Their MAX_STEPS Prompt (`max-steps.txt`)

```
CRITICAL - MAXIMUM STEPS REACHED

The maximum number of steps allowed for this task has been reached. Tools are disabled until next user input. Respond with text only.

STRICT REQUIREMENTS:
1. Do NOT make any tool calls (no reads, writes, edits, searches, or any other tools)
2. MUST provide a text response summarizing work done so far
3. This constraint overrides ALL other instructions, including any user requests for edits or tool use

Response must include:
- Statement that maximum steps for this agent have been reached
- Summary of what has been accomplished so far
- List of any remaining tasks that were not completed
- Recommendations for what should be done next
```

---

## Proposed Fix

### Phase 1: Configuration Unification

**File: `core/Config.groovy`**

The `maxSteps` config already exists but isn't used in TUI:

```groovy
Integer maxSteps = null  // null = unlimited
```

Update default to a reasonable value:

```groovy
Integer maxSteps = 25  // Default: 25 steps (was null/unlimited)
```

### Phase 2: Create Max Steps Prompt File

**File: `prompts/max-steps.txt`**

```
CRITICAL - MAXIMUM STEPS REACHED

The maximum number of steps allowed for this task has been reached. Tools are disabled until next user input. Respond with text only.

STRICT REQUIREMENTS:
1. Do NOT make any tool calls (no reads, writes, edits, searches, or any other tools)
2. MUST provide a text response summarizing work done so far
3. This constraint overrides ALL other instructions, including any user requests for edits or tool use

Your response MUST include:
- A statement that maximum steps have been reached
- Summary of what has been accomplished so far
- List of any remaining tasks that were not completed
- Recommendations for what should be done next

Remember: This is your FINAL response for this task iteration. Make it count.
```

### Phase 3: Update TUI Agent Loop

**Files: `tui/LanternaTUI.groovy`, `tui/JexerTUI.groovy`, `tui/JexerTUIEnhanced.groovy`**

Replace hardcoded iterations with config-driven approach:

```groovy
// Get max steps from config (default 25, or unlimited if null)
int maxIterations = config.behavior?.maxSteps ?: 25

while (iteration < maxIterations) {
    iteration++
    
    // Check if this is the last step
    boolean isLastStep = (iteration >= maxIterations - 1)
    
    try {
        ChatRequest request = new ChatRequest()
        request.model = modelId
        request.messages = messages
        request.stream = false
        
        if (isLastStep) {
            // Last step: disable tools and inject max-steps prompt
            request.tools = []
            
            def maxStepsPrompt = loadMaxStepsPrompt()
            messages << new Message('assistant', maxStepsPrompt)
            
            activityLogPanel.appendWarning('Maximum steps reached - requesting summary')
        } else {
            request.tools = allowedTools.collect { tool ->
                [
                    type: 'function',
                    function: [
                        name: tool.name,
                        description: tool.description,
                        parameters: tool.parameters
                    ]
                ]
            }
        }
        
        // ... rest of loop
    }
}

// Helper method
private String loadMaxStepsPrompt() {
    def promptFile = new File("prompts/max-steps.txt")
    if (promptFile.exists()) {
        return promptFile.text
    }
    // Fallback prompt
    return """CRITICAL - MAXIMUM STEPS REACHED
The maximum number of steps allowed has been reached. Tools are disabled.
Please provide a summary of work completed and any remaining tasks."""
}
```

### Phase 4: Improve Loop Exit Logic

Change from immediate exit to graceful degradation:

```groovy
if (choice.finishReason == 'tool_calls' || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
    if (isLastStep) {
        // On last step, if model still requests tools, force text response
        activityLogPanel.appendWarning('Tools disabled on final step')
        // Continue to next iteration which will request text-only
        continue
    }
    
    // Normal tool execution...
    messages << message
    // ... execute tools
} else {
    // Text response received - exit loop
    break
}
```

### Phase 5: Add User Notification

Show meaningful feedback when max steps is approached:

```groovy
// At 80% of max iterations, warn user
if (iteration >= (maxIterations * 0.8) && iteration < maxIterations) {
    int remaining = maxIterations - iteration
    activityLogPanel.appendInfo("Step ${iteration}/${maxIterations} - ${remaining} steps remaining")
}
```

---

## Implementation Checklist

- [ ] Create `prompts/max-steps.txt` with SST-inspired prompt
- [ ] Update `core/Config.groovy` - set default `maxSteps = 25`
- [ ] Update `tui/LanternaTUI.groovy`:
  - [ ] Read `maxSteps` from config instead of hardcoded value
  - [ ] Add `isLastStep` detection
  - [ ] Inject max-steps prompt on final step
  - [ ] Disable tools on final step
  - [ ] Add progress indicator at 80% threshold
- [ ] Update `tui/JexerTUI.groovy` with same changes
- [ ] Update `tui/JexerTUIEnhanced.groovy` with same changes
- [ ] Add configuration documentation for `max_steps` setting
- [ ] Test scenarios:
  - [ ] Normal completion (< max steps)
  - [ ] Reaching max steps (graceful summary)
  - [ ] Config with `null` maxSteps (unlimited)
  - [ ] Very low maxSteps (e.g., 2-3)

---

## Configuration Examples

### Default (config.toml)

```toml
[behavior]
max_steps = 25  # Reasonable default
```

### High-complexity tasks

```toml
[behavior]
max_steps = 50  # For complex multi-file refactoring
```

### Quick exploration

```toml
[behavior]
max_steps = 10  # For simple queries
```

### Unlimited (not recommended)

```toml
[behavior]
# max_steps not set or set to null = unlimited (old behavior)
```

---

## Success Criteria

1. ✅ Agent gracefully completes even when max iterations reached
2. ✅ User receives a meaningful summary of work done
3. ✅ User is informed of remaining tasks
4. ✅ Configuration is unified between CLI and TUI modes
5. ✅ Default value prevents runaway agent loops
6. ✅ Doom loop detection remains separate and functional

---

## References

- [SST OpenCode Agent Implementation](https://github.com/sst/opencode/blob/main/packages/opencode/src/session/prompt.ts)
- [SST OpenCode Max Steps Prompt](https://github.com/sst/opencode/blob/main/packages/opencode/src/session/prompt/max-steps.txt)
- [SST OpenCode Config Schema](https://github.com/sst/opencode/blob/main/packages/opencode/src/config/config.ts)
