# GLM-CLI Project Brief

## Overview

**glm-cli** is a Rust-based command-line AI coding agent built around Z.ai's GLM-4 models. It provides repository-aware assistance for reading, editing, and refactoring code directly from the terminal with native performance and cross-platform support.

Built with Rust for:
- **Native performance** — No runtime overhead, fast startup
- **Cross-platform binaries** — Single executable for Linux, macOS, Windows
- **Memory safety** — Rust's ownership model prevents common bugs
- **Async I/O** — Efficient streaming with Tokio runtime

Inspired by [SST OpenCode](https://github.com/sst/opencode), glm-cli uses:
- **clap** for CLI parsing with derive macros
- A **central agent loop** with async tool orchestration
- A **GLM provider** using reqwest for HTTP and SSE streaming
- **serde/schemars** for JSON Schema validation of tools

It targets both **English and Chinese** developers with bilingual prompts and behaviors.

## Z.ai GLM Integration

### Supported Models

| Model | Context | Use Case | Recommended For |
|-------|---------|----------|-----------------|
| `glm-4-flash` | 128K | Fast, cost-effective | Default for most operations |
| `glm-4` | 128K | Balanced quality/speed | General coding assistance |
| `glm-4-plus` | 128K | Higher quality reasoning | Complex refactoring |
| `glm-4.5` | 128K | Latest, multi-function calls | Advanced agent tasks |
| `glm-4v` | 128K | Vision capabilities | Image/diagram analysis |

### Key API Features

- **Tool/Function Calling** — Full support with JSON Schema definitions
- **Streaming (SSE)** — Real-time response streaming
- **Web Search** — Built-in web search tool integration
- **Thinking Mode** — Extended reasoning for GLM-4.5
- **Bilingual** — Native Chinese and English support

### API Endpoint

```
Base URL: https://open.bigmodel.cn/api/paas/v4/
Auth: Bearer token (API key)
```

## Goals

1. **Native performance CLI** — Fast startup, low memory, single binary
2. **Repo-aware file operations** — Read/search/write with safety guardrails
3. **First-class GLM integration** — Full tool calling and streaming support
4. **Extensible tool system** — Add custom tools via Rust traits or config
5. **Cross-platform** — Linux, macOS, Windows from single codebase

## Target Users

| Segment | Description |
|---------|-------------|
| **Primary** | Developers wanting fast, native CLI tools |
| **Secondary** | Chinese-speaking devs using GLM models |
| **Tertiary** | Teams needing cross-platform agent tooling |

## Key Features

### Core Commands

| Command | Description |
|---------|-------------|
| `glm chat` | Conversational assistant for Q&A and code help |
| `glm ask` | Answer questions with repository context |
| `glm edit` | Propose and apply edits with diff preview |
| `glm review` | Review code or diffs, suggest improvements |
| `glm agent` | Multi-step REPL with tool orchestration |

### GLM-Specific Features

- **Model selection**: `--model glm-4-flash|glm-4|glm-4-plus|glm-4.5`
- **Streaming output**: Real-time token streaming via SSE
- **Tool calling**: Parallel multi-function execution (GLM-4.5)
- **Thinking mode**: Extended reasoning with `--thinking` flag
- **Web search**: Built-in `--web-search` for up-to-date info
- **Bilingual**: `--lang auto|en|zh` for language-aware responses

### Developer Features

- **Single binary distribution** — No dependencies to install
- **TOML configuration** — Hierarchical config resolution
- **Session persistence** — SQLite or JSON file storage
- **Plugin system** — WASM or dynamic library plugins (future)
- **Shell completions** — Auto-generated for bash/zsh/fish

## Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Language** | Rust 2021 Edition | Core implementation |
| **CLI** | clap v4 | Argument parsing with derive |
| **Async** | Tokio | Async runtime |
| **HTTP** | reqwest | API calls |
| **Streaming** | eventsource-client | SSE streaming |
| **Serialization** | serde + schemars | JSON + JSON Schema |
| **Storage** | rusqlite | Session persistence |
| **Terminal** | ratatui (optional) | TUI interface |

## Success Metrics

- Binary size < 10MB (release, stripped)
- Startup time < 50ms
- Streaming latency < 100ms to first token
- Cross-compilation for 3+ platforms

## Constraints

- Initial scope: Single-user CLI (no server mode)
- File operations sandboxed to project root
- Default to GLM-4-flash for cost efficiency

## Related Documents

- [Architecture Design](./architecture.md)
- [Technical Specification](./technicalSpec.md)
- [Implementation Roadmap](./roadmap.md)