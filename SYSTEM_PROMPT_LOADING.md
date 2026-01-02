# System Prompt Loading for TUI Agents

## Problem

When users switched between agents using Tab key, only tool permissions and maxTurns changed. The agents did **not** load their specialized system prompts from prompt files, so the LLM behaved as a generic assistant with restricted tools rather than as a specialized agent.

## Solution

Added system prompt loading for main TUI agents (BUILD/PLAN/EXPLORE) to match the behavior of subagents.

## Changes Made

### 1. Created `prompts/build.txt`
Added system prompt for BUILD agent that defines:
- Role: Senior software engineer and development agent
- Strengths: Writing clean code, understanding codebases, implementing features
- Guidelines for making changes and writing code
- Focus on readability, maintainability, and testing

### 2. Updated `tui/LanternaTUI.groovy`
Modified `processInput()` method to load system prompt:
```groovy
// Get agent config for current type
AgentConfig agentConfig = agentRegistry.getCurrentAgentConfig()

// Load system prompt if available
def systemPrompt = agentConfig.loadPrompt()
if (systemPrompt && !systemPrompt.isEmpty()) {
    messages << new Message('system', systemPrompt)
}

messages << new Message('user', userInput)
```

## How It Works

Now when you switch agents and send commands:

### Before (Incomplete):
- ✅ Different tool permissions
- ✅ Different max iterations
- ❌ Same generic behavior

### After (Complete):
- ✅ Different tool permissions
- ✅ Different max iterations
- ✅ **Different system prompts with specialized personalities and guidelines**

## Agent System Prompts

### BUILD Agent (`prompts/build.txt`)
- **Role**: Senior software engineer
- **Focus**: Implementation, development, code writing
- **Tools**: All tools available (read, write, list, grep, glob, web_search)
- **Max turns**: 50
- **System prompt**: 1,168 characters

### PLAN Agent (`prompts/plan.txt`)
- **Role**: Code architecture and planning specialist
- **Focus**: Analysis, code exploration, creating implementation plans
- **Tools**: Read-only (no write_file or edit_file)
- **Max turns**: 30
- **System prompt**: 894 characters

### EXPLORE Agent (`prompts/explore.txt`)
- **Role**: File search specialist
- **Focus**: Rapid codebase navigation and searching
- **Tools**: Read-only tools only (read, glob, grep, list_files)
- **Max turns**: 15
- **System prompt**: 719 characters

### GENERAL Agent
- **Role**: Multi-step task execution subagent
- **Tools**: Most tools (no todo_write/todo_read)
- **Max turns**: 20
- **System prompt**: Uses description as fallback (35 characters)

## Benefits

1. **Consistent behavior** - Main agents now behave like subagents with full configuration
2. **Specialized expertise** - Each agent has clear role and guidelines
3. **Better responses** - LLM follows agent-specific instructions
4. **Clear separation** - BUILD for implementation, PLAN for analysis

## Testing

Verified all prompts load correctly:
- BUILD: 1,168 characters from build.txt
- PLAN: 894 characters from plan.txt
- EXPLORE: 719 characters from explore.txt
- GENERAL: 35 characters (description fallback)

## Usage Example

```
# Start in BUILD mode (default)
BUILD agent (Tab/Shift+Tab to switch) 
User: Create a new REST API endpoint
> [Agent writes code with write_file tool]

# Switch to PLAN mode with Tab
PLAN agent (Tab/Shift+Tab to switch)
User: Analyze the authentication system
> [Agent explores codebase with read-only tools, provides architecture analysis]

# Switch back to BUILD mode with Tab
BUILD agent (Tab/Shift+Tab to switch)
User: Implement the suggested changes
> [Agent writes implementation code]
```

## Future Enhancements

1. **Dynamic prompt updates** - Allow users to customize prompts via config
2. **Agent-specific models** - Each agent could use different models
3. **Context-aware prompts** - Include project context in system prompt
4. **Multi-turn conversation** - Maintain agent identity across messages
