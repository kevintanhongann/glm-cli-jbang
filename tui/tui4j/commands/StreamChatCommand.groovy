package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import core.GlmClient
import core.AgentRegistry
import core.AgentConfig
import core.Instructions
import models.ChatRequest
import models.Message as ChatMessage
import tools.Tool
import com.fasterxml.jackson.databind.ObjectMapper

class StreamChatCommand implements Command {

    private final GlmClient client
    private final List<Map> history
    private final def config
    private final String sessionId
    private final String currentCwd
    private final AgentRegistry agentRegistry
    private final List<Tool> tools
    private final ObjectMapper mapper = new ObjectMapper()
    private final java.util.concurrent.BlockingQueue<Message> messageQueue

    StreamChatCommand(GlmClient client, List<Map> history, config, String sessionId, String currentCwd, AgentRegistry agentRegistry, List<Tool> tools, java.util.concurrent.BlockingQueue<Message> queue) {
        this.client = client
        this.history = history
        this.config = config
        this.sessionId = sessionId
        this.currentCwd = currentCwd
        this.agentRegistry = agentRegistry
        this.tools = tools
        this.messageQueue = queue
    }

    @Override
    Message execute() {
        def fullContent = new StringBuilder()

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
            request.stream = true

            AgentConfig agentConfig = agentRegistry.getCurrentAgentConfig()

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

            client.streamMessage(request,
                { chunk ->
                    if (chunk.choices && chunk.choices[0].delta?.content) {
                        String content = chunk.choices[0].delta.content
                        fullContent.append(content)

                        messageQueue.offer(new StreamChunkMessage(content, false))
                    }
                },
                { fullResponse ->
                    messageQueue.offer(new StreamChunkMessage(fullContent.toString(), true))
                }
            )

            return new StatusMessage("Streaming started")

        } catch (Exception e) {
            return new ErrorMessage("Stream Error: ${e.message}", e)
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
