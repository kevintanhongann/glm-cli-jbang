package core

import models.ChatRequest
import models.ChatResponse
import models.Message
import tools.Tool
import com.fasterxml.jackson.databind.ObjectMapper

import tui.AnsiColors
import tui.DiffRenderer
import tui.InteractivePrompt
import tui.OutputFormatter
import tui.ProgressIndicator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Agent {
    private final GlmClient client
    private final List<Tool> tools = []
    private final List<Message> history = []
    private final ObjectMapper mapper = new ObjectMapper()
    private String model = "glm-4-flash"

    Agent(String apiKey, String model) {
        this.client = new GlmClient(apiKey)
        this.model = model
        AnsiColors.install()
    }

    void registerTool(Tool tool) {
        tools.add(tool)
    }

    void registerTools(List<Tool> tools) {
        this.tools.addAll(tools)
    }

    /**
     * Run the agent with a user prompt.
     * This handles the ReAct loop: Think -> Act -> Observe -> Think
     */
    void run(String prompt) {
        history.add(new Message("user", prompt))
        OutputFormatter.printHeader("GLM Agent")
        OutputFormatter.printInfo("Task: ${prompt}")

        while (true) {
            ChatRequest request = prepareRequest()
            
            ProgressIndicator spinner = new ProgressIndicator()
            spinner.start("Thinking...")
            String responseJson = client.sendMessage(request)
            spinner.stop(true)
            
            ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)
            
            def choice = response.choices[0]
            def message = choice.message
            
            // Print assistant thought (if any content)
            if (message.content) {
                OutputFormatter.printSection("Assistant")
                println message.content
                history.add(new Message("assistant", message.content))
            }

            if (choice.finishReason == "tool_calls" || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                
                def toolCalls = message.toolCalls
                history.add(message)

                toolCalls.each { toolCall ->
                    String functionName = toolCall.function.name
                    String arguments = toolCall.function.arguments
                    String callId = toolCall.id
                    
                    OutputFormatter.printInfo("Executing tool: ${AnsiColors.bold(functionName)}")
                    
                    // Safety & Diff Check for write_file
                    if (functionName == "write_file") {
                        try {
                            Map<String, Object> args = mapper.readValue(arguments, Map.class)
                            String pathStr = args.get("path")
                            String newContent = args.get("content")
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

                        if (!InteractivePrompt.confirm("Apply these changes?")) {
                            OutputFormatter.printWarning("Action denied by user.")
                            
                            Message toolMsg = new Message()
                            toolMsg.role = "tool"
                            toolMsg.content = "Error: User denied permission to execute this tool."
                            toolMsg.toolCallId = callId
                            history.add(toolMsg)
                            return // Skip execution
                        }
                    }

                    Tool tool = tools.find { it.name == functionName }
                    String result = ""
                    if (tool) {
                        try {
                            Map<String, Object> args = mapper.readValue(arguments, Map.class)
                            Object output = tool.execute(args)
                            result = output.toString()
                        } catch (Exception e) {
                            result = "Error executing tool: ${e.message}"
                        }
                    } else {
                        result = "Error: Tool not found."
                    }
                    
                    OutputFormatter.printSection("Tool Output")
                    println result
                    
                    Message toolMsg = new Message()
                    toolMsg.role = "tool"
                    toolMsg.content = result
                    toolMsg.toolCallId = callId
                    history.add(toolMsg)
                }
            } else {
                OutputFormatter.printSuccess("Task completed.")
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
                type: "function",
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

