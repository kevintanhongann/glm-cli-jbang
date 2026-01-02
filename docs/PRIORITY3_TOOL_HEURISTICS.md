# Priority 3: Tool Selection Heuristics Implementation Plan

## Overview

Implement explicit guidance and heuristics for tool selection, helping agents choose the right tools and use them in the correct sequence based on OpenCode's best practices.

## Status

- **Status:** Not Started
- **Priority:** Medium
- **Estimated Effort:** 3-4 days
- **Dependencies:** None

---

## Problem Statement

Currently, tool selection is left entirely to the LLM's autonomous decision-making. This leads to:

- Inconsistent tool usage patterns
- Suboptimal sequences (reading before globbing, etc.)
- Missed opportunities for parallelization
- No clear guidance on when to use which tool
- Agents don't know about parallel execution optimization

OpenCode solves this with:
- Explicit tool descriptions with clear "when to use" guidance
- Emphasis on parallel execution
- Clear sequences: glob → grep → read
- Delegation guidance for multi-round exploration

---

## Design

### Tool Selection Heuristics

```
Exploration Pattern:
  1. Broad discovery (Glob) → file patterns
  2. Content search (Grep) → narrow with regex
  3. Read files → examine specifics
  4. Delegate (Task) → for complex multi-round tasks
  5. Batch → execute 1-10 independent tools in parallel

Parallelization Rules:
  - Multiple independent reads → batch together
  - Grep + Glob → run in parallel
  - Git operations → run status, diff, log in parallel
  - Never chain dependent operations in batch

When to Delegate:
  - Multi-round exploration (glob, grep, read, repeat)
  - Complex multi-step tasks
  - Different aspects of codebase (arch, tests, docs)
  - Need specialized agent (explore, plan, build)
```

### Enhanced Tool Descriptions

Each tool should include:
- What it does
- When to use it
- Parameter requirements
- Common patterns
- When NOT to use it
- Parallel execution hints

---

## Implementation Plan

### Phase 1: Enhanced Tool Descriptions (Days 1-2)

#### 1.1 Update Glob Tool

**File:** `tools/GlobTool.groovy`

```groovy
package tools

class GlobTool implements Tool {

    @Override
    String getName() {
        return "glob"
    }

    @Override
    String getDescription() {
        return """
Fast file pattern matching tool that works with any codebase size.

**WHEN TO USE:**
- First step in exploration: Start with glob to discover file structure
- Finding files by pattern: "**/*.groovy", "src/**/*.ts"
- Broad discovery before narrowing with grep
- Finding all files of a certain type in a directory

**COMMON PATTERNS:**
- "**/*.groovy" → all Groovy files recursively
- "src/**/*.java" → all Java files in src/
- "test/*Test.groovy" → test files matching pattern
- "**/{pom.xml,build.gradle,package.json}" → build files

**PARAMETERS:**
- pattern: glob pattern (e.g., "**/*.groovy")
- path: optional starting directory (default: current)

**BEST PRACTICES:**
- Start broad, then narrow down
- Combine with grep in parallel for faster exploration
- Use with list_files for directory structure
- Returns files sorted by modification time (newest first)

**WHEN NOT TO USE:**
- When you know the exact file path → use read_file instead
- When searching file contents → use grep instead
- For complex multi-round exploration → use task tool with explore agent

**PARALLEL EXECUTION:**
- Can be combined with grep, list_files, or read_file in parallel
- Always batch independent searches together
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                pattern: [
                    type: "string",
                    description: "Glob pattern to match files (e.g., '**/*.groovy', 'src/**/*.ts')"
                ],
                path: [
                    type: "string",
                    description: "Starting directory for the search (default: current directory)",
                    default: "."
                ]
            ],
            required: ["pattern"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        // Existing implementation
    }
}
```

#### 1.2 Update Grep Tool

**File:** `tools/GrepTool.groovy`

