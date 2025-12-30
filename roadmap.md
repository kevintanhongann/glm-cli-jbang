# Implementation Roadmap: GLM-CLI

## Phase 1: Foundation (Days 1-2)
**Goal:** A working "Hello World" CLI that can talk to the API.

- [x] **Setup Project Structure**:
    - Initialize `glm.groovy`.
    - Configure `//DEPS` for Picocli and Jackson.
- [x] **CLI Skeleton**:
    - Implement `GlmCli` class with `@Command`.
    - Add `chat` subcommand placeholder.
- [x] **API Client (Basic)**:
    - Implement `GlmClient` with Java 11 `HttpClient`.
    - Create `ChatRequest` and `Message` POJOs.
    - Test simple non-streaming `POST` to Z.ai API.

## Phase 2: Streaming & Chat (Days 3-4)
**Goal:** Interactive chat experience with real-time streaming.

- [x] **SSE Implementation**:
    - parsing `data:` lines from the response stream.
    - Handling `[DONE]` signal.
- [x] **Interactive REPL**:
    - Implement a loop in `ChatCommand` to accept user input.
    - Maintain message history (Context).
    - Print streamed response to console.
- [x] **Configuration**:
    - Implement `ConfigLoader` to read `~/.glm/config.toml` (API key management).

## Phase 3: Tools & Agency (Days 5-7)
**Goal:** The agent can read/write files and execute actions.

- [x] **Tool Infrastructure**:
    - Define `Tool` interface.
    - Implement `ToolRegistry`.
- [x] **File System Tools**:
    - `ReadFileTool`, `WriteFileTool` (with safety checks).
    - `ListFilesTool` for listing directory contents.
- [x] **Agent Logic**:
    - Update `GlmClient` to handle `tool_calls` in response.
    - Implement the "Tool Execution Loop":
        1. Detect tool call.
        2. Execute tool.
        3. Create `tool` role message with result.
        4. Send back to API.

## Phase 4: Polish & Distribution (Days 8-9)
**Goal:** A robust, user-friendly tool ready for sharing.

- [x] **Diff View**:
    - When `WriteFileTool` is used, show a diff before confirming (if interactive).
- [x] **Safety Rails**:
    - Implement confirmation prompts for file modifications.
- [x] **Distribution**:
    - Create a `jbang-catalog.json`.
    - Document installation via `jbang app install ...`.

## Phase 5: Advanced Features (Current Focus)
- [x] **Documentation**: Create comprehensive implementation plan
- [ ] **Web Search Tool**: Integrate searching for external docs
  - [ ] Create WebSearchResponse model
  - [ ] Implement WebSearchClient
  - [ ] Create WebSearchTool
  - [ ] Add configuration options
  - [ ] Update all documentation
  - [ ] Write comprehensive tests
- [ ] **RAG (Retrieval Augmented Generation)**: Simple embedding-based search for large codebases.
- [ ] **TUI**: Replace standard console I/O with a rich TUI (using `lanterna` or similar) for better diffs.
