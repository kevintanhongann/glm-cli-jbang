# Priority 5: Permission System Implementation Plan

## Overview

Implement a granular permission system for agent tool access control, allowing fine-grained control over which tools each agent type can use, following OpenCode's permission-based approach.

## Status

- **Status:** Not Started
- **Priority:** Medium
- **Estimated Effort:** 4-5 days
- **Dependencies:** Priority 1 (Subagent Support) recommended

---

## Problem Statement

Currently, GLM-CLI has minimal permission control:
- Only write_file requires user confirmation
- No per-agent tool restrictions
- No allow/deny lists for tools
- No flexible permission levels
- Cannot restrict specific operations to specific agent types

OpenCode solves this with:
- Three-tier permission model (allow/deny/ask)
- Agent-specific tool restrictions
- Default permissions merged across all agents
- Fine-grained control per tool

---

## Design

### Permission Model

```
Three Permission Levels:
├─ allow: Automatically execute without confirmation
├─ deny: Block execution (auto-rejected)
└─ ask: Request user approval (interactive)

Permission Hierarchy:
1. Agent-specific permissions (highest priority)
2. User permissions (from config)
3. Default permissions (fallback)
```

### Permission Configuration

```toml
[permissions]
default = "allow"

[permissions.agents.explore]
tools = ["read_file", "glob", "grep", "list_files"]
mode = "allow"

[permissions.agents.plan]
tools = ["read_file", "glob", "grep", "list_files"]
mode = "allow"
exceptions = ["write_file", "edit_file"]

[permissions.agents.build]
tools = "all"
mode = "allow"

[permissions.tools]
write_file = "ask"
bash = "ask"
web_search = "allow"
```

---

## Implementation Plan

### Phase 1: Permission Models (Days 1-2)

#### 1.1 Create Permission Enum

**File:** `core/Permission.groovy`

```groovy
package core

enum Permission {
    ALLOW("allow", true, false),
    DENY("deny", false, false),
    ASK("ask", false, true)

    final String value
    final boolean autoExecute
    final boolean requiresConfirmation

    Permission(String value, boolean autoExecute, boolean requiresConfirmation) {
        this.value = value
        this.autoExecute = autoExecute
        this.requiresConfirmation = requiresConfirmation
    }

    static Permission fromString(String value) {
        def upper = value?.toUpperCase()
        return values().find { it.name() == upper } ?: ALLOW
    }

    boolean canExecute() {
        return this != DENY
    }
}
```

#### 1.2 Create Permission Configuration

**File:** `core/PermissionConfig.groovy`

```groovy
package core

import groovy.transform.Canonical

@Canonical
class PermissionConfig {
    Permission defaultPermission = Permission.ALLOW

    Map<String, Permission> toolPermissions = [:]
    Map<String, AgentPermission> agentPermissions = [:]

    Permission getToolPermission(String toolName) {
        // Check tool-specific permission
        if (toolPermissions.containsKey(toolName)) {
            return toolPermissions[toolName]
        }

        // Fall back to default
        return defaultPermission
    }

    Permission getAgentToolPermission(String agentType, String toolName) {
        // Check agent-specific permission
        if (agentPermissions.containsKey(agentType)) {
            def agentPerm = agentPermissions[agentType]

            // Check allowed tools
            if (!agentPerm.allowedTools.isEmpty() && !agentPerm.allowedTools.contains(toolName)) {
                return Permission.DENY
            }

            // Check denied tools
            if (agentPerm.deniedTools.contains(toolName)) {
                return Permission.DENY
            }

            // Check exceptions
            if (agentPerm.exceptions.containsKey(toolName)) {
                return agentPerm.exceptions[toolName]
            }
        }

        // Fall back to tool permission
        return getToolPermission(toolName)
    }

    boolean isToolAllowed(String toolName) {
        return getToolPermission(toolName).canExecute()
    }

    boolean isToolAllowedForAgent(String agentType, String toolName) {
        return getAgentToolPermission(agentType, toolName).canExecute()
    }

    Permission getEffectivePermission(String agentType, String toolName) {
        return getAgentToolPermission(agentType, toolName)
    }

    @Canonical
    static class AgentPermission {
        Permission mode = Permission.ALLOW
        List<String> allowedTools = []
        List<String> deniedTools = []
        Map<String, Permission> exceptions = [:]
    }
}
```

#### 1.3 Load Permissions from Config

**File:** `core/Config.groovy` (extend existing)

