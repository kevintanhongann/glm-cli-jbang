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
class SubagentOutput {
    String sessionId
    String agentType
    String task
    int turns
    String content
    Map<String, Integer> toolUsage
    List<ToolExecution> toolExecutions
    long duration
    boolean success
}

@Canonical
class ToolExecution {
    String toolName
    String arguments
    boolean success
    long duration
    String result
}

@Canonical
class Subagent {
    private final AgentConfig config
    private final GlmClient client
    private final List<Tool> tools
    private final List<Message> history = []
    private final ObjectMapper mapper = new ObjectMapper()
    private int turn = 0
    private String sessionId
    private Map<String, Integer> toolUsage = [:]
    private List<ToolExecution> toolExecutions = []

    Subagent(AgentConfig config, GlmClient client, List<Tool> allTools) {
        this.config = config
        this.client = client
        this.tools = config.filterTools(allTools)
        this.sessionId = UUID.randomUUID().toString()

        def prompt = config.loadPrompt()
        if (prompt && !prompt.isEmpty()) {
            history.add(new Message("system", prompt))
        }
    }

    SubagentOutput execute(String task) {
        OutputFormatter.printInfo("Launching ${AnsiColors.bold(config.name)} agent...")
        long startTime = System.currentTimeMillis()

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
                long duration = System.currentTimeMillis() - startTime
                return new SubagentOutput(
                    sessionId: sessionId,
                    agentType: config.name,
                    task: task,
                    turns: turn,
                    content: content ?: "No response from agent",
                    toolUsage: toolUsage,
                    toolExecutions: toolExecutions,
                    duration: duration,
                    success: true
                )
            }
        }

        long duration = System.currentTimeMillis() - startTime
        return new SubagentOutput(
            sessionId: sessionId,
            agentType: config.name,
            task: task,
            turns: turn,
            content: history[-1]?.content ?: "No response from agent",
            toolUsage: toolUsage,
            toolExecutions: toolExecutions,
            duration: duration,
            success: false
        )
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
            String functionName = toolCall.function.name
            String arguments = toolCall.function.arguments
            String callId = toolCall.id
            long toolStart = System.currentTimeMillis()

            Tool tool = tools.find { it.name == functionName }
            String result = ""
            boolean toolSuccess = false

            if (tool) {
                try {
                    def args = mapper.readValue(arguments, Map.class)
                    result = tool.execute(args).toString()
                    toolSuccess = true
                } catch (Exception e) {
                    result = "Error: ${e.message}"
                    toolSuccess = false
                }
            } else {
                result = "Error: Tool '${functionName}' not available for ${config.name} agent"
                toolSuccess = false
            }

            long toolDuration = System.currentTimeMillis() - toolStart

            toolUsage[functionName] = (toolUsage[functionName] ?: 0) + 1
            toolExecutions.add(new ToolExecution(
                toolName: functionName,
                arguments: arguments,
                success: toolSuccess,
                duration: toolDuration,
                result: result
            ))

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
