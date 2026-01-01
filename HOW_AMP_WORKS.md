# How Amp Works as a Coding Agent

## Overview

Amp is a frontier coding agent that operates as an LLM with access to tools, giving it the ability to modify code outside the context window. At its core, Amp implements a **ReAct (Reasoning + Acting)** pattern where the model autonomously decides when and how to use tools to accomplish multi-step tasks.

## Core Architecture

### The Three-Component System

Amp consists of three fundamental components:

1. **An LLM** - The reasoning engine (currently Claude Opus 4.5 for most tasks)
2. **A Loop** - The continuous conversation flow
3. **Enough Tokens** - Sufficient context window (up to 200k tokens default, expandable to 1M)

That's it. Everything else is tooling, optimization, and user experience improvements.

### Agent Definition

> An agent is "an LLM with access to tools, giving it the ability to modify something outside the context window."

The main agent you interact with can spawn subagents to delegate specialized tasks, creating a hierarchy of autonomous problem-solvers.

## Tool Calling Mechanism

### How Tools Work

The tool calling mechanism is surprisingly simple:

1. **You tell the model what tools are available** - Send tool definitions with name, description, and input schema
2. **When the model wants to use a tool** - It responds in a specific format indicating the tool name and parameters
3. **You execute the tool** - Run the tool function and send the result back to the model
4. **The model continues** - Uses the tool result to continue its task

This is analogous to saying "wink if you want me to raise my arm" - it's just a communication protocol.

### Built-in Tools

Amp includes a curated set of tools:

- **File Operations**: `read_file`, `edit_file`, `list_files`
- **Terminal**: Bash command execution
- **Search**: Codebase search agent
- **Specialized**: Librarian (GitHub search), Oracle (GPT-5 review), various MCP servers

### Tool Response Format

Tools return clear, structured responses:
- Success: "Successfully read 123 lines from src/main.groovy"
- Error: "Error: File not found at src/nonexistent.groovy"
- Partial: "Read 45 lines (file truncated at 1000 lines limit)"

## Subagents

### What Are Subagents?

Subagents are tools that are themselves agents. When the main agent spawns a subagent:
- It passes a prompt defining what the subagent should do
- The subagent has its own context window (completely fresh)
- The subagent can make autonomous decisions and use tools
- Results are summarized and returned to the main agent

### Why Subagents Matter

Subagents solve a critical problem: **context window management**

With a single agent, fixing errors or exploring branches consumes tokens from the main context. With subagents:
- Error fixing happens in a separate context window
- Multiple subagents can run in parallel
- The main agent only spends tokens on coordination, not details
- Complex tasks can span many iterations without exhausting context

### Types of Subagents

1. **Search Agent** - Read-only tools for codebase exploration (oldest subagent)
2. **Generic Subagents** - Full access like the main agent (newer, more powerful)
3. **Specialized Subagents** - Librarian (GitHub search), Oracle (code review)

### When Subagents Are Used

Subagents are most useful for:
- Multi-step tasks that can be broken into independent parts
- Operations producing extensive output not needed after completion
- Parallel work across different code areas
- Keeping the main thread's context clean while coordinating complex work

### Limitations

Subagents work in isolation:
- They can't communicate with each other
- You can't guide them mid-task
- They start fresh without your conversation's accumulated context
- The main agent only receives their final summary, not step-by-step work

## Agent Modes

Amp operates in three modes:

### Smart Mode (Default)
- Uses state-of-the-art models (currently Claude Opus 4.5) without constraints
- Maximum capability and autonomy
- Uses paid credits
- Up to 200k tokens of context

### Rush Mode
- Faster, cheaper, less capable
- Suitable for small, well-defined tasks
- Optimized for quick iterations

### Free Mode
- Free of charge, supported by ads
- Uses mix of top OSS models and frontier models with limited context
- Meets same security standards as paid mode
- No data required for training

## Conversation Management

### Thread-Based Interaction

Amp organizes all interactions into **threads** - conversations containing messages, context, and tool calls. This is analogous to version control for AI interactions.

### Handoff

When threads accumulate too much noise or you want to continue work in a new direction, use the **handoff** command. This:
- Drafts a new thread with relevant files and context
- Preserves the knowledge without the accumulated errors/failed attempts
- Allows you to continue with a clean context window

### Thread Sharing

Threads can be shared with different visibility levels:
- **Public**: Visible to anyone, searchable
- **Unlisted**: Anyone with link, shared with workspace
- **Workspace-shared**: All workspace members
- **Group-shared**: Specific groups (Enterprise)
- **Private**: Only you

### Referencing Other Threads

You can reference previous threads by URL or ID (e.g., `@T-7f395a45-7fae-4983-8de0-d02e61d30183`). Amp will extract relevant information to help with the current task.

## AGENTS.md Files

### Purpose

Amp looks in `AGENTS.md` files for guidance on:
- Codebase structure
- Build/test commands
- Conventions and patterns
- Architecture overview
- Review and release steps

