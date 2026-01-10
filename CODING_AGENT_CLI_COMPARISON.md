# Coding Agent CLI/TUI Comparison Analysis

A comprehensive comparison of the leading AI coding agent CLIs and TUIs based on architecture, features, and design philosophy.

## Executive Summary

| Agent | Language | Stars | License | Model | Best For |
|-------|----------|-------|---------|-------|----------|
| **Gemini CLI** | TypeScript | 89.3k | Apache-2.0 | Gemini 2.5 | Enterprise, Google ecosystem |
| **OpenCode** | TypeScript | 45k | MIT | Multi-provider | Open-source, provider flexibility |
| **Qwen Code** | TypeScript | 16.9k | Apache-2.0 | Qwen3-Coder | Free tier, Qwen optimization |
| **Amp** | TypeScript | N/A | Proprietary | Claude/GPT-5 | Frontier models, subagents |
| **Kilocode CLI** | TypeScript | N/A | N/A | Multi-provider | VS Code extension reuse |

---

## Architecture Comparison

### Package Structure

| Agent | Architecture | Packages |
|-------|--------------|----------|
| **Gemini CLI** | Two-package monorepo | `cli/` (frontend) + `core/` (backend) |
| **OpenCode** | Monorepo with workspaces | `opencode/`, `console/`, `sdk/`, `slack/` |
| **Qwen Code** | Two-package (forked Gemini) | `cli/` + `core/` + `sdk-typescript/` |
| **Amp** | Three-component system | LLM + Loop + Tools |
| **Kilocode CLI** | Headless Extension Host | VS Code mock + Ink UI + Extension |

### Design Philosophy

| Agent | Philosophy | Key Insight |
|-------|------------|-------------|
| **Gemini CLI** | Clean separation, extensibility | Tool discovery via command/MCP |
| **OpenCode** | Provider-agnostic, TUI-first | Client/server architecture enables remote control |
| **Qwen Code** | Gemini fork optimized for Qwen | Parser-level adaptations for Qwen-Coder |
| **Amp** | Raw model power, no guardrails | "LLM + Loop + Tokens" - everything else is optimization |
| **Kilocode CLI** | 100% code reuse | Virtual VS Code = exact extension behavior in terminal |

---

## Tool System Comparison

### Tool Categories

| Tool Type | Gemini | OpenCode | Qwen | Amp | Kilocode |
|-----------|--------|----------|------|-----|----------|
| File Read | ✅ | ✅ | ✅ | ✅ | ✅ |
| File Write | ✅ | ✅ | ✅ | ✅ | ✅ |
| File Edit (diff) | ✅ `replace` | ✅ | ✅ `edit` | ✅ `edit_file` | ✅ |
| Shell/Bash | ✅ | ✅ | ✅ | ✅ | ✅ |
| Glob/Search | ✅ | ✅ | ✅ | ✅ | ✅ |
| Web Search | ✅ (Google native) | ✅ | ✅ | ✅ | ❓ |
| MCP Support | ✅ | ✅ | ✅ | ✅ | ❓ |
| Subagents | ❓ | ✅ | ✅ | ✅ | ❓ |

### Tool Innovation Highlights

| Agent | Innovation |
|-------|------------|
| **Gemini CLI** | Multi-stage edit correction (self-correcting `replace` tool) |
| **OpenCode** | Built-in LSP support out-of-the-box |
| **Amp** | Oracle (GPT-5 review), Librarian (GitHub search), Toolboxes |
| **Kilocode CLI** | Real file system writes via mocked `vscode.workspace.applyEdit` |

---

## Subagent/Multi-Agent Support

| Agent | Subagent Support | Implementation |
|-------|------------------|----------------|
| **Gemini CLI** | Limited | Tool discovery mechanism |
| **OpenCode** | ✅ Build/Plan/General agents | Tab-switchable agents |
| **Qwen Code** | ✅ Task delegation | `task` tool for subagents |
| **Amp** | ✅ Advanced | Independent context windows, parallel execution |
| **Kilocode CLI** | ✅ Parallel mode | Git worktree sandboxing |

