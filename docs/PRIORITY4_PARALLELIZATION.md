# Priority 4: Enhanced Parallelization Implementation Plan

## Overview

Enhance parallel tool execution capabilities by implementing a Batch tool and optimizing the agent's ability to execute multiple independent tools simultaneously, following OpenCode's parallel execution patterns.

## Status

- **Status:** Not Started
- **Priority:** Medium
- **Estimated Effort:** 4-5 days
- **Dependencies:** None

---

## Problem Statement

Currently, GLM-CLI allows multiple tools in a single response but lacks:
- Explicit parallel execution optimization
- Batch tool for coordinated parallel execution
- Performance tracking and optimization
- Parallel execution limits and safety
- Visual feedback during parallel execution

OpenCode solves this with:
- A dedicated Batch tool for executing 1-10 tools in parallel
- Explicit guidance on parallelization
- Single request, multiple tool calls
- Partial failure handling

---

## Design

### Parallel Execution Model

```
Single Response → Multiple Tool Calls
    │
    ├─ Tool 1: glob(pattern: "**/*.groovy")
    ├─ Tool 2: grep(pattern: "class.*Controller", include: "*.groovy")
    ├─ Tool 3: read_file(path: "README.md")
    └─ Tool 4: read_file(path: "src/Agent.groovy")

↓ All execute in parallel

Results collected → Ordered by completion time → Returned together
```

### Batch Tool Interface

```groovy
batch(tools: [
  { name: "glob", arguments: { pattern: "**/*.groovy" } },
  { name: "grep", arguments: { pattern: "class.*Controller", include: "*.groovy" } }
])
```

---

## Implementation Plan

### Phase 1: Batch Tool (Days 1-2)

#### 1.1 Create Batch Tool

**File:** `tools/BatchTool.groovy`

