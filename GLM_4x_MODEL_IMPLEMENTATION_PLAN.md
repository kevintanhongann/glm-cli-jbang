# GLM-4.x Model Implementation Plan

## Overview

This document outlines the implementation plan for adding support for the latest Z.AI GLM models to GLM-CLI:

- **GLM-4.7** - Flagship LLM with enhanced coding and reasoning
- **GLM-4.6** - High-performance LLM with long context (200K)
- **GLM-4.5** - Agent-optimized LLM with thinking modes
- **GLM-4.6V** - Vision-Language Model with multimodal capabilities
- **GLM-4.5V** - Lightweight VLM with GUI agent support

### Key Features to Support

- **Thinking Mode**: Chain-of-thought reasoning with `thinking` parameter
- **Vision Capabilities**: Image/video input for VLM models via `image_url` content type
- **Enhanced Context**: Up to 200K tokens (vs. current 128K)
- **Reasoning Content**: Separate `reasoning_content` field in streaming responses
- **Model Variants**: Flash, FlashX, Air versions for different use cases

## Current State Analysis

### Existing Architecture

```
GlmClient.groovy        → HTTP client with JWT auth (open.bigmodel.cn)
ChatRequest.groovy      → Basic request model (model, messages, stream, temp)
ChatResponse.groovy      → Basic response model (id, choices, usage)
Message.groovy           → Simple role/content structure
Config.groovy            → TOML-based config with default_model = "glm-4-flash"
ChatCommand.groovy       → Interactive chat with streaming
AgentCommand.groovy      → ReAct agent with tools
```

### Limitations

1. No support for thinking mode parameter
2. No multimodal content support (text only)
3. No model capability metadata
4. Limited to older model names (glm-4, glm-4-flash, glm-4-plus)
5. No `reasoning_content` field in responses

## Implementation Phases

### Phase 1: Core API Updates

#### 1.2 Add Thinking Mode Support

**File**: `models/ChatRequest.groovy`

**Add field**:
```groovy
@JsonInclude(JsonInclude.Include.NON_NULL)
class ChatRequest {
    String model
    List<Message> messages
    Boolean stream
    Double temperature

    @JsonProperty("max_tokens")
    Integer maxTokens

    @JsonProperty("thinking")
    Map<String, Object> thinking  // NEW: e.g., ["type": "enabled"]

    @JsonProperty("tools")
    List<Object> tools

    @JsonProperty("tool_choice")
    Object toolChoice
}
```

**File**: `core/GlmClient.groovy`

**Update sendMessage/streamMessage**:
```groovy
String sendMessage(ChatRequest request, Map<String, Object> thinking = null) {
    if (thinking != null) {
        request.thinking = thinking
    }
    // ... rest of method
}
```

#### 1.3 Handle Reasoning Content

**File**: `models/ChatResponse.groovy`

**Update Message/Choice classes**:
```groovy
@JsonIgnoreProperties(ignoreUnknown = true)
class ChatResponse {
    String id
    Long created
    String model
    List<Choice> choices
    Usage usage

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        Integer index
        @JsonProperty("finish_reason")
        String finishReason
        Message message // For non-streaming
        Message delta   // For streaming
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Usage {
        @JsonProperty("prompt_tokens")
        Integer promptTokens
        @JsonProperty("completion_tokens")
        Integer completionTokens
        @JsonProperty("total_tokens")
        Integer totalTokens

        @JsonProperty("total_input_tokens")  // NEW: Z.AI API
        Integer totalInputTokens
    }
}
```

**File**: `models/Message.groovy`

**Add reasoning field**:
```groovy
@JsonInclude(JsonInclude.Include.NON_NULL)
class Message {
    String role
    String content

    @JsonProperty("reasoning_content")  // NEW: Thinking mode output
    String reasoningContent

    @JsonProperty("tool_calls")
    List<ToolCall> toolCalls

    @JsonProperty("tool_call_id")
    String toolCallId

    // ... rest of class
}
```

### Phase 2: Multimodal Support

#### 2.1 Update Message for Multimodal Content

**File**: `models/Message.groovy`

**Restructure to support both string and array content**:
```groovy
@JsonInclude(JsonInclude.Include.NON_NULL)
class Message {
    String role

    @JsonProperty("content")
    Object content  // String OR List<ContentItem>

    @JsonProperty("reasoning_content")
    String reasoningContent

    @JsonProperty("tool_calls")
    List<ToolCall> toolCalls

    @JsonProperty("tool_call_id")
    String toolCallId

    // String constructor (backward compatible)
    Message(String role, String content) {
        this.role = role
        this.content = content
    }

    // Array constructor (multimodal)
    Message(String role, List<ContentItem> contentItems) {
        this.role = role
        this.content = contentItems
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ContentItem {
        String type  // "text" or "image_url" or "video"

        String text  // For type="text"

        ImageUrl image_url  // For type="image_url"

        String video  // For type="video" (base64 or URL)

        static class ImageUrl {
            String url
            String detail  // "auto", "low", "high" (optional)
        }
    }

    // ... ToolCall classes remain same
}
```

#### 2.2 Helper Methods for Content Construction

**File**: `models/Message.groovy` or new `MessageBuilder.groovy`

```groovy
class MessageBuilder {
    static Message text(String role, String text) {
        new Message(role, text)
    }

    static Message image(String role, String imageUrl, String text) {
        return new Message(role, [
            new ContentItem(type: "text", text: text),
            new ContentItem(
                type: "image_url",
                image_url: new ImageUrl(url: imageUrl)
            )
        ])
    }

    static Message multiImage(String role, List<String> imageUrls, String text) {
        def items = [new ContentItem(type: "text", text: text)]
        imageUrls.each { url ->
            items.add(new ContentItem(
                type: "image_url",
                image_url: new ImageUrl(url: url)
            ))
        }
        return new Message(role, items)
    }
}
```

### Phase 3: Model Registry

#### 3.1 Create Model Information Class

**File**: `models/ModelInfo.groovy`

