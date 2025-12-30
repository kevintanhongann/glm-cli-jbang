# Tools Reference

This document provides comprehensive documentation for all available tools in GLM-CLI's agent system.

## Overview

Tools are the primary mechanism by which the agent interacts with your codebase. Each tool:
- Has a specific name and description
- Exposes a JSON Schema for parameters
- Returns structured results (success, error, or partial)
- Implements safety checks (where applicable)
- Follows the [Tool](./tools/Tool.groovy) interface

## Built-in Tools

### read_file

**Purpose**: Read the content of a file at a specified path.

**Permission Level**: Always allowed

#### Parameters

| Name | Type | Required | Description |
|------|-------|-----------|-------------|
| `path` | string | Yes | The path to the file to read |

#### Examples

```groovy
// Read a Groovy file
read_file(path: "src/main.groovy")

// Read a README
read_file(path: "README.md")

// Read a nested file
read_file(path: "models/ChatRequest.groovy")
```

#### Returns

**Success**: File content as string
```
Successfully read 123 lines from src/main.groovy
class Main {
    static void main(String[] args) {
        println("Hello, World!")
    }
}
```

**Error**: Clear error message
```
Error: File not found at src/nonexistent.groovy
```

**Partial Success**: Content with truncation notice
```
Read 45 lines (file truncated at 1000 lines limit)
[content truncated...]
```

#### Implementation Details

- Uses `Files.readString()` from `java.nio.file`
- Returns error if file doesn't exist
- No line limit (full file returned)
- Supports all text file formats

#### Use Cases

- Reading source code for analysis
- Checking configuration files
- Reviewing documentation
- Examining test files

---

### write_file

**Purpose**: Write or create files at a specified path with diff preview and user confirmation.

**Permission Level**: Requires user confirmation (safety check)

#### Parameters

| Name | Type | Required | Description |
|------|-------|-----------|-------------|
| `path` | string | Yes | The path to the file to write |
| `content` | string | Yes | The content to write to the file |

#### Examples

```groovy
// Create a new file
write_file(
    path: "src/Hello.groovy",
    content: "class Hello {\n    static void main(String[] args) {\n        println('Hello!')\n    }\n}"
)

// Overwrite existing file (shows diff)
write_file(
    path: "README.md",
    content: "# Updated Content\n\nNew description here."
)
```

#### Safety Features

1. **Diff Preview**: Shows changes before writing
2. **User Confirmation**: Requires explicit Y/n approval
3. **Path Validation**: Normalized paths, prevents directory traversal
4. **New File Detection**: Shows full content for new files

#### Diff Preview Format

```
--- Proposed Changes for src/User.groovy ---
Original: class User {
New:      class User extends BaseEntity {
---------------------------------------
Original:
New:      private String id
---------------------------------------
---------------------------------------
Allow write? [y/N]:
```

#### Returns

**Success**: Confirmation message
```
Successfully wrote 23 lines to src/User.groovy
```

**Denied**: Confirmation of denial
```
Action denied by user.
```

**Error**: Error details
```
Error: Unable to write file: Permission denied
```

#### Implementation Details

- Uses [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) for diffs
- `Files.writeString()` for writing
- Atomic write operation (or fails gracefully)
- Validates path is within project directory

#### Configuration

Control behavior via `safety_mode` in config:

```toml
[behavior]
safety_mode = "ask"        # Ask before each write (default)
# safety_mode = "always_allow" # Write without confirmation
# safety_mode = "strict"       # Deny all writes
```

#### Use Cases

- Creating new files
- Updating existing code
- Adding tests
- Modifying configuration
- Refactoring with AI assistance

---

### list_files

**Purpose**: List the contents of a directory.

**Permission Level**: Always allowed

#### Parameters

| Name | Type | Required | Default | Description |
|------|-------|-----------|---------|-------------|
| `directory` | string | Yes | - | The directory path to list |
| `recursive` | boolean | No | false | Whether to list files recursively |

