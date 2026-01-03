package core

import models.ChatRequest
import models.ChatResponse
import models.Message
import tools.Tool
import tools.TaskTool
import com.fasterxml.jackson.databind.ObjectMapper

import tui.AnsiColors
import tui.DiffRenderer
import tui.InteractivePrompt
import tui.OutputFormatter
import tui.ProgressIndicator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ToolCallHistory {

    String toolName
    String arguments
    Date timestamp

    ToolCallHistory(String toolName, String arguments) {
        this.toolName = toolName
        this.arguments = arguments
        this.timestamp = new Date()
    }

    boolean equals(ToolCallHistory other) {
        return this.toolName == other.toolName && this.arguments == other.arguments
    }

}

class Agent {

    private final GlmClient client
    private final List<Tool> tools = []
    private final List<Message> history = []
    private final ObjectMapper mapper = new ObjectMapper()
    private String model = 'glm-4.7'
    private int step = 0
    private final Config config
    private final List<ToolCallHistory> toolCallHistory = []
    private final SubagentPool subagentPool
    private final SessionManager sessionManager
    private final MessageStore messageStore
    private final String sessionId

    Agent(String apiKey, String model, String sessionId = null) {
        this.client = new GlmClient(apiKey)
        this.model = model
        this.config = Config.load()
        this.subagentPool = new SubagentPool(client, tools)
        this.sessionManager = SessionManager.instance
        this.messageStore = new MessageStore()

        AnsiColors.install()

        registerTool(new TaskTool(subagentPool))

        // Create or resume session
        if (sessionId) {
            this.sessionId = sessionId
            // Load existing messages
            def existingMessages = messageStore.getMessages(sessionId)
            history.addAll(existingMessages)
        } else {
            this.sessionId = sessionManager.createSession(
                System.getProperty('user.dir'),
                'BUILD',
                model
            )
        }
    }

    void shutdown() {
        subagentPool?.shutdown()
    }

    void registerTool(Tool tool) {
        tools.add(tool)
    }

    void registerTools(List<Tool> tools) {
        this.tools.addAll(tools)
    }

    private boolean isDoomLoop(String toolName, String arguments) {
        def lastThree = toolCallHistory.take(3)
        if (lastThree.size() < 3) return false

        return lastThree.every { call ->
            call.toolName == toolName && call.arguments == arguments
        }
    }

    private boolean shouldContinueOnDeny() {
        return config.experimental?.continueLoopOnDeny == true
    }

    /**
     * Load the system prompt with tool selection heuristics.
     * Looks for prompts/system.txt in the current directory first,
     * then falls back to the script directory.
     */
    private String loadSystemPrompt() {
        // Try current directory first
        def promptFile = new File('prompts/system.txt')
        if (promptFile.exists()) {
            return promptFile.text
        }

        // Try script directory (for installed JBang apps)
        def scriptDir = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
        def scriptPromptFile = new File(scriptDir, 'prompts/system.txt')
        if (scriptPromptFile.exists()) {
            return scriptPromptFile.text
        }

        return null
    }