```groovy
package models

enum ModelType {
    LLM,
    VLM
}

class ModelInfo {
    String id
    String displayName
    ModelType type
    String description
    Integer contextLength
    Integer maxOutputTokens
    Boolean supportsThinking
    Boolean supportsVision
    Boolean supportsVideo
    List<String> variants
    Double costPerMInput
    Double costPerMOutput
    String defaultUseCase
    String category  // "flagship", "high-speed", "lightweight", "free"
}

class ModelRegistry {
    private static final Map<String, ModelInfo> MODELS = [
        // GLM-4.7 Series
        "glm-4.7": new ModelInfo(
            id: "glm-4.7",
            displayName: "GLM-4.7",
            type: ModelType.LLM,
            description: "Latest flagship model with enhanced coding, reasoning, and agent capabilities",
            contextLength: 200000,
            maxOutputTokens: 128000,
            supportsThinking: true,
            supportsVision: false,
            supportsVideo: false,
            variants: [],
            costPerMInput: null,  // Subscription-based
            costPerMOutput: null,
            defaultUseCase: "Complex coding tasks, multi-step reasoning, full-stack development",
            category: "flagship"
        ),

        "glm-4.6": new ModelInfo(
            id: "glm-4.6",
            displayName: "GLM-4.6",
            type: ModelType.LLM,
            description: "High-performance model with 200K context, optimized for real-world coding",
            contextLength: 200000,
            maxOutputTokens: 128000,
            supportsThinking: true,
            supportsVision: false,
            supportsVideo: false,
            variants: ["glm-4.6-flashx", "glm-4.6-flash"],
            costPerMInput: 1.2,
            costPerMOutput: 3.0,
            defaultUseCase: "Long-context tasks, code refactoring, large project analysis",
            category: "flagship"
        ),

        "glm-4.5": new ModelInfo(
            id: "glm-4.5",
            displayName: "GLM-4.5",
            type: ModelType.LLM,
            description: "Agent-optimized MoE model with 355B total parameters",
            contextLength: 128000,
            maxOutputTokens: 96000,
            supportsThinking: true,
            supportsVision: false,
            supportsVideo: false,
            variants: ["glm-4.5-air", "glm-4.5-x", "glm-4.5-airx", "glm-4.5-flash"],
            costPerMInput: 0.5,
            costPerMOutput: 1.5,
            defaultUseCase: "Autonomous agents, tool invocation, web browsing",
            category: "flagship"
        ),

        "glm-4.5-flash": new ModelInfo(
            id: "glm-4.5-flash",
            displayName: "GLM-4.5-Flash",
            type: ModelType.LLM,
            description: "Free model with strong performance for reasoning, coding & agents",
            contextLength: 128000,
            maxOutputTokens: 96000,
            supportsThinking: true,
            supportsVision: false,
            supportsVideo: false,
            variants: [],
            costPerMInput: 0.0,
            costPerMOutput: 0.0,
            defaultUseCase: "Fast prototyping, cost-free development, learning experiments",
            category: "free"
        ),

        "glm-4.6v": new ModelInfo(
            id: "glm-4.6v",
            displayName: "GLM-4.6V",
            type: ModelType.VLM,
            description: "Flagship VLM with native multimodal tool calling, 128K context",
            contextLength: 128000,
            maxOutputTokens: 16000,
            supportsThinking: true,
            supportsVision: true,
            supportsVideo: true,
            variants: ["glm-4.6v-flashx", "glm-4.6v-flash"],
            costPerMInput: 1.5,
            costPerMOutput: 4.5,
            defaultUseCase: "Web UI generation, visual web search, document understanding",
            category: "flagship"
        ),

        "glm-4.5v": new ModelInfo(
            id: "glm-4.5v",
            displayName: "GLM-4.5V",
            type: ModelType.VLM,
            description: "Lightweight VLM (106B params) with GUI agent support",
            contextLength: 128000,
            maxOutputTokens: 16000,
            supportsThinking: true,
            supportsVision: true,
            supportsVideo: true,
            variants: [],
            costPerMInput: 0.6,
            costPerMOutput: 1.8,
            defaultUseCase: "GUI automation, image reasoning, video understanding",
            category: "lightweight"
        ),

        // Legacy model aliases (for backward compatibility)
        "glm-4": new ModelInfo(
            id: "glm-4",
            displayName: "GLM-4 (Legacy)",
            type: ModelType.LLM,
            description: "Legacy model alias, consider upgrading to GLM-4.7",
            contextLength: 128000,
            maxOutputTokens: 8192,
            supportsThinking: false,
            supportsVision: false,
            supportsVideo: false,
            variants: [],
            costPerMInput: 0.05,
            costPerMOutput: 0.1,
            defaultUseCase: "Basic chat, simple tasks",
            category: "legacy"
        ),

        "glm-4-flash": new ModelInfo(
            id: "glm-4-flash",
            displayName: "GLM-4-Flash (Legacy)",
            type: ModelType.LLM,
            description: "Legacy fast model, consider upgrading to GLM-4.5-Flash",
            contextLength: 128000,
            maxOutputTokens: 8192,
            supportsThinking: false,
            supportsVision: false,
            supportsVideo: false,
            variants: [],
            costPerMInput: 0.05,
            costPerMOutput: 0.1,
            defaultUseCase: "Quick responses, simple queries",
            category: "legacy"
        ),
    ]

    static ModelInfo getModel(String modelId) {
        ModelInfo info = MODELS.get(modelId)
        if (info == null) {
            throw new IllegalArgumentException("Unknown model: ${modelId}. Available: ${MODELS.keySet()}")
        }
        return info
    }

    static List<ModelInfo> getAllModels() {
        return MODELS.values().toList()
    }

    static List<ModelInfo> getModelsByType(ModelType type) {
        return MODELS.values().findAll { it.type == type }
    }

    static List<ModelInfo> getNonLegacyModels() {
        return MODELS.values().findAll { it.category != "legacy" }
    }

    static String suggestModel(String taskDescription) {
        // Simple heuristic - can be enhanced with NLP
        String desc = taskDescription.toLowerCase()

        if (desc.contains("image") || desc.contains("picture") || desc.contains("visual")) {
            return "glm-4.6v"
        }
        if (desc.contains("video") || desc.contains("screenshot")) {
            return "glm-4.6v"
        }
        if (desc.contains("code") && (desc.contains("complex") || desc.contains("full"))) {
            return "glm-4.7"
        }
        if (desc.contains("agent") || desc.contains("autonomous")) {
            return "glm-4.5"
        }
        if (desc.contains("long") && (desc.contains("context") || desc.contains("large"))) {
            return "glm-4.6"
        }

        return "glm-4.7"  // Default to flagship
    }
}
```

#### 3.2 Add Model Validation

**File**: `core/Agent.groovy`, `commands/ChatCommand.groovy`

```groovy
// At model selection
try {
    ModelInfo modelInfo = ModelRegistry.getModel(modelToUse)

    // Warn if using VLM without images
    if (modelInfo.type == ModelType.VLM && !hasImages) {
        System.out.println("Note: Using VLM model ${modelInfo.displayName} without image input.")
        System.out.println("Consider using an LLM model for text-only tasks.")
    }

    // Warn if using legacy model
    if (modelInfo.category == "legacy") {
        System.out.println("Warning: Using legacy model ${modelInfo.displayName}.")
        System.out.println("Consider upgrading to ${ModelRegistry.suggestModel('')} for better performance.")
    }

    // Check context requirements
    if (estimatedTokens > modelInfo.contextLength) {
        System.err.println("Error: Content exceeds model's context limit (${modelInfo.contextLength} tokens)")
        System.err.println("Consider: ${ModelRegistry.getModelsByType(ModelType.LLM).find { it.contextLength > estimatedTokens }?.id}")
        return
    }

} catch (IllegalArgumentException e) {
    System.err.println(e.message)
    // List available models
    println "\nAvailable models:"
    ModelRegistry.getAllModels().each {
        println "  - ${it.id}: ${it.displayName} (${it.category})"
    }
}
```

