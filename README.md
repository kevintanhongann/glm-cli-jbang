# GLM-CLI (JBang Edition)

![Version](https://img.shields.io/badge/version-0.1-blue?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)
![Groovy](https://img.shields.io/badge/Groovy-4.x-orange?style=flat-square)
![JBang](https://img.shields.io/badge/JBang-compatible-red?style=flat-square)

A native-performance AI coding agent CLI for Linux, macOS, and Windows, powered by Z.ai's GLM-4 models and built with JBang and Groovy.

![Demo](https://img.shields.io/badge/demo-available-lightgrey?style=flat-square)

## Features

| Feature | Description |
|---------|-------------|
| **Chat** | Interactive conversation with GLM-4 models (`glm-4-flash`, `glm-4`, `glm-4-plus`, `glm-4.5`) |
| **Agent** | Autonomous task execution with ReAct loop for reading/writing files |
| **Tools** | Built-in file system tools (`read_file`, `write_file`, `list_files`) with safety checks |
| **Streaming** | Real-time response streaming using Server-Sent Events (SSE) |
| **Diff Preview** | See file changes before applying with diff visualization |
| **Configurable** | TOML-based configuration for API keys, defaults, and behavior |
| **JWT Auth** | Secure API authentication with automatic token caching |

## Prerequisites

- **Java 11+** (Automatically managed by JBang if needed)
- **JBang**: [Installation Guide](https://jbang.dev/download/)

## Installation

You can run the CLI directly from the source or install it as a global command.

### Run Directly

```bash
# Download the script
curl -O https://raw.githubusercontent.com/yourusername/glm-cli-jbang/main/glm.groovy
chmod +x glm.groovy

# Run with help
./glm.groovy --help

# Start chatting
./glm.groovy chat "Hello!"
```

### Install as Global Command

```bash
jbang app install --name glm glm.groovy

# Now you can use it from anywhere
glm chat "Explain this code"
glm agent "Refactor the User class"
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
[api]
key = "your-api-key-here"
base_url = "https://open.bigmodel.cn/api/paas/v4/"

[behavior]
default_model = "glm-4-flash"
safety_mode = "ask" # 'ask' or 'always_allow'
language = "auto"    # 'auto', 'en', 'zh'
```

### Environment Variables

Alternatively, set the `ZAI_API_KEY` environment variable:

```bash
# For current session
export ZAI_API_KEY=your.key.here

# Add to ~/.bashrc or ~/.zshrc for persistence
echo 'export ZAI_API_KEY=your.key.here' >> ~/.bashrc
```

### Configuration Priority

1. Command-line flags (highest priority)
2. Environment variables
3. `~/.glm/config.toml`
4. Default values (lowest priority)

## Usage

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
glm chat --model glm-4 "Analyze this code for security issues"
```

Available models:
- `glm-4-flash` - Fast, cost-effective (default)
- `glm-4` - Balanced quality/speed
- `glm-4-plus` - Higher quality reasoning
- `glm-4.5` - Latest with multi-function calls
- `glm-4v` - Vision capabilities

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
glm agent "Extract the validation logic from User.groovy into a separate Validator class"

# Add tests
glm agent "Write comprehensive tests for the payment processing module"

# Fix bugs
glm agent "Investigate and fix the null pointer exception in AuthController"

# Documentation
glm agent "Add JavaDoc comments to all public methods in the API client"
```

## Agent System

GLM-CLI uses a **ReAct** (Reasoning + Acting) agent loop:

1. **Observe**: Read user input and conversation history
2. **Think**: Send context to GLM-4 API
3. **Act**: Either respond with text or call a tool
4. **Loop**: Append tool results and repeat until completion

### Available Tools

| Tool | Description | Permission Level |
|------|-------------|------------------|
| `read_file` | Read file contents | Always allowed |
| `write_file` | Write/create files with diff preview | Requires confirmation |
| `list_files` | List directory contents | Always allowed |

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
//DEPS info.picocli:picocli:4.7.6
//DEPS info.picocli:picocli-groovy:4.7.6
//DEPS com.fasterxml.jackson.core:jackson-databind:2.16.0
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.16.0
//DEPS com.auth0:java-jwt:4.4.0
//DEPS io.github.java-diff-utils:java-diff-utils:4.12
//DEPS org.slf4j:slf4j-simple:2.0.9
```

## FAQ

### How is this different from other AI coding agents?

**GLM-CLI** offers:
- 🇨🇳 **Native GLM-4 support** - Optimized for Z.ai's GLM models with bilingual (Chinese/English) capabilities
- 📦 **Zero-dependency distribution** - JBang manages JDK, no installation beyond the script
- 🔒 **Diff previews** - See changes before applying with approval workflow
- ⚡ **Native performance** - JVM optimization for fast startup and execution
- 🎯 **Groovy simplicity** - Concise, readable codebase that's easy to extend

### Can I use it with other LLM providers?

Currently, GLM-CLI is optimized for Z.ai's GLM-4 models. The OpenAI-compatible API format makes it theoretically possible to use with other providers, but this is not officially supported. Future versions may include multi-provider support.

### How do I get an API key?

1. Visit [bigmodel.cn](https://open.bigmodel.cn/)
2. Sign up for an account
3. Navigate to API Key management
4. Create a new API key
5. Add it to your config or set the `ZAI_API_KEY` environment variable

### Is my code safe?

GLM-CLI implements multiple safety features:
- **File sandboxing** - Operations restricted to project root
- **Explicit confirmation** - Write operations require user approval
- **Diff preview** - See changes before applying
- **No data transmission** - Only files explicitly read are sent to API
- **Configurable safety modes** - `ask` (default) or `always_allow`

### Can I use it offline?

GLM-CLI requires an internet connection to communicate with the GLM-4 API. It does not support offline operation or local LLMs currently.

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
- **Solution**: Set `ZAI_API_KEY` environment variable or configure `~/.glm/config.toml`

**Issue**: "Invalid API Key format"
- **Solution**: Ensure your API key follows the format `id.secret` from bigmodel.cn

**Issue**: "Connection timeout"
- **Solution**: Check your internet connection and verify the API endpoint URL

**Issue**: "Tool execution failed"
- **Solution**: Check file permissions and ensure files are within the project directory

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

## Documentation

- [Architecture Design](./architecture.md) - Technical architecture and components
- [Technical Specification](./technicalSpec.md) - API reference and data models
- [Agent Guidelines](./AGENTS.md) - Agent debugging and tool calling best practices
- [Style Guide](./STYLE_GUIDE.md) - Coding conventions and patterns
- [Development Guide](./DEVELOPMENT.md) - Local development setup
- [Tools Reference](./TOOLS.md) - Complete tool documentation
- [Configuration Guide](./CONFIGURATION.md) - Detailed configuration options
- [FAQ](./FAQ.md) - Frequently asked questions

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- Inspired by [SST OpenCode](https://github.com/sst/opencode)
- Built with [JBang](https://jbang.dev/)
- Powered by [Z.ai GLM-4](https://open.bigmodel.cn/)

---

**Join our community** | [GitHub Issues](https://github.com/yourusername/glm-cli-jbang/issues) | [Discussions](https://github.com/yourusername/glm-cli-jbang/discussions)