### Automatic Inclusion

Amp automatically includes:
- `AGENTS.md` in current directory and parent directories (up to $HOME)
- Subtree `AGENTS.md` files when reading files in that subtree
- `$HOME/.config/amp/AGENTS.md` and `$HOME/.config/AGENTS.md` if they exist
- Falls back to `AGENT.md` or `CLAUDE.md` if `AGENTS.md` doesn't exist

### File Mentions

You can @-mention other files in AGENTS.md to include them as context:
```
See @doc/style.md and @specs/**/*.md. When making commits, see @doc/git-commit-instructions.md.
```

Glob patterns are supported for batch inclusion.

### Granular Guidance

Use YAML frontmatter to apply guidance only to certain files:
```yaml
---
globs:
  - '**/*.ts'
  - '**/*.tsx'
---
Follow these TypeScript conventions:
- Never use the `any` type
...
```

## The Oracle

### What is the Oracle?

The Oracle is a specialized subagent powered by GPT-5 that serves as a "second opinion" model. It's:
- Better at complex reasoning and analysis tasks
- Slightly slower and more expensive
- Less suited to day-to-day code editing
- Available via the `oracle` tool

### When to Use the Oracle

The main agent can autonomously decide to use the oracle for:
- Debugging complex bugs
- Reviewing code changes
- Analyzing architecture
- Refactoring planning

### Explicit Invocation

You can explicitly ask Amp to use the oracle:
- "Use the oracle to review the last commit's changes"
- "Ask the oracle whether there isn't a better solution"
- "Use the oracle as much as possible for this bug investigation"

## The Librarian

### What is the Librarian?

The Librarian is a subagent for searching remote codebases. It can:
- Search all public code on GitHub
- Search your private GitHub repositories (with connection configured)
- Provide in-depth explanations and code analysis

### When to Use the Librarian

Tell Amp to summon the Librarian when you need to:
- Find code in multiple repositories
- Read code of frameworks and libraries you're using
- Find examples in open-source code
- Connect dots between your code and dependencies
- Investigate library implementations and behavior

### Configuration

Requires GitHub connection configured in Amp settings. Select which private repositories to authorize.

## Toolboxes

### Extending Amp with Scripts

Toolboxes allow you to extend Amp with simple scripts instead of writing MCP servers. When Amp starts, it invokes executables in a directory with `TOOLBOX_ACTION=describe`.

### Tool Description Format

Tools write their description to stdout as key-value pairs:
```
name: run-tests
description: use this tool instead of Bash to run tests in a workspace
dir: string the workspace directory
```

### Tool Execution

When Amp decides to use the tool, it runs the executable again with `TOOLBOX_ACTION=execute` and passes parameters via stdin.

### Recommended Use Cases

- Querying development databases
- Running test and build actions in the project
- Exposing CLI tools in a controlled manner

## Permissions

### Permission System

Before invoking a tool, Amp checks a permission list to decide whether to:
- **Allow** - Run without asking
- **Reject** - Deny outright
- **Ask** - Prompt the user for approval
- **Delegate** - Ask an external permission helper program

### Configuration

Permissions are configured in `amp.permissions`:
```json
"amp.permissions": [
  // Ask before running git commit
  { "tool": "Bash", "matches": { "cmd": "*git commit*" }, "action": "ask"},
  // Reject python commands
  { "tool": "Bash", "matches": { "cmd": ["*python *", "*python3 *"] }, "action": "reject"},
  // Allow all playwright MCP tools
  { "tool": "mcp__playwright_*", "action": "allow"}
]
```

### External Delegation

For maximum control, delegate to an external program:
```json
{ "action": "delegate", "to": "my-permission-helper", "tool": "Bash" }
```

The helper receives tool info via stdin and environment variables, then exits with:
- 0 = allow
- 1 = ask
- 2 = reject (stderr shown to model)

## MCP (Model Context Protocol)

### What is MCP?

MCP is a standard for extending Amp with additional tools from external servers. Servers can be local (CLI tools) or remote (HTTP/SSE).

### Local MCP Servers

```json
{
  "playwright": {
    "command": "npx",
    "args": ["-y", "@playwright/mcp@latest", "--headless", "--isolated"]
  }
}
```

### Remote MCP Servers

```json
{
  "semgrep": {
    "url": "https://mcp.semgrep.ai/mcp"
  },
  "sourcegraph": {
    "url": "${SRC_ENDPOINT}/.api/mcp/v1",
    "headers": {
      "Authorization": "token ${SRC_ACCESS_TOKEN}"
    }
  }
}
```

### OAuth Support

Amp supports OAuth for remote MCP servers:
- **Dynamic Client Registration**: Automatic for some servers like Linear
- **Manual Registration**: Configure client ID, secret, and scopes

### MCP Permissions