### Phase 4: Configuration Updates

#### 4.1 Extend Config Class

**File**: `core/Config.groovy`

```groovy
@JsonIgnoreProperties(ignoreUnknown = true)
class Config {
    @JsonProperty("api")
    ApiConfig api = new ApiConfig()

    @JsonProperty("behavior")
    BehaviorConfig behavior = new BehaviorConfig()

    @JsonProperty("web_search")
    WebSearchConfig webSearch = new WebSearchConfig()

    @JsonProperty("rag")
    RagConfig rag = new RagConfig()

    @JsonProperty("tui")
    TuiConfig tui = new TuiConfig()

    @JsonProperty("thinking")  // NEW
    ThinkingConfig thinking = new ThinkingConfig()

    static class ApiConfig {
        String key
        @JsonProperty("base_url")
        String baseUrl
        @JsonProperty("use_new_endpoint")  // NEW
        Boolean useNewEndpoint = true  // Default to new Z.AI API
        @JsonProperty("timeout_seconds")
        Integer timeoutSeconds = 30
    }

    static class BehaviorConfig {
        String language = "auto"
        @JsonProperty("safety_mode")
        String safetyMode = "ask"
        @JsonProperty("default_model")
        String defaultModel = "glm-4.7"  // NEW: Updated default
    }

    static class ThinkingConfig {  // NEW
        Boolean enabled = true
        @JsonProperty("default_mode")
        String defaultMode = "enabled"  // "enabled" or "disabled"
        @JsonProperty("always_enabled_for_models")
        List<String> alwaysEnabledForModels = ["glm-4.7", "glm-4.6", "glm-4.5"]
    }

    // ... other config classes unchanged
}
```

**Updated config.toml example**:
```toml
[api]
key = "your-api-key-here"
base_url = "https://api.z.ai/api/paas/v4/"
use_new_endpoint = true
timeout_seconds = 30

[behavior]
default_model = "glm-4.7"
safety_mode = "ask"
language = "auto"

[thinking]
enabled = true
default_mode = "enabled"
always_enabled_for_models = ["glm-4.7", "glm-4.6", "glm-4.5"]

[web_search]
enabled = true
default_count = 10

[rag]
enabled = false
cache_dir = "~/.glm/embeddings"

[tui]
colors_enabled = true
diff_context_lines = 3
```

### Phase 5: Command Updates

#### 5.1 Update ChatCommand

**File**: `commands/ChatCommand.groovy`

```groovy
@Command(name = "chat", description = "Start a chat session with GLM-4", mixinStandardHelpOptions = true)
class ChatCommand implements Runnable {

    @Option(names = ["-m", "--model"], description = "Model to use (default: glm-4.7)")
    String model = "glm-4.7"

    @Option(names = ["-t", "--thinking"], description = "Enable thinking mode (enabled/disabled)")
    String thinkingMode

    @Option(names = ["-i", "--image"], description = "URL of image to analyze (for VLM models)")
    String imageUrl

    @Option(names = ["-v", "--video"], description = "URL of video to analyze (for VLM models)")
    String videoUrl

    @Parameters(index = "0", arity = "0..1", description = "Initial message")
    String initialMessage

    private List<Message> history = []
    private GlmClient client
    private ModelInfo modelInfo

    @Override
    void run() {
        Config config = Config.load()
        String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
        String modelToUse = model ?: config.behavior.defaultModel

        if (!apiKey) {
            System.err.println("Error: API Key not found. Set ZAI_API_KEY env var or configure ~/.glm/config.toml")
            return
        }

        // Validate and get model info
        try {
            modelInfo = ModelRegistry.getModel(modelToUse)
        } catch (IllegalArgumentException e) {
            System.err.println(e.message)
            return
        }

        // Configure thinking mode
        Map<String, Object> thinking = null
        if (thinkingMode) {
            thinking = [type: thinkingMode]
        } else if (config.thinking.enabled && modelInfo.supportsThinking) {
            thinking = [type: config.thinking.defaultMode]
        }

        // Validate vision input
        if ((imageUrl || videoUrl) && !modelInfo.supportsVision) {
            System.err.println("Error: Model ${modelInfo.displayName} does not support vision input")
            System.err.println("Use a VLM model: glm-4.6v or glm-4.5v")
            return
        }

        client = new GlmClient(apiKey)
        println "Starting chat with model: ${modelInfo.displayName} (${modelInfo.id})"
        println "Context: ${modelInfo.contextLength} tokens | Max Output: ${modelInfo.maxOutputTokens} tokens"
        if (modelInfo.supportsThinking && thinking) {
            println "Thinking mode: ${thinking.type}"
        }
        println "(Type 'exit' or 'quit' to stop)\n"

        this.model = modelToUse

        if (initialMessage) {
            processInput(initialMessage, thinking, imageUrl, videoUrl)
        }

        Scanner scanner = new Scanner(System.in)
        while (true) {
            print "\n> "
            if (!scanner.hasNextLine()) break
            String input = scanner.nextLine().trim()

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break
            }
            if (input.isEmpty()) continue

            processInput(input, thinking, null, null)
        }
    }

    private void processInput(String input, Map<String, Object> thinking, String imageUrl, String videoUrl) {
        // Build message with multimodal support
        Message userMessage
        if (imageUrl || videoUrl) {
            List<ContentItem> items = [new ContentItem(type: "text", text: input)]
            if (imageUrl) {
                items.add(new ContentItem(
                    type: "image_url",
                    image_url: new ImageUrl(url: imageUrl)
                ))
            }
            if (videoUrl) {
                items.add(new ContentItem(type: "video", video: videoUrl))
            }
            userMessage = new Message("user", items)
        } else {
            userMessage = new Message("user", input)
        }

        history.add(userMessage)

        ChatRequest request = new ChatRequest()
        request.model = model
        request.messages = history
        request.stream = true
        if (thinking) {
            request.thinking = thinking
        }

        StringBuffer fullResponse = new StringBuffer()
        StringBuffer reasoningBuffer = new StringBuffer()

        try {
            client.streamMessage(request) { ChatResponse chunk ->
                if (chunk.choices && !chunk.choices.isEmpty()) {
                    def delta = chunk.choices[0].delta
                    if (delta) {
                        if (delta.reasoningContent) {
                            // Display reasoning in different format (optional)
                            print "\n[Thinking] "
                            print delta.reasoningContent
                            reasoningBuffer.append(delta.reasoningContent)
                            System.out.flush()
                        }
                        if (delta.content) {
                            print delta.content
                            fullResponse.append(delta.content)
                            System.out.flush()
                        }
                    }
                }
            }
            println() // Newline after stream
            history.add(new Message("assistant", fullResponse.toString()))

        } catch (Exception e) {
            System.err.println("\nError: ${e.message}")
            e.printStackTrace()
        }
    }
}
```

