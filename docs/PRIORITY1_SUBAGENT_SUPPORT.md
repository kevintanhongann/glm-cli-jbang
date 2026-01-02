# Priority 1: Subagent Support Implementation Plan

## Overview

Add the ability to spawn specialized subagents with independent context windows for parallel task execution, following the OpenCode pattern.

## Status

- **Status:** Not Started
- **Priority:** High
- **Estimated Effort:** 5-7 days
- **Dependencies:** None

---

## Problem Statement

Currently, GLM-CLI operates with a single agent that handles all tasks sequentially. This leads to:
- No ability to explore multiple aspects of a codebase in parallel
- Context window exhaustion on complex tasks
- No separation of concerns between exploration and execution
- Limited ability to handle complex multi-step workflows

OpenCode solves this with specialized agents (explore, plan, general, build) that can be spawned independently with fresh context windows.

---

## Design

### Agent Types

```groovy
enum AgentType {
    BUILD,      // Full access - read + write tools
    PLAN,       // Read-only - exploration and analysis
    EXPLORE,    // Fast exploration specialist
    GENERAL     // Multi-step task execution
}
```

### Agent Configuration Schema

```groovy
@Canonical
class AgentConfig {
    String name
    AgentType type
    String description
    List<String> allowedTools = []
    List<String> deniedTools = []
    int maxTurns = 10
    boolean hidden = false
    String model = "glm-4"
    Map<String, Object> options = [:]
}
```

### Subagent Workflow

```
Main Agent Context
    │
    ├─► Launch Explore Agent (Thread 1)
    │   └─► Find files matching pattern
    │
    ├─► Launch Explore Agent (Thread 2)
    │   └─► Search for specific code patterns
    │
    └─► Launch Plan Agent (Thread 3)
        └─► Analyze architecture

↓ All agents complete

Main Agent Context
    │
    ├─► Synthesize results
    ├─► Create execution plan
    └─► Execute using Build Agent
```

---

## Implementation Plan

### Phase 1: Agent Configuration System (Days 1-2)

#### 1.1 Create Agent Configuration Model

**File:** `core/AgentConfig.groovy`

```groovy
package core

import groovy.transformCanonical

@Canonical
class AgentConfig {
    String name
    AgentType type
    String description
    List<String> allowedTools = []
    List<String> deniedTools = []
    int maxTurns = 10
    boolean hidden = false
    String model = "glm-4"
    Map<String, Object> options = [:]

    List<Tool> filterTools(List<Tool> availableTools) {
        return availableTools.findAll { tool ->
            def toolName = tool.name

            // Explicitly denied
            if (toolName in deniedTools) {
                return false
            }

            // No allow list means all tools allowed
            if (allowedTools.isEmpty()) {
                return true
            }

            // Only tools in allow list
            return toolName in allowedTools
        }
    }

    boolean isToolAllowed(String toolName) {
        if (toolName in deniedTools) return false
        if (allowedTools.isEmpty()) return true
        return toolName in allowedTools
    }

    static AgentConfig build() {
        new AgentConfig(
            name: "build",
            type: AgentType.BUILD,
            description: "Full access agent for development work. Can read and write files.",
            maxTurns: 50
        )
    }

    static AgentConfig plan() {
        new AgentConfig(
            name: "plan",
            type: AgentType.PLAN,
            description: "Read-only agent for analysis and code exploration. Can only read files.",
            deniedTools: ["write_file", "edit_file"],
            maxTurns: 30
        )
    }

    static AgentConfig explore() {
        new AgentConfig(
            name: "explore",
            type: AgentType.EXPLORE,
            description: "Fast agent specialized for exploring codebases. Use glob and grep to find files and code patterns.",
            deniedTools: ["write_file", "edit_file", "todo_write", "todo_read"],
            allowedTools: ["read_file", "glob", "grep", "list_files"],
            maxTurns: 15,
            hidden: true
        )
    }

    static AgentConfig general() {
        new AgentConfig(
            name: "general",
            type: AgentType.GENERAL,
            description: "Multi-step task execution subagent.",
            deniedTools: ["todo_write", "todo_read"],
            maxTurns: 20,
            hidden: true
        )
    }
}

enum AgentType {
    BUILD, PLAN, EXPLORE, GENERAL
}
```

#### 1.2 Create Subagent Class

**File:** `core/Subagent.groovy`

