package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import tools.*
import com.fasterxml.jackson.databind.ObjectMapper
import core.Config
import core.Auth
import core.ModelCatalog
import core.SkillRegistry
import rag.RAGPipeline

class ExecuteToolCommand implements Command {

    private final List<Map> toolCalls
    private final List<Tool> tools
    private final String sessionId
    private final ObjectMapper mapper = new ObjectMapper()
    private final Config config

    ExecuteToolCommand(List<Map> toolCalls, List<Tool> tools, String sessionId) {
        this.toolCalls = toolCalls
        this.tools = tools
        this.sessionId = sessionId
        this.config = Config.load()
    }

    @Override
    Message execute() {
        def allResults = []

        for (call in toolCalls) {
            def result = executeSingleTool(call)
            allResults << result
        }

        def combinedResults = allResults.collect { it.content }.join("\n\n")

        return new ToolResultMessage(
            toolCalls[0].id,
            combinedResults,
            allResults
        )
    }

    private Map<String, Object> executeSingleTool(Map toolCall) {
        String toolName = toolCall.function.name
        String arguments = toolCall.function.arguments
        String callId = toolCall.id

        try {
            def toolInstance = tools.find { it.name == toolName }

            if (!toolInstance) {
                return [
                    tool_call_id: callId,
                    role: 'tool',
                    content: "Error: Tool '${toolName}' not found"
                ]
            }

            Map<String, Object> args = mapper.readValue(arguments, Map.class)

            if (toolInstance instanceof WriteFileTool) {
                toolInstance.setSessionId(sessionId)
            }

            Object output = toolInstance.execute(args)
            String result = output?.toString() ?: "Success"

            return [
                tool_call_id: callId,
                role: 'tool',
                content: result,
                tool_name: toolName
            ]
        } catch (Exception e) {
            return [
                tool_call_id: callId,
                role: 'tool',
                content: "Error executing ${toolName}: ${e.message}",
                error: true
            ]
        }
    }
}
