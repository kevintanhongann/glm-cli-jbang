# OpenCode Codebase Analysis

*Analysis generated for sst/opencode repository*

## Overview

**OpenCode** is an open-source AI coding agent that provides intelligent development assistance through a terminal user interface (TUI). Built by the creators of terminal.shop and neovim users, it aims to push the limits of what's possible in the terminal environment.

### Key Statistics
- **Stars**: 45k
- **Forks**: 3.9k
- **Language**: TypeScript (82.4%), CSS (8.4%), MDX (7.1%), Shell (0.7%), Astro (0.5%), Rust (0.3%)
- **License**: MIT
- **Latest Version**: v1.0.222 (as of Jan 1, 2026)
- **Contributors**: 482+
- **Commits**: 6,535+

---

## Architecture & Technology Stack

### Core Technologies
- **Runtime**: Bun (JavaScript runtime and package manager)
- **Primary Language**: TypeScript
- **Package Management**: Bun workspaces with monorepo structure
- **Build System**: Turborepo for build orchestration
- **UI Framework**: Solid.js for reactive UI components
- **Styling**: Tailwind CSS v4.1.11
- **API**: Hono framework for API endpoints
- **AI SDK**: Vercel AI SDK (ai@5.0.97)
- **Markdown**: Marked (17.0.1) with Shiki syntax highlighting (3.20.0)
- **Validation**: Zod (4.1.8) for schema validation

### Infrastructure
- **Platform**: Cloudflare
- **Backend Services**:
  - Stripe for payments
  - PlanetScale (database)
  - AWS S3 (@aws-sdk/client-s3)
- **Deployment**: SST (3.17.23) for infrastructure as code

### Development Tools
- **TypeScript**: 5.8.2 (with @typescript/native-preview for preview features)
- **Prettier**: 3.6.2 for code formatting
- **Husky**: 9.1.7 for Git hooks
- **Editor**: VS Code configuration included

---

## Repository Structure

### Root Directory Files
```
├── packages/          # Monorepo packages
├── infra/             # Infrastructure definitions
├── github/            # GitHub-specific files
├── nix/               # Nix packaging support
├── logs/              # Log files
├── patches/           # Dependency patches
├── script/            # Build scripts
├── scripts/           # Additional scripts
├── specs/             # Specifications
├── sdks/vscode/       # VS Code SDK integration
├── package.json       # Root package configuration
├── tsconfig.json      # TypeScript configuration
├── turbo.json         # Turborepo configuration
├── sst.config.ts      # SST infrastructure config
├── bun.lock           # Bun lock file
├── flake.nix          # Nix flake for reproducible builds
└── bunfig.toml        # Bun configuration
```

### Workspace Packages
Based on the package.json workspaces configuration:

```
packages/
├── opencode/          # Main OpenCode CLI application
├── console/           # Console application
│   └── app/           # Console app components
├── sdk/
│   └── js/            # JavaScript/TypeScript SDK
└── slack/             # Slack integration package
```

---

## Key Features & Functionality

### Built-in Agents

OpenCode implements a **ReAct (Reasoning + Acting) agent pattern** where the LLM autonomously decides when and how to use tools to accomplish multi-step tasks.

#### 1. Build Agent (Default)
- Full access agent for development work
- Can read and write files
- Can execute bash commands
- Suitable for development tasks

#### 2. Plan Agent (Read-Only)
- Read-only agent for analysis and code exploration
- Denies file edits by default
- Asks permission before running bash commands
- Ideal for exploring unfamiliar codebases or planning changes

#### 3. General Subagent
- Used internally for complex searches and multi-step tasks
- Invoked via `@general` in messages
- Handles specialized operations

### Agent Switching
Users can switch between agents using the `Tab` key.

---

## Tool System

### Tool Calling Best Practices

According to the AGENTS.md guidelines:

1. **Always use tools when applicable** - Tools extend capabilities beyond text generation
2. **Parallel tool execution** - Agent can call multiple tools simultaneously for independent operations
3. **Tool batching** - Batch similar operations to reduce API calls
4. **Parallel file reading** - Read multiple related files in one conversation turn

### Tool Types

Based on typical agent implementations, tools include:
- **File Operations**: read, write, list files
- **Code Execution**: bash commands
- **Content Search**: grep for searching file contents
- **Pattern Matching**: glob for file pattern matching
- **Web Search**: Access current information from the web
- **LSP Support**: Out-of-the-box Language Server Protocol support

### Tool Safety Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `ask` | Prompt before destructive operations (default) | Interactive sessions |
| `always_allow` | Execute without confirmation | Automated workflows |
| `strict` | Deny all writes | Read-only analysis |

---

## Installation & Distribution

### Installation Methods

OpenCode provides multiple installation options:

1. **Quick Install (YOLO)**
   ```bash
   curl -fsSL https://opencode.ai/install | bash
   ```