```groovy
package core

import tools.Tool
import models.Message
import com.fasterxml.jackson.databind.ObjectMapper
import tui.AnsiColors
import tui.OutputFormatter

@Canonical
class Subagent {
    private final AgentConfig config
    private final GlmClient client
    private final List<Tool> tools
    private final List<Message> history = []
    private final ObjectMapper mapper = new ObjectMapper()
    private int turn = 0

    Subagent(AgentConfig config, GlmClient client, List<Tool> allTools) {
        this.config = config
        this.client = client
        this.tools = config.filterTools(allTools)
    }

    String execute(String task) {
        OutputFormatter.printInfo("Launching ${AnsiColors.bold(config.name)} agent...")

        history.add(new Message("user", task))

        while (true) {
            turn++

            if (turn > config.maxTurns) {
                OutputFormatter.printWarning("Max turns (${config.maxTurns}) reached for ${config.name} agent")
                break
            }

            def response = sendRequest()

            if (response.finishReason == "tool_calls") {
                executeToolCalls(response.message.toolCalls)
            } else {
                // Task complete
                return response.message.content
            }
        }

        return history[-1].content
    }

    private ChatResponse sendRequest() {
        def req = new ChatRequest()
        req.model = config.model
        req.messages = history
        req.stream = false
        req.tools = tools.collect { tool ->
            [
                type: "function",
                function: [
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.parameters
                ]
            ]
        }

        def responseJson = client.sendMessage(req)
        return mapper.readValue(responseJson, ChatResponse.class)
    }

    private void executeToolCalls(List toolCalls) {
        toolCalls.each { toolCall ->
            def functionName = toolCall.function.name
            def arguments = toolCall.function.arguments
            def callId = toolCall.id

            Tool tool = tools.find { it.name == functionName }
            String result = ""

            if (tool) {
                try {
                    def args = mapper.readValue(arguments, Map.class)
                    result = tool.execute(args).toString()
                } catch (Exception e) {
                    result = "Error: ${e.message}"
                }
            } else {
                result = "Error: Tool '${functionName}' not available for ${config.name} agent"
            }

            def toolMsg = new Message()
            toolMsg.role = "tool"
            toolMsg.content = result
            toolMsg.toolCallId = callId
            history.add(toolMsg)
        }

        // Add assistant message with tool calls
        def assistantMsg = new Message()
        assistantMsg.role = "assistant"
        assistantMsg.content = null
        assistantMsg.toolCalls = toolCalls
        history.add(assistantMsg)
    }

    List<Message> getHistory() {
        return new ArrayList<>(history)
    }
}
```

### Phase 2: Subagent Spawning (Days 2-3)

#### 2.1 Create Subagent Pool

**File:** `core/SubagentPool.groovy`

```groovy
package core

import tools.Tool
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class SubagentPool {
    private final GlmClient client
    private final List<Tool> allTools
    private final ExecutorService executor
    private final AtomicInteger agentId = new AtomicInteger(0)

    SubagentPool(GlmClient client, List<Tool> allTools) {
        this.client = client
        this.allTools = allTools
        this.executor = Executors.newCachedThreadPool()
    }

    Subagent createAgent(AgentConfig config) {
        return new Subagent(config, client, allTools)
    }

    List<SubagentResult> spawnAgents(List<AgentTask> tasks) {
        List<Future<SubagentResult>> futures = []

        tasks.each { task ->
            def future = executor.submit({
                def agent = createAgent(task.config)
                def startTime = System.currentTimeMillis()

                try {
                    def result = agent.execute(task.prompt)
                    def duration = System.currentTimeMillis() - startTime

                    return new SubagentResult(
                        agentId: agentId.getAndIncrement(),
                        configName: task.config.name,
                        result: result,
                        history: agent.history,
                        duration: duration,
                        success: true
                    )
                } catch (Exception e) {
                    def duration = System.currentTimeMillis() - startTime
                    return new SubagentResult(
                        agentId: agentId.getAndIncrement(),
                        configName: task.config.name,
                        result: null,
                        history: agent.history,
                        duration: duration,
                        success: false,
                        error: e.message
                    )
                }
            } as Callable<SubagentResult>)

            futures.add(future)
        }

        // Wait for all agents to complete
        def results = futures.collect { it.get() }
        return results
    }

    void shutdown() {
        executor.shutdown()
        executor.awaitTermination(60, TimeUnit.SECONDS)
    }

    static class AgentTask {
        AgentConfig config
        String prompt

        AgentTask(AgentConfig config, String prompt) {
            this.config = config
            this.prompt = prompt
        }
    }

    @Canonical
    static class SubagentResult {
        int agentId
        String configName
        String result
        List<Message> history
        long duration
        boolean success
        String error
    }
}
```

