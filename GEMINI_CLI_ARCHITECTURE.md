# Gemini CLI Architecture Analysis

## Overview

Gemini CLI is an open-source AI agent that brings the power of Google Gemini directly into your terminal. It provides lightweight access to Gemini, offering the most direct path from prompt to model.

**Repository**: https://github.com/google-gemini/gemini-cli  
**Documentation**: https://geminicli.com/docs  
**License**: Apache-2.0  
**Stars**: 89.3k | Forks: 10.3k | Contributors: 424  
**Primary Language**: TypeScript (98.2%)

## Core Architecture

### Component Structure

Gemini CLI follows a clean two-package architecture with a modular tool system:

```
packages/
├── cli/          # Frontend - User-facing terminal interface
└── core/         # Backend - Model orchestration and tool execution
```

### 1. CLI Package (`packages/cli`)

**Purpose**: User-facing interface that manages terminal interaction and user experience

**Key Functions**:
- **Input Processing**: Handles various input types
  - Direct text entry
  - Slash commands (`/help`, `/clear`, `/model`, `/settings`)
  - At commands (`@file` for file references)
  - Exclamation commands (`!command` for shell execution)
  
- **History Management**: Maintains conversation history for context retention
  
- **Display Rendering**: Formats and presents responses with syntax highlighting and proper terminal formatting
  
- **Theme & UI Customization**: Supports customizable themes, colors, and UI elements
  
- **Configuration Management**: Manages settings through multiple channels
  - JSON settings files (`~/.gemini/settings.json`, `.gemini/settings.json`)
  - Environment variables
  - Command-line arguments
  - Interactive commands (`/settings`)

- **User Experience Features**:
  - Checkpointing for conversation save/resume
  - Session management
  - Custom commands
  - Keyboard shortcuts
  - Progress indicators
  - Diff visualization for file changes

### 2. Core Package (`packages/core`)

**Purpose**: Backend orchestration layer that manages Gemini API interactions and tool execution

**Key Functions**:
- **API Client**: Communicates with Google Gemini API (Gemini 2.5 Pro/Flash)
  
- **Prompt Construction**: Builds prompts incorporating:
  - Conversation history
  - Available tool definitions
  - Context from files (GEMINI.md)
  - System prompts
  - User configuration
  
- **Tool Registration & Execution**:
  - ToolRegistry manages available tools
  - Validates tool parameters
  - Executes tools based on model requests
  - Handles confirmation workflow
  
- **State Management**: Maintains conversation and session state
  
- **Server-side Configuration**: Handles server settings and policies

### 3. Tools System (`packages/core/src/tools/`)

**Purpose**: Modular capabilities that extend the model's functionality

The tool system is designed for extensibility through:
- **BaseTool Interface**: Defines the contract for all tools
- **Tool Registry**: Centralized management of available tools
- **Dynamic Discovery**: Command-based and MCP-based tool discovery

## Interaction Flow

```
┌─────────────┐
│   User      │
│  Input      │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  CLI Package │
│  (packages/  │
│     cli)     │
└──────┬──────┘
       │
       │ Sends input
       ▼
┌─────────────┐
│  Core Package │
│  (packages/  │
│     core)    │
│              │
│  • Constructs│
│    prompt    │
│  • Sends to  │
│  Gemini API  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Gemini API  │
│  (2.5 Pro/   │
│   Flash)     │
└──────┬──────┘
       │
       │ Returns response
       ▼
┌─────────────┐
│  Tool Needed?│
└──────┬──────┘
       │
       ├── No ──► Direct response
       │
       └── Yes ──► ┌─────────────┐
                  │  Tool Exec   │
                  │  + Approval  │
                  └──────┬──────┘
                         │
                         ▼
                  ┌─────────────┐
                  │  Tool Result│
                  └──────┬──────┘
                         │
                         ▼
                  ┌─────────────┐
                  │  Gemini API │
                  │  processes  │
                  │  result     │
                  └──────┬──────┘
                         │
                         ▼
                  ┌─────────────┐
                  │  Final      │
                  │  Response   │
                  └──────┬──────┘
                         │
                         ▼
                  ┌─────────────┐
                  │  CLI Display│
                  │  to User    │
                  └─────────────┘
```

### Step-by-Step Flow:

1. **User Input**: User types prompt/command in terminal (handled by CLI)
2. **Request to Core**: CLI sends input to Core package
3. **Request Processing**: Core constructs prompt with:
   - Conversation history
   - Available tool definitions
   - Context from GEMINI.md files
   - Sends to Gemini API
4. **Model Response**: Gemini returns either:
   - Direct answer, or
   - Request to use a tool (FunctionCall)
5. **Tool Execution** (if applicable):
   - Core retrieves tool from ToolRegistry
   - Tool validates parameters
   - Check if confirmation required (shouldConfirmExecute)
   - If yes, CLI prompts user for approval
   - Tool's execute() method runs with AbortSignal
   - ToolResult returned (llmContent + returnDisplay)
6. **Response Processing**: Tool result sent back to model for final response
7. **Display**: CLI formats and displays response to user

## Tool Architecture

### Tool Interface

Each tool implements the `BaseTool` interface:

```typescript
interface BaseTool {
  name: string                    // Unique internal name
  displayName: string              // User-friendly name
  description: string              // Provided to model
  parameterSchema: JSONSchema     // Tool parameters
  validateToolParams(): void       // Validate inputs
  getDescription(): string         // What tool will do
  shouldConfirmExecute(): boolean  // Need approval?
  execute(params): ToolResult     // Execute action
}
```

### ToolResult Structure

```typescript
interface ToolResult {
  llmContent: string | PartListUnion  // For model context
  returnDisplay: string | FileDiff      // For user display
}
```

### Tool Registry

The `ToolRegistry` class manages:

- **Tool Registration**: Collection of built-in and discovered tools
- **Tool Discovery**:
  - Command-based: Execute `tools.discoveryCommand` to get JSON tool definitions
  - MCP-based: Connect to MCP servers to discover tools
- **Schema Provision**: Expose FunctionDeclaration schemas to model
- **Tool Retrieval**: Get tool by name for execution

### Built-in Tools

#### File System Tools

1. **list_directory (ReadFolder)**
   - List files in directory
   - Optional gitignore respect
   - Parameters: `path` (required), `ignore`, `respect_git_ignore`
   - No confirmation required

2. **read_file (ReadFile)**
   - Read file content (text, images, audio, PDF)
   - Supports line ranges (offset/limit)
   - Parameters: `path` (required), `offset`, `limit`
   - Handles multimodal content (base64 encoding)
   - No confirmation required

3. **write_file (WriteFile)**
   - Write content to file (create or overwrite)
   - Creates parent directories
   - Parameters: `file_path`, `content` (both required)
   - **Confirmation Required**: Shows diff before writing

4. **glob (FindFiles)**
   - Find files by glob pattern
   - Sorted by modification time (newest first)
   - Parameters: `pattern` (required), `path`, `case_sensitive`, `respect_git_ignore`
   - No confirmation required

5. **search_file_content (SearchText)**
   - Search for regex patterns in files
   - Uses git grep if available, else system grep
   - Parameters: `pattern` (required), `path`, `include`
   - No confirmation required

6. **replace (Edit)**
   - In-place file modifications
   - Multi-stage edit correction for robustness
   - Parameters: `file_path`, `old_string`, `new_string` (all required), `expected_replacements`
   - **Confirmation Required**: Shows diff before editing
   - Handles file creation (if old_string is empty)

7. **read_many_files**
   - Reads multiple files or glob patterns
   - Used by `@` command in CLI
   - Efficient batch reading

#### Execution Tools

**run_shell_command (ShellTool)**
   - Execute shell commands
   - Supports interactive commands (if enabled)
   - Background process support (using &)
   - Parameters: `command` (required), `description`, `directory`

   **Features**:
   - Interactive mode (via node-pty)
   - Color output support
   - Custom pager (default: cat)
   - Returns: stdout, stderr, exit code, signal, background PIDs
   - **Confirmation Required** for potentially dangerous operations

   **Configuration**:
   ```json
   {
     "tools": {
       "shell": {
         "enableInteractiveShell": true,
         "showColor": true,
         "pager": "less"
       }
     }
   }
   ```

   **Command Restrictions**:
   - Allowlist via `tools.core`: `["run_shell_command(git)"]`
   - Blocklist via `tools.exclude`: `["run_shell_command(rm)"]`
   - Prefix matching for flexibility
   - Command chaining disabled for security
   - Blocklist takes precedence