2. **Package Managers**
   - npm: `npm i -g opencode-ai@latest`
   - bun/pnpm/yarn: Same as npm
   - Scoop (Windows): `scoop bucket add extras; scoop install extras/opencode`
   - Chocolatey (Windows): `choco install opencode`
   - Homebrew (macOS/Linux): `brew install opencode`
   - Arch Linux: `paru -S opencode-bin`
   - mise: `mise use -g opencode`
   - Nix: `nix run nixpkgs#opencode`

3. **Desktop App (BETA)**
   - Available for macOS (Apple Silicon & Intel), Windows, and Linux
   - Platforms: .dmg, .exe, .deb, .rpm, AppImage
   - Homebrew: `brew install --cask opencode-desktop`

### Installation Directory Priority
1. `$OPENCODE_INSTALL_DIR` - Custom installation directory
2. `$XDG_BIN_DIR` - XDG Base Directory Specification compliant path
3. `$HOME/bin` - Standard user binary directory
4. `$HOME/.opencode/bin` - Default fallback

---

## Client/Server Architecture

OpenCode implements a **client/server architecture** that enables:

- Server running on user's computer
- Remote control from various clients (e.g., mobile apps)
- TUI frontend is just one possible client
- Multiple client types can interact with the same backend

This architecture is one of the key differentiators from Claude Code.

---

## Development & Building

### Development Setup

```bash
# Run in development
bun dev

# Type checking
bun turbo typecheck

# Prepare for development (sets up husky)
bun run prepare
```

### Build System (Turborepo)

The project uses Turborepo for efficient monorepo builds:

```json
{
  "tasks": {
    "typecheck": {},
    "build": {
      "dependsOn": ["^build"],
      "outputs": ["dist/**"]
    },
    "opencode#test": {
      "dependsOn": ["^build"],
      "outputs": []
    }
  }
}
```

### Nix Support

Comprehensive Nix support for reproducible builds:
- Supports multiple architectures: aarch64-linux, x86_64-linux, aarch64-darwin, x86_64-darwin
- Includes dev shell with dependencies (bun, nodejs_20, pkg-config, openssl, git)
- Provides package builds for all supported platforms
- Development app: `nix run .#opencode-dev`

### TypeScript Configuration

- Extends `@tsconfig/bun/tsconfig.json`
- Uses TypeScript 5.8.2
- Native preview features enabled via `@typescript/native-preview@7.0.0-dev.20251207.1`

---

## Code Style Guidelines

The project follows specific style conventions (from STYLE_GUIDE.md):

- Keep things in one function unless composable or reusable
- Avoid unnecessary destructuring of variables
- Avoid `else` statements unless necessary
- Avoid `try`/`catch` when possible
- Avoid `any` types
- Avoid `let` statements (prefer `const`)
- Prefer single-word variable names where possible
- Use Bun APIs whenever possible (e.g., `Bun.file()`)

### Prettier Configuration
```json
{
  "semi": false,
  "printWidth": 120
}
```

---

## Key Dependencies

### Core Dependencies
- `ai`: 5.0.97 - Vercel AI SDK for LLM integration
- `hono`: 4.10.7 - Web framework for API endpoints
- `solid-js`: 1.9.10 - Reactive UI library
- `marked`: 17.0.1 - Markdown parser
- `shiki`: 3.20.0 - Syntax highlighting
- `zod`: 4.1.8 - Schema validation
- `tailwindcss`: 4.1.11 - Utility-first CSS framework
- `vite`: 7.1.4 - Build tool and dev server

### DevDependencies
- `@aws-sdk/client-s3`: 3.933.0 - AWS S3 client
- `sst`: 3.17.23 - Infrastructure as Code
- `turbo`: 2.5.6 - Turborepo build system
- `husky`: 9.1.7 - Git hooks

### Workspace Packages
- `@opencode-ai/plugin`: Internal plugin package
- `@opencode-ai/script`: Internal script package
- `@opencode-ai/sdk`: SDK package

---

## Documentation & Community

### Official Documentation
- Website: https://opencode.ai
- Documentation: https://opencode.ai/docs
- Agent docs: https://opencode.ai/docs/agents
- OpenCode Zen (model provider): https://opencode.ai/zen

### Community
- Discord: https://opencode.ai/discord
- X (Twitter): https://x.com/opencode
- GitHub Issues: 1.2k open issues
- Pull Requests: 546 open PRs

---

## Key Differentiators from Claude Code

Based on the FAQ section:

1. **100% Open Source** - Fully open-source vs proprietary
2. **Provider-Agnostic** - Can be used with Claude, OpenAI, Google, or local models
3. **Out-of-the-box LSP Support** - Built-in language server protocol support
4. **TUI Focus** - Built by terminal enthusiasts, pushing terminal UI limits
5. **Client/Server Architecture** - Allows remote control from various clients
6. **Created by terminal.shop team** - Expertise in terminal applications

