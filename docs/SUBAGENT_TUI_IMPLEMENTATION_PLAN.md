# Subagent Support with TUI Integration - Implementation Plan

## Overview

This plan implements OpenCode-style subagent capabilities in glm-cli-jbang, including parallel subagent execution and full TUI integration for visualizing subagent activity.

## Table of Contents

- [Part 1: Core Subagent Implementation](#part-1-core-subagent-implementation)
- [Part 2: TUI Representation](#part-2-tui-representation)
- [Part 3: CLI Output Enhancements](#part-3-cli-output-enhancements)
- [Implementation Order](#implementation-order)
- [Visual Examples](#visual-examples)

---

## Part 1: Core Subagent Implementation

### Phase 1: Enhanced TaskTool for Parallel Subagents

**File:** `tools/TaskTool.groovy`

**Changes:**

1. Add `parallel_tasks` parameter to support launching multiple subagents simultaneously
2. Leverage existing `SubagentPool.spawnAgents()` for parallel execution
3. Return structured output format compatible with LLM consumption

**Implementation:**

```groovy
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
            ],
            parallel_tasks: [
                type: "array",
                items: [
                    type: "object",
                    properties: [
                        agent_type: [
                            type: "string",
                            enum: ["explore", "plan", "build", "general"]
                        ],
                        task: [
                            type: "string"
                        ],
                        max_turns: [
                            type: "integer"
                        ]
                    ],
                    required: ["agent_type", "task"]
                ],
                description: "Launch multiple subagents in parallel"
            ]
        ],
        required: []  // Either agent_type+task OR parallel_tasks
    ]
}

@Override
Object execute(Map<String, Object> args) {
    try {
        // Handle parallel tasks
        if (args.containsKey("parallel_tasks")) {
            List taskDefs = args.get("parallel_tasks")
            return executeParallelTasks(taskDefs)
        }

        // Handle single task (existing behavior)
        String agentType = args.get("agent_type")
        String task = args.get("task")

        if (!agentType || !task) {
            return "Error: agent_type and task are required (or use parallel_tasks)"
        }

        AgentConfig config = AgentConfig.forName(agentType)

        if (args.containsKey("max_turns")) {
            config.maxTurns = ((Number) args.get("max_turns")).intValue()
        }

        OutputFormatter.printSection("Subagent: ${AnsiColors.bold(agentType.toUpperCase())}")
        OutputFormatter.printInfo(task)

        def agent = pool.createAgent(config)
        def result = agent.execute(task)

        OutputFormatter.printSuccess("Subagent completed in ${agent.turn} turns")

        return """**${agentType.toUpperCase()} AGENT RESULT:**

${result}
""".trim()

    } catch (Exception e) {
        return "Error executing subagent: ${e.message}"
    }
}

private Object executeParallelTasks(List taskDefs) {
    OutputFormatter.printSection("Parallel Subagents")

    List<SubagentPool.AgentTask> tasks = []
    taskDefs.eachWithIndex { taskDef, idx ->
        String agentType = taskDef.agent_type
        String task = taskDef.task
        int maxTurns = taskDef.max_turns ?: -1

        AgentConfig config = AgentConfig.forName(agentType)
        if (maxTurns > 0) {
            config.maxTurns = maxTurns
        }

        tasks.add(new SubagentPool.AgentTask(config, task))
        OutputFormatter.printInfo("${idx + 1}. ${AnsiColors.bold(agentType.toUpperCase())}: ${task.take(60)}...")
    }

    println()

    def results = pool.spawnAgents(tasks)

    OutputFormatter.printSeparator()
    results.eachWithIndex { result, idx ->
        String icon = result.success ? "✓" : "✗"
        String type = tasks[idx].config.name.toUpperCase()
        println("${icon} ${AnsiColors.bold(type)} agent (${result.duration}ms)")
        if (result.result) {
            println("  ${result.result.take(150)}...")
        }
        if (result.error) {
            println("  ${AnsiColors.red('Error:')} ${result.error}")
        }
        println()
    }

    OutputFormatter.printSuccess("All ${results.size()} subagents completed")

    return SubagentResultSynthesizer.formatForLLM(results)
}
```

**Dependencies:**
- Phase 2: SubagentResultSynthesizer
- Existing: SubagentPool, AgentConfig

---

### Phase 2: Result Synthesis Utilities

**New File:** `core/SubagentResultSynthesizer.groovy`

**Purpose:**
- Format subagent results for LLM consumption
- Extract useful metadata (files, tool usage)
- Provide structured summary for CLI and TUI

**Implementation:**

```groovy
package core

class SubagentResultSynthesizer {

    /**
     * Format subagent results for LLM consumption
     */
    static String formatForLLM(List<SubagentPool.SubagentResult> results) {
        StringBuilder output = new StringBuilder()
        output.append("## Subagent Results Summary\n\n")

        results.eachWithIndex { result, idx ->
            String statusIcon = result.success ? "✓" : "✗"
            output.append("### Agent ${idx + 1} (${result.configName})\n")
            output.append("- Status: ${statusIcon} ${result.success ? 'Success' : 'Failed'}\n")
            output.append("- Duration: ${result.duration}ms\n")
            output.append("- Turns: ${result.history?.size() / 2 ?: 'N/A'}\n")

            if (result.result) {
                output.append("- Result:\n${result.result}\n")
            }

            if (result.error) {
                output.append("- Error: ${result.error}\n")
            }

            output.append("\n")
        }

        // Add combined findings
        def files = extractFiles(results)
        if (files) {
            output.append("### Files Referenced\n")
            files.each { file ->
                output.append("- `${file}`\n")
            }
            output.append("\n")
        }

        def toolUsage = extractToolUsage(results)
        if (toolUsage) {
            output.append("### Tool Usage Summary\n")
            toolUsage.each { tool, count ->
                output.append("- ${tool}: ${count} calls\n")
            }
        }

        return output.toString()
    }

    /**
     * Extract all file references from subagent results
     */
    static List<String> extractFiles(List<SubagentPool.SubagentResult> results) {
        Set<String> files = []
        results.each { result ->
            if (result.result) {
                def matches = result.result =~ /`[^`]+\.\w+`/
                matches.each { match ->
                    def filePath = match[0].replaceAll(/`/, '')
                    if (filePath.contains('.')) {
                        files.add(filePath)
                    }
                }
            }
        }
        return files.toList().sort()
    }

    /**
     * Extract tool usage statistics
     */
    static Map<String, Integer> extractToolUsage(List<SubagentPool.SubagentResult> results) {
        Map<String, Integer> usage = [:]
        results.each { result ->
            if (result.history) {
                result.history.each { msg ->
                    if (msg.toolCalls) {
                        msg.toolCalls.each { tc ->
                            String toolName = tc.function.name
                            usage[toolName] = (usage[toolName] ?: 0) + 1
                        }
                    }
                }
            }
        }
        return usage
    }

    /**
     * Get summary statistics for display
     */
    static Map<String, Object> getSummaryStats(List<SubagentPool.SubagentResult> results) {
        return [
            total: results.size(),
            success: results.count { it.success },
            failed: results.count { !it.success },
            totalDuration: results.sum { it.duration },
            avgDuration: results.empty ? 0 : results.sum { it.duration } / results.size(),
            totalTurns: results.sum { r -> r.history ? r.history.size() / 2 : 0 }
        ]
    }
}
```

**Dependencies:** None
**Used by:**
- Phase 1: TaskTool
- Phase 3: Structured SubagentOutput

---

### Phase 3: Structured Subagent Output

**File:** `core/Subagent.groovy`

**Changes:**

1. Add `sessionId` field for tracking
2. Track tool execution per tool type
3. Return `SubagentOutput` object instead of plain string
4. Track execution duration

**Implementation:**

```groovy
// Add to Subagent.groovy

@Canonical
class SubagentOutput {
    String sessionId
    String agentType
    String task
    int turns
    String content
    Map<String, Integer> toolUsage
    List<ToolExecution> toolExecutions
    long duration
    boolean success
}

@Canonical
class ToolExecution {
    String toolName
    String arguments
    boolean success
    long duration
    String result
}

// Modify Subagent class
class Subagent {
    private final AgentConfig config
    private final GlmClient client
    private final List<Tool> tools
    private final List<Message> history = []
    private final ObjectMapper mapper = new ObjectMapper()
    private int turn = 0
    private String sessionId
    private Map<String, Integer> toolUsage = [:]
    private List<ToolExecution> toolExecutions = []

    Subagent(AgentConfig config, GlmClient client, List<Tool> allTools) {
        this.config = config
        this.client = client
        this.tools = config.filterTools(allTools)
        this.sessionId = UUID.randomUUID().toString()

        def prompt = config.loadPrompt()
        if (prompt && !prompt.isEmpty()) {
            history.add(new Message("system", prompt))
        }
    }

    SubagentOutput execute(String task) {
        OutputFormatter.printInfo("Launching ${AnsiColors.bold(config.name)} agent...")
        long startTime = System.currentTimeMillis()

        history.add(new Message("user", task))

        while (true) {
            turn++

            if (turn > config.maxTurns) {
                OutputFormatter.printWarning("Max turns (${config.maxTurns}) reached for ${config.name} agent")
                break
            }

            def response = sendRequest()

            if (response.choices[0].finishReason == "tool_calls") {
                executeToolCalls(response.choices[0].message.toolCalls)
            } else {
                def content = response.choices[0].message.content
                if (content) {
                    history.add(new Message("assistant", content))
                }
                long duration = System.currentTimeMillis() - startTime
                return new SubagentOutput(
                    sessionId: sessionId,
                    agentType: config.name,
                    task: task,
                    turns: turn,
                    content: content ?: "No response from agent",
                    toolUsage: toolUsage,
                    toolExecutions: toolExecutions,
                    duration: duration,
                    success: true
                )
            }
        }

        long duration = System.currentTimeMillis() - startTime
        return new SubagentOutput(
            sessionId: sessionId,
            agentType: config.name,
            task: task,
            turns: turn,
            content: history[-1]?.content ?: "No response from agent",
            toolUsage: toolUsage,
            toolExecutions: toolExecutions,
            duration: duration,
            success: false
        )
    }

    private void executeToolCalls(List toolCalls) {
        toolCalls.each { toolCall ->
            String functionName = toolCall.function.name
            String arguments = toolCall.function.arguments
            String callId = toolCall.id
            long toolStart = System.currentTimeMillis()

            Tool tool = tools.find { it.name == functionName }
            String result = ""
            boolean toolSuccess = false

            if (tool) {
                try {
                    def args = mapper.readValue(arguments, Map.class)
                    result = tool.execute(args).toString()
                    toolSuccess = true
                } catch (Exception e) {
                    result = "Error: ${e.message}"
                    toolSuccess = false
                }
            } else {
                result = "Error: Tool '${functionName}' not available for ${config.name} agent"
                toolSuccess = false
            }

            long toolDuration = System.currentTimeMillis() - toolStart

            // Track tool usage
            toolUsage[functionName] = (toolUsage[functionName] ?: 0) + 1
            toolExecutions.add(new ToolExecution(
                toolName: functionName,
                arguments: arguments,
                success: toolSuccess,
                duration: toolDuration,
                result: result
            ))

            def toolMsg = new Message()
            toolMsg.role = "tool"
            toolMsg.content = result
            toolMsg.toolCallId = callId
            history.add(toolMsg)
        }

        def assistantMsg = new Message()
        assistantMsg.role = "assistant"
        assistantMsg.content = null
        assistantMsg.toolCalls = toolCalls
        history.add(assistantMsg)
    }
}
```

**Dependencies:** None
**Impact:** Breaks compatibility with existing code expecting `execute()` to return `String`. Update `TaskTool` and `SubagentPool`.

---

### Phase 4: Subagent Permission Restrictions

**File:** `core/AgentConfig.groovy`

**Changes:**

1. Add `task` tool to denied tools for explore and general agents
2. This prevents nested subagents (subagents launching their own subagents)

**Implementation:**

```groovy
static AgentConfig explore() {
    new AgentConfig(
        name: "explore",
        type: AgentType.EXPLORE,
        description: "Fast agent specialized for exploring codebases. Use glob and grep to find files and code patterns.",
        deniedTools: ["write_file", "edit_file", "todo_write", "todo_read", "task"],  // Added "task"
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
        deniedTools: ["todo_write", "todo_read", "task"],  // Added "task"
        maxTurns: 20,
        hidden: true
    )
}
```

**Dependencies:** None
**Impact:** Prevents nested subagent calls, improving safety

---

### Phase 5: Configuration Options

**File:** `core/Config.groovy`

**Add:**

```groovy
// Add to Config class
static class SubagentConfig {
    int maxConcurrentSubagents = 5
    int subagentTimeout = 300  // seconds
    boolean enableParallelSubagents = true
    boolean showSubagentProgress = true
}

// In main Config class
SubagentConfig subagent = new SubagentConfig()
```

**Configuration file format (`~/.glm/config.toml`):**

```toml
[subagent]
max_concurrent_subagents = 5
subagent_timeout = 300
enable_parallel_subagents = true
show_subagent_progress = true
```

**Dependencies:** None
**Used by:** Phase 1 (TaskTool), Phase 11 (SubagentSessionManager)

---

### Phase 6: SubagentSessionManager

**New File:** `core/SubagentSessionManager.groovy`

**Purpose:**
- Track active subagent sessions
- Provide real-time status updates for TUI
- Manage session lifecycle

**Implementation:**

```groovy
package core

import java.util.concurrent.ConcurrentHashMap

class SubagentSessionManager {
    private static final SubagentSessionManager INSTANCE = new SubagentSessionManager()

    static SubagentSessionManager getInstance() { return INSTANCE }

    private ConcurrentHashMap<String, SubagentSession> activeSessions = [:]

    synchronized String registerSession(SubagentOutput output) {
        SubagentSession session = new SubagentSession(
            sessionId: output.sessionId,
            agentType: output.agentType,
            task: output.task,
            status: "pending",
            startTime: System.currentTimeMillis()
        )
        activeSessions[output.sessionId] = session
        return output.sessionId
    }

    synchronized void updateSession(String sessionId, String status) {
        if (activeSessions.containsKey(sessionId)) {
            activeSessions[sessionId].status = status
            activeSessions[sessionId].lastUpdate = System.currentTimeMillis()
        }
    }

    synchronized void completeSession(String sessionId, SubagentOutput output) {
        if (activeSessions.containsKey(sessionId)) {
            activeSessions[sessionId].status = "completed"
            activeSessions[sessionId].completedAt = System.currentTimeMillis()
            activeSessions[sessionId].turns = output.turns
            activeSessions[sessionId].success = output.success
            activeSessions[sessionId].result = output.content
        }
    }

    synchronized SubagentSession getSession(String sessionId) {
        return activeSessions[sessionId]
    }

    synchronized List<SubagentSession> getActiveSessions() {
        return activeSessions.values().findAll { it.status in ["pending", "running"] }
    }

    synchronized int getActiveCount() {
        return activeSessions.values().count { it.status in ["pending", "running"] }
    }

    synchronized List<SubagentSession> getAllSessions() {
        return new ArrayList<>(activeSessions.values())
    }

    synchronized void clearCompleted(int olderThanSeconds = 300) {
        long cutoff = System.currentTimeMillis() - (olderThanSeconds * 1000)
        activeSessions = activeSessions.findAll {
            it.value.status == "running" ||
            it.value.lastUpdate > cutoff
        }
    }

    @Canonical
    static class SubagentSession {
        String sessionId
        String agentType
        String task
        String status = "pending"  // pending, running, completed, error
        long startTime
        long completedAt = 0
        long lastUpdate
        int turns = 0
        boolean success = false
        String result = null
    }
}
```

**Dependencies:**
- Phase 3: SubagentOutput
- Used by: Phase 12 (TUI integration)

---

## Part 2: TUI Representation

### Phase 7: Sidebar SubagentSection Widget

**New File:** `tui/lanterna/sidebar/SubagentSection.groovy`

**Purpose:**
- Display active subagent status in sidebar
- Show progress for parallel subagent execution
- Provide collapsible view of subagent details

**Implementation:**

```groovy
package tui.lanterna.sidebar

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*
import tui.LanternaTheme
import core.SubagentSessionManager

class SubagentSection extends CollapsibleSection {

    private Label statusLabel
    private ProgressBar progressBar
    private Map<String, AgentStatusLabel> agentLabels = [:]
    private SubagentSessionManager sessionManager

    SubagentSection() {
        super("Subagents")
        setExpanded(true)
        this.sessionManager = SubagentSessionManager.getInstance()
        buildContent()
    }

    private void buildContent() {
        def panel = getContentPanel()
        panel.removeAllComponents()

        // Status line
        statusLabel = new Label("  No active subagents")
        statusLabel.setForegroundColor(TextColor.ANSI.GREEN)
        panel.addComponent(statusLabel)

        // Progress bar placeholder
        progressBar = new ProgressBar()
        panel.addComponent(progressBar)

        panel.addComponent(new Label(""))
    }

    void refresh() {
        def sessions = sessionManager.getAllSessions()

        if (sessions.isEmpty()) {
            statusLabel.setText("  No active subagents")
            progressBar.setVisible(false)
            statusLabel.setForegroundColor(TextColor.ANSI.GREEN)

            // Clear agent labels
            agentLabels.values().each { getPanel().removeComponent(it.label) }
            agentLabels.clear()
        } else {
            def active = sessions.findAll { it.status in ["pending", "running"] }
            def completed = sessions.findAll { it.status == "completed" }
            int total = sessions.size()

            statusLabel.setText("  ${active.size()} active / ${total} total")
            progressBar.setVisible(true)

            // Update progress bar
            double progress = total > 0 ? completed.size() / total : 0
            progressBar.setProgress(progress)

            statusLabel.setForegroundColor(
                active.size() > 0 ? TextColor.ANSI.YELLOW : TextColor.ANSI.GREEN
            )

            // Update or create agent labels
            sessions.each { session ->
                def agentLabel = agentLabels[session.sessionId]
                if (!agentLabel) {
                    agentLabel = new AgentStatusLabel(session.sessionId)
                    getPanel().addComponent(agentLabel.label)
                    agentLabels[session.sessionId] = agentLabel
                }
                agentLabel.update(session)
            }

            // Remove completed/old labels
            def sessionIds = sessions*.sessionId
            agentLabels.keySet().findAll { !(it in sessionIds) }.each { sessionId ->
                def agentLabel = agentLabels[sessionId]
                getPanel().removeComponent(agentLabel.label)
                agentLabels.remove(sessionId)
            }
        }

        invalidate()
    }

    void clear() {
        sessionManager.clearCompleted()
        refresh()
    }

    static class AgentStatusLabel {
        String sessionId
        Panel panel
        Label iconLabel
        Label nameLabel

        AgentStatusLabel(String sessionId) {
            this.sessionId = sessionId
            panel = new Panel()
            panel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

            iconLabel = new Label("⠋")
            iconLabel.setForegroundColor(TextColor.ANSI.YELLOW)
            panel.addComponent(iconLabel)

            nameLabel = new Label(" Agent")
            nameLabel.setForegroundColor(TextColor.ANSI.WHITE)
            panel.addComponent(nameLabel)
        }

        void update(SubagentSessionManager.SubagentSession session) {
            String icon = getStatusIcon(session.status)
            String status = session.status.capitalize()

            iconLabel.setText(icon)
            iconLabel.setForegroundColor(getStatusColor(session.status))

            nameLabel.setText(" ${session.agentType.toUpperCase()} (${status})")
        }

        private String getStatusIcon(String status) {
            switch (status) {
                case "pending": return "⏳"
                case "running": return "⠋"
                case "completed": return "✓"
                case "error": return "✗"
                default: return "•"
            }
        }

        private TextColor getStatusColor(String status) {
            switch (status) {
                case "pending": return TextColor.ANSI.YELLOW
                case "running": return TextColor.ANSI.YELLOW
                case "completed": return TextColor.ANSI.GREEN
                case "error": return TextColor.ANSI.RED
                default: return TextColor.ANSI.WHITE
            }
        }

        Panel getLabel() { return panel }
    }

    class ProgressBar extends Panel {
        private double progress = 0.0

        ProgressBar() {
            setPreferredSize(new com.googlecode.lanterna.TerminalSize(30, 1))
        }

        void setProgress(double value) {
            this.progress = Math.max(0, Math.min(1, value))
            invalidate()
        }

        @Override
        void drawSelf(com.googlecode.lanterna.graphics.TextGraphics graphics) {
            int width = getSize().getColumns()
            int filled = (int) (width * progress)

            String bar = "█" * filled + "░" * (width - filled)
            graphics.setForegroundColor(new TextColor.RGB(76, 175, 80))
            graphics.putString(0, 0, bar)
        }
    }
}
```

**Dependencies:**
- Phase 6: SubagentSessionManager
- Existing: CollapsibleSection, LanternaTheme

---

### Phase 8: SidebarPanel Integration

**File:** `tui/lanterna/widgets/SidebarPanel.groovy`

**Changes:**

1. Add SubagentSection to sidebar
2. Add refresh cycle for subagent status
3. Provide access methods for TUI

**Implementation:**

```groovy
// Add field
private SubagentSection subagentSection

// In constructor
subagentSection = new SubagentSection()

// In buildUI() - add to contentPanel
contentPanel.addComponent(sessionInfoSection)
contentPanel.addComponent(tokenSection)
contentPanel.addComponent(lspSection)
contentPanel.addComponent(modifiedFilesSection)
contentPanel.addComponent(subagentSection)  // Add here

// In refresh()
void refresh() {
    sessionInfoSection.refresh()
    tokenSection.refresh()

    LspManager.instance.updateDiagnosticCounts(sessionId)
    lspSection.refresh()

    modifiedFilesSection.refresh()

    // Add subagent section refresh
    subagentSection.refresh()

    updateScrollIndicator()
}

// Add public method
SubagentSection getSubagentSection() {
    return subagentSection
}
```

**Dependencies:**
- Phase 7: SubagentSection
- Existing: SidebarPanel structure

---

### Phase 9: ActivityLogPanel Subagent Display

**File:** `tui/lanterna/widgets/ActivityLogPanel.groovy`

**Add methods:**

```groovy
// Add these new methods for subagent display

void appendSubagentStart(String agentType, String task) {
    synchronized (content) {
        content.append("  🤖 Launching ${AnsiColors.cyan(agentType.toUpperCase())} agent...\n")
        content.append("     Task: ${task}\n")
    }
    updateDisplay()
}

void appendParallelAgentStart(List<Map<String, String>> tasks) {
    synchronized (content) {
        content.append("  🤖 Launching ${tasks.size()} parallel agents...\n")
        tasks.eachWithIndex { task, idx ->
            content.append("     ${idx + 1}. ${AnsiColors.cyan(task.agent_type?.toUpperCase())}\n")
        }
    }
    updateDisplay()
}

void appendSubagentProgress(String agentId, String status) {
    synchronized (content) {
        content.append("     • ${agentId.take(12)}: ${status}\n")
    }
    updateDisplay()
}

void appendSubagentResult(String agentType, String result, boolean success,
                         int turns, long duration, String sessionId) {
    synchronized (content) {
        String icon = success ? "✓" : "✗"
        content.append("  ${icon} ${AnsiColors.cyan(agentType.toUpperCase())} agent complete\n")
        content.append("     Turns: ${turns} | Duration: ${duration}ms\n")
        content.append("     Session: ${sessionId.take(12)}...\n")
        content.append("     Result: ${result.take(150)}...\n")
        content.append("\n")
    }
    updateDisplay()
}

void appendSubagentSummary(List results) {
    synchronized (content) {
        content.append("╔═════════════════════════════════════════════════════════╗\n")
        content.append("║             Subagent Execution Summary                    ║\n")
        content.append("╚═════════════════════════════════════════════════════════╝\n")
        content.append("\n")

        results.eachWithIndex { result, idx ->
            String icon = result.success ? "✓" : "✗"
            String color = result.success ? "green" : "red"
            content.append("Agent ${idx + 1} (${AnsiColors.cyan(result.configName)}):\n")
            content.append("  Status: ${AnsiColors[color](icon)} ${result.success ? 'Success' : 'Failed'}\n")
            content.append("  Duration: ${result.duration}ms\n")

            if (result.history) {
                int turns = result.history.size() / 2
                content.append("  Turns: ${turns}\n")
            }

            if (result.result) {
                content.append("  Result: ${result.result.take(100)}...\n")
            }

            if (result.error) {
                content.append("  Error: ${result.error}\n")
            }

            content.append("\n")
        }
    }
    updateDisplay()
}
```

**Dependencies:** None (uses existing infrastructure)

---

### Phase 10: HeaderPanel Subagent Status

**File:** `tui/lanterna/widgets/HeaderPanel.groovy`

**Add:**

```groovy
// Add field
private Label subagentStatusLabel

// In constructor
subagentStatusLabel = new Label('')
subagentStatusLabel.setForegroundColor(TextColor.ANSI.YELLOW)
rightPanel.addComponent(new Label('  '))
rightPanel.addComponent(subagentStatusLabel)

// Add method
void updateSubagentStatus(int running, int completed, int total) {
    if (total > 0) {
        String text
        if (running > 0) {
            text = "🤖 ${running}/${total} running"
        } else if (completed == total) {
            text = "✓ ${total} complete"
        } else {
            text = "🤖 ${completed}/${total} complete"
        }
        subagentStatusLabel.setText(text)
    } else {
        subagentStatusLabel.setText('')
    }
}

// Update existing update method signature
void update(int inputTokens, int outputTokens, int percentage, BigDecimal cost,
           int lspCount = 0, int lspErrors = 0, int subagentRunning = 0,
           int subagentCompleted = 0, int subagentTotal = 0) {
    updateContext(inputTokens, outputTokens, percentage, cost)
    updateLspStatus(lspCount, lspErrors)
    updateSubagentStatus(subagentRunning, subagentCompleted, subagentTotal)
}
```

**Dependencies:** None

---

### Phase 11: LanternaTUI Integration

**File:** `tui/LanternaTUI.groovy`

**Add:**

```groovy
// Add fields
private SubagentSessionManager subagentManager = SubagentSessionManager.instance
private Timer subagentStatusTimer

// In initialization (after TUI setup)
private void initSubagentTracking() {
    subagentStatusTimer = new Timer()
    subagentStatusTimer.scheduleAtFixedRate({
        updateSubagentUI()
    } as java.util.TimerTask, 0, 500)  // Update every 500ms
}

private void updateSubagentUI() {
    if (textGUI?.getGUIThread() != null) {
        textGUI.getGUIThread().invokeLater {
            def activeSessions = subagentManager.getActiveSessions()
            int running = activeSessions.count { it.status == "running" }
            int completed = subagentManager.getAllSessions().count { it.status == "completed" }
            int total = subagentManager.getAllSessions().size()

            // Update header
            headerPanel?.updateSubagentStatus(running, completed, total)

            // Update sidebar
            sidebarPanel?.subagentSection?.refresh()
        }
    }
}

// In shutdown method
void shutdown() {
    // ... existing code ...

    subagentStatusTimer?.cancel()
    subagentStatusTimer = null
}

// Expose method for TaskTool to use
void updateSubagentDisplay() {
    updateSubagentUI()
}
```

**Dependencies:**
- Phase 6: SubagentSessionManager
- Phase 7-10: TUI widgets

---

## Part 3: CLI Output Enhancements

### Phase 12: TaskTool CLI Output

**File:** `tools/TaskTool.groovy`

**Enhance executeParallelTasks() method:**

```groovy
private Object executeParallelTasks(List taskDefs) {
    OutputFormatter.printSection("Parallel Subagents")

    List<SubagentPool.AgentTask> tasks = []
    taskDefs.eachWithIndex { taskDef, idx ->
        String agentType = taskDef.agent_type
        String task = taskDef.task
        int maxTurns = taskDef.max_turns ?: -1

        AgentConfig config = AgentConfig.forName(agentType)
        if (maxTurns > 0) {
            config.maxTurns = maxTurns
        }

        tasks.add(new SubagentPool.AgentTask(config, task))

        String truncatedTask = task.length() > 60 ? task.take(60) + "..." : task
        println("  ${idx + 1}. ${AnsiColors.bold(agentType.toUpperCase())}: ${AnsiColors.cyan(truncatedTask)}")
    }

    println()
    OutputFormatter.printInfo("Executing ${tasks.size()} agents in parallel...")
    println()

    def results = pool.spawnAgents(tasks)

    OutputFormatter.printSeparator()

    // Display results with summary
    results.eachWithIndex { result, idx ->
        String icon = result.success ? "✓" : "✗"
        String type = tasks[idx].config.name.toUpperCase()
        String color = result.success ? "green" : "red"

        println("${AnsiColors[color](icon)} ${AnsiColors.bold(type)} agent (${result.duration}ms)")

        if (result.history) {
            int turns = result.history.size() / 2
            println("   Turns: ${turns}")
        }

        if (result.result) {
            String truncated = result.result.length() > 150 ? result.result.take(150) + "..." : result.result
            println("   ${AnsiColors.dim(truncated)}")
        }

        if (result.error) {
            println("   ${AnsiColors.red('Error:')} ${result.error}")
        }

        println()
    }

    // Summary statistics
    def stats = SubagentResultSynthesizer.getSummaryStats(results)
    OutputFormatter.printSeparator()
    println("Summary:")
    println("  Total: ${stats.total} agents")
    println("  ${AnsiColors.green('Success:')} ${stats.success}")
    println("  ${AnsiColors.red('Failed:')} ${stats.failed}")
    println("  Total duration: ${stats.totalDuration}ms")
    println("  Avg duration: ${String.format('%.0f', stats.avgDuration)}ms")
    println("  Total turns: ${stats.totalTurns}")
    println()

    OutputFormatter.printSuccess("All subagents completed")

    return SubagentResultSynthesizer.formatForLLM(results)
}
```

**Dependencies:**
- Phase 2: SubagentResultSynthesizer

---

## Implementation Order

### Priority 1 - Core Functionality (Week 1)

| Phase | File | Description | Dependencies |
|-------|------|-------------|---------------|
| 1 | `tools/TaskTool.groovy` | Enhanced TaskTool with parallel_tasks parameter | - |
| 2 | `core/SubagentResultSynthesizer.groovy` | New file - result synthesis utilities | Phase 1 |
| 4 | `core/AgentConfig.groovy` | Add task to denied tools | - |
| 6 | `core/SubagentSessionManager.groovy` | New file - session tracking | Phase 3 |

### Priority 2 - TUI Integration (Week 2)

| Phase | File | Description | Dependencies |
|-------|------|-------------|---------------|
| 7 | `tui/lanterna/sidebar/SubagentSection.groovy` | New file - sidebar widget | Phase 6 |
| 8 | `tui/lanterna/widgets/SidebarPanel.groovy` | Add SubagentSection to sidebar | Phase 7 |
| 9 | `tui/lanterna/widgets/ActivityLogPanel.groovy` | Add subagent display methods | - |
| 10 | `tui/lanterna/widgets/HeaderPanel.groovy` | Add subagent status to header | - |
| 11 | `tui/LanternaTUI.groovy` | Integrate subagent tracking | Phase 6, 7-10 |

### Priority 3 - Polish (Week 3)

| Phase | File | Description | Dependencies |
|-------|------|-------------|---------------|
| 3 | `core/Subagent.groovy` | Structured SubagentOutput | Phase 1 |
| 5 | `core/Config.groovy` | Add subagent config options | - |
| 12 | `tools/TaskTool.groovy` | Enhanced CLI output | Phase 2 |

---

## Visual Examples

### During Parallel Subagent Execution

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ GLM CLI                                    12,345 tokens • 45%  $0.0234  │
│                                                    🤖 2/5 subagents     │
├──────────────────────────┬──────────────────────────────────────────────────┤
│                        │ ┌──────────────────────────┐                    │
│  GLM> Running task...  │ │ ┌────────────────────────│                    │
│                        │ │ │ Session        📁 glm-cli│                    │
│                        │ │ └────────────────────────│                    │
│  🤖 Launching 3       │ │ ┌────────────────────────│                    │
│     parallel agents...  │ │ │ Tokens     12,345      │                    │
│                        │ │ └────────────────────────│                    │
│     1. EXPLORE         │ │ ┌────────────────────────│                    │
│     2. EXPLORE         │ │ │ LSP               ● 2  │                    │
│     3. PLAN            │ │ └────────────────────────│                    │
│                        │ │ ┌────────────────────────│                    │
│  ──────────────────────│ │ │ Subagents     ▼       │                    │
│                        │ │ ├────────────────────────│                    │
│  ⠋ EXPLORE_1: running│ │ │  2/5 agents              │                    │
│  ⠋ EXPLORE_2: running│ │ │  [████████░░░░░░░] 40%  │                    │
│  ⠋ PLAN_1:   running  │ │ │                          │                    │
│                        │ │ │ ⠋ EXPLORE (running)    │                    │
├──────────────────────────│ │ │ ⠋ PLAN   (running)     │                    │
│ glm-cli │ ● 2 LSP │  ○  │ │ └────────────────────────│                    │
│  MCP     │ BUILD (Tab)  │ └──────────────────────────┘                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ Type your message... (Ctrl+P for commands)                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

### After Completion

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ GLM CLI                                    14,567 tokens • 52%  $0.0289  │
├──────────────────────────┬──────────────────────────────────────────────────┤
│                        │ ┌──────────────────────────┐                    │
│  ╔═════════════════════│ │ │ ┌────────────────────────│                    │
│  ║ Subagent Summary    │ │ │ │ Subagents     ▼       │                    │
│  ╠═════════════════════│ │ │ ├────────────────────────│                    │
│  ║ Agent 1 (EXPLORE)   │ │ │ │  ✓ All 5 agents complete │                    │
│  ║   Status: ✓ Success │ │ │ │                          │                    │
│  ║   Duration: 234ms   │ │ │ │ ✓ EXPLORE (completed)  │                    │
│  ╠═════════════════════│ │ │ │ ✓ EXPLORE (completed)  │                    │
│  ║ Agent 2 (EXPLORE)   │ │ │ │ ✓ PLAN   (completed)    │                    │
│  ║   Status: ✓ Success │ │ │ └────────────────────────│                    │
│  ║   Duration: 189ms   │ │ ┌────────────────────────│                    │
│  ╠═════════════════════│ │ │ Modified    📄 3 files │                    │
│  ║ Agent 3 (PLAN)      │ │ └────────────────────────│                    │
│  ║   Status: ✓ Success │ └──────────────────────────┘                    │
│  ║   Duration: 412ms   │                                                      │
│  ╚═════════════════════│                                                      │
│                        │                                                      │
│  GLM> Synthesized findings: │                                                      │
│    • Found 12 relevant files                                                       │
│    • Identified 3 patterns                                                       │
│    • Architecture uses MVC                                                        │
├──────────────────────────┤                                                      │
│ glm-cli │ ● 2 LSP │  BUILD (Tab)                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│ Type your message...                                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Testing Checklist

### Core Functionality

- [ ] Single subagent execution via TaskTool
- [ ] Parallel subagent execution via TaskTool
- [ ] Result synthesis returns valid LLM format
- [ ] Subagent permissions prevent task nesting
- [ ] Session tracking updates correctly
- [ ] Configuration options are respected

### TUI Integration

- [ ] SubagentSection displays in sidebar
- [ ] Progress bar updates during execution
- [ ] Agent labels show correct status
- [ ] Header panel shows running count
- [ ] Activity log displays subagent events
- [ ] TUI refreshes on timer updates
- [ ] Completed sessions are cleared correctly

### CLI Mode

- [ ] Parallel tasks display correctly
- [ ] Summary statistics are accurate
- [ ] Error handling works properly
- [ ] Output formatting is readable

---

## Future Enhancements

### Optional Features (Out of Scope)

1. **User-Invoked Subagents**
   - Add `@agentname` syntax in chat
   - Add `/subagent` command for CLI
   - Auto-complete for agent types

2. **Subagent Result Caching**
   - Cache results by task hash
   - Reuse results for identical queries
   - Configurable cache TTL

3. **Nested Subagents** (with depth limit)
   - Allow subagents to spawn subagents
   - Configurable max depth (default: 1)
   - Visual nesting in TUI

4. **Subagent Communication**
   - Allow subagents to share state
   - Message passing between agents
   - Shared context windows

5. **Subagent Metrics Dashboard**
   - Dedicated subagent stats panel
   - Performance metrics per agent type
   - Historical execution graphs

---

## References

- OpenCode Subagent Implementation: `/home/kevintan/opencode/`
- GLM-CLI Existing Subagent Support: `core/Subagent.groovy`, `core/SubagentPool.groovy`
- Plan Mode Pattern: `commands/PlanCommand.groovy`
- Parallel Execution Guide: `docs/PARALLEL_EXECUTION.md`