```groovy
package tools

class GrepTool implements Tool {

    @Override
    String getName() {
        return "grep"
    }

    @Override
    String getDescription() {
        return """
Fast content search tool that works with any codebase size using regex.

**WHEN TO USE:**
- Searching file contents with specific patterns
- Finding where a function/class is defined
- Locating usage of a specific variable or method
- Narrowing down after glob for content search

**COMMON PATTERNS:**
- "class.*Controller" → find Controller classes
- "def myFunction|def my_method" → find function definitions
- "@Autowired|@Inject" → find dependency injection points
- "TODO|FIXME|HACK" → find code comments

**PARAMETERS:**
- pattern: regex pattern to search for
- path: optional directory to search (default: current)
- include: optional file filter (e.g., "*.groovy", "*.{ts,tsx}")
- case_sensitive: optional (default: false)

**BEST PRACTICES:**
- Use glob first to narrow search scope, then grep
- Combine with glob in parallel for faster exploration
- Returns max 100 matches with file paths and line numbers
- Sorted by modification time (newest first)
- Use include parameter to filter by file type

**WHEN NOT TO USE:**
- When searching file names → use glob instead
- When you know the exact file → use read_file instead
- For counting matches → use bash with 'rg' directly
- For complex multi-round exploration → use task tool with explore agent

**PARALLEL EXECUTION:**
- Can be combined with glob, read_file, or other grep calls in parallel
- Always batch independent searches together
- Multiple grep calls with different patterns in parallel
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                pattern: [
                    type: "string",
                    description: "Regular expression pattern to search for in file contents"
                ],
                path: [
                    type: "string",
                    description: "Directory to search in (default: current directory)",
                    default: "."
                ],
                include: [
                    type: "string",
                    description: "File pattern to filter (e.g., '*.groovy', '*.{ts,tsx}')"
                ],
                case_sensitive: [
                    type: "boolean",
                    description: "Whether to perform case-sensitive search (default: false)",
                    default: false
                ]
            ],
            required: ["pattern"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        // Existing implementation
    }
}
```

#### 1.3 Update ReadFile Tool

**File:** `tools/ReadFileTool.groovy`

```groovy
package tools

class ReadFileTool implements Tool {

    @Override
    String getName() {
        return "read_file"
    }

    @Override
    String getDescription() {
        return """
Read the complete contents of a file from the local filesystem.

**WHEN TO USE:**
- When you know the exact file path to read
- After discovering files with glob or grep
- Reading configuration files, documentation, or code
- Examining specific implementation details

**PARAMETERS:**
- path: absolute file path to read
- offset: optional line number to start reading (default: 0)
- limit: optional number of lines to read (default: all, max 2000)

**BEST PRACTICES:**
- Use absolute paths only (no relative paths)
- Read multiple files simultaneously when possible
- Default reads up to 2000 lines, specify offset/limit for large files
- Files over 2000 lines should be read in chunks
- Use offset/limit for specific sections

**WHEN NOT TO USE:**
- When searching for files → use glob instead
- When searching file contents → use grep instead
- When path is unknown → use glob/grep first to find it

**PARALLEL EXECUTION:**
- ALWAYS read multiple files simultaneously when they're independent
- Batch reads of related files together
- Can be combined with glob or grep in parallel for faster exploration
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [
                    type: "string",
                    description: "Absolute path to the file to read"
                ],
                offset: [
                    type: "integer",
                    description: "Line number to start reading from (0-based, default: 0)"
                ],
                limit: [
                    type: "integer",
                    description: "Number of lines to read (default: all, max 2000)"
                ]
            ],
            required: ["path"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        // Existing implementation
    }
}
```

#### 1.4 Update ListFiles Tool

**File:** `tools/ListFilesTool.groovy`

```groovy
package tools

class ListFilesTool implements Tool {

    @Override
    String getName() {
        return "list_files"
    }

    @Override
    String getDescription() {
        return """
List the contents of a directory with optional recursive search.

**WHEN TO USE:**
- Understanding directory structure
- Finding what files exist in a specific location
- Exploring project layout
- Checking if a directory exists before operations

**PARAMETERS:**
- path: directory path to list (default: current directory)
- recursive: whether to list subdirectories (default: false)

**BEST PRACTICES:**
- Use for directory structure overview
- Combine with glob for pattern matching
- Use recursive=True for full tree view
- Returns directories and files separately

**WHEN NOT TO USE:**
- When searching by pattern → use glob instead
- When searching contents → use grep instead
- For large codebases, use glob with patterns instead

**PARALLEL EXECUTION:**
- Can be combined with other list_files calls in parallel
- Can be combined with glob or grep in parallel
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [
                    type: "string",
                    description: "Directory path to list (default: current directory)"
                ],
                recursive: [
                    type: "boolean",
                    description: "Whether to list subdirectories recursively (default: false)"
                ]
            ],
            required: []
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        // Existing implementation
    }
}
```

