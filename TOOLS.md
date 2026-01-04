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

**Purpose**: Read the complete contents of a file from the local filesystem.

**Permission Level**: Always allowed

#### Parameters

| Name | Type | Required | Default | Description |
|------|-------|-----------|---------|-------------|
| `path` | string | Yes | - | Absolute file path to read |
| `offset` | integer | No | 0 | Line number to start reading from (0-based) |
| `limit` | integer | No | all | Number of lines to read (max 2000) |

#### Examples

```groovy
// Read entire file
read_file(path: "src/main.groovy")

// Read with pagination
read_file(path: "README.md", offset: 100, limit: 50)

// Read a nested file
read_file(path: "models/ChatRequest.groovy")
```

#### Returns

**Success**: File content with line numbers
```
Showing lines 1-10 of 50 total

    1	class Main {
    2	    static void main(String[] args) {
    3	        println("Hello, World!")
    4	    }
    5	}
```

**Image file**:
```
Image file: screenshots/example.png

This is an image file (image/png). It has been attached as base64 data.

Data URL: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA... (truncated)

Image dimensions and metadata can be extracted using bash tool with 'file' or 'identify' commands.
```

**PDF file**:
```
PDF file: docs/manual.pdf

This is a PDF document (145.32 KB). It has been attached as base64 data.

Data URL: data:application/pdf;base64,JVBERi0xLjQK... (truncated)

For PDF text extraction, use bash tool with 'pdftotext' or 'pdf2txt' commands.
```

**Binary file blocked**:
```
Binary file detected: archive.zip

File exists but cannot be displayed. Binary files (archives, executables, media, etc.) are blocked for safety.
```

**Error**: Clear error message
```
Error: File not found: src/nonexistent.groovy

Suggestion: Use glob to find the file or check the path.
```

**Pagination**:
```
Showing lines 100-150 of 500 total
    100	// More code here
    101	def anotherFunction() {
...
... 350 more lines (use offset=150 to continue)
```

#### Implementation Details

- Uses `Files.readAllLines()` for text files
- Returns content in cat -n format with line numbers
- Binary file detection by extension and content analysis
- Image/PDF support with base64 encoding
- Maximum 2000 lines per read (pagination supported)
- Lines longer than 2000 characters are truncated
- Tracks file reads for concurrent modification detection
- Blocked binary extensions: zip, tar, gz, exe, dll, so, jar, class, etc.

#### Use Cases

- Reading source code for analysis
- Checking configuration files
- Reviewing documentation
- Examining test files
- Viewing image files
- Accessing PDF documents

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

**Purpose**: List the contents of a directory in tree format with optional recursive search.

**Permission Level**: Always allowed

#### Parameters

| Name | Type | Required | Default | Description |
|------|-------|-----------|---------|-------------|
| `path` | string | No | . | Directory path to list |
| `recursive` | boolean | No | false | List files recursively |

#### Examples

```groovy
// List current directory (tree format)
list_files(path: ".")

// List recursively
list_files(path: "src", recursive: true)

// List specific subdirectory
list_files(path: "models/")
```

#### Returns

**Non-recursive tree listing**:
```
└── 📁 commands/
    📄 GlmCli.groovy
    📄 ChatCommand.groovy
    📄 AgentCommand.groovy
└── 📁 core/
    📄 Agent.groovy
    📄 Config.groovy
    📄 GlmClient.groovy
└── 📄 glm.groovy
└── 📄 README.md
```

**Recursive tree listing**:
```
└── 📁 commands/
    └── 📄 GlmCli.groovy
    └── 📄 ChatCommand.groovy
    └── 📄 AgentCommand.groovy
└── 📁 core/
    └── 📄 Agent.groovy
    └── 📁 models/
        └── 📄 ChatRequest.groovy
        └── 📄 ChatResponse.groovy
    └── 📄 Config.groovy
```

**Error**: Clear error message
```
Error: Directory not found: /invalid/path
```

