package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import core.GlmClient
import core.AgentRegistry
import core.AgentType
import core.AgentConfig
import core.Instructions
import core.TokenTracker
import core.SessionStatsManager
import models.ChatRequest
import models.ChatResponse
import models.Message as ChatMessage
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool
import tools.GrepTool
import tools.GlobTool
import tools.WebSearchTool
import tools.CodeSearchTool
import tools.Tool
import rag.RAGPipeline
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

class SendChatCommand implements Command {

    private final GlmClient client
    private final List<Map> history
    private final def config
    private final String sessionId
    private final String currentCwd
    private final AgentRegistry agentRegistry
    private final List<Tool> tools
    private final ObjectMapper mapper = new ObjectMapper()

    SendChatCommand(GlmClient client, List<Map> history, config, String sessionId, String currentCwd, AgentRegistry agentRegistry, List<Tool> tools) {
        this.client = client
        this.history = history
        this.config = config
        this.sessionId = sessionId
        this.currentCwd = currentCwd
        this.agentRegistry = agentRegistry
        this.tools = tools
    }

    @Override
    Message execute() {
        try {
            def parts = config.model.split('/', 2)
            String modelId = parts.length == 2 ? parts[1] : parts[0]

            List<ChatMessage> messages = []

            messages << new ChatMessage('system', loadSystemPrompt())

            for (msg in history) {
                messages << new ChatMessage(msg.role as String, msg.content as String)
            }

            def request = new ChatRequest()
            request.model = modelId
            request.messages = messages
            request.stream = false

            AgentConfig agentConfig = agentRegistry.getCurrentAgentConfig()
            int maxIterations = config.behavior?.maxSteps ?: 25

            List<Tool> allowedTools = []
            tools.each { tool ->
                if (agentConfig.isToolAllowed(tool.name)) {
                    allowedTools << tool
                }
            }

            if (!allowedTools.isEmpty()) {
                request.tools = allowedTools.collect { tool ->
                    [
                        type: 'function',
                        function: [
                            name: tool.name,
                            description: tool.description,
                            parameters: tool.parameters
                        ]
                    ]
                }
            }

            String responseJson = client.sendMessage(request)
            ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)

            def choice = response.choices[0]
            def message = choice.message

            if (response.usage) {
                int inputTokens = response.usage.promptTokens ?: 0
                int outputTokens = response.usage.completionTokens ?: 0
                BigDecimal cost = response.usage.cost ?: 0.0000

                TokenTracker.instance.recordTokens(sessionId, inputTokens, outputTokens, cost)
                SessionStatsManager.instance.updateTokenCount(sessionId, inputTokens, outputTokens, cost)
            }

            return new ChatResponseMessage(
                message.content,
                message.toolCalls,
                [usage: response.usage, finishReason: choice.finishReason]
            )
        } catch (Exception e) {
            return new ErrorMessage("API Error: ${e.message}", e)
        }
    }

    private String loadSystemPrompt() {
        AgentConfig agentConfig = agentRegistry.getCurrentAgentConfig()
        def promptFile = new File("prompts/${agentConfig.type.name().toLowerCase()}.txt")

        if (promptFile.exists()) {
            return promptFile.text
        }

        def customInstructions = Instructions.loadAll(currentCwd)
        if (!customInstructions.isEmpty()) {
            return customInstructions.join('\n\n')
        }

        return ""
    }
}