```groovy
package tools

import com.fasterxml.jackson.databind.ObjectMapper
import tui.AnsiColors
import tui.OutputFormatter
import tui.ProgressIndicator
import java.util.concurrent.*

class BatchTool implements Tool {

    private final List<Tool> availableTools
    private final ObjectMapper mapper = new ObjectMapper()
    private final ExecutorService executor

    BatchTool(List<Tool> availableTools) {
        this.availableTools = availableTools
        this.executor = Executors.newFixedThreadPool(10)
    }

    @Override
    String getName() {
        return "batch"
    }

    @Override
    String getDescription() {
        return """
Execute multiple independent tools in parallel for maximum performance.

**WHEN TO USE:**
- Multiple independent file reads: read_file for multiple files at once
- Grep + Glob combination: run searches in parallel
- Multiple grep searches: different patterns in parallel
- Git operations: git status, diff, log together

**CAPABILITIES:**
- Execute 1-10 tools in a single request
- All tools start simultaneously (no ordering guarantee)
- Partial failures don't stop other tools
- Results returned in order of completion

**PARAMETERS:**
- tools: list of tool executions, each with:
  - name: tool name (e.g., "read_file", "glob", "grep")
  - arguments: tool-specific parameters

**BEST PRACTICES:**
- Batch independent operations together
- Maximum 10 tools per batch (for performance)
- Results returned as fast as they complete
- Failed tools won't affect others

**EXAMPLE:**
```
batch(tools: [
  { name: "glob", arguments: { pattern: "**/*.groovy" } },
  { name: "grep", arguments: { pattern: "class.*Controller", include: "*.groovy" } },
  { name: "read_file", arguments: { path: "README.md" } }
])
```

**WHEN NOT TO USE:**
- Dependent operations (results of one needed by another)
- Sequential writes (each requires user confirmation)
- When order matters → execute tools separately

**LIMITATIONS:**
- Max 10 tools per batch
- No tool interdependencies
- All tools must be registered
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                tools: [
                    type: "array",
                    description: "List of tools to execute in parallel",
                    items: [
                        type: "object",
                        properties: [
                            name: [
                                type: "string",
                                description: "Tool name (e.g., 'read_file', 'glob', 'grep')"
                            ],
                            arguments: [
                                type: "object",
                                description: "Tool-specific parameters"
                            ]
                        ],
                        required: ["name", "arguments"]
                    ],
                    minItems: 1,
                    maxItems: 10
                ]
            ],
            required: ["tools"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        def toolsList = args.get("tools")

        if (toolsList == null || !(toolsList instanceof List)) {
            return "Error: 'tools' must be an array of tool executions"
        }

        if (toolsList.size() > 10) {
            return "Error: Maximum 10 tools allowed per batch. Please split into multiple batch calls."
        }

        OutputFormatter.printInfo("Executing ${AnsiColors.bold(toolsList.size())} tools in parallel...")

        // Create futures for parallel execution
        List<Future<BatchResult>> futures = []
        def startTime = System.currentTimeMillis()

        toolsList.each { toolSpec ->
            def future = executor.submit({
                executeTool(toolSpec)
            } as Callable<BatchResult>)

            futures.add(future)
        }

        // Collect results
        def results = []
        def successCount = 0
        def failureCount = 0

        futures.each { future ->
            try {
                def result = future.get()
                results.add(result)

                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                }
            } catch (Exception e) {
                results.add(new BatchResult(
                    toolName: "unknown",
                    success: false,
                    result: "Error: ${e.message}",
                    duration: 0
                ))
                failureCount++
            }
        }

        def duration = System.currentTimeMillis() - startTime

        OutputFormatter.printSuccess("Batch completed in ${duration}ms: ${AnsiColors.green(successCount)} succeeded, ${AnsiColors.red(failureCount)} failed")

        return formatResults(results, duration)
    }

    private BatchResult executeTool(Map<String, Object> toolSpec) {
        def toolName = toolSpec.get("name")
        def toolArgs = toolSpec.get("arguments")
        def startTime = System.currentTimeMillis()

        try {
            def tool = availableTools.find { it.name == toolName }

            if (tool == null) {
                return new BatchResult(
                    toolName: toolName,
                    success: false,
                    result: "Error: Tool '${toolName}' not found",
                    duration: System.currentTimeMillis() - startTime
                )
            }

            // Validate arguments
            def params = tool.parameters
            if (toolArgs == null || !(toolArgs instanceof Map)) {
                return new BatchResult(
                    toolName: toolName,
                    success: false,
                    result: "Error: 'arguments' must be an object with tool parameters",
                    duration: System.currentTimeMillis() - startTime
                )
            }

            // Execute tool
            def output = tool.execute(toolArgs)

            return new BatchResult(
                toolName: toolName,
                success: true,
                result: output?.toString() ?: "No output",
                duration: System.currentTimeMillis() - startTime
            )

        } catch (Exception e) {
            return new BatchResult(
                toolName: toolName,
                success: false,
                result: "Error: ${e.message}",
                duration: System.currentTimeMillis() - startTime
            )
        }
    }

    private String formatResults(List<BatchResult> results, long totalDuration) {
        def sb = new StringBuilder()

        sb.append("## Batch Execution Results\n")
        sb.append("Total time: ${totalDuration}ms\n")
        sb.append("Tools executed: ${results.size()}\n")
        sb.append("\n")

        results.eachWithIndex { result, i ->
            sb.append("### Tool ${i + 1}: ${AnsiColors.bold(result.toolName)}\n")
            sb.append("- Status: ${result.success ? AnsiColors.green("✓ Success") : AnsiColors.red("✗ Failed")}\n")
            sb.append("- Duration: ${result.duration}ms\n")
            sb.append("- Output:\n")

            if (result.result.length() > 500) {
                sb.append("```\n")
                sb.append(result.result.substring(0, 500))
                sb.append("\n... (truncated)\n```\n")
            } else {
                sb.append("```\n${result.result}\n```\n")
            }

            sb.append("\n")
        }

        return sb.toString()
    }

    void shutdown() {
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.SECONDS)
    }

    static class BatchResult {
        String toolName
        boolean success
        String result
        long duration
    }
}
```

### Phase 2: Parallel Execution Optimization (Days 2-3)

#### 2.1 Create ParallelExecutor

**File:** `core/ParallelExecutor.groovy`

```groovy
package core