#### 1.5 Update WriteFile Tool

**File:** `tools/WriteFileTool.groovy`

```groovy
package tools

class WriteFileTool implements Tool {

    @Override
    String getName() {
        return "write_file"
    }

    @Override
    String getDescription() {
        return """
Write or create a file with automatic diff preview and user confirmation.

**WHEN TO USE:**
- Creating new files
- Modifying existing files
- Writing code, documentation, or configuration

**PARAMETERS:**
- path: absolute file path to write
- content: full file content to write

**BEST PRACTICES:**
- Always read existing file first to understand context
- Use diff preview before writing (automatic)
- Write complete file content, not partial updates
- User confirmation required for file modifications
- Combined related edits into a single write

**WHEN NOT TO USE:**
- For small edits to specific sections → consider read + rewrite
- When you need to see the existing content first → read_file first

**PARALLEL EXECUTION:**
- File writes are sequential - cannot be parallelized
- Each write requires user confirmation
- Plan all writes before executing
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [
                    type: "string",
                    description: "Absolute path to the file to write"
                ],
                content: [
                    type: "string",
                    description: "Complete file content to write"
                ]
            ],
            required: ["path", "content"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        // Existing implementation
    }
}
```

### Phase 2: Create Heuristics Guide (Days 2-3)

#### 2.1 Create Tool Heuristics Document

**File:** `docs/TOOL_HEURISTICS.md`

```markdown
# Tool Selection Heuristics

## Exploration Sequence

Follow this sequence for codebase exploration:

```
1. Broad Discovery (Glob)
   ├── Find file patterns: "**/*.groovy", "src/**/*.ts"
   └── Understand project structure

2. Content Search (Grep)
   ├── Search for specific patterns: "class.*Controller"
   ├── Find function definitions: "def myFunction"
   └── Locate usage patterns

3. Read Files (Read)
   ├── Read specific files discovered
   ├── Understand implementation details
   └── Read related files in parallel

4. Deep Exploration (Task + Explore Agent)
   └── For complex, multi-round exploration tasks
```

## Tool Decision Tree

```
Need to find files?
  ├─ Know file name pattern? → Glob
  ├─ Know exact path? → ReadFile
  └─ Unknown structure? → Glob (broad pattern) → Grep (content)

Need to search content?
  ├─ Know file type? → Grep with include parameter
  ├─ Broad search needed? → Glob (scope) + Grep (content) in parallel
  └─ Specific search? → Grep directly

Need to read files?
  ├─ One file? → ReadFile
  ├─ Multiple independent files? → Batch multiple ReadFile calls
  └─ Related files that inform each other? → ReadFile with context

Need to make changes?
  ├─ Single file edit? → ReadFile → WriteFile (with diff)
  ├─ Multiple files? → Plan changes → Execute sequentially
  └─ Complex refactoring? → Use Plan Mode (Priority 2)

Complex, multi-step task?
  └─ Use Task tool with appropriate agent type
      ├── Explore agent: File discovery and code search
      ├── Plan agent: Read-only analysis
      └── Build agent: Full development work