---

## SDK Generation

To regenerate the JavaScript/TypeScript SDK:
```bash
./packages/sdk/js/script/build.ts
```

---

## Testing

The project includes testing setup:
- Test tasks configured in turbo.json
- Depends on build completion
- Located in `packages/opencode`

---

## Configuration Files Summary

### Build & Runtime
- `bunfig.toml`: Bun configuration with exact package matching
- `turbo.json`: Turborepo task definitions
- `flake.nix`: Nix reproducible environment configuration

### TypeScript
- `tsconfig.json`: Extends Bun's TypeScript config
- `sst-env.d.ts`: SST environment types

### Git
- `.gitignore`: Standard ignore patterns
- `.husky/`: Git hooks configuration

### Editor
- `.vscode/`: VS Code workspace settings
- `.editorconfig`: Editor consistency rules

---

## Patched Dependencies

The project patches one dependency:
- `ghostty-web@0.3.0`: Patch file at `patches/ghostty-web@0.3.0.patch`

---

## Platform Support

### Supported Operating Systems
- macOS (Apple Silicon and Intel)
- Linux (various distributions via .deb, .rpm, AppImage)
- Windows
- Nix (all major architectures)

### Cross-Platform
- Bun runtime ensures consistent behavior across platforms
- Nix flakes provide reproducible builds
- Multiple package manager integrations

---

## Security & Permissions

### Safety Modes for File Operations
- **Ask Mode**: Default, prompts user before destructive operations
- **Always Allow Mode**: Executes without confirmation
- **Strict Mode**: Denies all writes (read-only)

This ensures users maintain control over destructive operations while allowing flexibility for different use cases.

---

## Web Search Capabilities

The agent includes web search functionality to:
- Access current information (latest docs, news)
- Check current package versions
- Research recent trends or breaking changes
- Find recent bug reports or solutions

Web search can be configured with:
- Recency filters (noLimit, 1w, 1m, 1d)
- Domain filtering (for official documentation)
- Result count adjustment (default 10, max 50)

---

## Known Issues & Troubleshooting

Based on AGENTS.md debugging section:

| Issue | Symptoms | Solution |
|-------|----------|----------|
| Agent stuck in loop | Repeated tool calls without progress | Check tool returns; add timeout |
| Tool not found | "Error: Tool not found" | Verify tool registration and naming |
| Infinite reasoning | "Thinking..." without progress | Increase `maxSteps` limit; check model response |
| Permission denied | User denies write operation | Check safety_mode config; modify safety logic |
| Diff shows no changes | Empty diff in write proposal | Verify tool arguments; check file normalization |

---

## Version History

- Current latest: v1.0.222 (January 1, 2026)
- Total releases: 646
- Note: Remove versions older than 0.1.x before installing

---

## Performance Optimization Strategies

### Reducing API Calls
- Batch file reads when possible
- Use glob/list to locate files before reading
- Cache frequently accessed data
- Minimize redundant tool calls

### Context Management
- Prioritize recent messages
- Summarize long conversations
- Prune less relevant history
- Use targeted file reads instead of dumping entire codebase

### Token Management
- Track conversation length
- Implement context window limits
- Summarize long histories when needed
- Use efficient tool result formatting

---

## Future Roadmap (Inferred)

Based on the codebase and documentation:

1. **Enhanced LSP Support**: Continued improvements to language server protocol integration
2. **More Client Types**: Additional clients beyond TUI (mobile apps mentioned)
3. **Model Provider Expansion**: Continued provider-agnostic support as models evolve
4. **Plugin System**: Potential for external tools via Groovy scripts or similar
5. **Improved Terminal UI**: Pushing limits of terminal UI capabilities

---

## Contributing

For those interested in contributing:
- Read the contributing documentation before submitting PRs
- Follow the established style guidelines
- Ensure tests pass before submitting
- Use the configured TypeScript and Prettier settings

---

## Conclusion

OpenCode represents a sophisticated, open-source AI coding agent with:

- **Modern tech stack**: TypeScript, Bun, Solid.js, Tailwind CSS
- **Monorepo architecture**: Efficient package management with workspaces
- **Agent-based system**: ReAct pattern with specialized agents (build, plan, general)
- **Provider-agnostic design**: Works with multiple AI model providers
- **Cross-platform support**: Runs on all major operating systems
- **Terminal-first approach**: Built by terminal enthusiasts for terminal users
- **Client/server architecture**: Flexible deployment options
- **Active community**: 45k+ stars, 482+ contributors
- **Well-maintained**: Regular releases and active development

The project successfully combines cutting-edge AI technology with traditional terminal workflows, offering developers a powerful coding assistant that respects their preferences and provides flexibility in model choice and deployment.