#### Web Tools

1. **web_fetch (WebFetchTool)**
   - Retrieve content from URLs
   - Supports various content types
   - Parameters: URL, options
   - No confirmation required

2. **web_search (WebSearchTool)**
   - Search the web for current information
   - Google Search grounding integration
   - Parameters: query, options
   - No confirmation required

#### Memory Tools

**memory_tool (MemoryTool)**
   - Interact with AI's memory
   - Save and recall information across sessions
   - Parameters: action, data
   - No confirmation required

#### Additional Tools

**todo_tool (TodosTool)**
   - Create and manage structured task lists
   - Track progress during coding sessions
   - Parameters: action, todos

## Configuration System

### Configuration Layers (Precedence Order)

1. **Command-line arguments** (highest priority)
2. **Environment variables**
3. **Project settings** (`.gemini/settings.json`)
4. **User settings** (`~/.gemini/settings.json`)
5. **System settings**
6. **Default values** (lowest priority)

### Configuration Categories

#### General Settings
- Model selection (Gemini 2.5 Pro, Gemini 2.5 Flash)
- Session turn limits
- Context window settings
- Compression settings

#### UI Settings
- Theme customization (Light/Dark/Auto)
- Banner visibility
- Footer display
- Color schemes

#### Tool Settings
- Shell command configuration
- Interactive shell mode
- Command restrictions (allowlist/blocklist)
- MCP server configuration
- Tool discovery

#### Context Settings
- Include directories
- GEMINI.md context files
- .geminiignore file filtering
- Token caching

#### Security Settings
- Trusted folders
- Sandboxing configuration
- Approval modes
- Command restrictions

#### Advanced Settings
- Debug mode
- Custom bug reporting
- System prompt override
- Telemetry

## Key Design Principles

### 1. Modularity
- **Separation of Concerns**: CLI (frontend) separated from Core (backend)
- **Independent Development**: Both packages can evolve independently
- **Future Extensibility**: Enables multiple frontends for same backend

### 2. Extensibility
- **Tool System**: Modular tools that can be added without modifying core
- **Dynamic Discovery**: Command-based and MCP-based tool discovery
- **Custom Tools**: Users can define custom tools via JSON
- **MCP Protocol**: Standard for connecting external services

### 3. User Experience
- **Terminal-First**: Rich interactive terminal experience
- **Syntax Highlighting**: Code-aware formatting and display
- **Customizable Themes**: Personalizable UI appearance
- **Intuitive Commands**: Slash commands and at references
- **Checkpointing**: Save and resume complex conversations
- **Custom Commands**: Create reusable command shortcuts

### 4. Security
- **Approval Mechanisms**: User confirmation for destructive operations
- **Command Restrictions**: Allowlist/blocklist for shell commands
- **Sandboxing**: Isolates model changes from host environment
- **Path Validation**: Restricts file operations to root directory
- **Credential Management**: Secure OAuth and API key handling

### 5. Flexibility
- **Multiple Authentication Methods**:
  - Google OAuth (60 req/min, 1,000 req/day free)
  - Gemini API Key (100 req/day free)
  - Vertex AI (enterprise)
- **Multi-Modal Operation**:
  - Interactive mode (terminal UI)
  - Headless mode (scripts/CI with JSON/stream-json output)
  - GitHub Actions integration
- **Multimodal Support**: Text, images, audio, PDFs
- **Unix Philosophy**: Composable and scriptable

## Authentication

### Option 1: Login with Google (OAuth)
- **Best for**: Individual developers and Code Assist License holders
- **Benefits**:
  - **Free tier**: 60 requests/min, 1,000 requests/day
  - **Gemini 2.5 Pro** with 1M token context
  - No API key management
  - Automatic model updates

**Setup**:
```bash
gemini
# Choose "Login with Google"
# Follow browser authentication
```

**Organization Setup**:
```bash
export GOOGLE_CLOUD_PROJECT="YOUR_PROJECT_ID"
gemini
```

### Option 2: Gemini API Key
- **Best for**: Developers needing specific model control
- **Benefits**:
  - **Free tier**: 100 requests/day
  - Model selection flexibility
  - Usage-based billing for higher limits

