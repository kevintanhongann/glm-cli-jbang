package core

import models.ChatRequest
import models.ChatResponse
import models.Message
import tools.Tool
import com.fasterxml.jackson.databind.ObjectMapper

import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
import com.github.difflib.patch.Patch
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
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
        println "Agent started with task: ${prompt}"

        while (true) {
            ChatRequest request = prepareRequest()
            
            println "Thinking..."
            String responseJson = client.sendMessage(request)
            ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)
            
            def choice = response.choices[0]
            def message = choice.message
            
            // Print assistant thought (if any content)
            if (message.content) {
                println "Assistant: ${message.content}"
                history.add(new Message("assistant", message.content))
            }

            if (choice.finishReason == "tool_calls" || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                
                def toolCalls = message.toolCalls
                 history.add(message)

                toolCalls.each { toolCall ->
                    String functionName = toolCall.function.name
                    String arguments = toolCall.function.arguments
                    String callId = toolCall.id
                    
                    println "Request to execute tool: ${functionName}"
                    
                    // Safety & Diff Check
                    if (functionName == "write_file") {
                        try {
                            Map<String, Object> args = mapper.readValue(arguments, Map.class)
                            String pathStr = args.get("path")
                            String newContent = args.get("content")
                            Path path = Paths.get(pathStr).normalize()
                            
                            println "\n--- Proposed Changes for ${pathStr} ---"
                            if (Files.exists(path)) {
                                List<String> original = Files.readAllLines(path)
                                List<String> revised = newContent.lines().toList()
                                
                                Patch<String> patch = DiffUtils.diff(original, revised)
                                if (patch.getDeltas().isEmpty()) {
                                    println "(No changes detected)"
                                } else {
                                    patch.getDeltas().forEach { delta ->
                                        println "Original: ${delta.getSource()}"
                                        println "New:      ${delta.getTarget()}"
                                        println "--------------------------------"
                                    }
                                }
                            } else {
                                println "(New File)"
                                println newContent
                            }
                            println "---------------------------------------"
                        } catch (Exception e) {
                            println "Error generating diff: ${e.message}"
                        }

                        System.out.print("Allow write? [y/N]: ")
                        Scanner scanner = new Scanner(System.in)
                        String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "n"
                        if (!input.equalsIgnoreCase("y")) {
                            println "Action denied by user."
                            
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
                    
                    println "Tool Output: ${result}"
                    
                    Message toolMsg = new Message()
                    toolMsg.role = "tool"
                    toolMsg.content = result
                    toolMsg.toolCallId = callId
                    history.add(toolMsg)
                }
            } else {
                println "Task completed."
                break
            }
        }
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
