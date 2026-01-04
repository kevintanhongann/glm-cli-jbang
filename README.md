# GLM-CLI (JBang Edition)

![Version](https://img.shields.io/badge/version-0.1-blue?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)
![Groovy](https://img.shields.io/badge/Groovy-4.x-orange?style=flat-square)
![JBang](https://img.shields.io/badge/JBang-compatible-red?style=flat-square)

A native-performance AI coding agent CLI for Linux, macOS, and Windows, powered by multiple AI providers including Z.ai's GLM-4 models and OpenCode Zen's curated models, built with JBang and Groovy.

![Demo](https://img.shields.io/badge/demo-available-lightgrey?style=flat-square)

<img width="1910" height="941" alt="image" src="https://github.com/user-attachments/assets/af623ad0-f541-40ce-b040-c714c4ba7b9a" />

⚠️ Under Active Development - glm-cli-jbang is actively developed. If you ⭐ star and 👀 watch this repository, it would mean a lot to me.

## Features

| Feature | Description |
|---------|-------------|
| **Chat** | Interactive conversation with multiple AI providers and models |
| **Agent** | Autonomous task execution with ReAct loop for reading/writing files |
| **Web Search** | Integrated web search with real-time information retrieval |
| **Tools** | Built-in tools (`read_file`, `write_file`, `list_files`, `web_search`) with safety checks |
| **Streaming** | Real-time response streaming using Server-Sent Events (SSE) |
| **Diff Preview** | See file changes before applying with diff visualization |
| **Configurable** | TOML-based configuration for API keys, defaults, and behavior |
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

# Start chatting
./glm.groovy chat "Hello!"
```

### Install as Global Command

```bash
jbang app install --name glm glm.groovy

# Now you can use it from anywhere
glm chat -m opencode/big-pickle "Explain this code"
glm agent -m zai/glm-4.7 "Refactor User class"
```

### Verify Installation

```bash
glm --version
# Output: glm-cli 0.1
```

## Configuration

The CLI looks for a configuration file at `~/.glm/config.toml`.

### Configuration File Example

```toml
[behavior]
default_model = "opencode/big-pickle"  # Format: provider/model-id
safety_mode = "ask" # 'ask' or 'always_allow'
language = "auto"    # 'auto', 'en', 'zh'
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

### List Available Models

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

### Configuration Priority

1. Command-line flags (highest priority)
2. Provider credentials from `glm auth login`
3. `~/.glm/config.toml`
4. Default values (lowest priority)

## Usage

### Authentication

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

### Chat

Start an interactive session:

```bash
glm chat
```

Or send a single message:

```bash
glm chat "Explain this project structure"
```

With a specific model:

```bash
# Use OpenCode Zen's Big Pickle (free, stealth model)
glm chat -m opencode/big-pickle "Write a sorting algorithm"

# Use Zai's GLM-4.7
glm chat -m zai/glm-4.7 "Refactor this code"

# Use OpenCode Zen's GLM-4.7-free (free model)
glm chat -m opencode/glm-4.7-free "Help me debug"
```

### Agent

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

### Example Agent Workflows

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

## Agent System

GLM-CLI uses a **ReAct** (Reasoning + Acting) agent loop:

1. **Observe**: Read user input and conversation history
2. **Think**: Send context to the LLM API
3. **Act**: Either respond with text or call a tool
4. **Loop**: Append tool results and repeat until completion

### Available Tools

| Tool | Description | Permission Level |
|------|-------------|------------------|
| `read_file` | Read file contents | Always allowed |
| `write_file` | Write/create files with diff preview | Requires confirmation |
| `list_files` | List directory contents | Always allowed |
| `web_search` | Search web for current information | Always allowed |

### Tool Calling Patterns

The agent can:
- Call tools sequentially for multi-step tasks
- Show diff previews before file modifications
- Handle errors and retry operations
- Maintain context across tool calls

## Development

### Project Structure

```
glm-cli-jbang/
├── glm.groovy              # Main entry point (shebang, dependencies)
├── commands/               # CLI command implementations
│   ├── GlmCli.groovy       # Root command with subcommands
│   ├── ChatCommand.groovy  # Interactive chat command
│   └── AgentCommand.groovy # Autonomous agent command
├── core/                   # Core business logic
│   ├── Agent.groovy        # ReAct agent loop
│   ├── GlmClient.groovy    # HTTP/SSE client
│   └── Config.groovy       # Configuration handler
├── tools/                  # Tool implementations
│   ├── Tool.groovy         # Tool interface
│   ├── ReadFileTool.groovy
│   ├── WriteFileTool.groovy
│   └── ListFilesTool.groovy
└── models/                 # API data models
    ├── ChatRequest.groovy
    ├── ChatResponse.groovy
    └── Message.groovy
```

### Development Workflow

```bash
# Edit with IDE support
jbang edit glm.groovy

# Run tests (when available)
./glm.groovy test

# Install locally
jbang app install --force --name glm glm.groovy
```

### Dependencies

All dependencies are declared in `glm.groovy`:

```groovy
//DEPS info.picocli:picocli:4.7.7
//DEPS info.picocli:picocli-groovy:4.7.7
//DEPS com.fasterxml.jackson.core:jackson-databind:2.16.0
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.16.0
//DEPS com.auth0:java-jwt:4.4.0
//DEPS io.github.java-diff-utils:java-diff-utils:4.12
//DEPS org.slf4j:slf4j-simple:2.0.9
```

## FAQ

### How is this different from other AI coding agents?

**GLM-CLI** offers:
- 🌐 **Multi-provider support** - Works with Z.ai GLM models, OpenCode Zen, and more
- 📦 **Zero-dependency distribution** - JBang manages JDK, no installation beyond the script
- 🔒 **Diff previews** - See changes before applying with approval workflow
- ⚡ **Native performance** - JVM optimization for fast startup and execution
- 🎯 **Groovy simplicity** - Concise, readable codebase that's easy to extend

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

### Troubleshooting

**Issue**: "API Key not found"
- **Solution**: Run `glm auth login <provider>` to configure your credentials

**Issue**: "Invalid API Key format"
- **Solution**: Ensure your API key is valid for the selected provider

**Issue**: "Connection timeout"
- **Solution**: Check your internet connection and verify the API endpoint URL

**Issue**: "Tool execution failed"
- **Solution**: Check file permissions and ensure files are within the project directory

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

---

**Join our community** | [Discussions](https://discord.gg/rJfNM4bUx6)

🚀 You've been invited to join the GLM Coding Plan! Enjoy full support for Claude Code, Cline, and 10+ top coding tools — starting at just $3/month. Subscribe now and grab the limited-time deal! Link： https://z.ai/subscribe?ic=CQBKX9KCLF