### Amp's Subagent Advantage

Amp's subagent system stands out with:
- **Independent context windows**: Each subagent starts fresh
- **Parallel execution**: Multiple subagents can run simultaneously
- **Specialized agents**: Oracle (GPT-5), Librarian (GitHub), Search Agent
- **Token efficiency**: Main agent only pays for coordination

---

## Authentication & Pricing

| Agent | Free Tier | Auth Methods |
|-------|-----------|--------------|
| **Gemini CLI** | 60 req/min, 1K/day (OAuth) | Google OAuth, API Key, Vertex AI |
| **OpenCode** | Via OpenCode Zen | Multi-provider API keys |
| **Qwen Code** | 2,000 req/day | Qwen OAuth, OpenAI-compatible API |
| **Amp** | Yes (ads-supported) | API keys, SSO (Enterprise) |
| **Kilocode CLI** | Depends on provider | Multi-provider |

---

## Context Window & Token Management

| Agent | Context Window | Token Strategy |
|-------|----------------|----------------|
| **Gemini CLI** | 1M tokens (Gemini 2.5 Pro) | Smart compression, caching, checkpointing |
| **OpenCode** | Provider-dependent | Summarization, pruning, handoff |
| **Qwen Code** | Model-dependent | Token caching, context compression |
| **Amp** | 200k default, 1M max | Subagents for token isolation, handoff |
| **Kilocode CLI** | Provider-dependent | State sync between UI and extension |

---

## Safety & Permissions

| Feature | Gemini | OpenCode | Qwen | Amp | Kilocode |
|---------|--------|----------|------|-----|----------|
| Write confirmation | ✅ | ✅ | ✅ | ✅ | ✅ |
| Command restrictions | ✅ Allowlist/blocklist | ✅ Safety modes | ✅ Approval modes | ✅ Permissions system | ✅ |
| Sandboxing | ✅ | ✅ Docker/sandbox-exec | ✅ Docker | ❓ | ✅ Git worktree |
| External delegation | ❓ | ❓ | ❓ | ✅ Permission helpers | ❓ |

### Permission Modes Comparison

| Mode | Gemini | OpenCode | Qwen | Amp |
|------|--------|----------|------|-----|
| Ask (default) | ✅ | ✅ | ✅ | ✅ |
| Always allow | ✅ | ✅ | ✅ | ✅ |
| Strict/Deny | ✅ | ✅ | ✅ | ✅ |
| External delegate | ❓ | ❓ | ❓ | ✅ |

---

## IDE Integration

| Agent | VS Code | Other IDEs | SDK |
|-------|---------|------------|-----|
| **Gemini CLI** | ✅ Companion | Terminal-based | ❓ |
| **OpenCode** | ✅ | Desktop app (beta) | ✅ JS/TS SDK |
| **Qwen Code** | ✅ | Zed | ✅ TypeScript SDK |
| **Amp** | ✅ | Cursor, Windsurf, JetBrains, Neovim | ✅ TS/Python SDKs |
| **Kilocode CLI** | ✅ (IS the extension) | Terminal | ❓ |

---

## Unique Strengths

### Gemini CLI
- **Largest community**: 89.3k stars, 424 contributors
- **Google ecosystem**: Native Google Search grounding
- **Multimodal native**: Images, audio, PDF support
- **Enterprise-ready**: Vertex AI integration

### OpenCode
- **100% open-source**: MIT licensed, provider-agnostic
- **Client/server architecture**: Remote control capability
- **Built-in LSP**: Language server support out-of-the-box
- **TUI excellence**: Built by terminal.shop team

### Qwen Code
- **Best free tier**: 2,000 requests/day
- **Qwen-optimized**: Parser adaptations for Qwen-Coder models
- **Gemini heritage**: Inherits proven architecture