**Setup**:
```bash
# Get key from https://aistudio.google.com/apikey
export GEMINI_API_KEY="YOUR_API_KEY"
gemini
```

### Option 3: Vertex AI
- **Best for**: Enterprise teams and production workloads
- **Benefits**:
  - Enterprise security and compliance
  - Higher rate limits with billing
  - Google Cloud infrastructure integration

**Setup**:
```bash
# Get key from Google Cloud Console
export GOOGLE_API_KEY="YOUR_API_KEY"
export GOOGLE_GENAI_USE_VERTEXAI=true
gemini
```

## Features & Capabilities

### Core Features

1. **Code Understanding & Generation**
   - Query and edit large codebases
   - Multimodal app generation (from PDFs, images, sketches)
   - Natural language debugging

2. **Automation & Integration**
   - Operational task automation (PR queries, complex rebases)
   - MCP servers for media generation (Imagen, Veo, Lyria)
   - Non-interactive scripting workflows

3. **Advanced Capabilities**
   - **Google Search Grounding**: Real-time information integration
   - **Checkpointing**: Save/resume complex sessions
   - **Context Files** (GEMINI.md): Project-specific behavior customization
   - **Multimodal Support**: Text, images, audio, PDFs

### GitHub Integration

**Gemini CLI GitHub Action**: https://github.com/google-github-actions/run-gemini-cli

- **Pull Request Reviews**: Automated code review with contextual feedback
- **Issue Triage**: Automated labeling and prioritization
- **On-demand Assistance**: Mention `@gemini-cli` in issues/PRs
- **Custom Workflows**: Build automated workflows for team needs

## Interaction Patterns

### At Commands (`@`)

Reference files and directories directly:
```bash
# Read specific file
@src/main.ts Explain this code

# Read multiple files
@package.json @README.md Summarize the project

# Read directory
@src/ What's in the src folder?
```

### Slash Commands (`/`)

Manage CLI behavior:
- `/help` - Display available commands
- `/clear` - Clear conversation history
- `/model` - Select model
- `/settings` - Open settings
- `/checkpoint` - Save session
- `/restore` - Restore session
- `/exit` - Quit

### Exclamation Commands (`!`)

Execute shell commands directly:
```bash
!ls -la
!npm install
!git status
```

### Headless Mode

Non-interactive use for scripts and CI:

```bash
# Simple text response
gemini -p "Explain this codebase"

# JSON output for parsing
gemini -p "Summarize recent changes" --output-format json

# Stream JSON for real-time monitoring
gemini -p "Run tests" --output-format stream-json

# Non-interactive with specific model
gemini -m gemini-2.5-flash -p "Quick question"
```

## Context Management

### GEMINI.md

Custom context files for project-specific behavior:

```markdown
# GEMINI.md - Project Context

## Project Overview
This is a Node.js web application using Express.

## Coding Standards
- Use TypeScript strict mode
- Follow ESLint rules
- Write tests for all new features

## Build Process
```bash
npm run build
npm run test
```

## Architecture
- Frontend: React
- Backend: Express.js
- Database: PostgreSQL
```

### Token Caching

Optimize API usage and speed:
- Cache frequently accessed content
- Reduce redundant API calls
- Faster response times
- Lower costs

## Security Features

### Sandboxing

Isolates CLI from host environment:
- Docker-based isolation
- Configurable resource limits
- Network restrictions
- File system sandboxing

### Trusted Folders

Control execution policies by folder:
- Define trusted directories
- Different rules per folder
- Example: `~/.gemini/trusted-folders.json`

### Command Restrictions

Shell command allowlist/blocklist:
```json
{
  "tools": {
    "core": ["run_shell_command(git)", "run_shell_command(npm)"],
    "exclude": ["run_shell_command(rm)"]
  }
}
```

## Release Cadence

### Preview Releases
- **Frequency**: Weekly (Tuesdays, 23:59 UTC)
- **Tag**: `preview`
- **Purpose**: Early testing, may contain bugs
- **Installation**: `npm install -g @google/gemini-cli@preview`

### Stable Releases
- **Frequency**: Weekly (Tuesdays, 20:00 UTC)
- **Tag**: `latest` (default)
- **Purpose**: Full promotion of preview + bug fixes
- **Installation**: `npm install -g @google/gemini-cli`