#### 5.2 Update AgentCommand

**File**: `commands/AgentCommand.groovy`

```groovy
@Command(name = "agent", description = "Run an autonomous agent task", mixinStandardHelpOptions = true)
class AgentCommand implements Runnable {

    @Parameters(index = "0", description = "The task to perform")
    String task

    @Option(names = ["-m", "--model"], description = "Model to use (default: glm-4.5)")
    String model = "glm-4.5"

    @Option(names = ["-t", "--thinking"], description = "Enable thinking mode for agent")
    Boolean enableThinking = true

    @Option(names = ["--index-codebase", "-i"], description = "Path to codebase to index for RAG semantic search")
    String codebasePath

    @Option(names = ["--rag"], description = "Enable RAG-based code search (requires prior indexing)")
    boolean enableRag = false

    @Override
    void run() {
        Config config = Config.load()
        String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key

        if (!apiKey) {
            System.err.println("Error: API Key not found. Set ZAI_API_KEY env var or configure ~/.glm/config.toml")
            return
        }

        // Validate model
        try {
            ModelInfo modelInfo = ModelRegistry.getModel(model)
            println "Using agent model: ${modelInfo.displayName}"
            if (modelInfo.category == "legacy") {
                println "Warning: Consider upgrading to GLM-4.5 for better agent performance"
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.message)
            return
        }

        // Create agent with thinking mode
        Agent agent = new Agent(apiKey, model, enableThinking)

        // Register standard tools
        agent.registerTool(new ReadFileTool())
        agent.registerTool(new WriteFileTool())
        agent.registerTool(new ListFilesTool())

        if (config.webSearch.enabled) {
            agent.registerTool(new WebSearchTool(apiKey))
        }

        // RAG integration
        RAGPipeline ragPipeline = null
        if (codebasePath || enableRag || config.rag.enabled) {
            ragPipeline = new RAGPipeline(config.rag.cacheDir)
            if (codebasePath) {
                println "Indexing codebase at: ${codebasePath}"
                ragPipeline.indexCodebase(codebasePath)
            }
            agent.registerTool(new CodeSearchTool(ragPipeline))
        }

        try {
            agent.run(task)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
```

#### 5.3 Update Agent Class

**File**: `core/Agent.groovy`

```groovy
class Agent {
    private final String apiKey
    private final String model
    private final Map<String, Tool> tools = [:]
    private final GlmClient client
    private final boolean enableThinking
    private final int maxSteps = 20

    // Default updated to glm-4.5
    Agent(String apiKey, String model = "glm-4.5", boolean enableThinking = true) {
        this.apiKey = apiKey
        this.model = model
        this.enableThinking = enableThinking
        this.client = new GlmClient(apiKey)
    }

    void registerTool(Tool tool) {
        tools[tool.name] = tool
    }

    void run(String userTask) {
        List<Map<String, Object>> conversation = []

        for (int step = 0; step < maxSteps; step++) {
            // Prepare messages
            List<Message> messages = conversation.collect { msg ->
                new Message(msg.role as String, msg.content as String)
            }

            ChatRequest request = new ChatRequest()
            request.model = model
            request.messages = messages
            request.stream = false

            // Add tools
            if (!tools.isEmpty()) {
                request.tools = tools.values().collect { tool ->
                    [
                        type: "function",
                        function: [
                            name: tool.name,
                            description: tool.description,
                            parameters: tool.getParameters()
                        ]
                    ]
                }
            }
            request.toolChoice = "auto"

            // Enable thinking if supported
            try {
                ModelInfo modelInfo = ModelRegistry.getModel(model)
                if (enableThinking && modelInfo.supportsThinking) {
                    request.thinking = [type: "enabled"]
                }
            } catch (Exception e) {
                // Model info not available, skip thinking
            }

            try {
                String responseJson = client.sendMessage(request)
                ChatResponse response = new ObjectMapper().readValue(responseJson, ChatResponse.class)

                def assistantMessage = response.choices[0].message

                if (assistantMessage.toolCalls && !assistantMessage.toolCalls.isEmpty()) {
                    // Execute tools
                    for (def toolCall : assistantMessage.toolCalls) {
                        Tool tool = tools[toolCall.function.name]
                        if (!tool) {
                            System.err.println("Unknown tool: ${toolCall.function.name}")
                            continue
                        }

                        def args = new ObjectMapper().readValue(
                            toolCall.function.arguments,
                            Map.class
                        )

                        println "\n[Tool] ${tool.name}(${args})"
                        String result = tool.execute(args) as String

                        // Add tool result to conversation
                        conversation.add([
                            role: "assistant",
                            content: null,
                            tool_calls: [toolCall]
                        ])
                        conversation.add([
                            role: "tool",
                            tool_call_id: toolCall.id,
                            content: result
                        ])
                    }
                } else {
                    // Task complete
                    String content = assistantMessage.content
                    println "\n${content}"
                    break
                }

            } catch (Exception e) {
                System.err.println("Error: ${e.message}")
                e.printStackTrace()
                break
            }
        }

        if (steps >= maxSteps) {
            println "\nTask stopped after ${maxSteps} steps"
        }
    }
}
```

### Phase 6: Examples and Documentation

#### 6.1 Create Example Scripts

**File**: `examples/Glm47Example.groovy`

```groovy
#!/usr/bin/env jbang

//DEPS dev.langchain4j:langchain4j-community-zhipu-ai:1.10.0-beta18

import dev.langchain4j.community.model.zhipu.*
import dev.langchain4j.model.chat.*

/**
 * GLM-4.7 Example - Advanced coding with thinking mode
 *
 * Demonstrates:
 * - GLM-4.7 API integration
 * - Thinking mode for complex reasoning
 * - Structured JSON output
 * - Multi-step task decomposition
 */
class Glm47Example {

    static void main(String[] args) {
        String apiKey = System.getenv("ZAI_API_KEY") ?: args[0]
        if (!apiKey) {
            System.err.println("Usage: jbang Glm47Example.groovy <api-key>")
            return
        }

        // Create chat model with thinking enabled
        def chatModel = ZhipuAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .model("glm-4.7")
            .enableThinking(true)
            .temperature(0.7)
            .build()

        // Example 1: Complex coding task with reasoning
        println "=== Example 1: Refactoring with Thinking Mode ===\n"

        chatModel.stream("""
            You are a senior software architect. Refactor this class to follow SOLID principles:

            class User {
                String name
                String email
                String password
                String address
                String phone

                void saveToDatabase() { /* implementation */ }
                void sendWelcomeEmail() { /* implementation */ }
                void validatePassword() { /* implementation */ }
                void encryptPassword() { /* implementation */ }
                void formatPhoneNumber() { /* implementation */ }
            }

            Explain your approach before writing code. Break down into separate classes with clear responsibilities.
        """.trim()) { token ->
            print token
            System.out.flush()
        }

        println "\n\n=== Example 2: Full-stack task ===\n"

        // Example 2: Complete feature implementation
        chatModel.stream("""
            Create a complete REST API endpoint for user authentication in Java Spring Boot.

            Requirements:
            1. POST /api/auth/register
            2. POST /api/auth/login
            3. JWT token generation
            4. Password validation
            5. Email verification
            6. Error handling

            Include:
            - Controller
            - Service layer
            - Repository interface
            - DTOs
            - Validation annotations
            - Security configuration

            Think through the architecture first, then implement.
        """.trim()) { token ->
            print token
            System.out.flush()
        }

        println "\n\n=== Example 3: Algorithm optimization ===\n"

        // Example 3: Performance optimization
        chatModel.stream("""
            Optimize this function for better performance:

            def findDuplicates(List<String> items) {
                List<String> duplicates = []
                for (int i = 0; i < items.size(); i++) {
                    for (int j = i + 1; j < items.size(); j++) {
                        if (items[i] == items[j]) {
                            duplicates.add(items[i])
                        }
                    }
                }
                return duplicates
            }

            Explain the time complexity issue, then provide an optimized solution.
        """.trim()) { token ->
            print token
            System.out.flush()
        }
    }
}
```