```groovy
// Add permission loading to Config class
PermissionConfig loadPermissions() {
    def permConfig = new PermissionConfig()

    // Load default
    permConfig.defaultPermission = Permission.fromString(
        config?.permissions?.default ?: "allow"
    )

    // Load tool permissions
    def toolPerms = config?.permissions?.tools
    toolPerms?.each { toolName, permValue ->
        permConfig.toolPermissions[toolName] = Permission.fromString(permValue)
    }

    // Load agent permissions
    def agentPerms = config?.permissions?.agents
    agentPerms?.each { agentType, agentConfig ->
        def agentPerm = new PermissionConfig.AgentPermission(
            mode: Permission.fromString(agentConfig.mode ?: "allow"),
            allowedTools: agentConfig.tools == "all" ? [] : (agentConfig.tools as List<String>) ?: [],
            deniedTools: (agentConfig.exceptions as List<String>) ?: []
        )

        permConfig.agentPermissions[agentType] = agentPerm
    }

    return permConfig
}
```

### Phase 2: Permission Enforcement (Days 2-3)

#### 2.1 Create PermissionEnforcer

**File:** `core/PermissionEnforcer.groovy`

```groovy
package core

import tools.Tool
import tui.AnsiColors
import tui.InteractivePrompt
import tui.OutputFormatter
import com.fasterxml.jackson.databind.ObjectMapper

class PermissionEnforcer {

    private final PermissionConfig config
    private final ObjectMapper mapper = new ObjectMapper()

    PermissionEnforcer(PermissionConfig config) {
        this.config = config
    }

    PermissionResult checkPermission(String agentType, String toolName, Map<String, Object> arguments) {
        def permission = config.getEffectivePermission(agentType, toolName)

        switch (permission) {
            case Permission.ALLOW:
                return new PermissionResult(
                    allowed: true,
                    reason: "Allowed by permission configuration",
                    permission: permission
                )

            case Permission.DENY:
                return new PermissionResult(
                    allowed: false,
                    reason: "Denied by permission configuration",
                    permission: permission
                )

            case Permission.ASK:
                return askUser(agentType, toolName, arguments)

            default:
                return new PermissionResult(
                    allowed: false,
                    reason: "Unknown permission type: ${permission}",
                    permission: permission
                )
        }
    }

    private PermissionResult askUser(String agentType, String toolName, Map<String, Object> arguments) {
        OutputFormatter.printSection("Permission Request")

        println """
${AnsiColors.bold("Tool:")} ${AnsiColors.cyan(toolName)}
${AnsiColors.bold("Agent:")} ${AnsiColors.cyan(agentType)}
${AnsiColors.bold("Arguments:")}
${formatArguments(arguments)}
"""

        while (true) {
            def choice = InteractivePrompt.select(
                "Allow this tool execution?",
                ["Allow once", "Allow always for this agent", "Deny", "Cancel"]
            )

            switch (choice) {
                case 0: // Allow once
                    return new PermissionResult(
                        allowed: true,
                        reason: "Allowed by user (once)",
                        permission: Permission.ASK
                    )

                case 1: // Allow always
                    // Update config to allow
                    updateAgentPermission(agentType, toolName, Permission.ALLOW)
                    return new PermissionResult(
                        allowed: true,
                        reason: "Allowed by user (always for this agent)",
                        permission: Permission.ALLOW
                    )

                case 2: // Deny
                    updateAgentPermission(agentType, toolName, Permission.DENY)
                    return new PermissionResult(
                        allowed: false,
                        reason: "Denied by user",
                        permission: Permission.DENY
                    )

                case 3: // Cancel
                    throw new InterruptedException("User cancelled execution")
            }
        }
    }

    private String formatArguments(Map<String, Object> arguments) {
        def formatted = new StringBuilder()

        arguments?.each { key, value ->
            formatted.append("  ${key}: ")

            if (value instanceof Map) {
                formatted.append("<object>")
            } else if (value instanceof List) {
                formatted.append("<array>")
            } else if (value instanceof String && value.length() > 100) {
                formatted.append(value.substring(0, 100) + "...")
            } else {
                formatted.append(value)
            }

            formatted.append("\n")
        }

        return formatted.toString()
    }

    private void updateAgentPermission(String agentType, String toolName, Permission permission) {
        // This would update the config file
        // For now, just update in-memory config
        if (!config.agentPermissions.containsKey(agentType)) {
            config.agentPermissions[agentType] = new PermissionConfig.AgentPermission()
        }

        def agentPerm = config.agentPermissions[agentType]

        if (permission == Permission.ALLOW) {
            agentPerm.deniedTools.remove(toolName)
        } else if (permission == Permission.DENY) {
            agentPerm.deniedTools.add(toolName)
        }

        // TODO: Save to config file
    }

    static class PermissionResult {
        boolean allowed
        String reason
        Permission permission
    }
}
```