### Nightly Releases
- **Frequency**: Daily (00:00 UTC)
- **Tag**: `nightly`
- **Purpose**: Latest from main branch
- **Status**: May have pending issues
- **Installation**: `npm install -g @google/gemini-cli@nightly`

## Technical Stack

### Core Technologies
- **Node.js 20+**: Runtime environment
- **TypeScript**: Primary language (98.2%)
- **JavaScript**: Secondary (1.8%)
- **NPM**: Package management and distribution

### Build Tools
- **esbuild**: Fast JavaScript bundler
- **Vitest**: Unit testing framework
- **ESLint**: Linting with custom rules
- **Prettier**: Code formatting
- **Husky**: Git hooks management

### Specialized Tools
- **node-pty**: Interactive shell support
- **ripgrep**: Fast file searching
- **grep**: Pattern searching

### Deployment
- **Docker**: Containerization support
- **Homebrew**: macOS/Linux package manager
- **NPM**: Cross-platform distribution

## Ecosystem & Extensions

### MCP Integration

Model Context Protocol for extending capabilities:
- **GitHub**: List pull requests, issues
- **Slack**: Send messages, read channels
- **Google Drive**: Access documents
- **Databases**: Query and update
- **Media Generation**: Imagen, Veo, Lyria

**MCP Configuration**:
```json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"]
    },
    "slack": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-slack"]
    }
  }
}
```

### Custom Extensions

Create reusable commands and behaviors:
- Custom commands in settings
- Custom context files (GEMINI.md)
- Tool discovery via commands
- Hooks for workflow automation

## IDE Integration

### VS Code Companion

Seamless integration with VS Code:
- Terminal within VS Code
- Code-aware suggestions
- File context awareness
- Keyboard shortcuts

## Development Workflow

### Project Structure
```
gemini-cli/
├── packages/
│   ├── cli/              # Terminal interface
│   │   ├── src/
│   │   │   ├── commands/     # Slash commands
│   │   │   ├── ui/           # Terminal UI
│   │   │   └── history/      # Conversation management
│   └── core/             # Agent core
│       ├── src/
│       │   ├── tools/        # Built-in tools
│       │   ├── api/          # API client
│       │   ├── prompts/      # Prompt construction
│       │   └── registry/     # Tool registry
├── docs/                  # Documentation
├── integration-tests/      # End-to-end tests
├── scripts/               # Build/deployment scripts
├── schemas/               # JSON schemas
└── examples/              # Usage examples
```

### Testing
- **Unit Tests**: Vitest framework
- **Integration Tests**: Comprehensive end-to-end scenarios
- **CI/CD**: GitHub Actions automation
- **Chained E2E**: Multi-step workflow testing

### Contribution
- 424 contributors
- 3,744 commits
- Active development
- Comprehensive documentation
- Community-driven roadmap

## Performance Characteristics

### Multimodal Capabilities
- **Text**: Primary interaction mode
- **Images**: PNG, JPG, GIF, WEBP, SVG, BMP
- **Audio**: MP3, WAV, AIFF, AAC, OGG, FLAC
- **Documents**: PDF parsing

### Context Window
- **Gemini 2.5 Pro**: 1M token context
- **Large Codebase Support**: Entire projects in single conversation
- **Smart Context Management**: Compression, caching, checkpointing

### Rate Limits

| Authentication | Requests/Min | Requests/Day |
|----------------|----------------|---------------|
| Google OAuth    | 60             | 1,000         |
| API Key (Free)  | -              | 100            |
| Vertex AI       | Enterprise      | Custom         |

## Architecture Comparison with Other CLI Agents

### vs Qwen Code
| Feature | Gemini CLI | Qwen Code |
|---------|-------------|-------------|
| Language | TypeScript | TypeScript |
| Model | Gemini 2.5 Pro/Flash | Qwen3-Coder |
| License | Apache-2.0 | Apache-2.0 |
| Stars | 89.3k | 16.9k |
| OAuth Free Tier | 60 req/min, 1K/day | 2K/day |
| Context Window | 1M tokens | Varies by model |
| Google Search | Native integration | Via web tool |
| Interactive Shell | Yes (node-pty) | Yes |
| MCP Support | Yes | Yes |