#### Examples

```groovy
// List current directory
list_files(directory: ".")

// List with recursion
list_files(directory: "src", recursive: true)

// List specific subdirectory
list_files(directory: "models/")
```

#### Returns

**Flat directory listing**:
```
commands/
core/
models/
tools/
glm.groovy
README.md
```

**Recursive listing**:
```
commands/
  GlmCli.groovy
  ChatCommand.groovy
  AgentCommand.groovy
core/
  Agent.groovy
  Config.groovy
  GlmClient.groovy
models/
  ChatRequest.groovy
  ChatResponse.groovy
  Message.groovy
tools/
  Tool.groovy
  ReadFileTool.groovy
  WriteFileTool.groovy
  ListFilesTool.groovy
```

**Error**: Clear error message
```
Error: Directory not found: /invalid/path
```

#### Implementation Details

- Uses `Files.list()` for non-recursive
- Uses `Files.walk()` for recursive
- Sorted output for consistency
- Returns subdirectories and files

#### Use Cases

- Exploring project structure
- Finding files before reading
- Understanding directory layout
- Locating test files
- Checking for generated files

---

### web_search

**Purpose**: Search the web for current information, news, documentation, or facts.

**Permission Level**: Always allowed

#### Parameters

| Name | Type | Required | Default | Description |
|------|-------|-----------|---------|-------------|
| `search_query` | string | Yes | - | The search query string |
| `count` | integer | No | 10 | Number of results to return (1-50) |
| `search_domain_filter` | string | No | - | Filter results to specific domain (e.g., www.github.com, spring.io) |
| `search_recency_filter` | string | No | noLimit | Time-based filter: noLimit, 1d, 1w, 1m, 1y |

#### Examples

```groovy
// Basic search
web_search(search_query: "Java 21 features")

// Search with result count limit
web_search(search_query: "Groovy latest version", count: 5)

// Search specific domain
web_search(search_query: "Spring Boot", search_domain_filter: "spring.io")

// Search recent content
web_search(search_query: "AI news", search_recency_filter: "1w")

// Combined filters
web_search(search_query: "documentation", search_domain_filter: "docs.python.org", count: 3)
```

#### Returns

**Success**: Formatted search results with title, snippet, URL, and metadata

```
Found 5 results:

1. Java 21: What's New
   Java 21 introduces pattern matching, virtual threads, and more...
   URL: https://example.com/java21
   Published: 2025-09-20
   Source: Java Magazine

2. Virtual Threads in Java 21
   Virtual threads allow millions of concurrent operations...
   URL: https://example.com/virtual-threads
   Published: 2025-09-18
   Source: Tech Blog

3. Pattern Matching for Switch
   Enhanced pattern matching makes code more concise...
   URL: https://example.com/pattern-matching
   Published: 2025-09-15
   Source: Oracle

...
```

**Error**: Clear error message

```
Error performing web search: Failed to connect to API
```

**No Results**: Informative message

```
No search results found for query: 'xyz123abc'
```

**Validation Error**: Input validation failure

```
Error: count must be between 1 and 50
```

#### Implementation Details

- **API**: Uses Z.AI Web Search API
- **Response**: Returns structured results with title, content, link, media, publish_date, icon
- **Domain Filtering**: Restricts results to specified domain
- **Time Filtering**: Supports noLimit, 1d, 1w, 1m, 1y options
- **Result Limiting**: Configurable count (1-50, default: 10)
- **Content Truncation**: Summarizes content to 200 characters for readability
- **Error Handling**: Validates parameters and handles network/API errors gracefully

#### Use Cases

- **Research Current Information**: Finding recent documentation or news
- **Package Version Lookup**: Checking latest package versions
- **Breaking Changes**: Looking for recent API changes
- **Trend Research**: Researching current trends in technology
- **Documentation Search**: Finding official documentation for libraries/frameworks
- **Bug Investigation**: Searching for known issues and solutions
- **Learning Resources**: Finding tutorials and examples
- **Competitive Analysis**: Researching competitors' features

