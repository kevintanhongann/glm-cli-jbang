# MCP Implementation Plan for GLM-CLI-Jbang

## Executive Summary

Add MCP (Model Context Protocol) support using the official Java SDK to enable external tool connectivity, similar to OpenCode's implementation. The integration will leverage existing patterns (Tool interface, skills system) while adding MCP client capabilities, configuration, and TUI integration.

---

## Table of Contents

1. [Technology Stack](#technology-stack)
2. [Phase 1: Core MCP Infrastructure](#phase-1-core-mcp-infrastructure)
3. [Phase 2: Tool Registration & Discovery](#phase-2-tool-registration--discovery)
4. [Phase 3: CLI Commands](#phase-3-cli-commands)
5. [Phase 4: TUI Integration](#phase-4-tui-integration)
6. [Phase 5: OAuth Authentication](#phase-5-oauth-authentication)
7. [Phase 6: Resource System](#phase-6-resource-system)
8. [Phase 7: Permission System Integration](#phase-7-permission-system-integration)
9. [Phase 8: Configuration Management](#phase-8-configuration-management)
10. [Phase 9: Error Handling & Resilience](#phase-9-error-handling--resilience)
11. [Phase 10: Testing](#phase-10-testing)
12. [File Structure](#file-structure)
13. [Dependencies Summary](#dependencies-summary)
14. [Configuration File Format](#configuration-file-format)
15. [Migration Path](#migration-path)
16. [Success Criteria](#success-criteria)

---

## Technology Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **MCP SDK** | `io.modelcontextprotocol.sdk:mcp` | Official Java SDK, maintained with Spring AI |
| **JSON Library** | Jackson (via mcp-jackson2) | Already used in glm-cli-jbang |
| **Reactive Streams** | Project Reactor (included in SDK) | Handles async MCP communication |
| **Logging** | SLF4J | Standard facade, already compatible |

**Dependencies to add to `glm.groovy`:**
```groovy
//DEPS io.modelcontextprotocol.sdk:mcp:0.17.0
//DEPS io.projectreactor:reactor-core:3.6.6
```

---

## Phase 1: Core MCP Infrastructure

### 1.1 MCP Configuration

**File: `config/McpConfig.groovy`**
- Load MCP servers from config files
- Support multiple config sources (project, user, global)
- Schema for server definitions (local, remote, oauth)

```groovy
// ~/.glm/config.toml
[mcp]
[mcp.mcp_everything]
type = "local"
command = ["npx", "-y", "@modelcontextprotocol/server-everything"]
enabled = true

[mcp.context7]
type = "remote"
url = "https://mcp.context7.com/mcp"
headers = { CONTEXT7_API_KEY = "{env:CONTEXT7_API_KEY}" }
enabled = true
```

**Config Search Order:**
1. `.glm/mcp.json` (project-specific)
2. `~/.glm/mcp.json` (user-specific)
3. `~/.glm/config.toml` `[mcp]` section

### 1.2 MCP Client Manager

**File: `mcp/McpClientManager.groovy`**
- Singleton pattern for managing MCP connections
- Lifecycle management (initialize, connect, disconnect, shutdown)
- Status tracking per server (connected, failed, needs_auth, disabled)
- Transport selection (STDIO, HTTP, SSE)

**Key Methods:**
```groovy
class McpClientManager {
    static McpClientManager instance

    void initialize()
    void connect(String serverName)
    void disconnect(String serverName)
    Map<String, McpClient> getClients()
    Map<String, ServerStatus> getStatuses()
}
```

### 1.3 Tool Adapter

**File: `mcp/McpToolAdapter.groovy`**
- Convert MCP tools to glm-cli Tool interface
- Sanitize tool names: `serverName_toolName`
- Handle MCP tool execution via reactive streams

```groovy
class McpToolAdapter implements Tool {
    private McpClient client
    private McpToolDefinition mcpTool

    String getName() {
        def serverName = client.serverName.replaceAll(/[^a-zA-Z0-9_-]/, "_")
        def toolName = mcpTool.name.replaceAll(/[^a-zA-Z0-9_-]/, "_")
        return "${serverName}_${toolName}"
    }

    Map<String, Object> getParameters() {
        return mcpTool.inputSchema
    }

    Object execute(Map<String, Object> args) {
        // Call MCP tool and return result
    }
}
```

---

## Phase 2: Tool Registration & Discovery

### 2.1 Tool Discovery Service

**File: `mcp/McpToolDiscovery.groovy`**
- Query connected MCP servers for tool lists
- Convert tools to Tool interface instances
- Handle tool change notifications from MCP servers

```groovy
class McpToolDiscovery {
    static Map<String, Tool> discoverTools() {
        def tools = [:]
        def clients = McpClientManager.instance.clients

        clients.each { name, client ->
            if (client.isConnected()) {
                def mcpTools = client.listTools()
                mcpTools.each { mcpTool ->
                    def adapter = new McpToolAdapter(client, mcpTool)
                    tools[adapter.name] = adapter
                }
            }
        }
        return tools
    }
}
```

### 2.2 Integration with Agent

**Modification: `core/Agent.groovy`**
- Register MCP tools alongside built-in tools
- Apply permission patterns for MCP tools
- Handle tool-specific timeouts from MCP config

```groovy
// In Agent constructor
if (config.enableMcp) {
    def mcpTools = McpToolDiscovery.discoverTools()
    mcpTools.each { name, tool ->
        if (isToolAllowed(name, config.permissions)) {
            registerTool(tool)
        }
    }
}
```

---

## Phase 3: CLI Commands

### 3.1 MCP Command Group

**File: `commands/McpCommand.groovy`**

**Subcommands:**

| Subcommand | Purpose |
|------------|---------|
| `glm mcp list` | List all MCP servers and their status |
| `glm mcp add` | Interactive server addition wizard |
| `glm mcp remove <name>` | Remove an MCP server from config |
| `glm mcp connect <name>` | Manually connect a server |
| `glm mcp disconnect <name>` | Manually disconnect a server |
| `glm mcp auth <name>` | Start OAuth authentication flow |
| `glm mcp debug <name>` | Show detailed connection info |

```groovy
@Command(name = "mcp", subcommands = [McpListCommand.class, McpAddCommand.class, ...])
class McpCommand implements Runnable {
    @Override
    void run() {
        new CommandLine(this).usage(System.out)
    }
}
```

### 3.2 Add to Main CLI

**Modification: `commands/GlmCli.groovy`**
```groovy
@Command(subcommands = [ChatCommand.class, AgentCommand.class, McpCommand.class, ...])
class GlmCli implements Runnable
```

---

## Phase 4: TUI Integration

### 4.1 MCP Status Panel

**File: `tui/lanterna/widgets/McpStatusPanel.groovy`**
- Display connected MCP count with status indicators
- Show error states when connections fail
- Update in real-time via MCP event bus

**Status Indicators:**
- `●` Green: Connected
- `○` Yellow: Connecting
- `⊗` Red: Failed
- `⊘` Gray: Disabled

### 4.2 MCP Management Dialog

**File: `tui/lanterna/widgets/McpDialog.groovy`**
- Interactive dialog to toggle MCP servers on/off
- Show server details (type, command/url, status)
- Navigate with arrow keys, toggle with space

**Key Bindings:**
- `/mcp` - Open MCP dialog
- `Space` - Toggle server
- `Enter` - View server details
- `Esc` - Close dialog

### 4.3 Footer Panel Enhancement

**Modification: `tui/lanterna/widgets/FooterPanel.groovy`**
- Already has `mcpIndicator` placeholder (line 11, 50-52)
- Wire up to `McpClientManager` status updates

```groovy
void update(String directory, int lspCount, int lspErrors, int mcpCount, int mcpErrors, String agentName, ...) {
    // Already exists, just implement
    updateMcpStatus(mcpCount, mcpErrors)
}
```

---

## Phase 5: OAuth Authentication

### 5.1 OAuth Callback Server

**File: `mcp/OAuthCallbackServer.groovy`**
- Local HTTP server on port 19876 (or configurable)
- Handle OAuth authorization code callback
- Verify state parameter (CSRF protection)
- Exchange code for tokens and store securely

```groovy
class OAuthCallbackServer {
    private int port = 19876
    private HttpServer server

    void start()
    void stop()
    String waitForAuthCode()
}
```

### 5.2 Token Storage

**File: `mcp/OAuthTokenStorage.groovy`**
- Store OAuth tokens in `~/.glm/mcp-auth.json`
- Encrypt sensitive data if possible
- Check token expiration and refresh

```groovy
// ~/.glm/mcp-auth.json
{
  "context7": {
    "access_token": "...",
    "refresh_token": "...",
    "expires_at": "2026-01-15T10:30:00Z"
  }
}
```

### 5.3 OAuth Flow Integration

**File: `commands/McpAuthCommand.groovy`**
```groovy
@Command(name = "auth")
class McpAuthCommand implements Runnable {
    @Parameters(index = "0", description = "MCP server name")
    String serverName

    @Override
    void run() {
        def config = McpConfig.load()
        def server = config.mcpServers[serverName]

        if (server.oauth) {
            def callback = new OAuthCallbackServer()
            def authUrl = generateAuthUrl(server.oauth, callback.port)

            // Open browser
            openBrowser(authUrl)

            // Wait for callback
            def authCode = callback.waitForAuthCode()

            // Exchange for tokens
            def tokens = exchangeCodeForTokens(authCode, server.oauth)

            // Store tokens
            OAuthTokenStorage.instance.save(serverName, tokens)

            // Reconnect MCP server
            McpClientManager.instance.connect(serverName)
        }
    }
}
```

---

## Phase 6: Resource System

### 6.1 Resource Discovery

**File: `mcp/McpResourceManager.groovy`**
- Query MCP servers for resources (files, database entries, etc.)
- Convert to glm-cli resource format
- Support resource subscriptions if available

### 6.2 Resource Access Tool

**File: `tools/McpResourceTool.groovy`**
- New tool to access MCP resources
- Parameters: `serverName`, `resourceUri`
- Return resource content or metadata

```groovy
@Tool(name = "mcp_resource", description = "Access MCP server resources")
class McpResourceTool implements Tool {
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                server: [type: "string", description: "MCP server name"],
                uri: [type: "string", description: "Resource URI"]
            ],
            required: ["server", "uri"]
        ]
    }
}
```

---

## Phase 7: Permission System Integration

### 7.1 MCP Permission Patterns

**Modification: `core/Config.groovy`**
- Add MCP-specific permission configuration
- Support wildcard patterns: `serverName_*`, `*_toolName`

```toml
[permission]
"mcp_everything_*" = "ask"      # Ask permission for all tools
"context7_search" = "allow"     # Allow specific tool
"sentry_*" = "deny"             # Deny all Sentry tools

[tools]
"mcp_everything_*" = false      # Disable server tools completely
```

### 7.2 Permission Enforcement

**Modification: `core/Agent.groovy`**
- Apply MCP permissions before tool execution
- Integrate with existing `ask` permission system

---

## Phase 8: Configuration Management

### 8.1 Interactive MCP Server Addition

**Implementation: `commands/McpAddCommand.groovy`**
- Wizard-style prompts for server details
- Choose transport type (local/remote)
- For local: specify command and env vars
- For remote: specify URL and headers/OAuth
- Validate configuration before saving

```groovy
// Example wizard flow:
$ glm mcp add
? Server name: my-mcp-server
? Transport type: [1] Local (stdio)  [2] Remote (HTTP/SSE)
  Choose: 1
? Command: npx -y @modelcontextprotocol/server-everything
? Environment variables (optional, comma-separated key=value): API_KEY=secret
? Enable on startup: Yes
✓ Saved to ~/.glm/mcp.json
```

### 8.2 Configuration Validation

**File: `config/McpConfigValidator.groovy`**
- Validate MCP server configurations
- Check command existence for local servers
- Validate URL format for remote servers
- Test OAuth configuration if provided

---

## Phase 9: Error Handling & Resilience

### 9.1 Connection Retry Logic

**File: `mcp/McpConnectionRetry.groovy`**
- Exponential backoff for failed connections
- Configurable max retries and timeout
- Per-server retry policies

```groovy
// ~/.glm/config.toml
[mcp.connection]
max_retries = 3
initial_delay = 1000  # ms
max_delay = 30000    # ms
```

### 9.2 Error Reporting

**Modification: `mcp/McpClientManager.groovy`**
- Detailed error messages for common issues
- Capture MCP server error logs
- Provide debug info via `glm mcp debug <name>`

---

## Phase 10: Testing

### 10.1 Unit Tests

**Test Files:**
- `mcp/McpClientManagerTest.groovy`
- `mcp/McpToolAdapterTest.groovy`
- `mcp/McpConfigValidatorTest.groovy`

### 10.2 Integration Tests

**Test Files:**
- `tests/integration/McpIntegrationTest.groovy`
- Test with sample MCP server (e.g., server-everything)
- Verify tool discovery and execution

### 10.3 Manual Testing

**Test Scenarios:**
- Add/remove MCP servers via CLI
- Connect/disconnect manually
- Test OAuth flow (with a real OAuth MCP server)
- Verify TUI MCP status indicators
- Test tool permissions (allow/deny/ask)

---

## File Structure

```
glm-cli-jbang/
├── mcp/
│   ├── McpClientManager.groovy           # Central client management
│   ├── McpToolAdapter.groovy             # MCP → Tool adapter
│   ├── McpToolDiscovery.groovy           # Tool discovery service
│   ├── McpResourceManager.groovy         # Resource system
│   ├── McpConfig.groovy                  # Configuration loader
│   ├── McpConfigValidator.groovy         # Config validation
│   ├── McpConnectionRetry.groovy         # Retry logic
│   ├── OAuthCallbackServer.groovy         # OAuth callback handler
│   ├── OAuthTokenStorage.groovy          # Token persistence
│   └── McpEventBus.groovy                # Event notifications
│
├── commands/
│   ├── McpCommand.groovy                 # Main MCP command
│   ├── McpListCommand.groovy             # List servers
│   ├── McpAddCommand.groovy              # Add server wizard
│   ├── McpRemoveCommand.groovy           # Remove server
│   ├── McpConnectCommand.groovy          # Manual connect
│   ├── McpDisconnectCommand.groovy       # Manual disconnect
│   ├── McpAuthCommand.groovy             # OAuth flow
│   └── McpDebugCommand.groovy            # Debug info
│
├── tui/lanterna/widgets/
│   ├── McpStatusPanel.groovy             # Status indicator
│   └── McpDialog.groovy                  # Management dialog
│
├── tools/
│   └── McpResourceTool.groovy            # Resource access tool
│
├── tests/
│   ├── unit/mcp/
│   │   ├── McpClientManagerTest.groovy
│   │   └── McpToolAdapterTest.groovy
│   └── integration/
│       └── McpIntegrationTest.groovy
│
├── .glm/
│   ├── mcp.json                          # Project MCP config
│   └── mcp-auth.json                     # OAuth tokens
│
└── glm.groovy                            # Add MCP SDK dependencies
```

---

## Dependencies Summary

Add to `glm.groovy`:
```groovy
//DEPS io.modelcontextprotocol.sdk:mcp:0.17.0
//DEPS io.projectreactor:reactor-core:3.6.6
```

---

## Configuration File Format

**`~/.glm/mcp.json`:**
```json
{
  "mcp_everything": {
    "type": "local",
    "command": ["npx", "-y", "@modelcontextprotocol/server-everything"],
    "environment": {},
    "enabled": true,
    "timeout": 30000
  },
  "context7": {
    "type": "remote",
    "url": "https://mcp.context7.com/mcp",
    "enabled": true,
    "headers": {
      "CONTEXT7_API_KEY": "{env:CONTEXT7_API_KEY}"
    },
    "oauth": {
      "authorization_endpoint": "https://auth.context7.com/oauth/authorize",
      "token_endpoint": "https://auth.context7.com/oauth/token"
    }
  }
}
```

**`~/.glm/config.toml`:**
```toml
[mcp]
enabled = true

[mcp.connection]
max_retries = 3
initial_delay = 1000
max_delay = 30000

[permission]
"mcp_*" = "ask"
"mcp_everything_*" = "allow"
```

---

## Migration Path

1. **Phase 1-3 (Core + Tools + CLI):** MVP - basic MCP server support
2. **Phase 4 (TUI):** Add visual indicators and management
3. **Phase 5 (OAuth):** Enable OAuth-authenticated MCP servers
4. **Phase 6-7 (Resources + Permissions):** Full MCP feature parity
5. **Phase 8-10 (Config + Error Handling + Testing):** Production polish

---

## Key Differences from OpenCode

| Aspect | OpenCode (TypeScript) | glm-cli-jbang (Groovy/Java) |
|--------|----------------------|----------------------------|
| MCP SDK | `@modelcontextprotocol/sdk` | `io.modelcontextprotocol.sdk:mcp` |
| JSON | Native | Jackson (already used) |
| Reactive | Built-in async/await | Project Reactor |
| Config | opencode.json | TOML + JSON |
| CLI | Clack (custom) | Picocli |
| TUI | SolidJS + custom | Lanterna/Jexer/TUI4J |

---

## Success Criteria

- [ ] Can connect to local MCP servers (stdio)
- [ ] Can connect to remote MCP servers (HTTP/SSE)
- [ ] MCP tools appear in agent tool list
- [ ] Tool execution works correctly
- [ ] OAuth authentication flow works
- [ ] TUI shows MCP status indicators
- [ ] `glm mcp list` shows server status
- [ ] `glm mcp add` wizard works
- [ ] Permission system applies to MCP tools
- [ ] Resources can be accessed via MCP tools

---

## References

- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [MCP Specification](https://modelcontextprotocol.io/specification/)
- [OpenCode MCP Implementation](https://github.com/anomalyco/opencode)
- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
