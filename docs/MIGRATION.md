# Migration Guide: Adding LangChain4j Support

## Overview

GLM-CLI is being enhanced with [LangChain4j](https://github.com/langchain4j/langchain4j), a production-ready Java library for LLM integration. This guide shows how to use LangChain4j features.

## Quick Start

```bash
# Run the setup script
./glm-cli-jbang/scripts/setup-langchain4j.sh

# Source the environment
source ./glm-cli-jbang/scripts/setup-langchain4j.sh

# Now run GLM-CLI with LangChain4j
./glm.groovy chat "Hello with LangChain4j!"
```

## Usage

### Using ZAI Coding Plan (Default)

```bash
# Use ZAI Coding Plan provider
./glm.groovy chat --provider zai-coding-plan "Create a REST API client"

# Or set environment variable
export GLM_PROVIDER=zai-coding-plan

# Or set in config file
cat > ~/.glm/config.toml << EOF
[api]
key = "your.api.key"

[features]
provider = "zai-coding-plan"
EOF
```

### ZAI Coding Plan Benefits

| Feature | Standard GLM-4 | ZAI Coding Plan (GLM-4.7) |
|---------|------------------|---------------------|
| **Context Window** | 131,072 tokens | 204,800 tokens (56% larger) |
| **Thinking Mode** | Basic reasoning | Chain-of-thought with `reasoning_content` |
| **Tool Calling** | Basic JSON Schema | Up to 128 functions with web search |
| **Web Search** | Not available | Built-in web search integration |
| **Rate Limits** | Standard API limits | Subscription-based (120-2400 prompts/5hrs) |
| **Cost** | Pay per token | Zero token cost (input/output free with sub) |
| **Streaming** | Standard SSE | Enhanced SSE with `tool_stream` |

### Example: Streaming Chat with Thinking Mode

```groovy
import core.ZaiCodingPlanClient
import dev.langchain4j.model.chat.*

Config config = Config.load()
ZaiCodingPlanClient client = new ZaiCodingPlanClient(config)

def messages = [
    new ChatMessage(ChatMessageRole.USER.value(), "Analyze this code and suggest improvements")
]

println "Thinking..."
def response = client.generate(messages)

println "Response: ${response}"
```

### Example: Using Tool Calling

```groovy
import core.ZaiCodingPlanClient
import dev.langchain4j.model.tool.*

Config config = Config.load()
ZaiCodingPlanClient client = new ZaiCodingPlanClient(config)

def toolSpec = new ToolSpecification.builder()
    .name("read_file")
    .description("Read file content")
    .parameters([new ToolParameter.builder()
        .name("path")
        .type("string")
        .description("File path to read")
        .build()])
    .build()

def messages = [
    new ChatMessage(ChatMessageRole.USER.value(), "Read the README file"),
    new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a helpful coding assistant.")
]

println "Response with tool call..."
def response = client.generate(messages, null, [toolSpec])
```

### Configuration Options

Add to your `~/.glm/config.toml`:

```toml
[api]
key = "your.api.key"
provider = "zai-coding-plan"  # Use ZAI Coding Plan

[zai_coding_plan]
model = "glm-4.7"           # Default model
enable_thinking = true         # Enable chain-of-thought
enable_tools = true           # Enable tool calling
temperature = 0.7             # Sampling temperature
stream = true                # Enable streaming
```

### Advanced Features

#### Web Search Integration
ZAI Coding Plan provides built-in web search:

```bash
./glm.groovy chat "What is the latest version of Java?"
```

Response will include:
```json
{
  "web_search": [
    {
      "title": "Java 17 Features",
      "content": "Java 17 was released in September 2024 with major features including virtual threads, pattern matching, and improved performance...",
      "link": "https://openjdk.org/projects/jdk/17/"
    }
  ]
}
```

#### Structured JSON Output
Get clean JSON for programmatic use:

```bash
./glm.groovy chat -p zai-coding-plan "Generate a JSON config" --response-format json_object
```

### Benefits

| Feature | Standard GLM-4 | ZAI Coding Plan (GLM-4.7) |
|---------|------------------|---------------------|
| **Context Window** | 131,072 tokens | 204,800 tokens (56% larger) |
| **Thinking Mode** | Basic reasoning | Chain-of-thought with `reasoning_content` |
| **Tool Calling** | Basic JSON Schema | Up to 128 functions with web search |
| **Web Search** | Not available | Built-in web search integration |
| **Rate Limits** | Standard API limits | Subscription-based (120-2400 prompts/5hrs) |
| **Cost** | Pay per token | Zero token cost (input/output free with sub) |
| **Streaming** | Standard SSE | Enhanced SSE with `tool_stream` |

## Architecture

```
GLM-CLI with LangChain4j
┌─────────────────────────────────┐
│  Commands  │
│  ┌────────┴
│  │ Picocli  │
│  ├─ ChatCommand  │
│  │   ├─ AgentCommand  │
│  │   └─ Other Commands (future)
│  └─────────┤
│    GLM Models  │
│  ├─ Custom GLM Client  │
│  └─ LangChain4j Models  │
│     └─ 20+ Providers     │
│  └─────────────────┤
│      Tools        │
│  ├─ Custom Tool Interface  │
│  └─ LangChain4j Tools  │
│         Agent Logic │
│  └─────────────────┤
│       GLM API      │
└─────────────────────────┘
```

## Key Features

### 1. Multi-Provider Support
- Native GLM (Z.ai) via custom client
- OpenAI, Anthropic, Google Vertex, Azure OpenAI via LangChain4j
- Easy to switch providers with `-p langchain4j`

### 2. Advanced Agent Workflows
- Sequential agents (chain outputs)
- Parallel agents (simultaneous execution)
- Loop agents (iterative improvement)
- Conditional agents (rule-based routing)
- AgenticScope for shared state

### 3. Memory & Context
- `ChatMemory` - Message window with configurable size
- Context summarization for multi-turn conversations
- Persistent AgenticScope

### 4. Tool System
- Built-in tools: Web search, bash execution, etc.
- Easy custom tool registration via `@Tool` annotation
- JSON Schema auto-generation

### 5. Observability
- Built-in `AgentMonitor` - track all agent invocations
- Custom listeners via `@AgentListener` interface
- Tree-structured execution tracking

## Examples

### Basic Chat with LangChain4j

```bash
# Uses LangChain4j's ZhipuAiStreamingChatModel by default
./glm.groovy chat "Create a simple REST API client"
```

### Sequential Workflow

```java
// Example: Code review with 3 agents
AgenticServices.services()
    .sequenceBuilder()
    .subAgents(codeAnalyzerAgent, styleReviewerAgent, testWriterAgent)
    .outputKey("reviewResult")
    .build()

String review = reviewAgent.invoke(Map.of("code", "// Your code here"))
System.out.println("Code review: " + review)
```

### RAG (Retrieval-Augmented Generation)

LangChain4j makes implementing RAG easy:

```groovy
//DEPS dev.langchain4j:langchain4j-document-loaders:*:1.10.0-beta18

import dev.langchain4j.document.loader.FileSystemTextDocumentLoader
import dev.langchain4j.embedding.model.JlamaEmbeddingModel
import dev.langchain4j.store.InMemoryEmbeddingStore

// Create RAG pipeline
DocumentLoader loader = new FileSystemTextDocumentLoader()
EmbeddingModel embeddingModel = new JlamaEmbeddingModel()
InMemoryEmbeddingStore embeddingStore = new InMemoryEmbeddingStore()

// Load documents and create embeddings
List<Document> documents = loader.load("./docs/")
embeddingStore.addAll(embeddingModel.embed(documents))

// Search relevant documents
Retriever retriever = EmbeddingStoreRetriever.fromEmbeddingModel(embeddingModel)
        .k(5) // Top 5 results
        .retriever(embeddingStore)

// Use in chat
ChatLanguageModel chatModel = ZhipuAiChatModel.builder()
    .apiKey(config.api.key)
    .chatMemoryProvider(dev.langchain4j.community.zhipu.MessageWindowChatMemory.withMaxMessages(10))
    .retriever(retriever)
```

### Custom Tool

```groovy
@Tool(name = "web_search", description = "Search the web for information")
class WebSearchTool implements Tool {
    @Override
    String getName() { return "web_search" }
    
    @Override
    String getDescription() { return "Search web using built-in search engine" }
    
    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                query: [type: "string", description: "Search query"]
            ],
            required: ["query"]
        ]
    }
    
    @Override
    Object execute(Map<String, Object> args) {
        String query = args.get("query")
        // ... implement search logic
        return "Found 5 results for: ${query}"
    }
}

// Register with LangChain4j
AgenticServices agentServices = AgenticServices.builder()
    .chatModel(chatModel)
    .tools([webSearchTool])
    .build()
```

## Migration Strategy

### Phase 1: Foundation (Current Sprint)
✅ Add LangChain4j dependencies
✅ Create LangChain4j wrapper class
✅ Add `-p langchain4j` provider flag
✅ Test basic functionality

### Phase 2: Agent Enhancement (Next Sprint)
⏳ Keep custom Agent, add LangChain4j workflows
⏳ Create agent examples (sequential, parallel, loop)
⏳ Add tool registration guide

### Phase 3: Advanced Features (Future)
○ Implement RAG pipeline
○ Add web search tool
○ Enable memory management
○ Add observability dashboard

### Phase 4: Documentation (Ongoing)
📝 Update all markdown files with LangChain4j examples
📝 Create LangChain4j-specific guide

## Configuration

### New Config Options

```toml
[api]
key = "your.api.key"
provider = "glm"  # or "langchain4j"

[providers]
langchain4j = [
    models = ["glm-4-flash", "glm-4"],
    streaming = true
]

[features]
use_langchain4j = true
use_agentic_workflows = true
```

### Environment Variables

```bash
export GLM_PROVIDER=langchain4j  # Use LangChain4j
export LANGCHAIN4J_MODEL=gpt-4  # Use OpenAI via LangChain4j
```

## Troubleshooting

### Issue: LangChain4j dependency not found

```bash
# Run setup script
./glm-cli-jbang/scripts/setup-langchain4j.sh

# Manual download (if script fails)
# Visit: https://github.com/langchain4j/langchain4j/releases
# Download: langchain4j-community-zhipu-ai-1.10.0-beta18.jar
# Place in: ./lib/langchain4j-community-zhipu-ai-1.10.0-beta18.jar
```

### Issue: Class not found

**Symptom**: `ClassNotFoundException: dev.langchain4j.community.zhipu.ZhipuAiChatModel`

**Solution**: Run setup script to download the jar file

### Test Your Setup

```bash
# Test basic chat
./glm.groovy chat "Test message"

# Test provider switching
./glm.groovy chat -p langchain4j "Using LangChain4j provider"

# Check available models
./glm.groovy chat --help  # Should show LangChain4j models
```

## Next Steps

1. ✅ Test the LangChain4j integration
2. ✅ Add example agents using LangChain4j workflows
3. ✅ Document migration path in `README.md`
4. ✅ Update `AGENTS.md` with LangChain4j patterns
5. ✅ Create `MIGRATION.md` with detailed migration guide

---

**Want to dive deeper?** LangChain4j has powerful features:
- Advanced agent patterns with `@Planner`, `@LoopBuilder`, `@ParallelBuilder`
- RAG with embedding models and vector stores (Pinecone, Milvus, Weaviate)
- Multi-modal support (text, image, audio via Google Gemini, OpenAI)
- Streaming chat with any provider

Let me know which features interest you most!