#### Implementation Details

- Uses `Files.walk()` with depth control
- Tree-style formatting with indentation
- Automatically ignores common directories: node_modules, .git, .svn, .hg, dist, build, target, out
- Sorts: directories before files, alphabetically within each group
- Maximum 100 files displayed (shows truncation message if exceeded)
- Emoji icons: 📁 for directories, 📄 for files

#### Use Cases

- Exploring project structure
- Finding files before reading
- Understanding directory layout
- Locating test files
- Checking for generated files

---

### edit

**Purpose**: Performs precise string replacements in files with multiple matching strategies.

**Permission Level**: Requires user confirmation (safety check)

#### Parameters

| Name | Type | Required | Default | Description |
|------|-------|-----------|---------|-------------|
| `filePath` | string | Yes | - | Absolute path to the file to modify |
| `oldString` | string | Yes | - | Text to replace |
| `newString` | string | Yes | - | Text to replace it with (must be different from oldString) |
| `replaceAll` | boolean | No | false | Replace all occurrences of oldString |

#### Examples

```groovy
// Simple exact match replacement
edit(
    filePath: "src/User.groovy",
    oldString: "private String name",
    newString: "private String username"
)

// Replace all occurrences
edit(
    filePath: "config.properties",
    oldString: "localhost",
    newString: "production.example.com",
    replaceAll: true
)
```

#### Matching Strategies

The tool tries multiple strategies in order:

1. **Simple** - Exact string match
2. **LineTrimmed** - Ignores line indentation
3. **WhitespaceNormalized** - Normalizes spaces and tabs
4. **IndentationFlexible** - Removes consistent indentation

#### Safety Features

1. **Diff Preview**: Shows changes before applying
2. **User Confirmation**: Requires explicit Y/n approval
3. **Multiple Strategies**: Tries different matching approaches
4. **Concurrent Modification Check**: Prevents lost updates

#### Returns

**Success**: Number of lines changed
```
Successfully edited src/User.groovy: 1 line changed
```

**Error**: Matching failure
```
Error: oldString not found in file. Tried Simple and LineTrimmed strategies.
```

#### Use Cases

- Updating function implementations
- Modifying configuration values
- Refactoring specific code sections
- Making targeted edits

---

### multiedit

**Purpose**: Performs multiple edits to a single file atomically.

**Permission Level**: Requires user confirmation (safety check)

#### Parameters

| Name | Type | Required | Description |
|------|-------|-----------|-------------|
| `filePath` | string | Yes | Absolute path to the file to modify |
| `edits` | array | Yes | Array of edit operations (each with oldString, newString, replaceAll) |

#### Examples

```groovy
// Multiple related edits
multiedit(
    filePath: "src/User.groovy",
    edits: [
        [oldString: "private String name", newString: "private String username"],
        [oldString: "public String getName()", newString: "public String getUsername()"],
        [oldString: "this.name", newString: "this.username"]
    ]
)
```

#### Behavior

- All edits must succeed or none are applied (atomic)
- Edits are applied sequentially in the order provided
- Uses same matching strategies as edit tool

#### Safety Features

1. **Atomic Operations**: All edits succeed or none
2. **Validation**: Checks all edits before applying
3. **Diff Preview**: Shows all changes together
4. **User Confirmation**: Single approval for all edits

#### Returns

**Success**: Summary of edits applied
```
Successfully applied 3 edits to src/User.groovy (5 total lines changed)
```

**Error**: Edit failure
```
Error: Edit 2 - oldString not found. No changes were applied.
```

#### Use Cases

- Refactoring with multiple related changes
- Batch updating similar code patterns
- Complex multi-part modifications

---

### patch

**Purpose**: Applies a unified diff patch to modify multiple files.

**Permission Level**: Requires user confirmation (safety check)

#### Parameters

| Name | Type | Required | Description |
|------|-------|-----------|-------------|
| `patchText` | string | Yes | Full patch text in unified diff format |