**File**: `examples/Glm46Example.groovy`

```groovy
#!/usr/bin/env jbang

//DEPS dev.langchain4j:langchain4j-community-zhipu-ai:1.10.0-beta18

import dev.langchain4j.community.model.zhipu.*

/**
 * GLM-4.6 Example - Long context reasoning
 */
class Glm46Example {

    static void main(String[] args) {
        String apiKey = System.getenv("ZAI_API_KEY") ?: args[0]
        if (!apiKey) {
            System.err.println("Usage: jbang Glm46Example.groovy <api-key>")
            return
        }

        def chatModel = ZhipuAiChatModel.builder()
            .apiKey(apiKey)
            .model("glm-4.6")
            .enableThinking(true)
            .temperature(0.6)
            .build()

        println "=== Long Context Document Analysis ===\n"

        // Simulate large document analysis
        String document = """
            [Large document content - up to 200K tokens]
            Multiple files, codebases, or long reports can be analyzed in a single context.
        """.trim()

        String prompt = """
            Analyze this document and provide:
            1. Executive summary (3 bullet points)
            2. Key insights (5 bullet points)
            3. Action items (numbered list)
            4. Related topics (3 items)
            5. Open questions (3 items)

            Keep analysis focused and actionable.
        """.trim()

        String response = chatModel.generate(document + "\n\n" + prompt)
        println response
    }
}
```

**File**: `examples/Glm46vExample.groovy`

```groovy
#!/usr/bin/env jbang

//DEPS dev.langchain4j:langchain4j-community-zhipu-ai:1.10.0-beta18

import dev.langchain4j.community.model.zhipu.*

/**
 * GLM-4.6V Example - Vision-Language Model
 */
class Glm46vExample {

    static void main(String[] args) {
        String apiKey = System.getenv("ZAI_API_KEY") ?: args[0]
        if (!apiKey) {
            System.err.println("Usage: jbang Glm46vExample.groovy <api-key>")
            return
        }

        def chatModel = ZhipuAiChatModel.builder()
            .apiKey(apiKey)
            .model("glm-4.6v")
            .enableThinking(true)
            .build()

        println "=== Example 1: Image Analysis ===\n"

        String imageUrl = args[1] ?: "https://example.com/image.png"

        def userMessage = dev.langchain4j.data.message.UserMessage.from(
            "Analyze this image and describe what you see. Focus on:"
            + " 1. Main subject\n"
            + " 2. Composition\n"
            + " 3. Colors and lighting\n"
            + " 4. Mood or emotion conveyed\n"
            + " 5. Technical quality assessment\n",
            dev.langchain4j.data.message.ImageContent.from(imageUrl)
        )

        String response = chatModel.generate(userMessage)
        println response

        println "\n=== Example 2: Multi-Image Comparison ===\n"

        def multiImageMessage = dev.langchain4j.data.message.UserMessage.from(
            "Compare these two images. What are the similarities and differences?",
            dev.langchain4j.data.message.ImageContent.from(args[1]),
            dev.langchain4j.data.message.ImageContent.from(args[2])
        )

        response = chatModel.generate(multiImageMessage)
        println response
    }
}
```

**File**: `examples/Glm45vExample.groovy`

```groovy
#!/usr/bin/env jbang

//DEPS dev.langchain4j:langchain4j-community-zhipu-ai:1.10.0-beta18

import dev.langchain4j.community.model.zhipu.*

/**
 * GLM-4.5V Example - GUI Agent Tasks
 */
class Glm45vExample {

    static void main(String[] args) {
        String apiKey = System.getenv("ZAI_API_KEY") ?: args[0]
        if (!apiKey) {
            System.err.println("Usage: jbang Glm45vExample.groovy <api-key>")
            return
        }

        def chatModel = ZhipuAiChatModel.builder()
            .apiKey(apiKey)
            .model("glm-4.5v")
            .enableThinking(true)
            .build()

        println "=== Example 1: Web Screenshot Analysis ===\n"

        String screenshotUrl = args[1] ?: "https://example.com/screenshot.png"

        def message = dev.langchain4j.data.message.UserMessage.from(
            "I've taken a screenshot of a webpage. Please:"
            + " 1. Identify all interactive elements (buttons, links, forms)\n"
            + " 2. Describe the page layout\n"
            + " 3. Suggest 3 UX improvements\n"
            + " 4. Generate HTML/CSS code to recreate this design",
            dev.langchain4j.data.message.ImageContent.from(screenshotUrl)
        )

        String response = chatModel.generate(message)
        println response

        println "\n=== Example 2: Document OCR and Analysis ===\n"

        String documentUrl = args[2]

        def docMessage = dev.langchain4j.data.message.UserMessage.from(
            "Extract all text from this document image and organize it into a structured format."
            + " Then provide a 3-sentence summary.",
            dev.langchain4j.data.message.ImageContent.from(documentUrl)
        )

        response = chatModel.generate(docMessage)
        println response
    }
}
```

#### 6.2 Update README

**File**: `README.md`

**Update section: Features**

```markdown
| Feature | Description |
|---------|-------------|
| **Chat** | Interactive conversation with GLM-4 models (`glm-4.7`, `glm-4.6`, `glm-4.5`, `glm-4.5-flash`) |
| **Agent** | Autonomous task execution with ReAct loop for reading/writing files |
| **Vision** | Image and video analysis with GLM-4.6V and GLM-4.5V |
| **Web Search** | Integrated web search with real-time information retrieval |
| **Tools** | Built-in tools (`read_file`, `write_file`, `list_files`, `web_search`) with safety checks |
| **Streaming** | Real-time response streaming with thinking mode support |
| **Diff Preview** | See file changes before applying with diff visualization |
| **Configurable** | TOML-based configuration for API keys, models, and behavior |
| **JWT Auth** | Secure API authentication with automatic token caching |
```