    /**
     * Run the agent with a user prompt.
     * This handles the ReAct loop: Think -> Act -> Observe -> Think
     */
    void run(String prompt) {
        // Load system prompt first (tool selection heuristics)
        def systemPrompt = loadSystemPrompt()
        if (systemPrompt) {
            history.add(new Message('system', systemPrompt))
        }

        // Then load custom instructions
        def customInstructions = Instructions.loadAll()

        if (!customInstructions.isEmpty()) {
            customInstructions.each { instruction ->
                history.add(new Message('system', instruction))
            }
        }

        history.add(new Message('user', prompt))
        messageStore.saveMessage(sessionId, new Message('user', prompt))
        sessionManager.touchSession(sessionId)

        OutputFormatter.printHeader('GLM Agent')
        OutputFormatter.printInfo("Task: ${prompt}")

        while (true) {
            step++

            ChatRequest request = prepareRequest()

            if (config.behavior.maxSteps != null && step >= config.behavior.maxSteps) {
                OutputFormatter.printWarning("Maximum steps (${config.behavior.maxSteps}) reached. Disabling tools for final response.")
                request.tools = []
                def maxStepsMsg = new Message('assistant', 'You have reached the maximum number of allowed steps. Please provide a summary of the work completed and any remaining tasks or recommendations. Do not make any tool calls.')
                request.messages.add(maxStepsMsg)
            }

            ProgressIndicator spinner = new ProgressIndicator()
            spinner.start('Thinking...')
            String responseJson = client.sendMessage(request)
            spinner.stop(true)

            ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)

            def choice = response.choices[0]
            def message = choice.message

            // Print assistant thought (if any content)
            if (message.content) {
                OutputFormatter.printSection('Assistant')
                println message.content
                history.add(new Message('assistant', message.content))
                messageStore.saveMessage(sessionId, new Message('assistant', message.content))
                sessionManager.touchSession(sessionId)
            }

            if (choice.finishReason == 'tool_calls' || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                def toolCalls = message.toolCalls
                history.add(message)

                toolCalls.each { toolCall ->
                    String functionName = toolCall.function.name
                    String arguments = toolCall.function.arguments
                    String callId = toolCall.id

                    OutputFormatter.printInfo("Executing tool: ${AnsiColors.bold(functionName)}")

                    if (isDoomLoop(functionName, arguments)) {
                        OutputFormatter.printError("Doom loop detected! The same tool '${functionName}' has been called 3 times with identical arguments.")

                        boolean allowExecution = false
                        if (InteractivePrompt.confirm('Allow execution anyway? (otherwise loop will stop)')) {
                            allowExecution = true
                        }

                        if (!allowExecution) {
                            Message toolMsg = new Message()
                            toolMsg.role = 'tool'
                            toolMsg.content = "Execution stopped: Doom loop detected. The same tool '${functionName}' was called 3 times with identical arguments. Please try a different approach or modify your approach."
                            toolMsg.toolCallId = callId
                            history.add(toolMsg)

                            if (shouldContinueOnDeny()) {
                                OutputFormatter.printInfo('Continuing loop despite doom loop...')
                                return
                            } else {
                                OutputFormatter.printWarning('Stopping agent due to doom loop.')
                                return
                            }
                        }
                    }

                    // Safety & Diff Check for write_file
                    if (functionName == 'write_file') {
                        try {
                            Map<String, Object> args = mapper.readValue(arguments, Map.class)
                            String pathStr = args.get('path')
                            String newContent = args.get('content')
                            Path path = Paths.get(pathStr).normalize()

                            if (Files.exists(path)) {
                                String original = Files.readString(path)
                                println DiffRenderer.renderUnifiedDiff(original, newContent, pathStr)
                            } else {
                                OutputFormatter.printInfo("Creating new file: ${pathStr}")
                                OutputFormatter.printCode(newContent, getFileLanguage(pathStr))
                            }
                        } catch (Exception e) {
                            OutputFormatter.printError("Error generating diff: ${e.message}")
                        }

                        if (!InteractivePrompt.confirm('Apply these changes?')) {
                            OutputFormatter.printWarning('Action denied by user.')

                            Message toolMsg = new Message()
                            toolMsg.role = 'tool'
                            toolMsg.content = 'Error: User denied permission to execute this tool.'
                            toolMsg.toolCallId = callId
                            history.add(toolMsg)
                            return // Skip execution
                        }
                    }

                    Tool tool = tools.find { it.name == functionName }
                    String result = ''
                    if (tool) {
                        try {
                            Map<String, Object> args = mapper.readValue(arguments, Map.class)
                            Object output = tool.execute(args)
                            result = output.toString()

                            toolCallHistory.add(0, new ToolCallHistory(functionName, arguments))
                            if (toolCallHistory.size() > 10) {
                                toolCallHistory.remove(toolCallHistory.size() - 1)
                            }
                        } catch (Exception e) {
                            result = "Error executing tool: ${e.message}"
                        }
                    } else {
                        result = 'Error: Tool not found.'
                    }

                    OutputFormatter.printSection('Tool Output')
                    println result

                    Message toolMsg = new Message()
                    toolMsg.role = 'tool'
                    toolMsg.content = result
                    toolMsg.toolCallId = callId
                    history.add(toolMsg)
                }
            } else {
                OutputFormatter.printSuccess('Task completed.')
                break
            }
        }
    }

    private String getFileLanguage(String path) {
        if (path.endsWith('.groovy')) return 'groovy'
        if (path.endsWith('.java')) return 'java'
        if (path.endsWith('.py')) return 'python'
        if (path.endsWith('.js')) return 'javascript'
        if (path.endsWith('.ts')) return 'typescript'
        if (path.endsWith('.json')) return 'json'
        if (path.endsWith('.yaml') || path.endsWith('.yml')) return 'yaml'
        if (path.endsWith('.md')) return 'markdown'
        return ''
    }

    private ChatRequest prepareRequest() {
        ChatRequest req = new ChatRequest()
        req.model = model
        req.messages = history
        req.stream = false
        req.tools = tools.collect { tool ->
            [
                type: 'function',
                function: [
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.parameters
                ]
            ]
        }
        return req
    }

}