#### 2.2 Create Task Tool

**File:** `tools/TaskTool.groovy`

```groovy
package tools

import core.SubagentPool
import core.AgentConfig
import tui.AnsiColors
import tui.OutputFormatter
import com.fasterxml.jackson.databind.ObjectMapper

class TaskTool implements Tool {
    private final SubagentPool pool

    TaskTool(SubagentPool pool) {
        this.pool = pool
    }

    @Override
    String getName() {
        return "task"
    }

    @Override
    String getDescription() {
        return "Launch a specialized subagent to handle a complex, multi-step task. " +
               "Use this when you need to delegate work that requires multiple tool calls " +
               "or independent exploration. Specify the agent type: 'explore' for " +
               "codebase exploration, 'plan' for read-only analysis, or 'build' for " +
               "full development work."
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                agent_type: [
                    type: "string",
                    enum: ["explore", "plan", "build", "general"],
                    description: "Type of subagent to launch"
                ],
                task: [
                    type: "string",
                    description: "Detailed task description for the subagent"
                ],
                max_turns: [
                    type: "integer",
                    description: "Maximum number of turns for this subagent (default depends on agent type)"
                ]
            ],
            required: ["agent_type", "task"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String agentType = args.get("agent_type")
            String task = args.get("task")

            if (!agentType || !task) {
                return "Error: agent_type and task are required"
            }

            // Get base config
            AgentConfig config = switch(agentType) {
                case "explore" -> AgentConfig.explore()
                case "plan" -> AgentConfig.plan()
                case "build" -> AgentConfig.build()
                case "general" -> AgentConfig.general()
                default -> {
                    OutputFormatter.printWarning("Unknown agent type: ${agentType}, using 'general'")
                    yield AgentConfig.general()
                }
            }

            // Override max turns if specified
            if (args.containsKey("max_turns")) {
                config.maxTurns = ((Number) args.get("max_turns")).intValue()
            }

            OutputFormatter.printSection("Subagent: ${AnsiColors.bold(agentType)}")
            OutputFormatter.printInfo(task)

            // Execute subagent
            def agent = pool.createAgent(config)
            def result = agent.execute(task)

            OutputFormatter.printSuccess("Subagent completed in ${agent.turn} turns")

            return """
**${agentType.toUpperCase()} AGENT RESULT:**

${result}
""".trim()

        } catch (Exception e) {
            return "Error executing subagent: ${e.message}"
        }
    }
}
```

### Phase 3: Integration (Days 3-4)

#### 3.1 Update Agent.groovy

Add subagent pool to main Agent class:

```groovy
// In Agent class
private final SubagentPool subagentPool

Agent(String apiKey, String model) {
    this.client = new GlmClient(apiKey)
    this.model = model
    this.config = Config.load()
    this.subagentPool = new SubagentPool(client, tools)
    AnsiColors.install()

    // Register task tool
    registerTool(new TaskTool(subagentPool))
}

// Add shutdown hook
void shutdown() {
    subagentPool?.shutdown()
}
```

#### 3.2 Update AgentCommand.groovy

```groovy
// In AgentCommand class
@Option(names = ["--parallel-agents", "-p"], description = "Launch multiple agents in parallel (experimental)")
boolean parallelAgents = false

// Modify agent cleanup
agent.shutdown()
```

### Phase 4: Agent Prompts (Days 4-5)

#### 4.1 Create Agent Prompts

**File:** `prompts/explore.txt`

```
You are a file search specialist. You excel at thoroughly navigating and exploring codebases.

Your strengths:
- Rapidly finding files using glob patterns
- Searching code and text with powerful regex patterns
- Reading and analyzing file contents

Guidelines:
- Use Glob for broad file pattern matching
- Use Grep for searching file contents with regex
- Use Read when you know the specific file path you need to read
- Adapt your search approach based on the thoroughness level specified by the caller
- Return file paths as absolute paths in your final response
- Do not create any files or run commands that modify the user's system

Complete the user's search request efficiently and report your findings clearly.
```

**File:** `prompts/plan.txt`

```
You are a code architecture and planning specialist. Your role is to analyze codebases and create detailed implementation plans.

Your strengths:
- Understanding code architecture and patterns
- Identifying dependencies and relationships
- Creating clear, actionable implementation plans
- Considering edge cases and potential issues

Guidelines:
- Read relevant files to understand the current implementation
- Identify the key components and their relationships
- Consider testing, documentation, and migration paths
- Create a step-by-step implementation plan
- Focus on clarity and completeness

When asked to create a plan:
1. Analyze the request thoroughly
2. Explore the relevant parts of the codebase
3. Understand existing patterns and conventions
4. Create a detailed implementation plan
5. Include: Overview, Approach, Key Files, Step-by-Step Implementation, Testing Strategy, Risks
```

