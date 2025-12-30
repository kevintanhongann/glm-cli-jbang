# GLM-CLI Authentication Implementation Plan (Option 1)

## Overview

Implement authentication for GLM-CLI using simple API key storage, matching SST OpenCode's default behavior for most providers.

## Target Provider

- **Zai/Zhipu AI** (GLM-4 and coding plan API)
- API Key authentication (no OAuth required)

## Design Decisions

### Why Option 1 (Simple API Key)?

1. **Matches OpenCode's Default**: Most providers in OpenCode use simple API key prompts
2. **Appropriate for Zai**: Zhipu AI uses API keys, not OAuth
3. **Simpler Stack**: GLM-CLI uses Groovy/JBang - simpler than OpenCode's TypeScript/Bun
4. **Lower Maintenance**: Less code, fewer dependencies, easier to maintain
5. **Faster Implementation**: Can be completed in a single file

### What We're NOT Implementing

- ❌ Browser-based OAuth (not needed for Zai)
- ❌ Local HTTP callback server (only used by OpenCode for MCP servers)
- ❌ Plugin system (not required for initial implementation)
- ❌ Refresh tokens (Zai uses long-lived API keys)

## File Structure

```
glm-cli/
├── commands/
│   └── AuthCommand.groovy          # New file - auth command implementation
├── core/
│   └── Auth.groovy                 # New file - auth storage and retrieval
├── models/
│   └── Auth.groovy                 # New file - auth data models
├── docs/
│   └── AUTH_IMPLEMENTATION.md       # New file - this document
└── glm.groovy                      # Update - add auth command handler
```

## Implementation Steps

### Phase 1: Core Auth Storage

#### 1.1 Create Auth Data Models
**File**: `models/Auth.groovy`

```groovy
package models

/**
 * Authentication credential types
 */
class AuthCredential {
    String type              // "api", "oauth", "wellknown"
    String key              // API key (for type="api")
    String provider          // Provider ID (e.g., "zai", "zhipu")
    Long timestamp          // When credential was created
    Map extra              // Additional fields for extensibility
}
```

**Purpose**: Define authentication credential structure

**Dependencies**: None

**Testing**: Create test with sample credential JSON

#### 1.2 Create Auth Storage/Retrieval
**File**: `core/Auth.groovy`

```groovy
package core

import java.nio.file.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/**
 * Authentication storage and retrieval
 */
class Auth {
    private static final String AUTH_DIR = ".glm"
    private static final String AUTH_FILE = "auth.json"
    private static final File AUTH_PATH

    static {
        def homeDir = System.getProperty("user.home")
        AUTH_PATH = new File(homeDir, AUTH_DIR + "/" + AUTH_FILE)
    }

    /**
     * Store a credential for a provider
     */
    static void set(String provider, AuthCredential credential) {
        ensureAuthDir()
        def existing = all()
        existing[provider] = [
            type: credential.type,
            key: credential.key,
            provider: credential.provider,
            timestamp: System.currentTimeMillis()
        ]

        AUTH_PATH.text = JsonOutput.toJson(existing)
        AUTH_PATH.setReadable(true, true)
        AUTH_PATH.setWritable(true, true)
    }

    /**
     * Retrieve a credential for a provider
     */
    static AuthCredential get(String provider) {
        def all = all()
        def cred = all[provider]
        return cred ? new AuthCredential(
            type: cred.type,
            key: cred.key,
            provider: provider,
            timestamp: cred.timestamp
        ) : null
    }

    /**
     * Get all stored credentials
     */
    static Map<String, Map> all() {
        if (!AUTH_PATH.exists()) {
            return [:]
        }

        try {
            def slurper = new JsonSlurper()
            return slurper.parse(AUTH_PATH) as Map
        } catch (Exception e) {
            return [:]
        }
    }

    /**
     * Remove a credential for a provider
     */
    static void remove(String provider) {
        def existing = all()
        existing.remove(provider)
        AUTH_PATH.text = JsonOutput.toJson(existing)
    }

    /**
     * Check if a credential exists
     */
    static boolean has(String provider) {
        return get(provider) != null
    }

    /**
     * Clear all credentials
     */
    static void clear() {
        if (AUTH_PATH.exists()) {
            AUTH_PATH.delete()
        }
    }

    private static void ensureAuthDir() {
        def authDir = AUTH_PATH.parentFile
        if (!authDir.exists()) {
            authDir.mkdirs()
        }
    }
}
```

**Purpose**: Handle all credential storage operations