#### Best Practices

1. **Be Specific**: Use precise search terms for better results
   ```
   Good: "Java 21 virtual threads performance"
   Poor: "Java threads"
   ```

2. **Use Recency Filters**: For time-sensitive queries
   ```
   web_search(search_query: "latest news", search_recency_filter: "1w")
   ```

3. **Limit Domain**: When looking for official documentation
   ```
   web_search(search_query: "Spring Security", search_domain_filter: "spring.io")
   ```

4. **Adjust Count**: Balance between comprehensiveness and context usage
   ```
   Quick overview: count=3
   Comprehensive: count=10
   ```

5. **Combine with Other Tools**: Search then fetch or summarize
   ```groovy
   // Agent workflow: Search → Read → Summarize
   web_search(search_query: "REST API best practices", count: 5)
   webfetch(url: result_link)
   write_file(path: "best_practices.md", content: summary)
   ```

---

## Tool Interface

All tools must implement the `Tool` interface:

```groovy
package tools

interface Tool {
    String getName()
    String getDescription()
    Map<String, Object> getParameters()
    Object execute(Map<String, Object> args)
}
```

### Method Specifications

#### getName()

Returns the unique identifier for the tool.

- **Type**: `String`
- **Must be**: Lowercase snake_case
- **Unique across**: All registered tools
- **Used for**: Tool invocation by LLM

Example: `"read_file"`, `"write_file"`, `"bash_command"`

#### getDescription()

Returns a human-readable description of what the tool does.

- **Type**: `String`
- **Purpose**: Help LLM understand when to use the tool
- **Should include**:
  - What the tool does
  - Key parameters
  - Any important constraints

Example: `"Read the content of a file at the specified path. Returns the full file contents as a string."`

#### getParameters()

Returns the JSON Schema definition for tool parameters.

- **Type**: `Map<String, Object>`
- **Must follow**: JSON Schema draft-07 format
- **Structure**:
  ```groovy
  [
      type: "object",
      properties: [
          param_name: [
              type: "string|number|boolean|array|object",
              description: "Description of parameter",
              // Optional: enum, format, default, etc.
          ]
      ],
      required: ["param_name", ...]
  ]
  ```

#### execute()

Executes the tool with provided arguments.

- **Parameters**: `Map<String, Object> args` - The arguments from LLM
- **Returns**: `Object` - Result (string, map, list, etc.)
- **Error Handling**: Return error strings instead of throwing exceptions
- **Safety**: Validate arguments before execution

Example:
```groovy
Object execute(Map<String, Object> args) {
    try {
        String pathStr = args.get("path")
        Path path = Paths.get(pathStr).normalize()

        // Validation
        if (!path.startsWith(projectRoot)) {
            return "Error: Path outside project directory"
        }

        // Execution
        return Files.readString(path)
    } catch (Exception e) {
        return "Error: ${e.message}"
    }
}
```

## Creating Custom Tools

### Step 1: Implement Tool Interface

Create a new file in `tools/` directory:

```groovy
package tools

class MyCustomTool implements Tool {
    @Override
    String getName() {
        "my_custom_tool"
    }

    @Override
    String getDescription() {
        "Description of what this tool does"
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                param1: [
                    type: "string",
                    description: "First parameter description"
                ],
                param2: [
                    type: "number",
                    description: "Second parameter description"
                ]
            ],
            required: ["param1"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String param1 = args.get("param1")
            Number param2 = args.get("param2")

            // Validate
            if (param1 == null) {
                return "Error: param1 is required"
            }

            // Execute
            def result = doSomething(param1, param2)
            return "Success: ${result}"
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }
}
```

### Step 2: Register Tool

Register in your agent:

```groovy
Agent agent = new Agent(apiKey, model)
agent.registerTool(new MyCustomTool())
```

Or register multiple tools:

```groovy
agent.registerTools([
    new ReadFileTool(),
    new WriteFileTool(),
    new ListFilesTool(),
    new MyCustomTool()
])
```

### Step 3: Add to Source List

If creating a standalone tool, add to `glm.groovy`:

```groovy
//SOURCES tools/MyCustomTool.groovy
```

## Tool Best Practices

### 1. Always Validate Arguments

```groovy
Object execute(Map<String, Object> args) {
    // Validate required parameters
    if (!args.containsKey("path")) {
        return "Error: path is required"
    }

    // Validate parameter types
    def path = args.get("path")
    if (!(path instanceof String)) {
        return "Error: path must be a string"
    }

    // Validate parameter values
    String pathStr = (String) path
    if (pathStr.isEmpty()) {
        return "Error: path cannot be empty"
    }
}
```

### 2. Use Descriptive Error Messages

```groovy
// Good
return "Error: File not found at ${pathStr}. Please check the file path."

// Bad
return "Failed"
```

### 3. Handle Exceptions Gracefully

```groovy
try {
    return doWork(args)
} catch (FileNotFoundException e) {
    return "Error: File not found: ${e.message}"
} catch (SecurityException e) {
    return "Error: Permission denied: ${e.message}"
} catch (Exception e) {
    return "Error: ${e.message}"
}
```

### 4. Return Structured Results

```groovy
// Success
return [
    status: "success",
    lines: 123,
    path: pathStr,
    content: fileContent
]

// Error
return [
    status: "error",
    error: "File not found",
    path: pathStr
]
```

### 5. Consider Safety

```groovy
Object execute(Map<String, Object> args) {
    Path path = Paths.get(pathStr).normalize()

    // Prevent directory traversal
    if (!path.startsWith(projectRoot)) {
        return "Error: Path outside project directory not allowed"
    }

    // Prevent destructive operations
    if (isSystemPath(path)) {
        return "Error: System file modification not allowed"
    }
}
```

## Future Tools (Planned)

### grep

**Purpose**: Search for patterns in file contents.

**Parameters**:
- `pattern` (string, required): Regex pattern to search
- `path` (string): Directory to search (default: current)
- `recursive` (boolean): Search recursively (default: false)

### glob

**Purpose**: Find files matching a pattern.

**Parameters**:
- `pattern` (string, required): Glob pattern (e.g., `**/*.groovy`)
- `path` (string): Directory to search (default: current)

### bash

**Purpose**: Execute shell commands.

**Parameters**:
- `command` (string, required): Command to execute
- `directory` (string): Working directory

**Permission Level**: Asks before execution

### webfetch

**Purpose**: Fetch content from URLs.

**Parameters**:
- `url` (string, required): URL to fetch
- `format` (string): Output format (markdown/text/html)

## Tool Permissions

Tools can have different permission levels:

| Permission | Description | Example Tools |
|-----------|-------------|---------------|
| **Always Allowed** | Execute without confirmation | `read_file`, `list_files` |
| **Ask First** | Require user confirmation | `write_file`, `bash` |
| **Strict** | Only in certain modes | File deletions, system writes |

Configure permissions via:
- Config file `safety_mode` setting
- Agent-specific permission rules (future)
- Environment variables (future)

## Tool Execution Flow

```
1. Agent receives tool call from LLM
   ↓
2. Parse arguments (JSON -> Map<String, Object>)
   ↓
3. Validate arguments (type, presence, values)
   ↓
4. Check permissions (if applicable)
   ↓
5. [If write] Show diff preview
   ↓
6. [If write] Get user confirmation
   ↓
7. Execute tool logic
   ↓
8. Format result (success/error/partial)
   ↓
9. Return result to LLM as tool message
   ↓
10. LLM uses result to continue task
```

## References

- [Tool Interface](./tools/Tool.groovy)
- [Existing Tools](./tools/)
- [AGENTS.md](./AGENTS.md) - Tool calling guidelines
- [FAQ.md](./FAQ.md#how-do-i-add-custom-tools)