### Amp
- **Frontier models**: Uses best available (Claude Opus 4.5, GPT-5)
- **Subagent innovation**: Independent context windows
- **Thread sharing**: Version control for AI interactions
- **No guardrails**: Raw model power philosophy

### Kilocode CLI
- **100% code reuse**: Exact VS Code extension behavior
- **Zero drift**: CLI never lags behind extension features
- **Innovative architecture**: Virtual VS Code host pattern

---

## Weaknesses & Trade-offs

| Agent | Weaknesses |
|-------|------------|
| **Gemini CLI** | Locked to Google ecosystem, rate limits on free tier |
| **OpenCode** | Younger project, fewer integrations |
| **Qwen Code** | Model-specific, smaller community than Gemini |
| **Amp** | Proprietary, requires credits for smart mode |
| **Kilocode CLI** | Complex architecture, depends on VS Code API stability |

---

## Performance Benchmarks

### Terminal-Bench Results (where available)

| Agent | Model | Accuracy |
|-------|-------|----------|
| Qwen Code | Qwen3-Coder-480A35 | 37.5% |
| Qwen Code | Qwen3-Coder-30BA3B | 31.3% |

*Note: Other agents don't publish comparable benchmarks.*

---

## Recommendation Matrix

| Use Case | Recommended Agent | Why |
|----------|-------------------|-----|
| **Enterprise/Google Cloud** | Gemini CLI | Vertex AI, OAuth, 1M context |
| **Open-source purist** | OpenCode | MIT, provider-agnostic, LSP |
| **Free usage priority** | Qwen Code | 2,000 req/day free tier |
| **Frontier model access** | Amp | Claude Opus 4.5, GPT-5, subagents |
| **VS Code extension reuse** | Kilocode CLI | Zero code duplication |
| **Maximum community/stability** | Gemini CLI | 89.3k stars, 424 contributors |
| **Terminal-first workflow** | OpenCode | Built by terminal experts |
| **Complex multi-step tasks** | Amp | Subagent token isolation |

---

## Architecture Rankings

### 1. **Best Overall Architecture**: Amp
- Simple core (LLM + Loop + Tools)
- Subagents solve context window problem elegantly
- Multi-model strategy (right tool for each task)

### 2. **Best Open-Source**: OpenCode
- Provider-agnostic design
- Client/server enables future expansion
- Active 45k+ star community

### 3. **Best Enterprise-Ready**: Gemini CLI
- Google backing, Vertex AI integration
- Comprehensive security features
- Massive adoption (89.3k stars)

### 4. **Most Innovative Architecture**: Kilocode CLI
- Virtual VS Code host is genius for code reuse
- Eliminates extension/CLI drift problem
- Jotai state sync is elegant

### 5. **Best Value**: Qwen Code
- Generous free tier
- Proven Gemini architecture
- Growing ecosystem

---

## Conclusion

**For GLM-CLI development**, the most relevant inspirations are:

1. **Gemini CLI's tool system**: Multi-stage edit correction, tool discovery patterns
2. **Amp's subagent model**: Independent context windows for token efficiency
3. **OpenCode's agent switching**: Tab-based Build/Plan/General agent selection
4. **Kilocode's state management**: Jotai atoms for UI/backend sync

The market is converging on:
- ReAct (Reasoning + Acting) agent patterns
- MCP for tool extensibility
- Approval-based safety modes
- Provider-agnostic designs (except Gemini)

The key differentiator going forward will be **subagent orchestration** and **context window management** - areas where Amp currently leads.

---

*Analysis generated: January 2026*
*Based on: GEMINI_CLI_ARCHITECTURE.md, KILOCODE_CLI_ARCHITECTURE.md, HOW_AMP_WORKS.md, QWEN_CODE_ARCHITECTURE.md, opencode_analysis.md*