**Key Features**:
- JSON storage in `~/.glm/auth.json`
- Secure file permissions (owner read/write only)
- CRUD operations for credentials
- Error handling for malformed JSON

**Testing**:
- Test storing credentials
- Test retrieving credentials
- Test removing credentials
- Test file permissions
- Test error handling

### Phase 2: Auth Command Implementation

#### 2.1 Create Auth Command
**File**: `commands/AuthCommand.groovy`

```groovy
package commands

import core.Auth
import models.AuthCredential

/**
 * Authentication command implementation
 *
 * Usage:
 *   glm auth login [provider]     - Login to a provider
 *   glm auth logout [provider]     - Logout from a provider
 *   glm auth list                  - List all credentials
 */
class AuthCommand {

    // Provider configuration
    private static final Map PROVIDER_CONFIG = [
        zai: [
            name: "Zai/Zhipu AI",
            description: "GLM-4 and Coding Plan API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ],
        zhipu: [
            name: "Zhipu AI",
            description: "GLM-4 API",
            url: "https://open.bigmodel.cn/usercenter/apikeys"
        ]
    ]

    /**
     * Execute auth command
     */
    static void execute(String[] args) {
        if (args.length == 0) {
            printUsage()
            return
        }

        def subcommand = args[0]
        def subcommandArgs = args[1..-1] as String[]

        switch(subcommand) {
            case "login":
                handleLogin(subcommandArgs)
                break
            case "logout":
                handleLogout(subcommandArgs)
                break
            case "list":
                handleList()
                break
            case "ls":
                handleList()
                break
            default:
                println("Error: Unknown subcommand '$subcommand'")
                printUsage()
                System.exit(1)
        }
    }

    /**
     * Handle login command
     */
    static void handleLogin(String[] args) {
        println()

        // Determine provider
        def provider = args.length > 0 ? args[0] : "zai"

        // Validate provider
        if (!PROVIDER_CONFIG.containsKey(provider)) {
            println("Error: Unknown provider '$provider'")
            println("\nSupported providers: ${PROVIDER_CONFIG.keySet().join(', ')}")
            return
        }

        def config = PROVIDER_CONFIG[provider]

        // Display provider info
        println("Add credential for: ${config.name}")
        println("${config.description}")
        println()
        println("Create an API key at: ${config.url}")
        println()

        // Prompt for API key
        def console = System.console()
        if (console == null) {
            println("Error: Cannot read password - please run in an interactive terminal")
            System.exit(1)
        }

        def key = console.readLine("Enter your API key: ")
        if (key == null) {
            println("\nCancelled")
            return
        }

        key = key.trim()

        // Validate key
        if (key.isEmpty()) {
            println("Error: API key cannot be empty")
            System.exit(1)
        }

        // Store credential
        def credential = new AuthCredential(
            type: "api",
            key: key,
            provider: provider,
            timestamp: System.currentTimeMillis()
        )

        Auth.set(provider, credential)

        println()
        println("✓ Login successful")
        println("  Credential stored for: ${config.name}")
    }

    /**
     * Handle logout command
     */
    static void handleLogout(String[] args) {
        def credentials = Auth.all()

        if (credentials.isEmpty()) {
            println("No credentials found")
            return
        }

        // If provider specified, remove it
        if (args.length > 0) {
            def provider = args[0]
            if (!credentials.containsKey(provider)) {
                println("Error: No credential found for provider '$provider'")
                println("\nCredentials:")
                credentials.each { k, v ->
                    def config = PROVIDER_CONFIG[k]
                    def name = config?.name ?: k
                    println("  - $name ($k)")
                }
                return
            }

            Auth.remove(provider)
            println("✓ Removed credential for: ${provider}")
            return
        }

        // Interactive selection
        def console = System.console()
        println("Select provider to remove:")
        println()

        def providers = credentials.keySet() as List
        providers.eachWithIndex { provider, index ->
            def config = PROVIDER_CONFIG[provider]
            def name = config?.name ?: provider
            println("  ${index + 1}. $name ($provider)")
        }
        println()

        def selection = console.readLine("Enter number: ")
        if (selection == null) {
            println("\nCancelled")
            return
        }

        def index = selection.trim().toInteger() - 1
        if (index < 0 || index >= providers.size()) {
            println("Error: Invalid selection")
            return
        }

        def provider = providers[index]
        Auth.remove(provider)
        println("✓ Removed credential for: ${provider}")
    }

    /**
     * Handle list command
     */
    static void handleList() {
        def credentials = Auth.all()

        if (credentials.isEmpty()) {
            println("No credentials configured")
            return
        }

        println("Credentials (${credentials.size}):")
        println()

        credentials.each { provider, data ->
            def config = PROVIDER_CONFIG[provider]
            def name = config?.name ?: provider
            def description = config?.description ?: ""
            def type = data.type ?: "api"
            def timestamp = data.timestamp ?: 0

            println("  Provider: ${name}")
            println("    ID: ${provider}")
            println("    Type: ${type}")
            if (description) {
                println("    Description: ${description}")
            }
            if (timestamp > 0) {
                def date = new Date(timestamp)
                println("    Added: ${date}")
            }
            println("    Key: ${data.key ? '***' + data.key[-4..-1] : '(none)'}")
            println()
        }

        // Show storage location
        def homeDir = System.getProperty("user.home")
        def authPath = Auth.class.getResource('').path?.contains('core')
            ? new File(homeDir, ".glm/auth.json").absolutePath
            : "~/.glm/auth.json"
        println("Credentials file: ${authPath}")
    }

    /**
     * Print usage information
     */
    static void printUsage() {
        println("Usage: glm auth <command> [options]")
        println()
        println("Commands:")
        println("  login [provider]     Login to a provider (default: zai)")
        println("  logout [provider]     Logout from a provider")
        println("  list, ls              List all configured credentials")
        println()
        println("Providers:")
        PROVIDER_CONFIG.each { id, config ->
            println("  ${id.padRight(10)} - ${config.name}")
            println("               ${config.description}")
        }
    }
}
```

