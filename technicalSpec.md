# Technical Specification: GLM-CLI (JBang/Groovy)

## 1. Technology Stack

| Component | Choice | Reason |
|-----------|--------|--------|
| **Runtime** | **JBang** | Easy distribution, script-based dev, dependency management. |
| **Language** | **Groovy 4.x** | Concise syntax, scripting capabilities, powerful closures. |
| **CLI Framework** | **Picocli** | Best-in-class Java CLI, native JBang support, GraalVM compatible. |
| **HTTP Client** | **Java 11 HttpClient** | Built-in, async, lightweight, supports reactive streams (Flow API). |
| **JSON Processing** | **Jackson** | Robust, supports complex polymorphism for API responses. |
| **Schema Validation** | **NetworkNT JSON Schema** | For validating tool arguments against schemas. |
| **Config Format** | **TOML (via jackson-dataformat-toml)** | Standard for modern tools, easy to read. |
| **Database** | **SQLite (via groovy-sql)** | Simple session storage, single file, standard SQL. |

## 2. Dependencies (Maven Coordinates)

The `glm.groovy` file will declare:

```groovy
//DEPS info.picocli:picocli:4.7.6
//DEPS info.picocli:picocli-groovy:4.7.6
//DEPS com.fasterxml.jackson.core:jackson-databind:2.16.0
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.16.0
//DEPS org.xerial:sqlite-jdbc:3.44.1.0
//DEPS com.networknt:json-schema-validator:1.0.86
//DEPS org.slf4j:slf4j-simple:2.0.9
```

## 3. Class Design

### 3.1. `GlmClient`
Responsible for low-level communication with Z.ai.

*   **Methods:**
    *   `Stream<String> streamChat(ChatRequest request)`: Returns an SSE stream.
    *   `ChatResponse sendChat(ChatRequest request)`: Blocking call for non-streaming.
*   **Implementation Details:**
    *   Uses `HttpRequest.newBuilder().POST(...)`.
    *   Parses SSE lines starting with `data:`.
    *   Handles authentication (`Authorization: Bearer <key>`).

### 3.2. `Agent` (The State Machine)
Manages the conversation lifecycle.

*   **Properties:**
    *   `List<Message> history`
    *   `List<Tool> tools`
*   **Logic:**
    *   Maintains a `while(running)` loop.
    *   Detects `finish_reason` in LLM response.
    *   If `tool_calls`, invokes `ToolExecutor`.
    *   Aggregates tool outputs into a `ToolMessage` and recurses.

### 3.3. `Tool` Interface
Abstracts local capabilities.

```groovy
interface Tool {
    String getName()
    String getDescription()
    String getJsonSchema() // Returns parameter schema
    Object execute(Map<String, Object> params)
}
```

**Built-in Tools:**
1.  **`ReadFileTool`**:
    *   Params: `path` (string), `start_line` (int), `end_line` (int).
    *   Returns: File content string.
2.  **`WriteFileTool`**:
    *   Params: `path` (string), `content` (string).
    *   Returns: Success status.
3.  **`ListFilesTool`**:
    *   Params: `directory` (string), `recursive` (boolean).
    *   Returns: List of file paths.

## 4. Data Models (JSON Mapping)

We need to map strictly to the GLM-4 API structure (OpenAI compatible).

### `Message`
```groovy
class Message {
    String role // "user", "assistant", "system", "tool"
    String content
    List<ToolCall> tool_calls
    String tool_call_id // For tool responses
}
```

### `ChatRequest`
```groovy
class ChatRequest {
    String model = "glm-4-flash"
    List<Message> messages
    List<ToolDefinition> tools
    Boolean stream = true
    Double temperature = 0.7
}
```

## 5. Configuration (`~/.glm/config.toml`)

```toml
[api]
key = "your-api-key"
base_url = "https://open.bigmodel.cn/api/paas/v4/"
default_model = "glm-4-flash"

[behavior]
language = "auto" # auto, en, zh
safety_mode = "ask" # ask, always_allow, strict
```

## 6. CLI Commands & Arguments

### `glm chat`
*   Starts an interactive REPL session.
*   **Flags:**
    *   `--model <name>`: Override default model.
    *   `--system <text>`: Custom system prompt.

### `glm ask <query>`
*   One-shot question answer.
*   Example: `glm ask "How do I build this project?"`
*   Automatically includes file structure context if small enough.

### `glm agent <task>`
*   Autonomous execution.
*   Example: `glm agent "Create a unit test for Person.groovy"`
*   Will perform: Read File -> Think -> Write File.

## 7. Handling SSE (Server-Sent Events)

Java 11 `HttpClient` provides `HttpResponse.BodyHandlers.ofLines()`.
We will filter lines starting with `data:`, strip the prefix, and deserialize the JSON chunk.

*   **Chunk Handling:**
    *   Accumulate `delta.content` for display.
    *   Accumulate `delta.tool_calls` arguments (which may be split across chunks).

## 8. Cross-Platform Strategy
*   JBang handles the JDK installation.
*   Script runs on Linux/Mac/Windows shells.
*   File paths normalized using `java.nio.file.Path`.
