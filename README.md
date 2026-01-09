# GLM-CLI (JBang Edition)

![Version](https://img.shields.io/badge/version-1.0.0-blue?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)
![Groovy](https://img.shields.io/badge/Groovy-4.x-orange?style=flat-square)
![JBang](https://img.shields.io/badge/JBang-compatible-red?style=flat-square)

A native-performance AI coding agent CLI for Linux, macOS, and Windows, powered by multiple AI providers including Z.ai's GLM-4 models and OpenCode Zen's curated models, built with JBang and Groovy.

![Demo](https://img.shields.io/badge/demo-available-lightgrey?style=flat-square)

⚠️ Under Active Development - glm-cli-jbang is actively developed. If you ⭐ star and 👀 watch this repository, it would mean a lot to me.

## Features

| Feature | Description |
|---------|-------------|
| **TUI** | Full-screen terminal interface with sidebar showing LSP diagnostics, token usage, session info, and modified files |
| **Agent** | Autonomous task execution with ReAct loop for reading/writing files |
| **Chat** | Interactive conversation with multiple AI providers and models |
| **Web Search** | Integrated web search with real-time information retrieval |
| **LSP Integration** | Language Server Protocol support for diagnostics, hover, and goto definition |
| **Session Persistence** | H2 database for storing conversation history across sessions |
| **Skills** | Reusable agent workflows for common tasks |
| **RAG Pipeline** | Contextual understanding of your codebase |
| **Streaming** | Real-time response streaming using Server-Sent Events (SSE) |
| **Diff Preview** | See file changes before applying with diff visualization |
| **Tools** | 17 built-in tools with safety checks and permission levels |
| **Multi-Provider** | Support for Zai/Zhipu AI and OpenCode Zen providers |
| **Model Selection** | Choose from curated models including Big Pickle, GLM-4.7-free, and more |

## Prerequisites

- **Java 11+** (Automatically managed by JBang if needed)
- **JBang**: [Installation Guide](https://jbang.dev/download/)

## Installation

You can run the CLI directly from the source or install it as a global command.

### Run Directly

```bash
# Clone the repository
git clone https://github.com/kevintanhongann/glm-cli-jbang.git
cd glm-cli-jbang

# Run with help
./glm.groovy --help

# Start the TUI (default)
./glm.groovy

# Start with a specific model
./glm.groovy -m opencode/big-pickle
```

### Install as Global Command

```bash
jbang app install --name glm glm.groovy

# Now you can use it from anywhere
glm --help
glm chat -m opencode/big-pickle "Explain this code"
glm agent -m zai/glm-4.7 "Refactor User class"
```

### Verify Installation

```bash
glm --version
# Output: glm-cli 1.0.0
```

## Commands

| Command | Description |
|---------|-------------|
| `glm` | Launch the TUI (default) |
| `glm chat [message]` | Interactive chat session or send a single message |
| `glm agent "task"` | Autonomous task execution with ReAct loop |
| `glm auth <provider>` | Manage authentication for AI providers |
| `glm init [path]` | Initialize project with AGENTS.md |
| `glm session [list\|show\|clear]` | Manage persistent sessions |
| `glm models [provider]` | List available models |
| `glm skill <name> [args]` | Execute a registered skill |

### Chat Command

Start an interactive session or send a single message:

```bash
# Interactive chat session
glm chat

# Send a single message
glm chat "Explain this project structure"

# With a specific model
glm chat -m opencode/big-pickle "Write a sorting algorithm"
glm chat -m zai/glm-4.7 "Refactor this code"
glm chat -m opencode/glm-4.7-free "Help me debug"
```

### Agent Command

Ask the agent to perform a task involving file operations:

```bash
glm agent "Create a unit test for Person.groovy"
```

The agent will:
1. **Think** about the task and analyze requirements
2. **Propose** file changes with diff preview
3. **Ask** for your permission (Y/n)
4. **Execute** the write operation
5. **Verify** and report results

### Agent Workflows

```bash
# Refactor code
glm agent "Extract validation logic from User.groovy into a separate Validator class"

# Add tests
glm agent "Write comprehensive tests for the payment processing module"

# Fix bugs
glm agent "Investigate and fix the null pointer exception in AuthController"

# Documentation
glm agent "Add JavaDoc comments to all public methods in the API client"

# Research current information
glm agent "Search for recent news about Java 21 features and summarize"
```

### Authentication Command

Login to a provider:

```bash
# Login to OpenCode Zen (recommended, includes free models)
glm auth login opencode

# Login to Zai/Zhipu AI
glm auth login zai

# List configured credentials
glm auth list

# Logout from a provider
glm auth logout opencode
```

### Init Command

Initialize a project with AGENTS.md:

```bash
# Initialize current directory
glm init

# Initialize specific directory
glm init /path/to/project
```

### Session Command

Manage persistent sessions:

```bash
# List all sessions
glm session list

# Show session details
glm session show <session-id>

# Clear all sessions
glm session clear

# Start a new session
glm session start
```

### Models Command

List available models:

```bash
# List all models
glm models

# List models for a specific provider
glm models opencode
glm models zai

# Show detailed information
glm models --verbose

# Refresh model catalog
glm models --refresh
```

### Skill Command

Execute registered skills:

```bash
# List available skills
glm skill --list

# Execute a skill
glm skill code-review /path/to/file
glm skill add-tests --target=User.groovy
```

## Terminal UI (TUI)

Launch a full-screen terminal interface:

```bash
# Launch default TUI (Lanterna)
glm

# Launch with specific TUI
glm --tui lanterna    # Default, full-featured
glm --tui jexer       # Alternative TUI
glm --tui tui4j       # Another TUI implementation

# Simple console mode (no TUI)
glm --simple
```

### TUI Features

- **Sidebar Panels**:
  - LSP Diagnostics: Show compiler errors and warnings
  - Token Usage: Track API token consumption
  - Session Info: Current session statistics
  - Modified Files: Track changes made by the agent

- **Command Input**: Type messages with autocomplete
- **Activity Log**: View tool execution history
- **Model Selection**: Switch models on the fly

## Tools

The agent has access to 17 built-in tools:

| Tool | Description | Permission |
|------|-------------|------------|
| `read_file` | Read file contents | Always allowed |
| `write_file` | Write/create files with diff preview | Requires confirmation |
| `list_files` | List directory contents | Always allowed |
| `bash` | Execute bash commands | Requires confirmation |
| `grep` | Search file contents with regex | Always allowed |
| `glob` | Find files by pattern | Always allowed |
| `edit` | Make in-place file edits | Requires confirmation |
| `patch` | Apply patch operations | Requires confirmation |
| `multi_edit` | Batch file edits | Requires confirmation |
| `lsp` | Language Server Protocol queries | Always allowed |
| `web_search` | Search web for current information | Always allowed |
| `fetch_url` | Fetch content from URLs | Always allowed |
| `code_search` | AI-powered code search | Always allowed |
| `batch` | Execute multiple tools | Depends on tools |
| `task` | Execute subagent tasks | Always allowed |
| `skill` | Execute registered skills | Always allowed |

### Tool Calling Patterns

The agent can:
- Call tools sequentially for multi-step tasks
- Show diff previews before file modifications
- Handle errors and retry operations
- Maintain context across tool calls
- Execute tools in parallel for efficiency

## Agent System

GLM-CLI uses a **ReAct** (Reasoning + Acting) agent loop:

1. **Observe**: Read user input and conversation history
2. **Think**: Send context to the LLM API
3. **Act**: Either respond with text or call a tool
4. **Loop**: Append tool results and repeat until completion

## Configuration

The CLI looks for a configuration file at `~/.glm/config.toml`.

### Configuration File Example

```toml
[behavior]
default_model = "opencode/big-pickle"  # Format: provider/model-id
safety_mode = "ask" # 'ask' or 'always_allow'
language = "auto"    # 'auto', 'en', 'zh'

[tui]
type = "lanterna"    # 'lanterna', 'jexer', 'tui4j'
theme = "dark"       # 'dark', 'light', 'high-contrast'
```

### Available Providers

#### OpenCode Zen (Default)
- **Models**: Big Pickle, GLM-4.7-free, GLM-4.6, Kimi K2, Qwen3 Coder, Grok Code, MiniMax M2.1
- **Auth**: `glm auth login opencode`
- **Get API Key**: https://opencode.ai/auth
- **Free models**: Big Pickle, GLM-4.7-free, Grok Code, MiniMax M2.1

#### Zai/Zhipu AI
- **Models**: GLM-4-flash, GLM-4.7
- **Auth**: `glm auth login zai`
- **Get API Key**: https://open.bigmodel.cn/usercenter/apikeys

### Configuration Priority

1. Command-line flags (highest priority)
2. Provider credentials from `glm auth login`
3. `~/.glm/config.toml`
4. Default values (lowest priority)

## Project Structure

```
glm-cli-jbang/
├── glm.groovy                    # Main entry point (shebang, dependencies)
├── commands/                     # CLI command implementations
│   ├── GlmCli.groovy             # Root command with subcommands
│   ├── ChatCommand.groovy        # Interactive chat command
│   ├── AgentCommand.groovy       # Autonomous agent command
│   ├── AuthCommand.groovy        # Authentication management
│   ├── InitCommand.groovy        # Project initialization
│   ├── SessionCommand.groovy     # Session management
│   ├── ModelsCommand.groovy      # Model listing
│   └── SkillCommand.groovy       # Skills execution
├── core/                         # Core business logic
│   ├── Agent.groovy              # ReAct agent loop
│   ├── SessionManager.groovy     # Session persistence (H2)
│   ├── GlmClient.groovy          # HTTP/SSE client
│   ├── Config.groovy             # Configuration handler
│   ├── LSPManager.groovy         # Language Server Protocol
│   ├── TokenTracker.groovy       # Token usage tracking
│   ├── Subagent.groovy           # Subagent execution
│   ├── ModelCatalog.groovy       # Model catalog management
│   └── WebSearchClient.groovy    # Web search client
├── tools/                        # Tool implementations (17 tools)
│   ├── Tool.groovy               # Tool interface
│   ├── ReadFileTool.groovy       # Read file contents
│   ├── WriteFileTool.groovy      # Write files
│   ├── ListFilesTool.groovy      # List directory contents
│   ├── BashTool.groovy           # Execute bash commands
│   ├── GrepTool.groovy           # Search file contents
│   ├── GlobTool.groovy           # Find files by pattern
│   ├── EditTool.groovy           # In-place edits
│   ├── PatchTool.groovy          # Apply patches
│   ├── MultiEditTool.groovy      # Batch edits
│   ├── LSPTool.groovy            # LSP integration
│   ├── WebSearchTool.groovy      # Web search
│   ├── FetchUrlTool.groovy       # Fetch URLs
│   ├── CodeSearchTool.groovy     # AI code search
│   ├── BatchTool.groovy          # Execute multiple tools
│   ├── TaskTool.groovy           # Subagent tasks
│   └── SkillTool.groovy          # Execute skills
├── models/                       # API data models
│   ├── ChatRequest.groovy
│   ├── ChatResponse.groovy
│   ├── Message.groovy
│   ├── Session.groovy
│   ├── TokenStats.groovy
│   ├── Diagnostic.groovy
│   └── Skill.groovy
├── tui/                          # Terminal UI implementations
│   ├── LanternaTUI.groovy        # Default Lanterna-based TUI
│   ├── JexerTUI.groovy           # Jexer-based TUI
│   ├── Tui4jTUI.groovy           # TUI4J-based TUI
│   ├── shared/                   # Shared TUI components
│   ├── lanterna/                 # Lanterna widgets
│   ├── jexer/                    # Jexer widgets
│   └── tui4j/                    # TUI4J components
├── rag/                          # RAG pipeline for context
│   ├── CodebaseLoader.groovy
│   ├── CodeChunker.groovy
│   ├── EmbeddingService.groovy
│   └── RAGPipeline.groovy
└── tests/                        # Test suite
```

## Development

### Development Workflow

```bash
# Edit with IDE support
jbang edit glm.groovy

# Run tests
./glm.groovy test

# Install locally
jbang app install --force --name glm glm.groovy
```

### Dependencies

All dependencies are declared in `glm.groovy`:

```groovy
//DEPS dev.langchain4j:langchain4j:1.10.0
//DEPS dev.langchain4j:langchain4j-zhipu-ai:0.36.2
//DEPS info.picocli:picocli:4.7.7
//DEPS info.picocli:picocli-groovy:4.7.7
//DEPS org.apache.groovy:groovy-json:4.0.27
//DEPS org.apache.groovy:groovy-sql:4.0.27
//DEPS com.auth0:java-jwt:4.4.0
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.15.2
//DEPS io.github.java-diff-utils:java-diff-utils:4.12
//DEPS dev.langchain4j:langchain4j-easy-rag:1.0.0-beta2
//DEPS dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.0.0-beta2
//DEPS org.fusesource.jansi:jansi:2.4.1
//DEPS com.googlecode.lanterna:lanterna:3.1.2
//DEPS io.gitlab.autumnmeowmeow:jexer:2.0.0
//DEPS com.williamcallahan:tui4j:0.2.5
//DEPS com.h2database:h2:2.2.224
```

## FAQ

### How is this different from other AI coding agents?

**GLM-CLI** offers:
- 🖥️ **Full Terminal UI** - TUI with sidebar showing diagnostics, tokens, and session info
- 🌐 **Multi-provider support** - Works with Z.ai GLM models, OpenCode Zen, and more
- 📦 **Zero-dependency distribution** - JBang manages JDK, no installation beyond the script
- 🔒 **Diff previews** - See changes before applying with approval workflow
- ⚡ **Native performance** - JVM optimization for fast startup and execution
- 🎯 **Groovy simplicity** - Concise, readable codebase that's easy to extend
- 🔧 **17 Built-in tools** - File operations, bash, grep, glob, LSP, and more

### Can I use it with other LLM providers?

Yes! GLM-CLI supports multiple providers out of the box:
- **OpenCode Zen** - Recommended for free models like Big Pickle and GLM-4.7-free
- **Zai/Zhipu AI** - Direct access to GLM-4 models

The OpenAI-compatible API format makes it possible to add support for additional providers in the future.

### How do I get an API key?

**For OpenCode Zen (recommended):**
1. Visit https://opencode.ai/auth
2. Sign up and get your API key
3. Run `glm auth login opencode`

**For Zai/Zhipu AI:**
1. Visit [bigmodel.cn](https://open.bigmodel.cn/)
2. Sign up for an account
3. Navigate to API Key management
4. Create a new API key
5. Run `glm auth login zai`

### Is my code safe?

GLM-CLI implements multiple safety features:
- **File sandboxing** - Operations restricted to project root
- **Explicit confirmation** - Write operations require user approval
- **Diff preview** - See changes before applying
- **No data transmission** - Only files explicitly read are sent to API
- **Configurable safety modes** - `ask` (default) or `always_allow`
- **Bash command approval** - Bash tool requires explicit permission

### Can I use it offline?

GLM-CLI requires an internet connection to communicate with the LLM APIs. It does not support offline operation or local LLMs currently.

### How do I add custom tools?

Custom tools must implement the `Tool` interface:

```groovy
package tools

class MyCustomTool implements Tool {
    String getName() { "my_tool" }
    String getDescription() { "Description of what this tool does" }
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                param1: [type: "string", description: "Parameter description"]
            ],
            required: ["param1"]
        ]
    }
    Object execute(Map<String, Object> args) {
        // Tool implementation
        return "Result"
    }
}
```

Then register it in your agent:

```groovy
Agent agent = new Agent(apiKey, model)
agent.registerTool(new MyCustomTool())
```

### How do I create a custom skill?

Skills are reusable agent workflows:

```groovy
class MySkill implements Skill {
    String getName() { "my-skill" }
    String getDescription() { "Perform my custom task" }
    List<Parameter> getParameters() {
        return [
            new Parameter("target", "Target file or directory", true)
        ]
    }
    String execute(Map<String, Object> args, Agent agent) {
        // Skill implementation
        return agent.run("Perform the task on ${args.target}")
    }
}
```

Register the skill:

```groovy
SkillRegistry.register(new MySkill())
```

### Troubleshooting

**Issue**: "API Key not found"
- **Solution**: Run `glm auth login <provider>` to configure your credentials

**Issue**: "Invalid API Key format"
- **Solution**: Ensure your API key is valid for the selected provider

**Issue**: "Connection timeout"
- **Solution**: Check your internet connection and verify the API endpoint URL

**Issue**: "Tool execution failed"
- **Solution**: Check file permissions and ensure files are within the project directory

**Issue**: "TUI failed to start"
- **Solution**: Use `--simple` flag for console mode, or check terminal size requirements

## Documentation

- [Architecture Design](./architecture.md) - Technical architecture and components
- [Technical Specification](./technicalSpec.md) - API reference and data models
- [Agent Guidelines](./AGENTS.md) - Agent debugging and tool calling best practices
- [Style Guide](./STYLE_GUIDE.md) - Coding conventions and patterns
- [Development Guide](./DEVELOPMENT.md) - Local development setup
- [Tools Reference](./TOOLS.md) - Complete tool documentation
- [Configuration Guide](./CONFIGURATION.md) - Detailed configuration options
- [FAQ](./FAQ.md) - Frequently asked questions
- [Contributing](./CONTRIBUTING.md) - Contribution guidelines

## License

MIT License

## Acknowledgments

- Inspired by [SST OpenCode](https://github.com/sst/opencode)
- Built with [JBang](https://jbang.dev/)
- Powered by [Z.ai GLM-4](https://open.bigmodel.cn/) and [OpenCode Zen](https://opencode.ai/)
- Architecture inspired by [Google Gemini CLI](https://github.com/google-gemini/gemini-cli)

---

**Join our community** | [Discussions](https://discord.gg/rJfNM4bUx6)

🚀 You've been invited to join the GLM Coding Plan! Enjoy full support for Claude Code, Cline, and 10+ top coding tools — starting at just $3/month. Subscribe now and grab the limited-time deal! Link: https://z.ai/subscribe?ic=CQBKX9KCLF