**Purpose**: Implement CLI auth commands

**Key Features**:
- Login with provider selection
- Logout with interactive selection
- List all credentials (with masked keys)
- Provider information display
- Helpful error messages
- Unicode checkmarks for success

**Testing**:
- Test login with valid key
- Test login with invalid provider
- Test login with empty key
- Test logout with provider name
- Test logout interactive selection
- Test list command
- Test error handling

### Phase 3: CLI Integration

#### 3.1 Update Main CLI
**File**: `glm.groovy`

```groovy
#!/usr/bin/env jbang

// ... existing imports ...

import commands.AuthCommand

// ... existing code ...

def main(String[] args) {
    if (args.length == 0) {
        printUsage()
        return
    }

    def command = args[0]
    def commandArgs = args[1..-1] as String[]

    // ... existing command handling ...

    switch(command) {
        // ... existing cases ...

        case "auth":
            AuthCommand.execute(commandArgs)
            break

        // ... existing cases ...

        default:
            println("Error: Unknown command '$command'")
            printUsage()
            System.exit(1)
    }
}

// ... existing code ...
```

**Purpose**: Integrate auth command into main CLI

**Changes**: Add `auth` case to command switch

**Testing**:
- Test `glm auth login`
- Test `glm auth logout`
- Test `glm auth list`
- Test error messages

### Phase 4: Integration with Existing Components

#### 4.1 Update GLMClient for Auth
**File**: `core/GlmClient.groovy`

Add authentication support:

```groovy
class GlmClient {
    private String apiKey

    GlmClient() {
        // Try to load from auth store
        def credential = Auth.get("zai")
        if (credential != null) {
            this.apiKey = credential.key
        }

        // Fallback to environment variable
        if (this.apiKey == null) {
            this.apiKey = System.getenv("ZAI_API_KEY")
        }
    }

    GlmClient(String apiKey) {
        this.apiKey = apiKey
    }

    // ... existing methods ...
}
```

**Priority**: Medium - can be done after initial auth implementation

#### 4.2 Update Config.groovy
**File**: `core/Config.groovy`

Add auth configuration support:

```groovy
class Config {
    // ... existing code ...

    /**
     * Get API key for a provider
     */
    static String getApiKey(String provider) {
        // Check auth store first
        def credential = Auth.get(provider)
        if (credential != null && credential.key) {
            return credential.key
        }

        // Fall back to environment variables
        def envVar = provider.toUpperCase() + "_API_KEY"
        return System.getenv(envVar)
    }
}
```

**Priority**: Medium - can be done after initial auth implementation

## Security Considerations

### File Permissions
- Auth file should only be readable/writable by owner
- Use `setReadable(true, true)` and `setWritable(true, true)`

### Key Masking
- Never display full API keys in output
- Show only last 4 characters: `***sk-1234`

### Input Validation
- Validate provider names (alphanumeric, hyphens)
- Validate key is not empty
- Sanitize user input before storage