**Update section: Available Models**

```markdown
### Available Models

GLM-CLI supports the latest Z.AI models:

#### Language Models (LLM)

| Model | Description | Context | Use Case |
|-------|-------------|----------|-----------|
| `glm-4.7` | Latest flagship with enhanced coding & reasoning | 200K | Complex coding, multi-step reasoning, full-stack development |
| `glm-4.6` | High-performance with long context | 200K | Long-context tasks, code refactoring, large projects |
| `glm-4.5` | Agent-optimized with tool invocation | 128K | Autonomous agents, web browsing, complex workflows |
| `glm-4.5-flash` | Free model with strong performance | 128K | Prototyping, learning experiments, cost-free dev |
| `glm-4-flash` | Legacy fast model (consider upgrading) | 128K | Quick responses, simple queries |

#### Vision-Language Models (VLM)

| Model | Description | Context | Use Case |
|-------|-------------|----------|-----------|
| `glm-4.6v` | Flagship VLM with multimodal tool calling | 128K | Web UI generation, visual search, document understanding |
| `glm-4.5v` | Lightweight VLM with GUI agent support | 128K | GUI automation, image reasoning, video understanding |

**Note**: VLM models require image/video input via `--image` or `--video` flags.
```

**Update section: Usage with new models**

```markdown
### Chat with Thinking Mode

```bash
# Enable thinking mode for complex tasks
glm chat --thinking enabled "Design a microservices architecture for an e-commerce platform"

# Disable thinking mode for quick responses
glm chat --thinking disabled "What's the capital of France?"

# Use specific model
glm chat --model glm-4.7 --thinking enabled "Refactor this legacy code to follow SOLID principles"
```

### Vision Analysis

```bash
# Analyze an image
glm chat --model glm-4.6v --image https://example.com/screenshot.png "Analyze this UI design"

# Analyze a video
glm chat --model glm-4.6v --video https://example.com/demo.mp4 "Summarize the key actions shown"

# Web screenshot to code
glm chat --model glm-4.6v --image ./screenshot.png "Generate HTML/CSS code for this design"
```

### Agent with New Models

```bash
# Use GLM-4.5 for agent tasks (default)
glm agent "Create a REST API for user management"

# Use GLM-4.7 for complex coding tasks
glm agent --model glm-4.7 "Add authentication to the existing API"

# Disable thinking for simple tasks
glm agent --thinking false "Update README with new features"
```
```

#### 6.3 Update CONFIGURATION.md

**File**: `CONFIGURATION.md`

**Add sections**:

```markdown
### API Configuration

```toml
[api]
key = "your-api-key-id.secret"
base_url = "https://api.z.ai/api/paas/v4/"
use_new_endpoint = true  # Enable new Z.AI API
timeout_seconds = 30
```

- `key`: Your Z.AI API key in `id.secret` format
- `base_url`: Override API endpoint (default: new Z.AI API)
- `use_new_endpoint`: Use new `api.z.ai` endpoint (recommended)
- `timeout_seconds`: HTTP request timeout

### Model Configuration

```toml
[behavior]
default_model = "glm-4.7"  # Updated default
language = "auto"  # "auto", "en", "zh"
safety_mode = "ask"  # "ask", "always_allow"
```

- `default_model`: Default model to use (see `Available Models` section)
- `language`: Response language preference
- `safety_mode`: File write permission handling

### Thinking Mode Configuration

```toml
[thinking]
enabled = true
default_mode = "enabled"
always_enabled_for_models = ["glm-4.7", "glm-4.6", "glm-4.5"]
```

- `enabled`: Enable thinking mode globally
- `default_mode`: Default thinking mode ("enabled" or "disabled")
- `always_enabled_for_models`: Models that always use thinking mode

**Thinking Mode**: Enables chain-of-thought reasoning for complex tasks. Increases response time but improves quality.
```

### Phase 7: Testing

#### 7.1 Unit Tests

Create `tests/ModelRegistryTest.groovy`:

```groovy
import models.ModelInfo
import models.ModelRegistry
import models.ModelType

class ModelRegistryTest {
    static void main(String[] args) {
        // Test 1: Model retrieval
        ModelInfo glm47 = ModelRegistry.getModel("glm-4.7")
        assert glm47.id == "glm-4.7"
        assert glm47.contextLength == 200000
        assert glm47.supportsThinking == true
        println "✓ Model retrieval works"

        // Test 2: Unknown model
        try {
            ModelRegistry.getModel("invalid-model")
            assert false, "Should throw exception"
        } catch (IllegalArgumentException e) {
            println "✓ Unknown model exception works"
        }

        // Test 3: Get by type
        List<ModelInfo> llms = ModelRegistry.getModelsByType(ModelType.LLM)
        assert llms.size() > 0
        println "✓ Get models by type works (${llms.size()} LLMs found)"

        // Test 4: Get non-legacy
        List<ModelInfo> nonLegacy = ModelRegistry.getNonLegacyModels()
        assert nonLegacy.every { it.category != "legacy" }
        println "✓ Filter non-legacy models works"

        // Test 5: Model suggestion
        String suggested = ModelRegistry.suggestModel("I need to analyze an image")
        assert suggested.contains("v")
        println "✓ Model suggestion works (${suggested})"

        println "\nAll tests passed!"
    }
}
```

#### 7.2 Integration Tests

Create `tests/IntegrationTest.groovy`:

```groovy
import core.GlmClient
import core.Config
import models.ChatRequest
import models.Message
import models.ModelRegistry

class IntegrationTest {

    static void testModel(String modelId) {
        println "\n=== Testing ${modelId} ==="

        try {
            ModelInfo info = ModelRegistry.getModel(modelId)
            println "Context: ${info.contextLength}"
            println "Thinking: ${info.supportsThinking}"
            println "Vision: ${info.supportsVision}"

            Config config = Config.load()
            String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
            if (!apiKey) {
                println "  ✗ Skipped (no API key)"
                return
            }

            GlmClient client = new GlmClient(apiKey)
            ChatRequest request = new ChatRequest()
            request.model = modelId
            request.messages = [new Message("user", "Say 'Hello from ${modelId}'")]
            request.stream = false

            if (info.supportsThinking) {
                request.thinking = [type: "enabled"]
            }

            String response = client.sendMessage(request)
            println "  ✓ API call successful"
            println "  Response: ${response.take(100)}..."

        } catch (Exception e) {
            println "  ✗ Failed: ${e.message}"
        }
    }

    static void main(String[] args) {
        println "GLM-CLI Integration Tests\n"

        // Test new models
        testModel("glm-4.7")
        testModel("glm-4.6")
        testModel("glm-4.5")
        testModel("glm-4.5-flash")
        testModel("glm-4.6v")
        testModel("glm-4.5v")

        // Test legacy models (backward compatibility)
        testModel("glm-4")
        testModel("glm-4-flash")

        println "\n=== Tests Complete ==="
    }
}
```

