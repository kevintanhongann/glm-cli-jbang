# Frequently Asked Questions

This document answers common questions about GLM-CLI.

## Table of Contents

- [General Questions](#general-questions)
- [Installation & Setup](#installation--setup)
- [Usage](#usage)
- [API & Models](#api--models)
- [Troubleshooting](#troubleshooting)
- [Advanced Topics](#advanced-topics)

## General Questions

### What is GLM-CLI?

GLM-CLI is an AI-powered coding assistant that runs in your terminal. It uses Z.ai's GLM-4 models to help you with:
- Chat-based Q&A and code explanations
- Autonomous file editing and refactoring
- Multi-step task execution
- Code generation and modification

### How is this different from other AI coding agents?

| Feature | GLM-CLI | Others |
|---------|-----------|--------|
| **Primary LLM** | GLM-4 (optimized for Chinese/English) | Often Claude, GPT |
| **Runtime** | JBang/Groovy (JVM) | Various (Python, Rust, Node) |
| **Distribution** | Single script file | Packages/binaries |
| **Installation** | `jbang install` or download | Package managers |
| **GLM-4 Support** | Native, optimized | Limited or via adapters |
| **File Sandbox** | Built-in with diff preview | Varies |
| **Zero Dependencies** | Yes (JBang handles JDK) | Often requires runtime |

### Can I use this with other LLM providers?

Currently, GLM-CLI is optimized for Z.ai's GLM-4 models. The OpenAI-compatible API format makes it theoretically possible to use with other providers, but this is not officially supported.

Future versions may include multi-provider support (contributions welcome!).

### Is GLM-CLI open source?

Yes! GLM-CLI is open source under the MIT License. See [LICENSE](LICENSE) for details.

## Installation & Setup

### How do I install GLM-CLI?

See [README.md Installation](./README.md#installation) for detailed instructions.

Quick start:
```bash
# Direct run
curl -O https://raw.githubusercontent.com/yourusername/glm-cli-jbang/main/glm.groovy
chmod +x glm.groovy
./glm.groovy --help

# Or install globally
jbang app install --name glm glm.groovy
```

### Do I need Java installed?

No! JBang will automatically download and manage the appropriate JDK for you. Just make sure you have JBang installed.

### Where do I get an API key?

1. Visit [bigmodel.cn](https://open.bigmodel.cn/)
2. Sign up for an account
3. Navigate to API Key management
4. Create a new API key
5. Format is `id.secret`

### How do I configure my API key?

Three options:

**Option 1: Environment variable**
```bash
export ZAI_API_KEY=your.api.key.here
```

**Option 2: Config file**
```bash
mkdir -p ~/.glm
cat > ~/.glm/config.toml << EOF
[api]
key = "your.api.key.here"
EOF
```

**Option 3: Command-line flag** (if implemented)
```bash
glm --api-key=your.api.key.here chat "Hello"
```

### Can I use GLM-CLI offline?

No, GLM-CLI requires an internet connection to communicate with the GLM-4 API. It does not support offline operation or local LLMs currently.

### How do I uninstall GLM-CLI?

```bash
# If installed globally
jbang app remove glm

# Remove config and data
rm -rf ~/.glm

# Remove downloaded script
rm glm.groovy
```

## Usage

### How do I start a chat session?

```bash
# Interactive chat
glm chat

# One-off question
glm chat "Explain this code"

# With specific model
glm chat --model glm-4 "Help with this issue"
```

### How do I use the agent for file operations?

```bash
# Simple task
glm agent "Create a Hello World script"

# Complex multi-step task
glm agent "Refactor User class and add unit tests"

# The agent will:
# 1. Think about the task
# 2. Read files if needed
# 3. Show diff preview
# 4. Ask for permission
# 5. Make changes
```

### What GLM-4 models are available?

| Model | Context | Speed | Use Case |
|-------|---------|--------|----------|
| `glm-4-flash` | 128K | Fastest | Default for most operations |
| `glm-4` | 128K | Fast | General coding assistance |
| `glm-4-plus` | 128K | Medium | Complex reasoning |
| `glm-4.5` | 128K | Varies | Latest with multi-function calls |
| `glm-4v` | 128K | Medium | Vision/image analysis |

### Can I continue a previous conversation?

Currently, conversation history is maintained during a single session. Persistent session storage is planned for future versions.

### How do I exit the chat?

Type `exit` or `quit` and press Enter.

## API & Models

### How much does GLM-4 cost?

Pricing is determined by Z.ai. Current rates:
- `glm-4-flash`: Most cost-effective
- `glm-4`, `glm-4-plus`, `glm-4.5`: Higher quality, higher cost

See [Z.ai Pricing](https://open.bigmodel.cn/price) for current pricing.

### Are there rate limits?

Yes, GLM-4 API has rate limits based on your subscription tier. If you hit limits:
- Reduce request frequency
- Upgrade your subscription
- Use `glm-4-flash` for faster responses

### How do I check my API usage?

Check your usage at:
- [Z.ai Console](https://open.bigmodel.cn/usercenter)
- API key dashboard
- Usage analytics section

### What's the token limit?

GLM-4 models support up to 128K context tokens. This translates to approximately:
- 100K characters
- 15K-20K lines of code
- Multiple medium files or one large file

GLM-CLI manages context window automatically.

## Troubleshooting

### "API Key not found" error

**Symptoms**: CLI fails to start with API key error

**Solutions**:
1. Check `ZAI_API_KEY` environment variable: `echo $ZAI_API_KEY`
2. Verify config file exists: `cat ~/.glm/config.toml`
3. Ensure API key format is correct: `id.secret`
4. Restart terminal after setting environment variable

### "Invalid API Key format" error

**Symptoms**: Error about malformed API key

**Solutions**:
1. Verify your key from [bigmodel.cn](https://open.bigmodel.cn/)
2. Ensure it's not truncated or has extra spaces
3. Format should be: `1234567890.abcdefghijklmnopqrstuvwxyz`

### "Connection timeout" error

**Symptoms**: CLI hangs or times out

**Solutions**:
1. Check internet connection
2. Verify API endpoint is reachable: `curl https://open.bigmodel.cn`
3. Check firewall/proxy settings
4. Try again after a moment (API might be busy)

### "Tool execution failed" error

**Symptoms**: Agent can't perform file operations

**Solutions**:
1. Check file permissions in project directory
2. Ensure you're in a valid project directory
3. Verify files are within project root (not system paths)
4. Check available disk space

### Agent stuck in a loop

**Symptoms**: Agent keeps calling the same tool repeatedly

**Solutions**:
1. Interrupt with Ctrl+C
2. Try rephrasing your request
3. Be more specific about what you want
4. Use `--verbose` flag to see what's happening

### Diff shows no changes

**Symptoms**: Write operation shows empty diff

**Solutions**:
1. Verify file actually changed
2. Check file path is correct
3. File might be new (no previous content to diff)

### Slow response times

**Symptoms**: Long wait times for API responses

**Solutions**:
1. Use `glm-4-flash` for faster responses
2. Check internet connection speed
3. API might be under heavy load
4. Reduce context size (fewer files)

### Out of memory errors

**Symptoms**: JVM out of memory errors

**Solutions**:
1. Close other applications
2. Use smaller model for large tasks
3. Reduce context by being more specific
4. Increase JVM memory: `export JAVA_OPTS="-Xmx2g"`

## Advanced Topics

### How do I add custom tools?

Create a new tool class implementing `Tool` interface:

```groovy
package tools

class MyCustomTool implements Tool {
    @Override
    String getName() { "my_tool" }

    @Override
    String getDescription() { "Description of tool" }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                param1: [type: "string", description: "Parameter"]
            ],
            required: ["param1"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        // Implementation
        return "Result"
    }
}
```

Register it in your agent:
```groovy
Agent agent = new Agent(apiKey, model)
agent.registerTool(new MyCustomTool())
```

### How does the diff preview work?

GLM-CLI uses [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) to generate diffs:

1. Before write, agent reads existing file content
2. Compares old content with proposed new content
3. Generates and displays diff
4. Asks for user confirmation
5. Writes only if approved

### Is my code safe with GLM-CLI?

GLM-CLI implements multiple safety features:

| Safety Feature | Description |
|---------------|-------------|
| **File Sandbox** | Operations restricted to project directory |
| **Path Validation** | Normalized paths, prevents directory traversal |
| **Confirmation Required** | Destructive operations need approval |
| **Diff Preview** | See changes before applying |
| **No Auto-Commit** | Never automatically commits to git |
| **Configurable Safety** | Set `safety_mode = strict` for read-only |

**Never share**: API keys, passwords, secrets, or sensitive data with the agent.

### Can I use GLM-CLI in CI/CD?

Yes! Here's an example GitHub Actions workflow:

```yaml
name: Review Code with GLM

on: [pull_request]

jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Install JBang
        run: curl -Ls https://sh.jbang.dev | bash -s - app setup
      - name: Run GLM review
        env:
          ZAI_API_KEY: ${{ secrets.ZAI_API_KEY }}
        run: |
          jbang glm.groovy chat "Review this PR: $(git diff origin/main...HEAD)"
```

### How do I debug the agent?

Enable verbose logging:

```bash
# Add verbose flag (if implemented)
glm agent -v "Debug this task"

# Or check logs
tail -f ~/.glm/logs/agent.log

# Enable Java debug logging
export JAVA_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
glm agent "Task"
```

### Can I integrate with my editor?

Yes! While native editor integration is planned, you can:

**Vim/Neovim**:
```vim
" Add to .vimrc
command! GLMChat !glm chat
command! -nargs=1 GLMAgent !glm agent <args>
```

**VSCode**:
Create tasks in `.vscode/tasks.json`:
```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "GLM Chat",
      "type": "shell",
      "command": "glm",
      "args": ["chat"]
    }
  ]
}
```

**Emacs**:
```elisp
;; Add to init.el
(defun glm-chat (input)
  (interactive "sChat: ")
  (shell-command (concat "glm chat \"" input "\"")))
```

### How does token usage affect my billing?

GLM-4 charges for:
- **Input tokens**: Your prompt + file contents + conversation history
- **Output tokens**: Agent responses

To minimize costs:
- Use `glm-4-flash` for simple tasks
- Be specific in your requests
- Use plan mode for analysis (no writes)
- Clear context when switching tasks

### Can I use GLM-CLI for non-coding tasks?

Yes! GLM-CLI is useful for:
- Document writing and editing
- Data analysis with file tools
- Research and summarization
- Bash command generation (when bash tool is added)

Just provide the relevant files and describe your task.

## Getting Help

### Where can I get support?

- **Documentation**: [README.md](./README.md) and other docs
- **GitHub Issues**: [Report bugs](https://github.com/yourusername/glm-cli-jbang/issues)
- **GitHub Discussions**: [Ask questions](https://github.com/yourusername/glm-cli-jbang/discussions)
- **Contributing**: [CONTRIBUTING.md](./CONTRIBUTING.md)

### How do I report a bug?

Use the bug report template in [GitHub Issues](https://github.com/yourusername/glm-cli-jbang/issues):

1. Search existing issues first
2. Create new issue with bug template
3. Include:
   - Description of bug
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment (OS, Java version, GLM-CLI version)
   - Logs or error messages

### How do I request a feature?

Create a GitHub issue with:
- Problem description
- Proposed solution
- Use cases or examples
- Alternatives considered

## Additional Resources

- [Architecture](./architecture.md) - Technical architecture
- [Technical Spec](./technicalSpec.md) - API reference
- [Agent Guidelines](./AGENTS.md) - Agent system guide
- [Style Guide](./STYLE_GUIDE.md) - Code conventions
- [Configuration](./CONFIGURATION.md) - Config details
- [Tools Reference](./TOOLS.md) - Complete tool docs

---

Still have questions? [Open a discussion](https://github.com/yourusername/glm-cli-jbang/discussions) or [create an issue](https://github.com/yourusername/glm-cli-jbang/issues)!