### Error Handling
- Don't expose file paths in error messages
- Use generic error messages for auth failures
- Log detailed errors for debugging

## Testing Plan

### Unit Tests
1. **Auth Storage Tests**
   - Test storing single credential
   - Test storing multiple credentials
   - Test retrieving credential
   - Test removing credential
   - Test clearing all credentials
   - Test file permissions
   - Test error handling for corrupted JSON

2. **Auth Command Tests**
   - Test login with valid key
   - Test login with invalid provider
   - Test login with empty key
   - Test logout with specific provider
   - Test logout interactive selection
   - Test list command output
   - Test error messages

### Integration Tests
1. **CLI Integration Tests**
   - Test `glm auth login` command
   - Test `glm auth logout` command
   - Test `glm auth list` command
   - Test auth credential usage in GLMClient
   - Test environment variable fallback

2. **End-to-End Tests**
   - Login to zai provider
   - Use credential to make API call
   - Logout and verify credential removed
   - Re-login and verify previous credential replaced

## Success Criteria

### Minimum Viable Product (MVP)
- [x] Users can run `glm auth login`
- [x] Users can enter Zai API key
- [x] Credentials stored in `~/.glm/auth.json`
- [x] Users can run `glm auth list` to see credentials
- [x] Users can run `glm auth logout` to remove credentials
- [x] GLMClient can use stored credentials
- [x] Environment variable fallback works

### Stretch Goals (Post-MVP)
- [ ] Support for multiple providers
- [ ] Credential encryption (if needed)
- [ ] Credential validation against Zai API
- [ ] OAuth support (if Zai provides it)
- [ ] Interactive provider selection
- [ ] Credential expiry handling

## Implementation Timeline

### Week 1: Core Storage
- Day 1-2: Implement `models/Auth.groovy`
- Day 3-4: Implement `core/Auth.groovy`
- Day 5: Unit tests for auth storage

### Week 2: Command Implementation
- Day 1-3: Implement `commands/AuthCommand.groovy`
- Day 4: Update `glm.groovy` for CLI integration
- Day 5: Unit tests for auth command

### Week 3: Integration & Testing
- Day 1-2: Integrate with GLMClient
- Day 3-4: Integration testing
- Day 5: Documentation and final polish

## Documentation Requirements

### User Documentation
1. **README.md Updates**
   - Add authentication section
   - Document `glm auth` commands
   - Provide Zai API key setup instructions
   - Troubleshooting guide

2. **USAGE.md Updates**
   - Auth command usage
   - Examples for each subcommand
   - Configuration options

3. **AUTH.md** (New File)
   - Authentication architecture
   - Credential storage format
   - Security considerations
   - Extending to new providers

### Developer Documentation
1. **CONTRIBUTING.md Updates**
   - How to add new providers
   - Auth storage best practices
   - Testing guidelines

2. **API Documentation**
   - Auth class API
   - AuthCredential model
   - Provider config format

## Rollout Plan

### Phase 1: Internal Testing
- Test with development team
- Validate Zai API key authentication
- Fix bugs and UX issues

### Phase 2: Beta Release
- Release as beta feature
- Gather user feedback
- Monitor for issues

### Phase 3: General Release
- Document in release notes
- Update README
- Mark as stable feature

## Future Enhancements

1. **Multiple Providers**
   - Add OpenAI, Anthropic, Google
   - Support per-project provider selection

2. **Credential Encryption**
   - Encrypt stored keys with user password
   - Use OS keychain if available

3. **OAuth Support**
   - If Zai provides OAuth
   - Browser-based authentication
   - Refresh token handling

4. **Credential Validation**
   - Validate API keys against provider API
   - Show credential health status

5. **Project-Specific Credentials**
   - Store credentials in project config
   - Override global credentials
   - Secure sharing via `.env`

## Open Questions

1. Should we support credential encryption? (Defer until security audit)
2. Should we add credential validation? (Phase 2 enhancement)
3. Should credentials be per-project or global? (Start global, add project later)
4. Should we support OAuth for Zai? (Only if Zai provides it)

## References

- SST OpenCode auth implementation: https://github.com/sst/opencode
- Zhipu AI API docs: https://open.bigmodel.cn/dev/api
- GLM-4 model documentation: https://open.bigmodel.cn/dev/howuse/coding
- JBang documentation: https://www.jbang.dev/documentation/guide/latest/intro.html

---

**Document Version**: 1.0
**Last Updated**: 2025-12-31
**Status**: Ready for Implementation