Control which MCP servers can connect:
```json
"amp.mcpPermissions": [
  { "matches": { "command": "npx", "args": "* @playwright/mcp@*" }, "action": "allow" },
  { "matches": { "url": "*/malicious.com*" }, "action": "reject" }
]
```

## Model Strategy

### Multi-Model Approach

Amp uses multiple models for different purposes:
- **Main Agent**: Claude Opus 4.5 (primary), can switch based on evaluation
- **Oracle**: GPT-5 (for complex reasoning and analysis)
- **Search Agent**: Gemini 3 Flash (50% faster than before)
- **Rush/Free Modes**: Various OSS and frontier models

### Model Evaluation

Amp continuously evaluates new frontier models and switches when appropriate. They believe in "going where the models take it" - no backcompat, no legacy features.

### Token Usage

- Default context: 200k tokens
- Maximum context: 1 million tokens (with Claude Sonnet 4)
- Unconstrained token usage in smart mode
- Optimized for short threads (cheaper, better, easier to reason)

## Key Principles

### 1. Unconstrained Token Usage
Amp doesn't artificially limit tokens in smart mode. Let the task determine token needs.

### 2. Always Uses the Best Models
Continuous model evaluation and switching to whatever works best.

### 3. Raw Model Power
No guardrails or sanitization that prevents the model from being effective.

### 4. Built to Evolve
No concern for backward compatibility. Features die if they're not useful.

## Context Management

### Why Short Threads Are Better

- Cheaper: Fewer tokens consumed
- Better: Easier for the model to reason about focused conversations
- Less noise: Failed attempts and errors don't accumulate
- Fresh starts: Easier to handoff to new thread when things go wrong

### Context Management Techniques

1. **Be explicit** about what you want
2. **Keep it short and focused** - one task per thread
3. **Don't make the model guess** - provide context you know
4. **Use AGENTS.md** for codebase guidance
5. **Abandon threads** that have accumulated too much noise
6. **Provide feedback** on how to review work

## Prompting Best Practices

### Effective Prompts

- "Make `observeThreadGuidanceFiles` return `Omit<ResolvedGuidanceFile, 'content'>[]`..."
- "Run `<build command>` and fix all the errors"
- "Look at `<URL>` to see this UI. Then change it to look more minimal."
- "Run git blame on this file and figure out who added that function."
- "Use 3 subagents to convert these CSS files to Tailwind."

### Prompting Principles

1. **First prompt carries weight** - Sets direction for the conversation
2. **Be deliberate** - Cmd/Ctrl+Enter to submit is a reminder
3. **Keep it focused** - Break large tasks into smaller sub-tasks
4. **Tell the agent how to review** - What commands, tests, or logs to check

## Comparison with Other Agents

### What Makes Amp Different

1. **No Magic** - It's just an LLM, a loop, and tools. No secret sauce.
2. **Opinionated** - Features die if they're not useful
3. **On the Frontier** - Built to work with latest models, not legacy
4. **Thread Sharing** - Version control for AI interactions
5. **Subagents with Independent Context** - Solves token management
6. **Multi-Model Strategy** - Uses the right model for each task
7. **Tool Permissions** - Granular control over what can run

### What's NOT in Amp

- No compaction (replaced with handoff)
- No legacy features or backward compatibility concerns
- No artificial constraints on token usage
- No guardrails that prevent effectiveness

## Integration Points

### CLI
- Install via curl or npm
- Interactive mode with TUI
- Execute mode with `-x` flag
- Streaming JSON output for programmatic use
- Shell mode with `$` and `$$` commands

### Editor Extensions
- VS Code, Cursor, Windsurf, JetBrains, Neovim
- Reads diagnostics, open files, selections
- Edit files through IDE with undo support
- Amp Tab for in-editor completions
- CLI-IDE integration for rich context

### SDKs
- TypeScript SDK
- Python SDK
- For programmatic access

## Security

### Enterprise Features

- SSO (Okta, SAML) and directory sync
- Zero data retention for text inputs (Enterprise)
- Advanced thread visibility controls
- Managed user settings
- IP allowlisting

### General Security

- Secret redaction (identifies and redacts secrets)
- No data required for training (even free mode)
- MCP permissions for controlling external tool access
- Tool-level permissions for granular control

## Conclusion

Amp demonstrates that building a powerful coding agent doesn't require secret technology or complex engineering. At its core, it's:

- **An LLM** with access to tools
- **A loop** that manages conversation flow
- **Enough tokens** to work with real codebases

The rest is thoughtful tooling, careful system prompt engineering, and giving models what they need: tools, context, and the freedom to use them effectively.

The emergence of subagents with independent context windows is particularly significant - it commoditizes a skill that previously required high-level expertise: managing context windows effectively. This enables new classes of "inception" where agents can spawn other agents to solve complex, multi-part problems without running out of tokens.

Everything about Amp - from its multi-model approach to its thread sharing system - is built around one insight: give models the tools and context they need, then get out of the way.