#### 7.3 Vision Model Tests

Create `tests/VisionTest.groovy`:

```groovy
import models.Message
import models.Message.ContentItem
import models.Message.ImageUrl
import core.GlmClient
import core.Config

class VisionTest {

    static void testVision(String model, String imageUrl) {
        println "\n=== Testing ${model} with vision ==="

        try {
            Config config = Config.load()
            String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
            if (!apiKey) {
                println "  ✗ Skipped (no API key)"
                return
            }

            GlmClient client = new GlmClient(apiKey)
            List<ContentItem> items = [
                new ContentItem(type: "text", text: "Describe this image in 2 sentences."),
                new ContentItem(
                    type: "image_url",
                    image_url: new ImageUrl(url: imageUrl)
                )
            ]

            ChatRequest request = new ChatRequest()
            request.model = model
            request.messages = [new Message("user", items)]
            request.thinking = [type: "enabled"]
            request.stream = false

            String response = client.sendMessage(request)
            println "  ✓ Vision API call successful"
            println "  Response: ${response}"

        } catch (Exception e) {
            println "  ✗ Failed: ${e.message}"
        }
    }

    static void main(String[] args) {
        String testImage = args[0] ?: "https://via.placeholder.com/300"

        println "Vision Model Tests\n"
        println "Using test image: ${testImage}\n"

        testVision("glm-4.6v", testImage)
        testVision("glm-4.5v", testImage)

        // Test with non-VLM model (should fail)
        println "\n=== Testing LLM with image (should fail) ===\n"
        testVision("glm-4.7", testImage)

        println "\n=== Tests Complete ==="
    }
}
```

### Phase 8: Migration & Compatibility

#### 8.1 Migration Guide

**File**: `docs/MIGRATION_TO_GLM_4x.md`

```markdown
# Migration Guide to GLM-4.x Models

## Overview

This guide helps you migrate from legacy GLM-4 models to the latest GLM-4.7/4.6/4.5 series.

## Benefits of Upgrading

1. **Better Performance**: 40-50% improvement on coding benchmarks
2. **Longer Context**: Up to 200K tokens (vs. 128K)
3. **Thinking Mode**: Chain-of-thought reasoning for complex tasks
4. **Vision Support**: Native image/video analysis with VLM models
5. **Lower Cost**: GLM-4.5-Flash is free
6. **Better Agents**: Improved tool invocation and task planning

## Quick Migration

### Step 1: Update Config

Edit `~/.glm/config.toml`:

```toml
[behavior]
default_model = "glm-4.7"  # Changed from "glm-4-flash"

[thinking]
enabled = true  # New section
default_mode = "enabled"

[api]
use_new_endpoint = true  # Enable new API
```

### Step 2: Update Scripts

**Old**:
```bash
glm agent "Create a REST API"  # Uses glm-4 by default
```

**New**:
```bash
glm agent --model glm-4.5 "Create a REST API"  # Explicit model
# Or update default_model in config
```

### Step 3: Test with New API

```bash
# Test basic chat
glm chat --model glm-4.7 "Hello, how are you?"

# Test with thinking mode
glm chat --model glm-4.7 --thinking enabled "Explain how JWT authentication works"

# Test agent
glm agent --model glm-4.5 "Add unit tests for UserService"
```

## Model Mapping

| Old Model | New Model | Notes |
|------------|-------------|--------|
| `glm-4` | `glm-4.7` | Latest flagship, better performance |
| `glm-4-plus` | `glm-4.6` | Long context, improved reasoning |
| `glm-4-flash` | `glm-4.5-flash` | Free, same speed, better quality |
| `glm-4v` | `glm-4.6v` | Enhanced multimodal capabilities |

## Breaking Changes

### API Endpoint

**Old**: `https://open.bigmodel.cn/api/paas/v4/chat/completions`
**New**: `https://api.z.ai/api/paas/v4/chat/completions`

**Fix**: Set `api.use_new_endpoint = true` in config or use old endpoint via `api.base_url`

### Message Content Structure

**Old**: Simple string content only
**New**: Supports array for multimodal (text + images)

**Fix**: Update Message.groovy to handle `Object content` instead of `String content`

### Thinking Mode

**Old**: Not supported
**New**: Optional via `thinking` parameter

**Fix**: Add `request.thinking = [type: "enabled"]` to enable

## Troubleshooting

### Issue: "Unknown model: glm-4"

**Solution**: Update to `glm-4.7` or add legacy support:

```toml
[behavior]
default_model = "glm-4.7"  # Or keep "glm-4" if legacy support needed
```

### Issue: Vision input not working

**Solution**: Ensure using VLM model:

```bash
# Wrong
glm chat --model glm-4.7 --image photo.png

# Right
glm chat --model glm-4.6v --image photo.png
```

### Issue: Slower responses with thinking mode

**Solution**: Disable for simple tasks:

```bash
glm chat --thinking disabled "What's 2+2?"
```

### Issue: Connection timeout with new endpoint

**Solution**: Check internet connection or fall back to old endpoint:

```toml
[api]
base_url = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
use_new_endpoint = false
```

## Rollback Plan

If issues occur, revert to old setup:

```toml
[behavior]
default_model = "glm-4-flash"

[api]
base_url = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
use_new_endpoint = false

[thinking]
enabled = false
```

## Getting Help