import tools.Tool
import java.util.concurrent.*

class ParallelExecutor {

    private final ExecutorService executor
    private final int maxThreads = 10

    ParallelExecutor() {
        this.executor = Executors.newFixedThreadPool(maxThreads)
    }

    List<ToolResult> executeParallel(List<ToolExecution> executions, List<Tool> availableTools) {
        List<Future<ToolResult>> futures = []

        executions.each { exec ->
            def future = executor.submit({
                executeSingle(exec, availableTools)
            } as Callable<ToolResult>)
            futures.add(future)
        }

        return futures.collect { it.get() }
    }

    private ToolResult executeSingle(ToolExecution exec, List<Tool> availableTools) {
        def startTime = System.currentTimeMillis()

        try {
            def tool = availableTools.find { it.name == exec.toolName }

            if (tool == null) {
                return new ToolResult(
                    toolName: exec.toolName,
                    success: false,
                    output: "Tool not found",
                    duration: System.currentTimeMillis() - startTime
                )
            }

            def output = tool.execute(exec.arguments)

            return new ToolResult(
                toolName: exec.toolName,
                success: true,
                output: output?.toString(),
                duration: System.currentTimeMillis() - startTime
            )

        } catch (Exception e) {
            return new ToolResult(
                toolName: exec.toolName,
                success: false,
                output: "Error: ${e.message}",
                duration: System.currentTimeMillis() - startTime
            )
        }
    }

    void shutdown() {
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.SECONDS)
    }

    static class ToolExecution {
        String toolName
        Map<String, Object> arguments

        ToolExecution(String toolName, Map<String, Object> arguments) {
            this.toolName = toolName
            this.arguments = arguments
        }
    }

    static class ToolResult {
        String toolName
        boolean success
        String output
        long duration
    }
}
```

#### 2.2 Update Agent.groovy

Enhance agent to track and optimize parallel execution:

```groovy
// In Agent class
private final ParallelExecutor parallelExecutor = new ParallelExecutor()
private final List<ToolExecutionStats> executionStats = []

Agent(String apiKey, String model) {
    // ... existing code
    registerTool(new BatchTool(tools))
}

// Add statistics tracking
void trackExecution(String toolName, long duration, boolean success) {
    def stats = executionStats.find { it.toolName == toolName }

    if (stats == null) {
        stats = new ToolExecutionStats(toolName: toolName)
        executionStats.add(stats)
    }

    stats.count++
    stats.totalDuration += duration
    stats.successCount += success ? 1 : 0
}

// Get parallel execution suggestions
List<String> getParallelSuggestions() {
    // Analyze recent tool calls and suggest parallelization
    def suggestions = []

    // Find tools that are often called together
    // This is a simplified version - could use more sophisticated analysis

    return suggestions
}

// Cleanup
void shutdown() {
    parallelExecutor?.shutdown()
    batchTool?.shutdown()
}

static class ToolExecutionStats {
    String toolName
    int count = 0
    long totalDuration = 0
    int successCount = 0

    double getAverageDuration() {
        return count > 0 ? totalDuration / count : 0
    }

    double getSuccessRate() {
        return count > 0 ? successCount / count : 0
    }
}
```

### Phase 3: Progress Monitoring (Day 3)

#### 3.1 Create ParallelProgressMonitor

**File:** `core/ParallelProgressMonitor.groovy`

```groovy
package core

import tui.AnsiColors
import tui.OutputFormatter

class ParallelProgressMonitor {

    private Map<String, ToolProgress> progress = [:]
    private int totalTools = 0
    private int completedTools = 0

    void startTool(String toolName) {
        totalTools++
        progress[toolName] = new ToolProgress(
            toolName: toolName,
            status: "running",
            startTime: System.currentTimeMillis()
        )

        updateDisplay()
    }

