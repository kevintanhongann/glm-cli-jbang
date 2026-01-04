# Roo-Code Architecture Analysis

## Executive Summary

This document presents an architectural analysis of the Roo-Code coding agent, highlighting key design patterns, components, and architectural decisions that can inform the development of the GLM CLI JBang project.

## Table of Contents

1. [Project Structure](#project-structure)
2. [Core Components](#core-components)
3. [Tool System Architecture](#tool-system-architecture)
4. [State Management](#state-management)
5. [Configuration System](#configuration-system)
6. [API Layer Architecture](#api-layer-architecture)
7. [Unique Architectural Patterns](#unique-architectural-patterns)
8. [Comparison with GLM CLI JBang](#comparison-with-glm-cli-jbang)
9. [Key Insights for GLM CLI JBang](#key-insights-for-glm-cli-jbang)

---

## Project Structure

### Monorepo Organization

```
Roo-Code/
├── src/                          # Main extension source
├── packages/                      # Shared packages
│   ├── core/                     # Platform-agnostic core functionality
│   ├── types/                    # TypeScript type definitions
│   ├── cloud/                    # Cloud services (auth, bridge, sync)
│   ├── telemetry/                # Analytics and telemetry
│   ├── build/                    # Build utilities
│   └── ipc/                      # IPC utilities
├── webview-ui/                   # React-based UI
├── apps/
│   ├── vscode-e2e/              # E2E tests
│   └── web-roo-code/            # Web version
└── .roo/                        # Built-in rules and commands
```

**Key Features:**
- Uses pnpm workspaces + Turborepo for fast builds
- Shared packages for code reuse
- Clear separation between extension core and UI
- Multiple deployment targets (VS Code, web)

### Main Entry Point

**File:** `src/extension.ts`

The `activate()` function orchestrates initialization:

```typescript
export async function activate(context: vscode.ExtensionContext) {
    // 1. Setup logging and output channels
    // 2. Initialize telemetry service
    // 3. Initialize MDM service
    // 4. Initialize i18n for localization
    // 5. Initialize TerminalRegistry for shell execution
    // 6. Initialize Claude Code OAuth manager
    // 7. Initialize CodeIndexManager for codebase indexing
    // 8. Create ContextProxy for state management
    // 9. Initialize ClineProvider (main provider)
    // 10. Initialize CloudService for sync and auth
    // 11. Register commands, code actions, URI handlers
}
```

---

## Core Components

### 1. ClineProvider (Webview Orchestrator)

**Path:** `src/core/webview/ClineProvider.ts`

**Responsibilities:**
- Manages webview lifecycle (sidebar + tab panels)
- Maintains stack of Task instances (clineStack)
- Handles webview ↔ extension message communication
- Integrates with MCP (Model Context Protocol) servers
- Manages code index, marketplace, and skills services
- Coordinates checkpoint services
- Exposes API for other extensions to interact with Roo

**Key Methods:**
```typescript
postStateToWebview()           // Syncs state to UI
postMessageToWebview()         // Sends messages to React UI
resolveTaskForApi()            // Gets current task for API calls
createTask()                   // Starts new task
handleMessage()                // Routes webview messages
```

### 2. Task (Coding Agent Engine)

**Path:** `src/core/task/Task.ts`

**Responsibilities:**
- Orchestrates conversation with AI models
- Parses and executes tool calls
- Manages conversation history (apiConversationHistory)
- Handles context window management
- Implements checkpoint/recovery system
- Manages todo list and task state
- Tracks consecutive mistakes and auto-retries

**Key Properties:**
```typescript
class Task {
    clineMessages: ClineMessage[]        // Display messages for UI
    apiConversationHistory: ApiMessage[]  // API messages for LLM
    consecutiveMistakeCount: number
    task: string                          // Current task description
    cwd: string                           // Working directory
    mode: string                          // Current mode (code, architect, etc.)
}
```

**Key Methods:**
```typescript
startLoop()         // Main task execution loop
resumeTask()        // Resume from checkpoint
say()              // Send messages to UI
executeTool()      // Execute tool calls
manageContext()    // Handle context condensing
saveState()        // Persist task state
```

---

## Tool System Architecture

### Tool Base Class

**Path:** `src/core/tools/BaseTool.ts`

Abstract base class providing dual protocol support:

```typescript
export abstract class BaseTool<TName extends ToolName> {
    abstract readonly name: TName
    abstract parseLegacy(params): ToolParams<TName>  // XML protocol
    abstract execute(params, task, callbacks): Promise<void>
    async handlePartial(task, block): Promise<void>  // Streaming support
    async handle(task, block, callbacks): Promise<void>
}
```

**Tool Protocol Support:**
- **XML Protocol**: Legacy format `<tool_name><param>value</param></tool_name>`
- **Native Protocol**: OpenAI-style structured function calls

### Available Tools

**File Operations:**
- `ReadFileTool` - Read files with line ranges, multiple file support
- `WriteToFileTool` - Write content to files
- `EditFileTool` - Search/replace with regex support
- `SearchReplaceTool` - Simple search/replace
- `ApplyDiffTool` - Apply unified diffs
- `ApplyPatchTool` - Apply patch files
- `ListFilesTool` - List directory contents
- `SearchFilesTool` - Search files with regex

**Execution Tools:**
- `ExecuteCommandTool` - Run shell commands with approval
- `RunSlashCommandTool` - Run custom slash commands

**AI/Analysis Tools:**
- `CodebaseSearchTool` - Semantic codebase search (uses embeddings)
- `BrowserActionTool` - Browser automation (click, type, scroll, screenshot)
- `GenerateImageTool` - Image generation
- `AskFollowupQuestionTool` - Request user input

**Task Management:**
- `AttemptCompletionTool` - Mark task as complete
- `SwitchModeTool` - Change operation mode
- `NewTaskTool` - Start subtask
- `UpdateTodoListTool` - Manage todo items

**Integration Tools:**
- `UseMcpToolTool` - Call MCP server tools
- `AccessMcpResourceTool` - Access MCP resources
- `FetchInstructionsTool` - Load custom instructions

### Tool Building Pipeline

**Path:** `src/core/task/build-tools.ts`

```typescript
export async function buildNativeToolsArray(options: BuildToolsOptions)
```

**Process:**
1. Get native tools with dynamic read_file configuration
2. Filter tools based on mode restrictions (tool groups)
3. Add MCP server tools if available
4. Add custom tools from `.roo/tools` directories
5. Return OpenAI-formatted tool array

---

## State Management

### ContextProxy (State Abstraction)

**Path:** `src/core/config/ContextProxy.ts`

Wraps VS Code's extension context with caching:

```typescript
class ContextProxy {
    private stateCache: GlobalState
    private secretCache: SecretState

    async initialize() {
        // Load from VS Code globalState and secrets
    }

    getValue<K extends keyof GlobalState>(key: K): GlobalState[K]
    setValue<K extends keyof GlobalState>(key: K, value): Promise<void>
    getSecret<K extends keyof SecretState>(key: K): Promise<SecretState[K]>
    setSecret<K extends keyof SecretState>(key: K, value): Promise<void>
}
```

**State Keys:**
- `taskHistory` - Past tasks
- `apiConfigs` - Provider configurations
- `allowedCommands` - Approved terminal commands
- `language` - UI language
- Various migration flags

### ProviderSettingsManager (API Configurations)

**Path:** `src/core/config/ProviderSettingsManager.ts`

Manages multiple provider profiles:

```typescript
interface ProviderProfiles {
    currentApiConfigName: string
    apiConfigs: Record<string, ProviderSettingsWithId>
    modeApiConfigs: Record<string, string>
    cloudProfileIds: string[]
    migrations: { ... }
}
```

**Features:**
- Multiple provider profiles (Anthropic, OpenAI, etc.)
- Mode-specific model selection
- Cloud profile synchronization
- Model migrations (e.g., "roo/code-supernova" → "roo/code-supernova-1-million")

### Task Persistence

**Path:** `src/core/task-persistence/`

**Two persistence layers:**
- `apiMessages.ts` - API-level messages for LLM context
- `taskMessages.ts` - Display-level messages for UI
- `taskMetadata.ts` - Task metadata (mode, model, status)

---

## Configuration System

### Modes Configuration

**Path:** `src/shared/modes.ts`

**Built-in Modes:**
- `code` - Everyday coding, file operations
- `architect` - System planning, specs, migrations
- `ask` - Fast answers, explanations
- `debug` - Issue tracing, adding logs
- Additional modes defined in `.roomodes` file

**Mode Structure:**
```typescript
interface ModeConfig {
    slug: string
    name: string
    roleDefinition: string
    whenToUse: string
    description: string
    groups: GroupEntry[]
    customInstructions?: string
    source: "global" | "project"
}
```

**Tool Groups:**
- `read` - File reading tools
- `edit` - File editing tools (with fileRegex filters)
- `command` - Terminal commands
- `browser` - Browser automation
- `mcp` - MCP server tools

### Custom Modes

**Path:** `.roomodes`

Examples:
- **test mode** - Vitest testing specialist
- **design-engineer mode** - UI/React/Tailwind specialist
- **translate mode** - Localization management
- **issue-fixer mode** - GitHub issue resolution
- **integration-tester mode** - E2E testing

### Roo Configuration Directories

**Path:** `.roo/`

Built-in rules and commands:
- `commands/` - Custom slash commands
- `rules/` - General rules
- `rules-code/` - Code-specific rules
- `rules-docs-extractor/` - Documentation extraction rules

---

## API Layer Architecture

### Unified API Handler

**Path:** `src/api/index.ts`

Factory function to build provider-specific API handlers:

```typescript
export async function buildApiHandler(
    providerSettings: ProviderSettings,
    context: vscode.ExtensionContext
): Promise<ApiHandler>
```

### Supported Providers

**Path:** `src/api/providers/`

Providers include:
- Anthropic (Claude)
- OpenAI / OpenAI Native
- AWS Bedrock (Anthropic/Bedrock)
- Vertex AI
- OpenRouter
- LM Studio
- Roo (official)
- And 20+ other providers (DeepSeek, Groq, Mistral, etc.)

### API Handler Interface

```typescript
interface ApiHandler {
    createMessage(
        systemPrompt: string,
        messages: ApiMessage[],
        tools?: ChatCompletionTool[],
        metadata: ApiHandlerCreateMessageMetadata
    ): ApiStream

    getModelInfo(): ModelInfo
    completePrompt(prompt: string): Promise<string>
}
```

### Streaming and Transform

**Path:** `src/api/transform/stream.ts`

```typescript
class ApiStream extends ReadableStream {
    // Provides streaming responses with:
    // - Text blocks
    // - Tool calls
    // - Usage statistics (tokens, cost)
    // - Grounding sources
}
```

---

## Unique Architectural Patterns

### 1. Dual Protocol Support

Tools support both XML and Native protocols:
- **Legacy/XML**: Human-readable, debugging-friendly
- **Native/OpenAI**: Structured, type-safe, parallel tool calls

Implemented via `BaseTool.parseLegacy()` and `BaseTool.execute()` separation.

### 2. Assistant Message Parsing

**Path:** `src/core/assistant-message/`

Two parsing strategies:
- **V1 Parser** (`parseAssistantMessage.ts`) - Character-by-character XML parsing
- **V2 Parser** (`AssistantMessageParser.ts`) - Enhanced with native protocol support
- **Native Tool Call Parser** - For OpenAI-style function calls

Supports streaming (partial messages) for real-time UI updates.

### 3. Context Window Management

**Path:** `src/core/context-management/`

**Features:**
- Automatic conversation condensing (summarization)
- Context window error recovery
- Token budget management
- Graceful degradation strategies

**Key Functions:**
- `manageContext()` - Decide when to condense
- `summarizeConversation()` - Generate summaries
- `getEffectiveApiHistory()` - Get messages for LLM

### 4. Checkpoint/Recovery System

**Path:** `src/core/checkpoints/`

**Two Services:**
- `ShadowCheckpointService` - Background checkpoints
- `RepoPerTaskCheckpointService` - Git-based checkpoints

**Features:**
- Automatic checkpoints on tool calls
- Restore to any checkpoint
- Diff between checkpoints
- Configurable timeout (default: 60 seconds)

### 5. MCP (Model Context Protocol) Integration

**Path:** `src/services/mcp/`

**Components:**
- `McpHub` - Central MCP server registry
- `McpServerManager` - Singleton manager for all providers
- Individual MCP tools exposed to LLM

**Features:**
- Dynamic tool discovery from MCP servers
- Resource access (files, prompts, etc.)
- Server creation support
- Tool filtering by mode

### 6. Auto-Approval System

**Path:** `src/core/auto-approval/`

**Features:**
- Configurable auto-approval rules
- Pattern-based approval (file patterns, commands)
- Mode-specific rules
- Safety overrides

### 7. Code Indexing

**Path:** `src/services/code-index/`

**Uses Tree-sitter** for:
- Fast symbol extraction
- Code structure understanding
- Semantic search support
- Embedding generation for codebase search

---

## Webview UI Architecture

**Path:** `webview-ui/`

**Tech Stack:**
- React with TypeScript
- Tailwind CSS V4
- Shadcn/ui components
- i18n for localization
- Vite for bundling

**Key Components:**
- `App.tsx` - Main application
- Various UI components for messages, chat, settings
- Context providers for state management
- Custom hooks for extension interaction

---

## Message Flow

```
User Input (Webview)
    ↓
webviewMessageHandler (ClineProvider)
    ↓
Task.startLoop() / Task.resumeTask()
    ↓
buildApiHandler(providerSettings)
    ↓
buildNativeToolsArray({mode, cwd, ...})
    ↓
generateSystemPrompt(context, mode, tools, ...)
    ↓
ApiHandler.createMessage(systemPrompt, messages, tools)
    ↓
Streaming Response (ApiStream)
    ↓
parseAssistantMessage(response) [V1 or V2]
    ↓
ToolExecution Loop:
    For each tool:
        ↓
        BaseTool.handle(task, block, callbacks)
        ↓
        parseLegacy() OR use nativeArgs
        ↓
        execute()
        ↓
        pushToolResult() to Task
    ↓
Repeat until attempt_completion or user interruption
```

---

## Key Design Decisions

1. **Monorepo with Turborepo** - Fast builds, shared code
2. **Type Safety First** - Comprehensive TypeScript types in `@roo-code/types`
3. **Dual Protocol** - Maintain backward compatibility while supporting modern tool calling
4. **Event-Driven** - Extensive use of EventEmitter for loose coupling
5. **Service-Oriented** - Clear separation of concerns (services/, integrations/)
6. **Mode-Based Architecture** - Different tool sets and instructions for different workflows
7. **Checkpoint-First** - Built-in recovery system for reliability
8. **Cloud-Native** - Designed to work with Roo Code Cloud for sync/auth
9. **MCP Integration** - Extensible via Model Context Protocol
10. **Custom Tool Support** - Experimental feature for user-defined tools

---

## Comparison with GLM CLI JBang

### Similarities

| Aspect | Roo-Code | GLM CLI JBang |
|--------|----------|---------------|
| Language | TypeScript/JavaScript | Groovy/JBang |
| Entry Point | VS Code Extension (`extension.ts`) | CLI (`GlmCli.groovy`) |
| Task Management | Task class with conversation history | Agent class with MessageStore |
| Tool System | BaseTool with dual protocol | Agent tools (Bash, File operations) |
| State Persistence | ContextProxy + task persistence | SessionManager + MessageStore |
| Configuration | ProviderSettingsManager + modes | Config + AgentConfig |
| API Layer | buildApiHandler factory | GlmClient |
| Subagents | NewTaskTool + mode switching | Subagent + SubagentPool |

### Architectural Differences

#### 1. Platform Context
- **Roo-Code**: VS Code extension with rich UI (React webview)
- **GLM CLI JBang**: Terminal-first CLI with optional TUI (Lanterna/Jexer)

#### 2. Tool Architecture
- **Roo-Code**: Dual protocol (XML + Native), streaming support, rich tool ecosystem
- **GLM CLI JBang**: Simpler tool model, focused on file operations and bash commands

#### 3. State Management
- **Roo-Code**: ContextProxy with caching, separate API/display message layers
- **GLM CLI JBang**: SessionManager with MessageStore, simpler persistence

#### 4. Modes/Agents
- **Roo-Code**: Mode-based with tool groups, custom modes in `.roomodes`
- **GLM CLI JBang**: AgentType with SubagentPool, agent switching capability

#### 5. Context Management
- **Roo-Code**: Sophisticated context window management with summarization
- **GLM CLI JBang**: TokenTracker but less advanced condensing

#### 6. Integration
- **Roo-Code**: LSP, MCP, Code Index, Cloud services
- **GLM CLI JBang**: LSPManager, basic integrations

---

## Key Insights for GLM CLI JBang

### 1. Dual Protocol Support

**Insight:** Roo-Code's dual protocol system enables backward compatibility while supporting modern tool calling.

**Recommendation:** Consider supporting both XML-style and JSON-style tool calls for flexibility.

### 2. Mode-Based Architecture

**Insight:** Mode-based tool groups provide fine-grained control over agent capabilities.

**Recommendation:** Extend GLM CLI JBang's AgentType system with:
- Tool groups (read, edit, command, etc.)
- Mode-specific tool filtering
- Custom mode configuration files (similar to `.roomodes`)

### 3. Streaming Tool Execution

**Insight:** Roo-Code's `handlePartial()` enables real-time UI updates during tool execution.

**Recommendation:** Add streaming support to tool execution for better UX.

### 4. Checkpoint System

**Insight:** Built-in checkpoint/recovery provides reliability and undo capability.

**Recommendation:** Implement checkpoint system using:
- Shadow checkpoints (in-memory)
- Git-based checkpoints for recovery

### 5. Context Window Management

**Insight:** Automatic context condensing prevents hitting token limits.

**Recommendation:** Enhance TokenTracker with:
- Conversation summarization
- Context window error recovery
- Graceful degradation strategies

### 6. MCP Integration

**Insight:** Model Context Protocol enables extensible tool ecosystem.

**Recommendation:** Consider MCP server support for:
- Dynamic tool discovery
- Resource access
- Server creation

### 7. Auto-Approval System

**Insight:** Pattern-based auto-approval reduces user friction for safe operations.

**Recommendation:** Implement auto-approval rules:
- File pattern matching
- Command pattern matching
- Mode-specific rules

### 8. Code Indexing

**Insight:** Tree-sitter-based code indexing enables semantic search.

**Recommendation:** Add code indexing with Tree-sitter for:
- Fast symbol extraction
- Semantic codebase search
- Better context understanding

### 9. Separation of API and Display Messages

**Insight:** Roo-Code separates API messages (for LLM) from display messages (for UI).

**Recommendation:** Refactor MessageStore to maintain:
- API-level messages (optimized for LLM context)
- Display-level messages (optimized for UI)

### 10. Event-Driven Architecture

**Insight:** Extensive use of EventEmitter enables loose coupling between components.

**Recommendation:** Add event system for:
- Task lifecycle events (start, complete, error)
- State changes
- Tool execution events

### 11. Rich Tool Ecosystem

**Insight:** Roo-Code has 20+ tools covering various use cases.

**Recommendation:** Expand GLM CLI JBang's tool set:
- Browser automation tool
- Codebase search (semantic)
- Diff/patch application
- More advanced file operations

### 12. Mode-Specific Configuration

**Insight:** Modes can have custom instructions and tool configurations.

**Recommendation:** Extend AgentConfig with:
- Custom instructions per agent type
- Tool filtering patterns
- Specialized prompts

---

## Recommended Architecture Enhancements for GLM CLI JBang

### Phase 1: Core Improvements

1. **Streaming Support**
   - Add streaming to tool execution
   - Real-time progress updates

2. **Mode-Based Tool Groups**
   - Define tool groups (read, edit, command, search)
   - Associate groups with AgentType
   - Add file regex filtering for edit tools

3. **Context Window Management**
   - Implement conversation summarization
   - Add context condensing logic
   - Error recovery for context limits

### Phase 2: Reliability Features

4. **Checkpoint System**
   - Shadow checkpoints (in-memory)
   - Git-based checkpoints
   - Restore and diff functionality

5. **Auto-Approval System**
   - Pattern-based approval
   - Mode-specific rules
   - Safety overrides

6. **Event System**
   - Task lifecycle events
   - State change events
   - Tool execution events

### Phase 3: Advanced Features

7. **Code Indexing**
   - Tree-sitter integration
   - Symbol extraction
   - Semantic search

8. **MCP Integration**
   - MCP server support
   - Dynamic tool discovery
   - Resource access

9. **Rich Tool Ecosystem**
   - Browser automation
   - Codebase search
   - Advanced file operations

### Phase 4: Configuration System

10. **Custom Agent Configuration**
    - Agent config files (similar to `.roomodes`)
    - Custom instructions
    - Tool filtering

11. **Provider Management**
    - Multiple provider profiles
    - Mode-specific model selection
    - Migration support

---

## Conclusion

Roo-Code demonstrates a sophisticated, production-ready architecture for AI coding agents. Key strengths include:

1. **Flexibility** - Mode-based architecture with custom configurations
2. **Reliability** - Checkpoint system, error recovery, auto-retries
3. **Extensibility** - MCP integration, custom tools, provider abstraction
4. **User Experience** - Streaming responses, rich UI, auto-approval
5. **Type Safety** - Comprehensive TypeScript types

The GLM CLI JBang project can benefit from adopting several of these patterns, particularly:
- Mode-based tool groups
- Context window management
- Checkpoint system
- Streaming support
- Event-driven architecture

These enhancements would make GLM CLI JBang more robust, flexible, and user-friendly while maintaining its CLI-first approach.

---

## References

- Roo-Code Repository: https://github.com/Roo-Code/Roo-Code
- GLM CLI JBang Repository: (local path)
- Model Context Protocol: https://modelcontextprotocol.io/
- Tree-sitter: https://tree-sitter.github.io/tree-sitter/

---

*Document generated: January 4, 2026*