#### 2.2 Update Agent.groovy

Add permission checks:

```groovy
// In Agent class
private final PermissionConfig permissionConfig
private final PermissionEnforcer permissionEnforcer

Agent(String apiKey, String model) {
    this.client = new GlmClient(apiKey)
    this.model = model
    this.config = Config.load()
    this.permissionConfig = config.loadPermissions()
    this.permissionEnforcer = new PermissionEnforcer(permissionConfig)
    // ... rest of initialization
}

// In run() method, before executing tools:
toolCalls.each { toolCall ->
    def functionName = toolCall.function.name
    def arguments = mapper.readValue(toolCall.function.arguments, Map.class)
    def agentType = "build" // or determine from context

    // Check permission
    def permResult = permissionEnforcer.checkPermission(agentType, functionName, arguments)

    if (!permResult.allowed) {
        OutputFormatter.printError("Permission denied: ${permResult.reason}")

        def toolMsg = new Message()
        toolMsg.role = "tool"
        toolMsg.content = "Error: Tool execution denied. ${permResult.reason}"
        toolMsg.toolCallId = callId
        history.add(toolMsg)
        continue
    }

    // Permission granted, execute tool
    // ... existing execution code
}
```

### Phase 3: Default Agent Permissions (Day 3)

#### 3.1 Create Default Permissions

**File:** `core/DefaultPermissions.groovy`

```groovy
package core

class DefaultPermissions {

    static PermissionConfig createDefaultConfig() {
        def config = new PermissionConfig()

        // Default: allow everything
        config.defaultPermission = Permission.ALLOW

        // Explore agent: read-only
        config.agentPermissions["explore"] = new PermissionConfig.AgentPermission(
            mode: Permission.ALLOW,
            allowedTools: ["read_file", "glob", "grep", "list_files"],
            deniedTools: [
                "write_file", "edit_file", "bash",
                "todo_write", "todo_read", "task"
            ]
        )

        // Plan agent: read-only (except .opencode/plan/)
        config.agentPermissions["plan"] = new PermissionConfig.AgentPermission(
            mode: Permission.ALLOW,
            allowedTools: ["read_file", "glob", "grep", "list_files"],
            deniedTools: [
                "write_file", "edit_file", "bash",
                "todo_write", "todo_read", "task"
            ],
            exceptions: [
                "write_file": Permission.ASK  // Allow if writing to .opencode/plan/
            ]
        )

        // Build agent: full access
        config.agentPermissions["build"] = new PermissionConfig.AgentPermission(
            mode: Permission.ALLOW,
            allowedTools: [],  // Empty = all tools
            deniedTools: []
        )

        // General agent: no todo tools
        config.agentPermissions["general"] = new PermissionConfig.AgentPermission(
            mode: Permission.ALLOW,
            allowedTools: [],  // All except denied
            deniedTools: ["todo_write", "todo_read"]
        )

        return config
    }

    static void saveDefaultConfig() {
        def config = createDefaultConfig()

        def tomlContent = """
[permissions]
default = "allow"

[permissions.tools]
write_file = "ask"
bash = "ask"
web_search = "allow"
fetch_url = "allow"

[permissions.agents.explore]
mode = "allow"
tools = ["read_file", "glob", "grep", "list_files"]
exceptions = ["write_file", "edit_file", "bash", "todo_write", "todo_read", "task"]

[permissions.agents.plan]
mode = "allow"
tools = ["read_file", "glob", "grep", "list_files"]
exceptions = ["write_file", "edit_file", "bash", "todo_write", "todo_read", "task"]

[permissions.agents.build]
mode = "allow"
tools = "all"

[permissions.agents.general]
mode = "allow"
tools = "all"
exceptions = ["todo_write", "todo_read"]
""".stripIndent()

        def configFile = new File(System.getProperty("user.home"), ".glm/permissions.toml")
        configFile.parentFile?.mkdirs()
        configFile.text = tomlContent
    }
}
```

### Phase 4: Configuration Management (Days 3-4)

