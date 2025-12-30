//DEPS dev.langchain4j:langchain4j-community-zhipu-ai:1.10.0-beta18
//DEPS dev.langchain4j:langchain4j-agentic:1.10.0-beta18

package core

import dev.langchain4j.community.zhipu.*
import dev.langchain4j.agentic.AgenticServices
import dev.langchain4j.community.zhipu.model.ZhipuAiChatModel
import dev.langchain4j.community.zhipu.model.ZhipuAiStreamingChatModel
import dev.langchain4j.model.chat.*
import dev.langchain4j.model.output.*

/**
 * ZAI Coding Plan client wrapper for GLM-CLI.
 * 
 * This client integrates with Z.AI's Coding Plan service (zai-coding-plan),
 * which provides GLM-4.7 with advanced reasoning and tool calling capabilities
 * specifically for coding scenarios and IDE integrations.
 * 
 * Key features:
 * - Extended context (204,800 tokens vs 131,072 for standard GLM-4)
 * - Thinking mode (chain of thought reasoning)
 * - Tool calling support (up to 128 functions)
 * - Web search integration
 * - Structured JSON output
 * - Zero token cost for input/output (with subscription)
 */
class ZaiCodingPlanClient {
    
    private final String apiKey
    private final String model
    private final String baseUrl
    private final boolean enableThinking
    private final boolean enableTools
    private final boolean streamOutput
    private final Double temperature
    
    ZaiCodingPlanClient(Config config) {
        this.apiKey = config.api.key
        this.model = config.behavior.defaultModel ?: "glm-4.7"
        this.baseUrl = config.api.baseUrl ?: "https://api.z.ai/api/coding/paas/v4"
        this.enableThinking = true
        this.enableTools = true
        this.streamOutput = config.behavior.stream ?: true
        this.temperature = config.agent.temperature ?: 0.7
    }
    
    /**
     * Get the underlying ChatModel (streaming or non-streaming).
     */
    ChatModel getChatModel() {
        if (streamOutput) {
            return ZhipuAiStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .baseUrl(baseUrl)
                    .temperature(temperature)
                    .enableThinking(enableThinking)
                    .build()
        } else {
            return ZhipuAiChatModel.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .baseUrl(baseUrl)
                    .temperature(temperature)
                    .enableThinking(enableThinking)
                    .build()
        }
    }
    
    /**
     * Generate a chat completion response.
     * 
     * @param messages List of conversation messages
     * @param systemPrompt Optional system message
     * @param toolDefinitions Optional tool definitions
     * @return The AI response as String
     */
    String generate(List<ChatMessage> messages, String systemPrompt = null, List<ToolSpecification> toolDefinitions = null) {
        ChatModel chatModel = getChatModel()
        AiMessage aiMessage = new AiMessage()
        
        // Set system message if provided
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            aiMessage.systemContent = systemPrompt
        }
        
        // Set tool definitions if provided
        if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
            chatModel.toolDefinitions(toolDefinitions)
        }
        
        // Add user messages
        for (ChatMessage msg : messages) {
            if (msg.role() == ChatMessageRole.USER.value()) {
                aiMessage.addUserMessage(msg.content())
            } else if (msg.role() == ChatMessageRole.ASSISTANT.value()) {
                aiMessage.addAssistantMessage(msg.content())
            }
        }
        
        // Generate response (synchronous for simplicity)
        ChatCompletionResponse response = chatModel.generate(aiMessage)
        
        if (response.choices != null && !response.choices.isEmpty()) {
            return response.choices.get(0).message().content()
        }
        
        return "No response from ZAI Coding Plan API"
    }
    
    /**
     * Generate a chat completion response with streaming.
     * 
     * @param messages List of conversation messages
     * @param onToken Callback for each token
     * @return The AI response as String
     */
    void generateStream(List<ChatMessage> messages, Closure onToken) {
        if (streamOutput) {
            ZhipuAiStreamingChatModel chatModel = (ZhipuAiStreamingChatModel) getChatModel()
            AiMessage aiMessage = new AiMessage()
            
            // Set system message if provided
            if (messages && !messages.isEmpty()) {
                def firstMessage = messages.get(0)
                if (firstMessage.role() == ChatMessageRole.SYSTEM.value()) {
                    aiMessage.systemContent = firstMessage.content()
                }
            }
            
            chatModel.stream(aiMessage, onToken)
        } else {
            println "Error: Streaming not enabled. Use generate() instead."
        }
    }
}
