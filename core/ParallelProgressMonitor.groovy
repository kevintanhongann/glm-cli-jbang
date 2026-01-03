package core

import tui.AnsiColors

/**
 * Provides visual feedback during parallel tool execution.
 * Shows a progress bar and status of running/completed tools.
 */
class ParallelProgressMonitor {

    private Map<String, ToolProgress> progress = [:]
    private int totalTools = 0
    private int completedTools = 0
    private boolean enabled = true

    ParallelProgressMonitor(boolean enabled = true) {
        this.enabled = enabled
    }

    /**
     * Mark a tool as started.
     */
    synchronized void startTool(String toolName) {
        totalTools++
        progress[toolName] = new ToolProgress(
            toolName: toolName,
            status: 'running',
            startTime: System.currentTimeMillis()
        )

        if (enabled) {
            updateDisplay()
        }
    }

    /**
     * Mark a tool as completed.
     */
    synchronized void completeTool(String toolName, boolean success, String result = null) {
        def prog = progress[toolName]
        if (prog) {
            prog.status = success ? 'success' : 'failed'
            prog.endTime = System.currentTimeMillis()
            prog.duration = prog.endTime - prog.startTime
            prog.result = result
            completedTools++

            if (enabled) {
                updateDisplay()
            }
        }
    }

    /**
     * Update the terminal display with current progress.
     */
    private void updateDisplay() {
        // Only update if we have a console
        if (System.console() == null) return
        if (totalTools == 0) return

        // Clear previous line
        print "\r"

        // Build progress bar
        def percent = ((completedTools / totalTools) * 100).intValue()
        def barWidth = 20
        def filled = ((percent / 100.0) * barWidth).intValue()
        def empty = barWidth - filled

        def barFilled = '█' * filled
        def barEmpty = '░' * empty

        print AnsiColors.cyan('▶')
        print " Parallel: [${barFilled}${barEmpty}] ${percent}% "
        print "(${completedTools}/${totalTools} tools)"

        // Print status of running tools
        def running = progress.values().findAll { it.status == 'running' }
        if (!running.isEmpty()) {
            def names = running.collect { it.toolName }.take(3).join(', ')
            if (running.size() > 3) {
                names += ", +${running.size() - 3} more"
            }
            print AnsiColors.dim(" │ Running: ${names}")
        }

        // Pad with spaces to clear any leftover characters
        print '          '

        // If all complete, print summary on new line
        if (completedTools == totalTools && totalTools > 0) {
            println ''
            printSummary()
        }
    }

    /**
     * Print a summary of all tool executions.
     */
    private void printSummary() {
        progress.values().each { prog ->
            def icon = prog.status == 'success' ?
                AnsiColors.green('✓') :
                AnsiColors.red('✗')

            println "  ${icon} ${prog.toolName} (${prog.duration}ms)"
        }
    }

    /**
     * Get the current progress percentage.
     */
    int getProgressPercent() {
        if (totalTools == 0) return 0
        return ((completedTools / totalTools) * 100).intValue()
    }

    /**
     * Get count of successful tools.
     */
    int getSuccessCount() {
        return progress.values().count { it.status == 'success' }
    }

    /**
     * Get count of failed tools.
     */
    int getFailureCount() {
        return progress.values().count { it.status == 'failed' }
    }

    /**
     * Get total execution time across all tools.
     */
    long getTotalDuration() {
        return progress.values().sum { it.duration ?: 0 } ?: 0
    }

    /**
     * Reset the monitor for a new batch.
     */
    synchronized void reset() {
        progress.clear()
        totalTools = 0
        completedTools = 0
    }

    /**
     * Enable or disable display updates.
     */
    void setEnabled(boolean enabled) {
        this.enabled = enabled
    }

    /**
     * Represents the progress of a single tool execution.
     */
    static class ToolProgress {

        String toolName
        String status  // running, success, failed
        long startTime
        long endTime
        long duration
        String result

    }

}
