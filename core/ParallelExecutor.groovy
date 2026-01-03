package core

import tools.Tool
import java.util.concurrent.*

class ParallelExecutor {

    private final ExecutorService executor
    private final int maxThreads

    ParallelExecutor(int maxThreads = 10) {
        this.maxThreads = maxThreads
        this.executor = Executors.newFixedThreadPool(maxThreads)
    }

    /**
     * Execute multiple tools in parallel and collect results.
     *
     * @param executions List of tool executions to perform
     * @param availableTools List of registered tools
     * @param timeoutMs Timeout per tool in milliseconds (default 120000 = 2 minutes)
     * @return List of results in order of completion
     */
    List<ToolResult> executeParallel(List<ToolExecution> executions, List<Tool> availableTools, long timeoutMs = 120000) {
        if (executions.isEmpty()) {
            return []
        }

        List<Future<ToolResult>> futures = []

        executions.each { exec ->
            def future = executor.submit({
                executeSingle(exec, availableTools)
            } as Callable<ToolResult>)
            futures.add(future)
        }

        def results = []
        futures.each { future ->
            try {
                def result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                results.add(result)
            } catch (TimeoutException e) {
                results.add(new ToolResult(
                    toolName: 'unknown',
                    success: false,
                    output: "Error: Execution timed out after ${timeoutMs}ms",
                    duration: timeoutMs
                ))
                future.cancel(true)
            } catch (Exception e) {
                results.add(new ToolResult(
                    toolName: 'unknown',
                    success: false,
                    output: "Error: ${e.message}",
                    duration: 0
                ))
            }
        }

        return results
    }

    private ToolResult executeSingle(ToolExecution exec, List<Tool> availableTools) {
        def startTime = System.currentTimeMillis()

        try {
            def tool = availableTools.find { it.name == exec.toolName }

            if (tool == null) {
                return new ToolResult(
                    toolName: exec.toolName,
                    success: false,
                    output: "Tool '${exec.toolName}' not found",
                    duration: System.currentTimeMillis() - startTime
                )
            }

            def output = tool.execute(exec.arguments)

            return new ToolResult(
                toolName: exec.toolName,
                success: true,
                output: output?.toString() ?: 'No output',
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

    /**
     * Check if the executor is still running and able to accept tasks.
     */
    boolean isRunning() {
        return !executor.isShutdown() && !executor.isTerminated()
    }

    /**
     * Get the maximum number of threads in the pool.
     */
    int getMaxThreads() {
        return maxThreads
    }

    /**
     * Shutdown the executor gracefully.
     */
    void shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println('ParallelExecutor did not terminate cleanly')
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Represents a tool execution request.
     */
    static class ToolExecution {

        String toolName
        Map<String, Object> arguments

        ToolExecution(String toolName, Map<String, Object> arguments) {
            this.toolName = toolName
            this.arguments = arguments ?: [:]
        }

    }

    /**
     * Represents the result of a tool execution.
     */
    static class ToolResult {

        String toolName
        boolean success
        String output
        long duration

        @Override
        String toString() {
            return "[${toolName}] ${success ? 'OK' : 'FAILED'} (${duration}ms): ${output?.take(100) ?: 'no output'}..."
        }

    }

}
