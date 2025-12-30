# Style Guide for GLM-CLI

This document outlines the coding conventions and style guidelines for contributing to GLM-CLI.

## Core Principles

- **Keep things simple** - Single responsibility for functions and classes
- **Minimize complexity** - Avoid unnecessary destructuring and control flow
- **Prefer readability** - Clear, maintainable code over clever tricks
- **Be consistent** - Follow established patterns throughout the codebase

## Code Style Guidelines

### General Conventions

- **DO NOT** do unnecessary destructuring of variables
- **DO NOT** use `else` statements unless necessary
- **DO NOT** use `try`/`catch` if it can be avoided
- **AVOID** `try`/`catch` where possible
- **AVOID** `else` statements - use guard clauses instead
- **AVOID** using `any` type - use explicit types where important
- **AVOID** `let` statements (JavaScript/Groovy equivalent) when possible
- **PREFER** single word variable names where appropriate
- **PREFER** early returns over nested conditionals

### Groovy-Specific Guidelines

#### Type Annotations

Use explicit type annotations for:
- Method signatures and public APIs
- Class fields
- Function parameters
- Return types

```groovy
// Good
String execute(Map<String, Object> args) {
    // implementation
}

// Avoid where clear from context
def value = getValue() // OK for internal use
```

#### Groovy Dynamic Typing

Leverage Groovy's dynamic typing where appropriate:
- Use dynamic types for rapid iteration
- Use explicit types for stable public APIs
- Use `Map<String, Object>` for flexible tool arguments

### File Organization

| Directory | Purpose |
|-----------|---------|
| `glm.groovy` | Entry point - declares dependencies and sources |
| `commands/` | CLI command implementations (Picocli-based) |
| `core/` | Core business logic (GLM client, agent, config) |
| `models/` | Data transfer objects and DTOs |
| `tools/` | Tool implementations for agent capabilities |

### Package Structure

All source files must declare their package at the top:

```groovy
package commands
package core
package models
package tools
```

### Imports

**Group imports logically:**
1. Java standard library (`java.*`)
2. Third-party libraries (alphabetical)
3. Project packages

```groovy
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.file.Files

import com.fasterxml.jackson.databind.ObjectMapper
import com.auth0.jwt.JWT

import models.ChatRequest
import core.Agent
```

**Import Guidelines:**
- Avoid wildcard imports unless necessary
- Group related imports together
- Remove unused imports

### Naming Conventions

| Element | Convention | Example |
|---------|-------------|---------|
| **Classes** | PascalCase | `GlmClient`, `ChatCommand`, `ReadFileTool` |
| **Methods** | camelCase | `sendMessage`, `registerTool`, `getName` |
| **Constants** | UPPER_SNAKE_CASE | `BASE_URL`, `EXPIRATION_SECONDS` |
| **Fields** | camelCase, private | `apiKey`, `client` |
| **Interfaces** | PascalCase | `Tool`, `Configurable` |
| **Packages** | lowercase | `core`, `commands`, `tools` |

### Class Design

#### Field Modifiers

- Use `private` for non-public fields
- Use `final` for fields that should not be reassigned
- Provide appropriate constructors

```groovy
class GlmClient {
    private final String apiKey
    private final HttpClient client

    GlmClient(String apiKey) {
        this.apiKey = apiKey
        this.client = HttpClient.newBuilder().build()
    }
}
```

#### Constructors

- Provide default constructors for JSON deserialization
- Use overloaded constructors for convenience

```groovy
class Message {
    String role
    String content

    // Default constructor for JSON
    Message() {}

    // Convenience constructor
    Message(String role, String content) {
        this.role = role
        this.content = content
    }
}
```

#### Nested Classes

Use static inner classes for nested data structures:

```groovy
class Message {
    String role
    String content
    List<ToolCall> toolCalls

    static class ToolCall {
        String id
        String type
        Function function
    }
}
```

### Annotations

#### Jackson Annotations (JSON Mapping)

```groovy
// Rename JSON field
@JsonProperty("field_name")
private String fieldName

// Ignore unknown JSON fields
@JsonIgnoreProperties(ignoreUnknown = true)

// Exclude null values from serialization
@JsonInclude(JsonInclude.Include.NON_NULL)
```

#### Picocli Annotations (CLI Commands)

```groovy
// Command class
@Command(name = "chat", description = "Start a chat session")
class ChatCommand implements Runnable { }

// Command-line options
@Option(names = ["-m", "--model"], description = "Model to use")
String model = "glm-4-flash"

// Positional arguments
@Parameters(index = "0", arity = "0..1", description = "Initial message")
String initialMessage
```

### Error Handling

#### Exception Types

- Use `IllegalArgumentException` for invalid input validation
- Use `RuntimeException` for general runtime errors
- Use checked exceptions only when necessary

#### Error Patterns

```groovy
// Tool execution: return error strings instead of throwing
Object execute(Map<String, Object> args) {
    try {
        return doWork(args)
    } catch (Exception e) {
        return "Error: ${e.message}"
    }
}

// Input validation: throw early
if (apiKey == null || apiKey.isEmpty()) {
    throw new IllegalArgumentException("API key cannot be null or empty")
}
```

#### Try-Catch Guidelines

- Catch specific exceptions when possible
- Provide meaningful error messages
- Use early returns to avoid deep nesting