#### 4.1 Create Permissions Command

**File:** `commands/PermissionsCommand.groovy`

```groovy
package commands

import core.Permission
import core.PermissionConfig
import core.DefaultPermissions
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "permissions", description = "Manage tool permissions for agents")
class PermissionsCommand implements Runnable {

    @Option(names = ["--show", "-s"], description = "Show current permissions")
    boolean show

    @Option(names = ["--init"], description = "Initialize default permissions file")
    boolean init

    @Option(names = ["--check"], description = "Check permission for a tool")
    String check

    @Option(names = ["--agent"], description = "Agent type for --check")
    String agent = "build"

    void run() {
        if (init) {
            initializePermissions()
        } else if (show) {
            showPermissions()
        } else if (check) {
            checkPermission(check, agent)
        } else {
            showUsage()
        }
    }

    private void initializePermissions() {
        try {
            DefaultPermissions.saveDefaultConfig()
            println "Default permissions initialized: ~/.glm/permissions.toml"
            println ""
            println "Edit this file to customize permissions."
        } catch (Exception e) {
            println "Error initializing permissions: ${e.message}"
        }
    }

    private void showPermissions() {
        def config = loadPermissionConfig()

        println """
${AnsiColors.bold("Permissions Configuration")}
${AnsiColors.dim("─" * 40)}

${AnsiColors.cyan("Default Permission:")} ${config.defaultPermission.value}

${AnsiColors.cyan("Tool Permissions:")}
${formatToolPermissions(config.toolPermissions)}

${AnsiColors.cyan("Agent Permissions:")}
${formatAgentPermissions(config.agentPermissions)}
""".stripIndent()
    }

    private void checkPermission(String toolName, String agentType) {
        def config = loadPermissionConfig()
        def permission = config.getEffectivePermission(agentType, toolName)

        println """
${AnsiColors.bold("Permission Check")}
${AnsiColors.dim("─" * 40)}

${AnsiColors.cyan("Tool:")} ${toolName}
${AnsiColors.cyan("Agent:")} ${agentType}
${AnsiColors.cyan("Permission:")} ${formatPermission(permission)}

${formatPermissionInfo(permission)}
""".stripIndent()
    }

    private PermissionConfig loadPermissionConfig() {
        def configFile = new File(System.getProperty("user.home"), ".glm/permissions.toml")
        if (configFile.exists()) {
            // Parse TOML (simplified - would use a real TOML parser)
            return DefaultPermissions.createDefaultConfig()
        } else {
            return DefaultPermissions.createDefaultConfig()
        }
    }

    private String formatToolPermissions(Map<String, Permission> perms) {
        if (perms.isEmpty()) return "  (none)"

        return perms.collect { name, perm ->
            "  ${formatPermission(perm)} ${name}"
        }.join("\n")
    }

    private String formatAgentPermissions(Map<String, PermissionConfig.AgentPermission> perms) {
        if (perms.isEmpty()) return "  (none)"

        return perms.collect { agentType, agentPerm ->
            def allowed = agentPerm.allowedTools.isEmpty() ?
                "all" :
                agentPerm.allowedTools.join(", ")

            def denied = agentPerm.deniedTools.join(", ")

            """
  ${agentType}:
    Mode: ${agentPerm.mode.value}
    Allowed: ${allowed}
    Denied: ${denied}
""".stripIndent()
        }.join("\n")
    }

    private String formatPermission(Permission perm) {
        switch (perm) {
            case Permission.ALLOW:
                return AnsiColors.green("✓ allow")
            case Permission.DENY:
                return AnsiColors.red("✗ deny")
            case Permission.ASK:
                return AnsiColors.yellow("? ask")
        }
    }

    private String formatPermissionInfo(Permission perm) {
        switch (perm) {
            case Permission.ALLOW:
                return "This tool will execute automatically without confirmation."
            case Permission.DENY:
                return "This tool is blocked and cannot be executed."
            case Permission.ASK:
                return "You will be asked for permission before this tool executes."
        }
    }

    private void showUsage() {
        println """
Usage: ./glm.groovy permissions [options]

Options:
  --show, -s         Show current permissions
  --init             Initialize default permissions file
  --check TOOL       Check permission for a specific tool
  --agent AGENT      Agent type for --check (default: build)

Examples:
  ./glm.groovy permissions --init
  ./glm.groovy permissions --show
  ./glm.groovy permissions --check write_file --agent explore
"""
    }
}
```