    void completeTool(String toolName, boolean success, String result) {
        def prog = progress[toolName]
        if (prog) {
            prog.status = success ? "success" : "failed"
            prog.endTime = System.currentTimeMillis()
            prog.duration = prog.endTime - prog.startTime
            prog.result = result
            completedTools++

            updateDisplay()
        }
    }

    private void updateDisplay() {
        // Only update if we're in TUI mode
        if (!System.console()) return

        // Clear previous line
        print "\r"

        // Build progress bar
        def percent = (completedTools / totalTools * 100).intValue()
        def barWidth = 20
        def filled = (percent / 100 * barWidth).intValue()
        def empty = barWidth - filled

        print AnsiColors.cyan("▶")
        print " Parallel: [${"█" * filled}${"░" * empty}] ${percent}% "
        print "(${completedTools}/${totalTools} tools)     "

        // Print status of running tools
        def running = progress.values().findAll { it.status == "running" }
        if (!running.isEmpty()) {
            def names = running.collect { it.toolName }.join(", ")
            print AnsiColors.dim("│ Running: ${names}")
        }

        // Clear rest of line
        print "\r"

        // If all complete, print summary
        if (completedTools == totalTools && totalTools > 0) {
            println ""
            progress.values().each { prog ->
                def icon = prog.status == "success" ?
                    AnsiColors.green("✓") :
                    AnsiColors.red("✗")

                println "  ${icon} ${prog.toolName} (${prog.duration}ms)"
            }
        }
    }

    void reset() {
        progress.clear()
        totalTools = 0
        completedTools = 0
    }

    static class ToolProgress {
        String toolName
        String status  // running, success, failed
        long startTime
        long endTime
        long duration
        String result
    }
}
```

### Phase 4: Documentation and Examples (Day 4)

#### 4.1 Create Parallelization Guide

**File:** `docs/PARALLEL_EXECUTION.md`

```markdown
# Parallel Execution Guide

## Overview

GLM-CLI supports parallel tool execution to maximize performance when exploring and modifying codebases.

## How It Works

When you call multiple independent tools in a single response, they execute in parallel:

```groovy
// All three execute simultaneously
read_file(path: "src/main.groovy")
read_file(path: "src/config.groovy")
read_file(path: "README.md")
```

**Benefits:**
- 3x faster than sequential execution
- Reduced total turnaround time
- Better user experience

## When to Parallelize

### Always Parallelize These:

✅ **Multiple independent file reads:**
```groovy
read_file(path: "src/Agent.groovy")
read_file(path: "src/Config.groovy")
read_file(path: "tools/ReadFileTool.groovy")
```

✅ **Grep + Glob combination:**
```groovy
glob(pattern: "**/*.groovy")
grep(pattern: "class.*Controller", include: "*.groovy")
```

✅ **Multiple grep searches:**
```groovy
grep(pattern: "@Autowired", include: "*.groovy")
grep(pattern: "@Inject", include: "*.groovy")
grep(pattern: "def.*Service", include: "*.groovy")
```

✅ **Git operations:**
```groovy
bash(command: "git status")
bash(command: "git diff")
bash(command: "git log --oneline -5")
```

### Never Parallelize These:

❌ **Dependent operations:**
```groovy
// Don't do this - read depends on write completing
write_file(path: "file.groovy", content: "...")
read_file(path: "file.groovy")
```

❌ **Sequential writes:**
```groovy
// Each write requires user confirmation
write_file(path: "file1.groovy", content: "...")
write_file(path: "file2.groovy", content: "...")
```

❌ **Operations where order matters:**
```groovy
// These must be sequential
bash(command: "git add .")
bash(command: "git commit -m 'changes'")
bash(command: "git push")
```

## Batch Tool

The `batch` tool provides explicit parallel execution for multiple tools:

### Syntax

```groovy
batch(tools: [
  { name: "tool_name", arguments: { /* params */ } },
  { name: "tool_name", arguments: { /* params */ } }
])
```

### Example

```groovy
batch(tools: [
  { name: "glob", arguments: { pattern: "**/*.groovy" } },
  { name: "grep", arguments: { pattern: "class.*Controller", include: "*.groovy" } },
  { name: "read_file", arguments: { path: "README.md" } }
])
```