### vs Claude Code (SST OpenCode)
| Feature | Gemini CLI | Claude Code |
|---------|-------------|-------------|
| Language | TypeScript | Groovy |
| Model | Gemini 2.5 | Claude 3.5 Sonnet |
| License | Apache-2.0 | MIT |
| Free Tier | Yes (OAuth) | No (API key only) |
| Context Window | 1M tokens | ~200K tokens |
| Multimodal | Native (images, audio, PDF) | Via tools |
| Google Search | Native | Via web tool |
| Shell Interactive | Yes | Yes |

## Key Innovations

### 1. Multistage Edit Correction
The `replace` tool incorporates a sophisticated correction mechanism:
- If initial `old_string` isn't found or matches multiple locations
- Tool leverages Gemini model to iteratively refine the string
- Self-correction process identifies unique segments
- Significantly improves edit success rate

### 2. Rich Content Support
Tools can return `PartListUnion` for multimodal content:
- Mix of Part objects (images, audio) and strings
- Single tool execution can return multiple rich pieces
- Enables advanced workflows

### 3. Google Search Grounding
Native integration for real-time information:
- Grounding capabilities built into Gemini API
- Web searches leverage Google's infrastructure
- Up-to-date information without separate API calls

### 4. Command Restrictions System
Flexible and secure shell command control:
- Prefix matching for flexibility
- Allowlist/blocklist approach
- Command chaining disabled
- Blocklist precedence for security

### 5. Tool Discovery Patterns
Multiple extension mechanisms:
- **Command-based**: Execute custom command to get JSON tool definitions
- **MCP-based**: Connect to MCP servers for tools
- **Dynamic registration**: Runtime tool discovery

## Security Considerations

### Tool Safety
- **Approval Required**: Write operations need user confirmation
- **Command Restrictions**: Allowlist/blocklist for shell
- **Path Validation**: Prevents directory traversal
- **Sandboxing**: Isolates potentially dangerous operations

### Data Privacy
- **Local Storage**: Credentials cached locally
- **OAuth Tokens**: Short-lived, secure storage
- **Telemetry**: Optional usage collection
- **No Code Upload**: Processing via API or local

### OAuth Security
- Browser-based flow
- Google Account authentication
- Secure credential management
- Project-based organization access

### Shell Security
- Command validation
- Chaining disabled
- Environment isolation (GEMINI_CLI=1)
- Interactive mode optional

## Future Roadmap

Based on the repository structure and documentation:
- Enhanced MCP ecosystem
- More tools and integrations
- Improved multimodal support
- Better interactive shell features
- Performance optimizations
- Additional language support

## Conclusion

Gemini CLI represents a mature, enterprise-grade AI CLI agent with:

1. **Massive Adoption**: 89.3k stars, 10.3k forks (5x larger than Qwen Code)
2. **Strong Architecture**: Clean two-package separation with robust tool system
3. **Enterprise Features**: Vertex AI integration, Google Search grounding, OAuth
4. **Multimodal Native**: First-class support for images, audio, PDFs
5. **Google Integration**: Deep integration with Google ecosystem
6. **Extensible Design**: Multiple tool discovery mechanisms (command, MCP)
7. **Security-First**: Comprehensive approval, restriction, and sandboxing features
8. **Production-Ready**: Mature release cadence, extensive testing

Its architecture demonstrates Google's commitment to:
- **Developer Experience**: Rich terminal UI, checkpointing, custom commands
- **Enterprise Readiness**: Multiple authentication methods, Google Cloud integration
- **Extensibility**: Tool API, MCP support, custom extensions
- **Security**: Multiple layers of protection for production use

The tool system's multi-stage edit correction, rich content support, and flexible discovery mechanisms set a high bar for AI CLI agents. Gemini CLI serves as the foundation for derivative projects like Qwen Code, demonstrating its thoughtful and production-tested architecture.

---

**Sources**:
- Gemini CLI README: https://github.com/google-gemini/gemini-cli
- Architecture Documentation: https://geminicli.com/docs/architecture
- Tools API: https://geminicli.com/docs/core/tools-api
- File System Tools: https://geminicli.com/docs/tools/file-system
- Shell Tool: https://geminicli.com/docs/tools/shell
- Official Documentation: https://geminicli.com/docs
