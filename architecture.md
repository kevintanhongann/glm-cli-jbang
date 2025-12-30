# Architecture Design: GLM-CLI (JBang/Groovy Edition)

## 1. Architectural Overview

**GLM-CLI** is a command-line interface tool implemented using **Groovy** and **JBang**. It serves as an AI coding companion, leveraging Z.ai's GLM-4 models.

The architecture emphasizes:
*   **Script-based Modularity:** utilizing JBang's ability to compose scripts.
*   **Standard Java Ecosystem:** leveraging robust libraries (Picocli, Jackson, Java 11 HTTP).
*   **Agentic Loop:** A central state machine handling the interaction between the user, the LLM, and local tools.

### High-Level Components

```mermaid
graph TD
    User[User Terminal] -->|Input| CLI[CLI Entry Point (Picocli)]
    CLI -->|Command Dispatch| Commands[Command Modules]
    Commands -->|Chat/Ask| Agent[Agent Loop]
    
    Agent -->|Context| ContextMgr[Context Manager]
    Agent -->|API Call| Client[GLM API Client]
    Agent -->|Tool Call| ToolRegistry[Tool Registry]
    
    Client -->|HTTP/SSE| ZAI[Z.ai GLM-4 API]
    ToolRegistry -->|File I/O| FS[File System]
    ToolRegistry -->|Search| Web[Web Search]
    
    ContextMgr -->|Persistence| DB[(SQLite/JSON Session)]
```

## 2. Core Components

### 2.1. JBang Runtime Environment
*   **Execution:** The app runs as a standalone script or binary (via `jbang install`).
*   **Dependency Management:** Dependencies (`//DEPS`) are declared inline, ensuring reproducibility.
*   **Modularization:** Code is split into logical source files (`//SOURCES`), keeping the main script clean.

### 2.2. CLI Layer (Picocli)
*   **Entry Point:** `GlmCli.groovy` handles the main dispatch.
*   **Subcommands:**
    *   `chat`: Interactive conversation.
    *   `ask`: One-off questions with context.
    *   `edit`: Direct code modification requests.
    *   `agent`: Autonomous multi-step task execution.
*   **Mixins:** Standard configuration for verbose logging, help, and version info.

### 2.3. The Agent Loop
The core "brain" of the CLI. It implements a **ReAct** (Reasoning + Acting) loop:
1.  **Observe:** Read user input and conversation history.
2.  **Think:** Send history to GLM-4 API.
3.  **Act:**
    *   If text response: Stream to user.
    *   If tool call: Execute the tool (local code, file system, web search).
4.  **Loop:** Append tool results to history and repeat until completion.

### 2.4. GLM Client Layer
*   **Library:** `java.net.http.HttpClient` (Async).
*   **Protocol:**
    *   **Standard:** HTTP POST for non-streaming.
    *   **Streaming:** Server-Sent Events (SSE) for real-time tokens.
*   **Payloads:** Strictly typed POJOs serialized via Jackson to match Z.ai's OpenAI-compatible format.

### 2.5. Tool System
*   **Definition:** Tools are defined as Groovy classes implementing a `Tool` trait/interface.
*   **Schema Generation:** Reflection or manual definition generates the JSON Schema required by the LLM.
*   **Sandboxing:** File system tools are restricted to the project root to prevent accidental system-wide changes.

## 3. Data Flow

### Chat/Agent Flow
1.  **Initialization:** CLI loads configuration (`~/.glm/config.toml`) and initializes the `Agent`.
2.  **Input:** User provides a prompt (e.g., "Refactor the User class").
3.  **Context Building:** The `ContextManager` gathers relevant file snippets or recent history.
4.  **LLM Request:** `GlmClient` sends the request to Z.ai with available tool definitions.
5.  **Streaming:** The response is streamed to `stdout`.
6.  **Intervention (Tool Call):**
    *   LLM output: `tool_call: { name: "read_file", args: { path: "src/User.groovy" } }`
    *   CLI pauses stream, executes `read_file`.
    *   Result: `file_content: "class User { ... }"`
    *   CLI sends result back to LLM.
7.  **Finalization:** LLM provides the final answer or code diff.

## 4. File Structure (JBang)

```text
glm-cli/
├── glm.groovy          # Main entry point (shebang, dependencies)
├── commands/
│   ├── ChatCommand.groovy
│   ├── AgentCommand.groovy
│   └── ConfigCommand.groovy
├── core/
│   ├── Agent.groovy    # Main loop logic
│   ├── GlmClient.groovy # HTTP/SSE client
│   └── Config.groovy   # Configuration handler
├── tools/
│   ├── Tool.groovy     # Interface
│   ├── FileTools.groovy
│   └── WebTools.groovy
└── models/
    ├── ChatRequest.groovy
    └── ChatResponse.groovy
```

## 5. Security & Safety
*   **File Access:** All file operations are rooted to the current working directory (`user.dir`).
*   **Confirmation:** "Dangerous" tools (write/delete) require user confirmation (y/n) unless `--force` is used.
*   **API Keys:** Stored in OS keychain or secure environment variables, never logged.