### Batch Tool Benefits

- Explicit parallel execution
- Progress tracking
- Partial failure handling
- Performance metrics

## Performance Comparison

### Sequential Execution

```
read_file (100ms) → grep (150ms) → read_file (100ms) = 350ms
```

### Parallel Execution

```
read_file (100ms)
                \
                 → All start together = 150ms (max of durations)
                /
grep (150ms)
```

**Speedup: 2.3x**

## Limitations

- **Max 10 tools per batch:** Performance degrades beyond this
- **No interdependencies:** Tools can't share results
- **Order not guaranteed:** Tools complete in arbitrary order
- **Resource limits:** Bounded by thread pool size (10 threads)

## Best Practices

1. **Start with broad searches (glob), then narrow (grep)**
2. **Read multiple files simultaneously when possible**
3. **Combine glob + grep in parallel for faster discovery**
4. **Use batch tool for coordinated parallel execution**
5. **Limit to 5-10 parallel tools for best performance**
6. **Group related operations together**
7. **Avoid mixing reads and writes in parallel**

## Common Patterns

### Pattern 1: Discover and Read

```groovy
// Step 1: Discover files in parallel
glob(pattern: "**/*.groovy")
glob(pattern: "**/*.java")

// Step 2: Read found files
read_file(path: "src/main.groovy")
read_file(path: "src/config.groovy")
```

### Pattern 2: Search and Investigate

```groovy
// Broad search in parallel
glob(pattern: "**/*.groovy")
grep(pattern: "class.*Controller", include: "*.groovy")
grep(pattern: "class.*Service", include: "*.groovy")

// Read relevant files
read_file(path: "src/AuthController.groovy")
read_file(path: "src/AuthService.groovy")
```

### Pattern 3: Using Batch Tool

```groovy
batch(tools: [
  { name: "glob", arguments: { pattern: "**/*.groovy" } },
  { name: "grep", arguments: { pattern: "@Component", include: "*.groovy" } },
  { name: "read_file", arguments: { path: "README.md" } },
  { name: "read_file", arguments: { path: "pom.xml" } }
])
```

## Monitoring

Parallel execution shows progress:

```
▶ Parallel: [████████████████░░] 75% (3/4 tools) │ Running: grep
```

After completion:

```
✓ glob (45ms)
✓ grep (120ms)
✓ read_file (80ms)
✓ read_file (60ms)
```

## Configuration

Add to `~/.glm/config.toml`:

```toml
[parallel_execution]
enabled = true
max_parallel_tools = 10
thread_pool_size = 10
progress_display = true
```

## Troubleshooting

### Tools not executing in parallel?

Check:
- Are the tools truly independent?
- Are you calling them in separate turns?
- Is parallel_execution enabled in config?

### Performance not improving?

- Ensure tools are CPU-bound or I/O-bound (not both)
- Limit to 5-8 tools for best performance
- Check system resources (CPU, memory)

### Batch tool failing?

- Verify all tools are registered
- Check arguments format (must be objects)
- Ensure max 10 tools per batch
```

---

## Success Criteria

- [ ] Batch tool implemented and working
- [ ] ParallelExecutor created
- [ ] Progress monitoring functional
- [ ] Parallelization guide complete
- [ ] Performance benchmarks show improvement
- [ ] Documentation updated
- [ ] Examples provided

---

## Dependencies

- None (can start immediately)

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Thread exhaustion | Medium | Limit to 10 threads, use bounded pool |
| Complex debugging | Low | Add detailed logging for parallel execution |
| Performance degradation | Medium | Add benchmarks, tune limits |
| Resource contention | Low | Monitor memory/CPU usage |

---

## References

- OpenCode Batch Tool: `/home/kevintan/opencode/packages/opencode/src/tool/batch.ts`
- Parallel execution patterns in Claude Code and Amp

---

**Document Version:** 1.0
**Created:** 2025-01-02
**Priority:** Medium
