package tools

import com.fasterxml.jackson.databind.ObjectMapper
import tui.AnsiColors
import tui.OutputFormatter
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
        return 'batch'
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
- Cannot batch itself (recursion disallowed)
 """.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                tools: [
                    type: 'array',
                    description: 'List of tools to execute in parallel',
                    items: [
                        type: 'object',
                        properties: [
                            name: [
                                type: 'string',
                                description: "Tool name (e.g., 'read_file', 'glob', 'grep')"
                            ],
                            arguments: [
                                type: 'object',
                                description: 'Tool-specific parameters'
                            ]
                        ],
                        required: ['name', 'arguments']
                    ],
                    minItems: 1,
                    maxItems: 10
                ]
            ],
            required: ['tools']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        def toolsList = args.get('tools')

        if (toolsList == null || !(toolsList instanceof List)) {
            return "Error: 'tools' must be an array of tool executions"
        }

        if (toolsList.size() > 10) {
            return 'Error: Maximum 10 tools allowed per batch. Please split into multiple batch calls.'
        }

        if (toolsList.isEmpty()) {
            return 'Error: At least one tool must be specified'
        }

        // Check for recursion (batch tool cannot batch itself)
        def hasRecursiveBatch = toolsList.any { it.get('name') == 'batch' }
        if (hasRecursiveBatch) {
            return 'Error: batch tool cannot be called recursively within a batch'
        }

        OutputFormatter.printInfo("Executing ${AnsiColors.bold(toolsList.size().toString())} tools in parallel...")

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
                def result = future.get(120, TimeUnit.SECONDS) // 2 minute timeout per tool
                results.add(result)

                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                }
            } catch (TimeoutException e) {
                results.add(new BatchResult(
                    toolName: 'unknown',
                    success: false,
                    result: 'Error: Tool execution timed out after 120 seconds',
                    duration: 120000
                ))
                failureCount++
                future.cancel(true)
            } catch (Exception e) {
                results.add(new BatchResult(
                    toolName: 'unknown',
                    success: false,
                    result: "Error: ${e.message}",
                    duration: 0
                ))
                failureCount++
            }
        }

        def duration = System.currentTimeMillis() - startTime

        OutputFormatter.printSuccess("Batch completed in ${duration}ms: ${AnsiColors.green(successCount.toString())} succeeded, ${AnsiColors.red(failureCount.toString())} failed")

        return formatResults(results, duration)
    }

    private BatchResult executeTool(Map<String, Object> toolSpec) {
        def toolName = toolSpec.get('name')
        def toolArgs = toolSpec.get('arguments')
        def startTime = System.currentTimeMillis()

        try {
            def tool = availableTools.find { it.name == toolName }

            if (tool == null) {
                return new BatchResult(
                    toolName: toolName?.toString() ?: 'unknown',
                    success: false,
                    result: "Error: Tool '${toolName}' not found. Available tools: ${availableTools.collect { it.name }.join(', ')}",
                    duration: System.currentTimeMillis() - startTime
                )
            }

            // Validate arguments
            if (toolArgs == null) {
                toolArgs = [:]
            }

            if (!(toolArgs instanceof Map)) {
                return new BatchResult(
                    toolName: toolName.toString(),
                    success: false,
                    result: "Error: 'arguments' must be an object with tool parameters",
                    duration: System.currentTimeMillis() - startTime
                )
            }

            // Execute tool
            def output = tool.execute(toolArgs as Map<String, Object>)

            return new BatchResult(
                toolName: toolName.toString(),
                success: true,
                result: output?.toString() ?: 'No output',
                duration: System.currentTimeMillis() - startTime
            )
        } catch (Exception e) {
            return new BatchResult(
                toolName: toolName?.toString() ?: 'unknown',
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
            def statusIcon = result.success ? '✓' : '✗'
            def statusText = result.success ? 'Success' : 'Failed'

            sb.append("### Tool ${i + 1}: ${result.toolName}\n")
            sb.append("- Status: ${statusIcon} ${statusText}\n")
            sb.append("- Duration: ${result.duration}ms\n")
            sb.append("- Output:\n")

            if (result.result.length() > 2000) {
                sb.append("```\n")
                sb.append(result.result.substring(0, 2000))
                sb.append("\n... (truncated, ${result.result.length()} total characters)\n```\n")
            } else {
                sb.append("```\n${result.result}\n```\n")
            }

            sb.append("\n")
        }

        return sb.toString()
    }

    void shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (InterruptedException e) {
            executor.shutdownNow()
        }
    }

    static class BatchResult {

        String toolName
        boolean success
        String result
        long duration

    }

}