#### 4.2 Load Prompts in AgentConfig

```groovy
// In AgentConfig class
String loadPrompt() {
    def promptFile = new File("prompts/${type.name().toLowerCase()}.txt")
    if (promptFile.exists()) {
        return promptFile.text
    }
    return description
}
```

### Phase 5: Testing (Days 6-7)

#### 5.1 Unit Tests

**File:** `tests/SubagentTest.groovy`

```groovy
import core.Subagent
import core.AgentConfig
import tools.Tool
import core.GlmClient

class SubagentTest {
    void testExploreAgent() {
        def config = AgentConfig.explore()
        def tools = [new MockReadTool(), new MockGlobTool(), new MockGrepTool()]
        def subagent = new Subagent(config, new MockGlmClient(), tools)

        def result = subagent.execute("Find all controller files")

        assert result.contains("controllers")
        assert subagent.turn > 0
    }

    void testPlanAgent() {
        def config = AgentConfig.plan()
        def tools = [new MockReadTool(), new MockGlobTool(), new MockGrepTool()]
        def subagent = new Subagent(config, new MockGlmClient(), tools)

        def result = subagent.execute("Analyze the authentication system")

        assert result.contains("authentication")
        assert result.contains("plan") || result.contains("implementation")
    }

    void testToolFiltering() {
        def config = AgentConfig.explore()
        def allTools = [new MockReadTool(), new MockWriteTool(), new MockGlobTool()]
        def filtered = config.filterTools(allTools)

        assert filtered.size() == 2  // read and glob only
        assert filtered.every { !(it.name in ["write_file"]) }
    }
}
```

#### 5.2 Integration Tests

**File:** `tests/SubagentIntegrationTest.groovy`

```groovy
import core.Agent
import core.Config
import tools.TaskTool

class SubagentIntegrationTest {
    void testParallelExploration() {
        def agent = new Agent("test-key", "glm-4")

        agent.run("Use the explore agent to find all Groovy files in the project")

        // Verify task tool was called
        assert agent.toolCallHistory.any { it.toolName == "task" }
    }

    void testAgentHandoff() {
        def agent = new Agent("test-key", "glm-4")

        agent.run("Launch a plan agent to analyze the current architecture, then report back")

        // Verify agent completed successfully
        assert agent.step > 1
    }
}
```

#### 5.3 Manual Testing

```bash
# Test explore agent
./glm.groovy agent "Use the task tool with agent_type='explore' to find all files matching '**/*.groovy'"

# Test plan agent
./glm.groovy agent "Launch a plan agent to analyze the tool system architecture"

# Test parallel agents
./glm.groovy agent "Use the explore agent to find controller files, and another to find service files. Synthesize the results."

# Test max turns
./glm.groovy agent "Use the explore agent to find files, set max_turns to 5"
```

---

## Configuration

Add to `~/.glm/config.toml`:

```toml
[subagents]
enabled = true
max_concurrent = 3
default_max_turns = 15

[subagents.explore]
max_turns = 15
model = "glm-4"

[subagents.plan]
max_turns = 30
model = "glm-4"

[subagents.build]
max_turns = 50
model = "glm-4"

[subagents.general]
max_turns = 20
model = "glm-4"
```

---

## Success Criteria

- [ ] Subagents can be spawned with different configurations
- [ ] Tool filtering works correctly per agent type
- [ ] Subagents execute independently with their own context
- [ ] Parallel execution works correctly
- [ ] Results are returned and integrated into main agent
- [ ] Agent prompts improve task performance
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Documentation updated

---

## Dependencies

- None (can start immediately)

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| API rate limiting | Medium | Implement request queuing and retry logic |
| Memory usage with many subagents | Medium | Limit concurrent agents, use thread pool |
| Context loss between agents | Low | Summarize agent results before passing to next |
| Complex debugging | Medium | Add detailed logging for agent execution |

---

## References

- OpenCode agent implementation: `/home/kevintan/opencode/packages/opencode/src/agent/`
- OpenCode explore agent prompt: `packages/opencode/src/agent/prompt/explore.txt`
- Subagent patterns in Claude Code and Amp

---

**Document Version:** 1.0
**Created:** 2025-01-02
**Priority:** High
