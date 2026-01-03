# Tool Selection Heuristics

Comprehensive guide for tool selection in GLM-CLI agents.

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
  └─ Complex refactoring? → Use Plan Mode

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
| Read | Medium | File contents | Full file (2000 lines max) |
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

---

**Version:** 1.0  
**Last Updated:** 2026-01-03