### Phase 5: Testing (Days 4-5)

#### 5.1 Unit Tests

**File:** `tests/PermissionTest.groovy`

```groovy
import core.Permission
import core.PermissionConfig
import core.PermissionEnforcer

class PermissionTest {
    void testDefaultPermissions() {
        def config = DefaultPermissions.createDefaultConfig()

        assert config.defaultPermission == Permission.ALLOW
        assert config.agentPermissions.containsKey("explore")
        assert config.agentPermissions.containsKey("plan")
        assert config.agentPermissions.containsKey("build")
    }

    void testExploreAgentRestrictions() {
        def config = DefaultPermissions.createDefaultConfig()

        assert !config.isToolAllowedForAgent("explore", "write_file")
        assert !config.isToolAllowedForAgent("explore", "bash")
        assert config.isToolAllowedForAgent("explore", "read_file")
        assert config.isToolAllowedForAgent("explore", "glob")
    }

    void testPermissionEnforcer() {
        def config = DefaultPermissions.createDefaultConfig()
        def enforcer = new PermissionEnforcer(config)

        // Allow case
        def result = enforcer.checkPermission("explore", "read_file", [:])
        assert result.allowed

        // Deny case
        result = enforcer.checkPermission("explore", "write_file", [:])
        assert !result.allowed
        assert result.permission == Permission.DENY
    }

    void testToolPermissionOverride() {
        def config = new PermissionConfig()
        config.defaultPermission = Permission.ALLOW
        config.toolPermissions["write_file"] = Permission.ASK

        assert config.getToolPermission("read_file") == Permission.ALLOW
        assert config.getToolPermission("write_file") == Permission.ASK
    }
}
```

#### 5.2 Integration Tests

```bash
# Test permission initialization
./glm.groovy permissions --init

# Test showing permissions
./glm.groovy permissions --show

# Test checking permissions
./glm.groovy permissions --check write_file --agent explore
./glm.groovy permissions --check read_file --agent build

# Test agent with restricted permissions
./glm.groovy agent "Use the task tool with agent_type='explore' to write a file"
# Should be denied

# Test permission during execution
./glm.groovy agent "Run 'rm -rf /' using bash"
# Should ask for confirmation (if enabled)
```

---

## Configuration

### Default Permissions File: `~/.glm/permissions.toml`

```toml
[permissions]
default = "allow"

[permissions.tools]
# Specific tool overrides
write_file = "ask"
bash = "ask"
web_search = "allow"
fetch_url = "allow"
code_search = "allow"

[permissions.agents.explore]
mode = "allow"
tools = ["read_file", "glob", "grep", "list_files"]
exceptions = [
    "write_file", "edit_file", "bash",
    "todo_write", "todo_read", "task"
]

[permissions.agents.plan]
mode = "allow"
tools = ["read_file", "glob", "grep", "list_files"]
exceptions = [
    "write_file", "edit_file", "bash",
    "todo_write", "todo_read", "task"
]

[permissions.agents.build]
mode = "allow"
tools = "all"

[permissions.agents.general]
mode = "allow"
tools = "all"
exceptions = ["todo_write", "todo_read"]
```

---

## Success Criteria

- [ ] Permission enum and models created
- [ ] PermissionConfig loads from TOML
- [ ] PermissionEnforcer checks permissions before execution
- [ ] Agent tool restrictions enforced
- [ ] User prompts for "ask" permissions
- [ ] Default permissions defined for all agent types
- [ ] Permissions command implemented
- [ ] Unit tests pass
- [ ] Integration tests pass

---

## Dependencies

- **Priority 1 (Subagent Support):** Recommended for testing agent-specific permissions

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Too restrictive permissions | High | Default to allow, let users configure |
| Permission prompts too frequent | Medium | Cache decisions, offer "always" option |
| Complex configuration | Medium | Provide sensible defaults, documentation |
| Breaking existing workflows | Low | Default config allows everything |

---

## Future Enhancements

- **Permission templates:** Pre-defined permission sets for different use cases
- **Permission audit log:** Track permission decisions
- **Time-based permissions:** Allow during specific hours
- **Context-aware permissions:** Different rules based on context
- **Permission groups:** Group related tools together

---

## References

- OpenCode Permissions: `/home/kevintan/opencode/packages/opencode/src/permission/next.ts`
- Permission systems in Claude Code and Amp

---

**Document Version:** 1.0
**Created:** 2025-01-02
**Priority:** Medium