```

## Parallel Execution Rules

### Always Parallelize

✅ Multiple independent file reads:
```groovy
read_file(path: "src/main.groovy")
read_file(path: "src/config.groovy")
read_file(path: "src/agent.groovy")
```

✅ Grep + Glob combination:
```groovy
glob(pattern: "**/*.groovy")
grep(pattern: "class.*Controller", include: "*.groovy")
```

✅ Multiple grep searches:
```groovy
grep(pattern: "@Autowired", include: "*.groovy")
grep(pattern: "@Inject", include: "*.groovy")
grep(pattern: "new HttpClient", include: "*.groovy")
```

✅ Git operations:
```groovy
bash(command: "git status")
bash(command: "git diff")
bash(command: "git log --oneline -5")
```

### Never Parallelize

❌ Dependent operations:
```groovy
// Don't do this - read might fail if write hasn't completed
write_file(path: "file.groovy", content: "...")
read_file(path: "file.groovy")
```

❌ Sequential edits:
```groovy
// Each write requires user confirmation
write_file(path: "file1.groovy", content: "...")
write_file(path: "file2.groovy", content: "...")
```

### Use `&&` for Sequential Dependencies

```groovy
bash(command: "git add . && git commit -m 'Changes'")
```

## Agent Tool Access

| Agent Type | Read | Write | Glob | Grep | List | Task |
|------------|------|-------|------|------|------|------|
| Explore    | ✓    | ✗     | ✓    | ✓    | ✓    | ✗    |
| Plan       | ✓    | ✗     | ✓    | ✓    | ✓    | ✗    |
| Build      | ✓    | ✓     | ✓    | ✓    | ✓    | ✓    |
| General    | ✓    | ✓     | ✓    | ✓    | ✓    | ✗    |

## Tool Performance Characteristics

| Tool | Speed | Best For | Typical Results |
|------|-------|----------|-----------------|
| Glob | Fast | File discovery | 10-1000 files |
| Grep | Fast | Content search | 10-100 matches |
| Read | Medium | File contents | Full file (2000 lines) |
| List | Fast | Directory structure | 10-1000 files |
| Write | Slow | File creation/modification | User confirmation |

## Common Patterns

### Pattern 1: Find and Read
```groovy
// Step 1: Find files
glob(pattern: "**/*Controller.groovy")

// Step 2: Read found files in parallel
read_file(path: "src/AuthController.groovy")
read_file(path: "src/UserController.groovy")
read_file(path: "src/DataController.groovy")
```

### Pattern 2: Search and Investigate
```groovy
// Step 1: Broad search in parallel
glob(pattern: "**/*.groovy")
grep(pattern: "class.*Service", include: "*.groovy")

// Step 2: Read relevant files
read_file(path: "src/AuthService.groovy")
read_file(path: "src/UserService.groovy")
```

### Pattern 3: Explore Architecture
```groovy
// Use explore agent for complex tasks
task(agent_type: "explore", task: """
Explore the codebase architecture:
1. Find all controller files
2. Find all service files
3. Find all repository files
4. Identify the pattern of how they connect
""")
```

### Pattern 4: Before Writing
```groovy
// Always read first
read_file(path: "src/Agent.groovy")
read_file(path: "src/Config.groovy")

// Then write changes
write_file(path: "src/Agent.groovy", content: "...")
```

## When to Use Explore Agent

Use the Task tool with agent_type="explore" when:

✅ Multi-round exploration needed (glob → grep → read → repeat)
✅ Need to search across multiple file types and locations
✅ Investigating complex architecture or relationships
✅ Finding patterns across the entire codebase
✅ Need adaptive search strategy

Do NOT use explore agent for:

❌ Simple single-file reads → use read_file directly
❌ Known file paths → use read_file directly
❌ One-off searches → use glob/grep directly

## Tool Usage Statistics

Based on typical workflows:

- **Glob:** 30% of exploration starts with glob
- **Grep:** 40% of exploration uses grep (often with glob)
- **ReadFile:** 60% of operations are reads
- **WriteFile:** 20% of operations are writes
- **ListFiles:** 15% of operations use list_files
- **Task:** 10% of operations delegate to subagents

Most common sequence:
1. Glob (30%)
2. Glob + Grep in parallel (40%)
3. ReadFile (single) (20%)
4. ReadFile (multiple in parallel) (10%)

## Configuration

Add to `~/.glm/config.toml`:

```toml
[tool_heuristics]
enabled = true
parallel_execution = true
max_parallel_tools = 10
suggest_explore_agent = true
```
```

### Phase 3: System Prompts (Days 3-4)

#### 3.1 Create Agent System Prompt

**File:** `prompts/system.txt`

