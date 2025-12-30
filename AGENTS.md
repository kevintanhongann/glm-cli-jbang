# Agent Guidelines for GLM-CLI

This document provides guidelines for working with and extending the agent system in GLM-CLI, inspired by [SST OpenCode](https://github.com/sst/opencode) architecture.

## Overview

GLM-CLI implements a **ReAct** (Reasoning + Acting) agent pattern where the LLM autonomously decides when and how to use tools to accomplish multi-step tasks. The agent maintains a conversation history and iteratively reasons, acts, and observes until the task is complete.

## Debugging

### Local Development Testing

To test the agent in development:

```bash
# Run directly with JBang
./glm.groovy agent "Test task"

# Or install locally and test
jbang app install --force --name glm glm.groovy
glm agent "Read README.md and summarize"
```

### Verbose Logging

Enable verbose output to see the agent's thinking process:

```bash
# Add verbose flag (if implemented)
glm agent -v "Complex task"

# Or check logs in ~/.glm/logs/
tail -f ~/.glm/logs/agent.log
```

### Common Debugging Scenarios

| Issue | Symptoms | Solution |
|-------|----------|----------|
| Agent stuck in loop | Repeated tool calls without progress | Check if tools are returning expected results; add timeout |
| Tool not found | "Error: Tool not found" | Ensure tool is registered with agent; check tool name matches |
| Infinite reasoning | "Thinking..." without tool calls or response | Increase `maxSteps` limit; check model response format |
| Permission denied | User denies write operation | Check safety_mode config; modify safety logic if needed |
| Diff shows no changes | Agent proposes write with empty diff | Verify tool arguments; check file normalization |

### Tracing Agent Execution

To trace the agent's decision process:

1. **Monitor conversation history** - Check what messages are being sent to the API
2. **Inspect tool calls** - Log tool name, arguments, and results
3. **Review finish reasons** - Understand why the agent stopped or continued
4. **Validate JSON schemas** - Ensure tool parameters match expected format

## Tool Calling

### Best Practices

#### ALWAYS USE TOOLS WHEN APPLICABLE

Tools extend the agent's capabilities beyond text generation. Use them for:
- File operations (read, write, list)
- Code execution (bash commands)
- Content search (grep)
- Pattern matching (glob)

#### Parallel Tool Execution

The agent can call multiple tools in parallel when appropriate. This is especially useful for:
- Reading multiple files simultaneously
- Running independent operations
- Gathering context from different sources

Example pattern:
```groovy
// Agent might call these in parallel:
// 1. read_file("src/main.groovy")
// 2. read_file("src/config.groovy")
// 3. list_files("src/")
```

#### Tool Batching

Batch similar operations to reduce API calls:
- Read multiple related files in one conversation turn
- Combine related edits into a single write operation
- Use glob/list before reading multiple files

#### Tool Argument Validation

Always validate tool arguments before execution:
```groovy
Object execute(Map<String, Object> args) {
    String pathStr = args.get("path")
    Path path = Paths.get(pathStr).normalize()

    // Validate path
    if (!path.startsWith(Paths.get(".").toAbsolutePath().normalize())) {
        return "Error: Path outside project directory"
    }

    // ... rest of tool logic
}
```

### Tool Response Format

Tools should return clear, structured responses:

**Success Response:**
```groovy
return "Successfully read 123 lines from src/main.groovy"
```

**Error Response:**
```groovy
return "Error: File not found at src/nonexistent.groovy"
```

**Partial Success:**
```groovy
return "Read 45 lines (file truncated at 1000 lines limit)"
```

### Tool Timeout and Error Handling

Implement timeouts for long-running operations:
```groovy
Object execute(Map<String, Object> args) {
    try {
        // Tool logic with timeout
        return result
    } catch (TimeoutException e) {
        return "Error: Operation timed out after 30 seconds"
    } catch (Exception e) {
        return "Error: ${e.message}"
    }
}
```

## Agent Configuration

### Tool Permissions

Control which tools an agent can use:

```groovy
Agent agent = new Agent(apiKey, model)

// Register only read tools for a plan agent
agent.registerTool(new ReadFileTool())
agent.registerTool(new ListFilesTool())

// Or all tools for a build agent
agent.registerTool(new ReadFileTool())
agent.registerTool(new WriteFileTool())
agent.registerTool(new ListFilesTool())
```

### Safety Modes

Configure safety behavior for file operations:

| Mode | Description | Use Case |
|------|-------------|----------|
| `ask` | Prompt user before destructive operations (default) | Interactive sessions |
| `always_allow` | Execute without confirmation | Automated workflows |
| `strict` | Deny all writes | Read-only analysis |

### Max Steps

Limit the number of reasoning iterations:
```groovy
Agent agent = new Agent(apiKey, model)
agent.setMaxSteps(10) // Prevent infinite loops
```

## Multi-Agent Coordination

GLM-CLI can support multiple agent personas with different capabilities:

### Build Agent (Full Access)
- Can read and write files
- Can execute bash commands (future)
- Suitable for development work

### Plan Agent (Read-Only)
- Can only read files and list directories
- Denies file edits by default
- Ideal for code exploration and planning

### General Subagent
- Used internally for complex multi-step tasks
- Invoked via `@general` in prompts
- Handles specialized operations

## Agent Prompts

### System Prompt Construction

The agent system prompt should include:

1. **Role Definition** - Clear description of the agent's purpose
2. **Tool Descriptions** - What tools are available and how to use them
3. **Safety Guidelines** - When to ask for permission
4. **Output Format** - How to structure responses

Example:
```groovy
String systemPrompt = """
You are an AI coding agent specialized in Groovy development.

Available tools:
- read_file: Read file contents
- write_file: Write or create files (requires user approval)
- list_files: List directory contents

Guidelines:
- Always think before acting
- Use tools to gather context before making changes
- Show diff previews for all file modifications
- Ask for permission before destructive operations

Output format:
- Think through the problem step by step
- Propose solutions with clear reasoning
- Use tool calls to execute operations
"""
```

### Context Management

Manage context window by:
- Prioritizing recent messages
- Summarizing long conversations
- Pruning less relevant history
- Using targeted file reads instead of dumping entire codebase

## Error Recovery

### Tool Failure Recovery

When a tool fails, the agent should:
1. Analyze the error message
2. Propose an alternative approach
3. Attempt to recover or inform the user

Example flow:
```
User: "Fix authentication bug"
Agent: [Thinking] Let me read the auth module...
Agent: [Tool] read_file("src/auth/AuthController.groovy")
Result: "Error: File not found"
Agent: [Thinking] File doesn't exist. Let me list the directory structure...
Agent: [Tool] list_files("src/")
Result: ["main.groovy", "config.groovy", "utils/"]
Agent: The auth module doesn't exist yet. Would you like me to create it?
```

### Timeout Recovery

Implement exponential backoff for retries:
- First attempt: immediate
- Second attempt: wait 1 second
- Third attempt: wait 3 seconds
- Fourth attempt: fail and inform user

## Performance Optimization

### Reduce API Calls

- Batch file reads when possible
- Use glob/list to locate files before reading
- Cache frequently accessed data
- Minimize redundant tool calls

### Streaming vs Blocking

Use streaming for:
- Long responses
- Real-time feedback
- Large code generations

Use blocking for:
- Quick operations
- Tool result processing
- Small responses

### Token Management

Monitor token usage:
- Track conversation length
- Implement context window limits
- Summarize long histories when needed
- Use efficient tool result formatting

## Testing Agents

### Unit Testing Tools

```groovy
void testReadFileTool() {
    def tool = new ReadFileTool()
    def result = tool.execute([path: "test.txt"])

    assert result.contains("expected content")
    assert !result.startsWith("Error:")
}
```

### Integration Testing Agent Flows

Test common scenarios:
- Reading and modifying a file
- Multi-file refactoring
- Error handling and recovery
- Permission workflows

### Regression Testing

Ensure new changes don't break:
- Existing tool behavior
- Agent decision patterns
- Safety mechanisms
- Response formatting

## Extending the Agent System

### Adding New Tools

1. Implement `Tool` interface
2. Define clear name and description
3. Specify JSON Schema for parameters
4. Implement `execute()` with error handling
5. Register with agent
6. Update documentation

### Custom Agent Behaviors

Extend `Agent` class to:
- Implement custom reasoning logic
- Add specialized tool combinations
- Create agent personas
- Implement custom safety checks

### Plugin System (Future)

Consider supporting external tools via:
- Groovy scripts in `.glm/tools/`
- Dynamic class loading
- Tool registration via config
- Sandboxed execution environment

## Troubleshooting Common Issues

### Agent Makes Poor Decisions

**Symptoms**: Agent takes inefficient paths or makes unnecessary tool calls

**Solutions**:
- Improve system prompt clarity
- Add examples of desired behavior
- Refine tool descriptions
- Add constraints to prevent anti-patterns

### Agent Gets Stuck in Loops

**Symptoms**: Repeating the same tool calls or reasoning

**Solutions**:
- Implement max steps limit
- Add loop detection logic
- Improve error messages to guide recovery
- Review tool return format for clarity

### Diff Preview Shows No Changes

**Symptoms**: Write tool proposes changes but diff is empty

**Solutions**:
- Check file normalization logic
- Verify path resolution
- Ensure content comparison is accurate
- Add explicit change detection

## References

- [ReAct Paper](https://arxiv.org/abs/2210.03629) - Reasoning and Acting in Language Models
- [SST OpenCode AGENTS.md](https://github.com/sst/opencode/blob/dev/AGENTS.md) - Inspiration for this document
- [GLM-4 API Documentation](https://open.bigmodel.cn/dev/api) - Tool calling specification