- Join community: [GitHub Discussions](https://github.com/yourusername/glm-cli-jbang/discussions)
- Report issues: [GitHub Issues](https://github.com/yourusername/glm-cli-jbang/issues)
- Documentation: [Z.AI API Docs](https://docs.z.ai/)
```

#### 8.2 Backward Compatibility

**File**: `core/GlmClient.groovy`

Add fallback to old endpoint:

```groovy
private String getBaseUrl(Config config) {
    if (config?.api?.useNewEndpoint == false) {
        return "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    }
    if (config?.api?.baseUrl) {
        return config.api.baseUrl + "/chat/completions"
    }
    return "https://api.z.ai/api/paas/v4/chat/completions"  // New default
}
```

**File**: `models/ModelRegistry.groovy`

Keep legacy models in registry:

```groovy
"glm-4": new ModelInfo(
    id: "glm-4",
    displayName: "GLM-4 (Legacy)",
    type: ModelType.LLM,
    description: "Legacy model. Consider upgrading to GLM-4.7",
    contextLength: 128000,
    maxOutputTokens: 8192,
    supportsThinking: false,
    supportsVision: false,
    supportsVideo: false,
    variants: [],
    costPerMInput: 0.05,
    costPerMOutput: 0.1,
    defaultUseCase: "Basic chat",
    category: "legacy"
),
```

Add deprecation warning:

```groovy
static ModelInfo getModel(String modelId) {
    ModelInfo info = MODELS.get(modelId)
    if (info == null) {
        throw new IllegalArgumentException("Unknown model: ${modelId}")
    }

    // Warn about legacy models
    if (info.category == "legacy") {
        System.err.println("Warning: Using legacy model ${info.displayName}")
        System.err.println("Consider upgrading to ${suggestModel('')} for better performance")
    }

    return info
}
```

### Phase 9: Documentation Updates

#### 9.1 Update AGENTS.md

Add section on thinking mode:

```markdown
### Thinking Mode

GLM-4.7, GLM-4.6, and GLM-4.5 support "thinking mode" which enables chain-of-thought reasoning.

**When to Use Thinking Mode:**

✅ **Recommended for**:
- Complex multi-step tasks
- Code refactoring with architectural considerations
- System design decisions
- Problem-solving with constraints
- Debugging complex issues

❌ **Not recommended for**:
- Simple factual queries
- Quick code snippets
- Translation tasks
- Status checks

**Configuration**:

```groovy
// In config
[thinking]
enabled = true
default_mode = "enabled"

// In command
glm agent --thinking enabled "Complex task"
glm agent --thinking disabled "Simple task"
```

**Thinking vs. Non-Thinking**:

| Aspect | Thinking Mode | Non-Thinking Mode |
|---------|---------------|-------------------|
| Response Time | 2-3x slower | Fast |
| Quality | Higher for complex tasks | Good for simple tasks |
| Token Usage | Higher (reasoning tokens) | Lower |
| Use Case | Complex reasoning | Quick responses |

**Example Comparison**:

```
# Without thinking (fast)
User: "Create a REST endpoint"
Assistant: [Immediately generates code]

# With thinking (slower but better)
User: "Create a REST endpoint"
Assistant: [Thinking] I need to consider...
    [Thinking] What HTTP methods?
    [Thinking] What validation?
    [Thinking] Error handling?
    Assistant: [Generates comprehensive code]
```
```

#### 9.2 Update TOOLS.md

Add vision tool examples:

```markdown
### Vision Integration

When using VLM models (glm-4.6v, glm-4.5v), the agent can process images:

**Examples**:

```bash
# Analyze screenshot
glm agent --model glm-4.6v "Analyze ./screenshot.png and suggest UX improvements"

# Document OCR
glm agent --model glm-4.5v "Extract text from ./invoice.pdf and create summary"

# Web design to code
glm agent --model glm-4.6v "Convert ./design.png to React code"
```

**Note**: Vision tools currently require manual image passing via CLI flags. Automatic image processing from tools is planned for future releases.
```

### Phase 10: Final Checklist

#### 10.1 Pre-Release Checklist

- [ ] **Core API Updates**
  - [ ] Update GlmClient base URL
  - [ ] Add thinking mode support to ChatRequest
  - [ ] Handle reasoning_content in ChatResponse
  - [ ] Test JWT auth with new endpoint

- [ ] **Multimodal Support**
  - [ ] Update Message for array content
  - [ ] Create ContentItem classes
  - [ ] Add MessageBuilder helpers
  - [ ] Test image/video input

- [ ] **Model Registry**
  - [ ] Create ModelInfo class
  - [ ] Implement ModelRegistry
  - [ ] Add all 5 new models
  - [ ] Add legacy model aliases
  - [ ] Implement model validation

- [ ] **Configuration**
  - [ ] Add thinking config section
  - [ ] Add use_new_endpoint flag
  - [ ] Update default_model to glm-4.7
  - [ ] Document new config options

- [ ] **Commands**
  - [ ] Update ChatCommand with --thinking, --image, --video
  - [ ] Update AgentCommand with --thinking
  - [ ] Add model validation
  - [ ] Display model info at startup

- [ ] **Examples**
  - [ ] Create Glm47Example.groovy
  - [ ] Create Glm46Example.groovy
  - [ ] Create Glm46vExample.groovy
  - [ ] Create Glm45vExample.groovy

- [ ] **Documentation**
  - [ ] Update README.md with new models
  - [ ] Create MIGRATION_TO_GLM_4x.md
  - [ ] Update CONFIGURATION.md
  - [ ] Update AGENTS.md with thinking mode
  - [ ] Update TOOLS.md with vision

- [ ] **Testing**
  - [ ] Unit tests for ModelRegistry
  - [ ] Integration tests for each model
  - [ ] Vision model tests
  - [ ] Thinking mode tests
  - [ ] Backward compatibility tests

- [ ] **Migration**
  - [ ] Test config upgrade path
  - [ ] Verify legacy models still work
  - [ ] Add deprecation warnings

#### 10.2 Post-Release Tasks

- [ ] Monitor usage statistics for model adoption
- [ ] Gather user feedback on thinking mode
- [ ] Analyze performance metrics (latency, quality)
- [ ] Plan V2 features based on feedback

## Timeline Estimate

| Phase | Effort | Dependencies |
|--------|---------|--------------|
| Phase 1: Core API Updates | 1-2 days | None |
| Phase 2: Multimodal Support | 1-2 days | Phase 1 |
| Phase 3: Model Registry | 1 day | None |
| Phase 4: Configuration | 0.5 day | None |
| Phase 5: Command Updates | 1-2 days | Phases 1-4 |
| Phase 6: Examples & Docs | 2-3 days | Phase 5 |
| Phase 7: Testing | 2-3 days | All phases |
| Phase 8: Migration | 1-2 days | Phase 7 |
| Phase 9: Doc Updates | 1 day | All phases |
| Phase 10: Final Checklist | 0.5 day | All phases |

**Total Estimate**: 10-16 days

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| New API endpoint unstable | High | Keep old endpoint as fallback |
| JWT auth differs for new API | Medium | Test early, implement fallback |
| Multimodal content complexity | Medium | Thorough testing, examples |
| Thinking mode increases latency | Low | User control via config |
| Breaking changes for users | Medium | Migration guide, backward compatibility |
| Model rate limits | Low | Implement retry logic, caching |

## Success Criteria

1. ✅ All 5 new models (4.7, 4.6, 4.5, 4.6v, 4.5v) work correctly
2. ✅ Thinking mode enabled/disabled works
3. ✅ Vision input (image/video) works for VLM models
4. ✅ Backward compatibility maintained for legacy models
5. ✅ Configuration upgrade path documented and tested
6. ✅ All examples run successfully
7. ✅ Test coverage > 80% for new features
8. ✅ Documentation complete and clear
9. ✅ Migration guide available
10. ✅ No breaking changes for existing users (opt-in upgrade)

## Conclusion

This implementation plan provides a comprehensive roadmap for adding GLM-4.7, GLM-4.6, GLM-4.5, GLM-4.6V, and GLM-4.5V support to GLM-CLI. The phased approach minimizes risk while ensuring thorough testing and documentation.

Key deliverables:
- Updated API client with new endpoint support
- Multimodal message handling
- Comprehensive model registry
- Thinking mode integration
- Vision capabilities for VLM models
- Complete migration path
- Extensive examples and documentation

The implementation balances new features with backward compatibility, ensuring existing users can upgrade at their own pace while new users immediately benefit from the latest model capabilities.
