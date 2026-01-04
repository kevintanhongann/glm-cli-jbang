package core

import tools.Tool
import groovy.transform.Canonical
import models.Message
import models.ChatRequest
import models.ChatResponse
import com.fasterxml.jackson.databind.ObjectMapper
import tui.shared.AnsiColors
import tui.shared.OutputFormatter

@Canonical
class Subagent {
    private final AgentConfig config
    private final GlmClient client
    private final List<Tool> tools
    private final List<Message> history = []
    private final ObjectMapper mapper = new ObjectMapper()
    private int turn = 0

    Subagent(AgentConfig config, GlmClient client, List<Tool> allTools) {
        this.config = config
        this.client = client
        this.tools = config.filterTools(allTools)
        
        def prompt = config.loadPrompt()
        if (prompt && !prompt.isEmpty()) {
            history.add(new Message("system", prompt))
        }
    }

    String execute(String task) {
        OutputFormatter.printInfo("Launching ${AnsiColors.bold(config.name)} agent...")

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
                return content ?: "No response from agent"
            }
        }

        return history[-1]?.content ?: "No response from agent"
    }

    private ChatResponse sendRequest() {
        def req = new ChatRequest()
        req.model = config.model
        req.messages = history
        req.stream = false
        req.tools = tools.collect { tool ->
            [
                type: "function",
                function: [
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.parameters
                ]
            ]
        }

        try {
            def responseJson = client.sendMessage(req)
            return mapper.readValue(responseJson, ChatResponse.class)
        } catch (Exception e) {
            System.err.println "Subagent error: ${e.message}"
            throw e
        }
    }

    private void executeToolCalls(List toolCalls) {
        toolCalls.each { toolCall ->
            def functionName = toolCall.function.name
            def arguments = toolCall.function.arguments
            def callId = toolCall.id

            Tool tool = tools.find { it.name == functionName }
            String result = ""

            if (tool) {
                try {
                    def args = mapper.readValue(arguments, Map.class)
                    result = tool.execute(args).toString()
                } catch (Exception e) {
                    result = "Error: ${e.message}"
                }
            } else {
                result = "Error: Tool '${functionName}' not available for ${config.name} agent"
            }

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

    List<Message> getHistory() {
        return new ArrayList<>(history)
    }
}
