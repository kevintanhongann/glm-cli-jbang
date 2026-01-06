package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import tools.*

class ExecuteToolCommand implements Command {

    private final List<Map> toolCalls

    ExecuteToolCommand(List<Map> toolCalls) {
        this.toolCalls = toolCalls
    }

    @Override
    Message execute() {
        def results = []

        for (call in toolCalls) {
            try {
                def tool = getTool(call.function.name)
                // In a real implementation, we'd use Jackson to parse arguments
                // For Phase 1, we just return a stub
                results << [
                    tool_call_id: call.id,
                    role: 'tool',
                    content: "Tool ${call.function.name} execution stub"
                ]
            } catch (Exception e) {
                results << [
                    tool_call_id: call.id,
                    role: 'tool',
                    content: "Error: ${e.message}"
                ]
            }
        }

        return new ToolResultMessage(
            toolCalls[0].id,
            results.collect { it.content }.join("\n")
        )
    }

    private Tool getTool(String name) {
        // This is a simplified version of tool lookup
        return null
    }

}