```groovy
// Good
try {
    result = process()
} catch (TimeoutException e) {
    return "Error: Operation timed out"
} catch (IOException e) {
    return "Error: Failed to read file"
}
```

### Synchronous and Asynchronous Patterns

#### Synchronous Requests

```groovy
// Blocking call
String response = client.sendMessage(request)
```

#### Streaming with Callbacks

```groovy
// Async streaming with closure
client.streamMessage(request) { ChatResponse chunk ->
    // Process each chunk
    if (chunk.choices && !chunk.choices.isEmpty()) {
        print chunk.choices[0].delta.content
    }
}
```

### Configuration

- Config is loaded from `~/.glm/config.toml` using `Config.load()`
- Fallback to environment variables (e.g., `ZAI_API_KEY`)
- Use static factory methods for config loading

```groovy
Config config = Config.load()
String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
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

// Source files
//SOURCES commands/GlmCli.groovy
//SOURCES core/Agent.groovy
// ...
```

### Comment Style

#### Documentation Comments

Use JavaDoc-style comments for public methods and classes:

```groovy
/**
 * Run the agent with a user prompt.
 * This handles the ReAct loop: Think -> Act -> Observe -> Think
 *
 * @param prompt The user's request
 */
void run(String prompt) {
    // implementation
}
```

#### Inline Comments

- Keep inline comments minimal and meaningful
- Comment "why", not "what"
- Delete code instead of commenting it out

```groovy
// Good
// Validate path to prevent directory traversal attacks
Path path = Paths.get(pathStr).normalize()

// Avoid
// Check if path is valid
Path path = Paths.get(pathStr).normalize()
```

### Safety and Security

#### File Operations

- Validate file paths before operations
- Use `Paths.get(path).normalize()` for path handling
- Restrict operations to project directory

```groovy
Path path = Paths.get(pathStr).normalize()
if (!path.startsWith(projectRoot)) {
    throw new SecurityException("Path outside project directory")
}
```

#### API Keys and Secrets

- Never log or expose API keys
- Use environment variables or config files for sensitive data
- Mask secrets in error messages

```groovy
// Good
println "Using API key: ${apiKey.take(8)}..."

// Bad
println "Using API key: ${apiKey}"
```

### Type Safety

#### Dynamic vs Static Typing

```groovy
// Use dynamic for internal, short-lived variables
def result = process(input)

// Use explicit types for public APIs and parameters
String execute(Map<String, Object> args) {
    // ...
}

// Use Map for flexible tool arguments
Object execute(Map<String, Object> args) {
    String path = args.get("path")
    // ...
}
```

#### Collection Types

```groovy
// Use explicit generic types
List<Tool> tools = []
Map<String, Object> parameters = [:]

// Avoid raw types where possible
// Bad: def tools = []
// Good: List<Tool> tools = []
```

### Code Patterns

#### Guard Clauses

```groovy
// Good - guard clause
if (apiKey == null || apiKey.isEmpty()) {
    throw new IllegalArgumentException("API key required")
}
// ... continue with valid apiKey

// Avoid - nested if
if (apiKey != null && !apiKey.isEmpty()) {
    // ... lots of indentation
}
```

#### Early Returns

```groovy
// Good
Object execute(Map<String, Object> args) {
    if (!args.containsKey("path")) {
        return "Error: path is required"
    }
    // ... continue
}

// Avoid - deeply nested conditions
Object execute(Map<String, Object> args) {
    if (args.containsKey("path")) {
        if (isValid(args.get("path"))) {
            // ... deep nesting
        }
    }
}
```

### Testing Guidelines

#### Test Structure

When tests are added, use a Groovy testing framework like Spock or JUnit:

```groovy
class GlmClientSpec extends Specification {
    def "send message returns response"() {
        given: "a client with API key"
        def client = new GlmClient("test-key")

        when: "sending a message"
        def response = client.sendMessage(request)

        then: "response is valid"
        response != null
        response.choices != null
    }
}
```

#### Test Naming

- Use descriptive test names
- Separate test phases (given, when, then)

### Linting and Formatting

When linting tools are available:
- Run linter before committing
- Fix all linting errors
- Format code consistently

## Examples

### Good Code

```groovy
class ReadFileTool implements Tool {
    @Override
    String getName() { "read_file" }

    @Override
    String getDescription() { "Read file contents" }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [type: "string", description: "File path"]
            ],
            required: ["path"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get("path")
        if (pathStr == null) {
            return "Error: path is required"
        }

        try {
            Path path = Paths.get(pathStr).normalize()
            return Files.readString(path)
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }
}
```

### Code to Avoid

```groovy
// Too many else statements
def execute(args) {
    if (args.path) {
        // ...
    } else {
        if (args.url) {
            // ...
        } else {
            // deeply nested
        }
    }
}

// Better: use guard clauses
def execute(args) {
    if (!args.path) return "Error: path required"

    try {
        return doWork(args)
    } catch (Exception e) {
        return "Error: ${e.message}"
    }
}
```

## Resources

- [Groovy Style Guide](http://groovy-lang.org/style-guide.html)
- [Effective Java](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)
- [SST OpenCode Style Guide](https://github.com/sst/opencode/blob/dev/STYLE_GUIDE.md) - Inspiration
