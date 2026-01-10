package core

class SubagentResultSynthesizer {

    static String formatForLLM(List<SubagentPool.SubagentResult> results) {
        StringBuilder output = new StringBuilder()
        output.append("## Subagent Results Summary\n\n")

        results.eachWithIndex { result, idx ->
            String statusIcon = result.success ? "✓" : "✗"
            output.append("### Agent ${idx + 1} (${result.configName})\n")
            output.append("- Status: ${statusIcon} ${result.success ? 'Success' : 'Failed'}\n")
            output.append("- Duration: ${result.duration}ms\n")
            output.append("- Turns: ${result.history ? result.history.size() / 2 : 'N/A'}\n")

            if (result.result) {
                output.append("- Result:\n${result.result}\n")
            }

            if (result.error) {
                output.append("- Error: ${result.error}\n")
            }

            output.append("\n")
        }

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