#### Examples

```groovy
// Apply a git diff
patch(
    patchText: '''--- a/file.txt
+++ b/file.txt
@@ -1,3 +1,4 @@
 old line
+new line'''
)
```

#### Supported Operations

- **add**: Create new files
- **update**: Modify existing files
- **delete**: Remove files
- **move**: Rename/move files

#### Safety Features

1. **Preview**: Shows summary before applying
2. **User Confirmation**: Explicit approval required
3. **Multi-file Safety**: Tracks all file changes
4. **Rollback**: No changes if any operation fails

#### Returns

**Success**: Summary of applied changes
```
Successfully applied patch to 3 file(s):
  📝 add: src/NewFeature.groovy (25 lines)
  ✏️ update: src/Existing.groovy (5 lines)
  🗑️ delete: src/Old.groovy
```

**Error**: Parse or application failure
```
Error: Some patch operations failed.

src/missing.groovy: File not found
```

#### Use Cases

- Applying git diff output
- Reverting changes from patches
- Applying changes from external tools
- Bulk multi-file modifications

---

### lsp

**Purpose**: Language Server Protocol operations for code intelligence.

**Permission Level**: Always allowed

#### Parameters

| Name | Type | Required | Description |
|------|-------|-----------|-------------|
| `operation` | string | Yes | LSP operation (goToDefinition, findReferences, hover, documentSymbol, workspaceSymbol) |
| `filePath` | string | Yes | Absolute path to the file |
| `position` | object | No* | Cursor position with line and character (0-indexed) |
| `query` | string | No* | Search query (for workspaceSymbol) |

* Required for specific operations (see below)

#### Examples

```groovy
// Find where symbol is defined
lsp(
    operation: "goToDefinition",
    filePath: "src/User.groovy",
    position: [line: 10, character: 5]
)

// Find all references
lsp(
    operation: "findReferences",
    filePath: "src/User.groovy",
    position: [line: 10, character: 5]
)

// Get documentation
lsp(
    operation: "hover",
    filePath: "src/User.groovy",
    position: [line: 10, character: 5]
)

// List symbols in file
lsp(
    operation: "documentSymbol",
    filePath: "src/User.groovy"
)

// Search workspace
lsp(
    operation: "workspaceSymbol",
    filePath: "src/User.groovy",
    query: "UserService"
)
```

#### Supported Operations

| Operation | Required Params | Description |
|-----------|----------------|-------------|
| `goToDefinition` | position | Find where a symbol is defined |
| `findReferences` | position | Find all usages of a symbol |
| `hover` | position | Get documentation and type information |
| `documentSymbol` | none | Get all symbols in current file |
| `workspaceSymbol` | query | Search symbols across all files |

#### Requirements

- LSP must be enabled in configuration
- Language server must be configured for the file type
- File must be part of a valid project

#### Returns

**goToDefinition**:
```
Found 1 definition(s):

1. file:///home/user/src/UserService.groovy at line 5
```

**findReferences**:
```
Found 3 reference(s):

1. file:///home/user/src/Controller.groovy at line 20
2. file:///home/user/src/Service.groovy at line 15
3. file:///home/user/src/Tests.groovy at line 8
```

**hover**:
```
Returns the full class documentation with type information, parameters, and return values.
```

**documentSymbol**:
```
Found 8 symbol(s) in file:

  class UserService (line 5)
  private String id (line 7)
  public void createUser() (line 10)
  public void deleteUser() (line 15)
```

**workspaceSymbol**:
```
Found 5 symbol(s) matching 'UserService':

1. UserService in models (class)
   File: file:///home/user/src/UserService.groovy at line 5
```

**Error**:
```
Error: No LSP server available for this file type. Check your LSP configuration.
```

#### Use Cases

- Understanding code navigation
- Finding symbol usage across codebase
- Getting inline documentation
- Exploring code structure
- Quick symbol lookup

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