```
You are GLM-CLI, a coding assistant that uses tools to explore and modify codebases.

## Tool Selection Guidelines

**ALWAYS follow this sequence for exploration:**
1. Start with Glob for broad file discovery
2. Use Grep to search for specific content patterns
3. Read files to examine implementation details
4. Delegate to explore agent for complex, multi-round exploration

**ALWAYS USE PARALLEL TOOLS:**
- Multiple independent file reads: Read them all at once
- Grep + Glob: Run in parallel for faster exploration
- Multiple grep searches: Run different patterns in parallel
- Git operations: Run git status, diff, and log together

**WHEN TO DELEGATE:**
- Complex, multi-round exploration (glob, grep, read, repeat)
- Searching across multiple file types or locations
- Investigating architecture and relationships
- Finding patterns across the entire codebase

Use the "task" tool with agent_type="explore" in these cases.

**TOOL ACCESS BY AGENT TYPE:**
- Explore agent: read_file, glob, grep, list_files only (no writes)
- Plan agent: read_file, glob, grep, list_files only (no writes)
- Build agent: all tools (full access)
- General agent: all tools except todo_write/todo_read

## Tool Decision Tree

```
Need to find files?
  ├─ Know pattern? → Glob
  ├─ Know path? → ReadFile
  └─ Unknown? → Glob → Grep → ReadFile

Need to search content?
  ├─ Know file type? → Grep with include
  ├─ Broad search? → Glob + Grep in parallel
  └─ Specific? → Grep directly

Need to read?
  ├─ One file? → ReadFile
  ├─ Multiple files? → Batch ReadFile calls
  └─ Complex exploration? → Task tool (explore agent)

Need to write?
  ├─ Single file? → ReadFile first → WriteFile
  ├─ Multiple files? → Plan → Execute sequentially
  └─ Complex? → Use Plan Mode
```

## Best Practices

1. **Parallelize everything:** Run multiple independent tools in a single response
2. **Batch file reads:** Read multiple files simultaneously when possible
3. **Use globs first:** Discover structure before content searching
4. **Read before writing:** Always understand context before modifying
5. **Delegate when needed:** Use explore agent for complex, multi-round tasks

## Performance

- Glob: Fastest (10-1000 files)
- Grep: Fast (10-100 matches)
- ReadFile: Medium (full file)
- WriteFile: Slowest (requires confirmation)
- Task: Medium (subagent execution)

Always parallelize to maximize speed!
```

### Phase 4: Agent Integration (Day 4)

#### 4.1 Update Agent.groovy

Add system prompt to agent:

```groovy
private String loadSystemPrompt() {
    def promptFile = new File("prompts/system.txt")
    if (promptFile.exists()) {
        return promptFile.text
    }
    return "You are a helpful coding assistant."
}

private ChatRequest prepareRequest() {
    ChatRequest req = new ChatRequest()
    req.model = model

    // Add system prompt at the beginning
    def messages = [new Message("system", loadSystemPrompt())]
    messages.addAll(history)
    req.messages = messages

    req.stream = false
    req.tools = tools.collect { /* ... */ }
    return req
}
```

---

## Success Criteria

- [ ] All tool descriptions updated with heuristics
- [ ] Tool decision tree created and documented
- [ ] Parallel execution rules documented
- [ ] Agent system prompt created
- [ ] Common patterns documented
- [ ] When to use explore agent clarified
- [ ] Configuration options added
- [ ] Documentation (TOOL_HEURISTICS.md) complete

---

## Dependencies

- None (can start immediately)

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| LLM ignores heuristics | Low | Emphasize in system prompt, add examples |
| Too much context in descriptions | Low | Keep descriptions concise but clear |
| Outdated documentation | Medium | Keep docs synced with implementation |

---

## References

- OpenCode tool descriptions: `/home/kevintan/opencode/packages/opencode/src/tool/*.txt`
- OpenCode agent prompts: `/home/kevintan/opencode/packages/opencode/src/agent/prompt/`
- Tool usage patterns in Claude Code and Amp

---

**Document Version:** 1.0
**Created:** 2025-01-02
**Priority:** Medium
