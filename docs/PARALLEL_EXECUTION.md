# Parallel Execution Guide

## Overview

GLM-CLI supports parallel tool execution to maximize performance when exploring and modifying codebases.

## How It Works

When you call multiple independent tools in a single response, they execute in parallel:

```groovy
// All three execute simultaneously
read_file(path: "src/main.groovy")
read_file(path: "src/config.groovy")
read_file(path: "README.md")
```

**Benefits:**
- 3x faster than sequential execution
- Reduced total turnaround time
- Better user experience

## When to Parallelize

### Always Parallelize These:

✅ **Multiple independent file reads:**
```groovy
read_file(path: "src/Agent.groovy")
read_file(path: "src/Config.groovy")
read_file(path: "tools/ReadFileTool.groovy")
```

✅ **Grep + Glob combination:**
```groovy
glob(pattern: "**/*.groovy")
grep(pattern: "class.*Controller", include: "*.groovy")
```

✅ **Multiple grep searches:**
```groovy
grep(pattern: "@Autowired", include: "*.groovy")
grep(pattern: "@Inject", include: "*.groovy")
grep(pattern: "def.*Service", include: "*.groovy")
```

✅ **Git operations:**
```groovy
bash(command: "git status")
bash(command: "git diff")
bash(command: "git log --oneline -5")
```

### Never Parallelize These:

❌ **Dependent operations:**
```groovy
// Don't do this - read depends on write completing
write_file(path: "file.groovy", content: "...")
read_file(path: "file.groovy")
```

❌ **Sequential writes:**
```groovy
// Each write requires user confirmation
write_file(path: "file1.groovy", content: "...")
write_file(path: "file2.groovy", content: "...")
```

❌ **Operations where order matters:**
```groovy
// These must be sequential
bash(command: "git add .")
bash(command: "git commit -m 'changes'")
bash(command: "git push")
```

## Batch Tool

The `batch` tool provides explicit parallel execution for multiple tools:

### Syntax

```groovy
batch(tools: [
  { name: "tool_name", arguments: { /* params */ } },
  { name: "tool_name", arguments: { /* params */ } }
])
```

### Example

```groovy
batch(tools: [
  { name: "glob", arguments: { pattern: "**/*.groovy" } },
  { name: "grep", arguments: { pattern: "class.*Controller", include: "*.groovy" } },
  { name: "read_file", arguments: { path: "README.md" } }
])
```

### Batch Tool Benefits

- Explicit parallel execution
- Progress tracking
- Partial failure handling
- Performance metrics

## Performance Comparison

### Sequential Execution

```
read_file (100ms) → grep (150ms) → read_file (100ms) = 350ms
```

### Parallel Execution

```
read_file (100ms)
                 \
                  → All start together = 150ms (max of durations)
                 /
grep (150ms)
```

**Speedup: 2.3x**

## Limitations

- **Max 10 tools per batch:** Performance degrades beyond this
- **No interdependencies:** Tools can't share results
- **Order not guaranteed:** Tools complete in arbitrary order
- **Resource limits:** Bounded by thread pool size (10 threads)
- **Timeout:** Each tool has a 2-minute timeout

## Best Practices

1. **Start with broad searches (glob), then narrow (grep)**
2. **Read multiple files simultaneously when possible**
3. **Combine glob + grep in parallel for faster discovery**
4. **Use batch tool for coordinated parallel execution**
5. **Limit to 5-10 parallel tools for best performance**
6. **Group related operations together**
7. **Avoid mixing reads and writes in parallel**

## Common Patterns

### Pattern 1: Discover and Read

```groovy
// Step 1: Discover files in parallel
glob(pattern: "**/*.groovy")
glob(pattern: "**/*.java")

// Step 2: Read found files
read_file(path: "src/main.groovy")
read_file(path: "src/config.groovy")
```

### Pattern 2: Search and Investigate

```groovy
// Broad search in parallel
glob(pattern: "**/*.groovy")
grep(pattern: "class.*Controller", include: "*.groovy")
grep(pattern: "class.*Service", include: "*.groovy")

// Read relevant files
read_file(path: "src/AuthController.groovy")
read_file(path: "src/AuthService.groovy")
```

### Pattern 3: Using Batch Tool

```groovy
batch(tools: [
  { name: "glob", arguments: { pattern: "**/*.groovy" } },
  { name: "grep", arguments: { pattern: "@Component", include: "*.groovy" } },
  { name: "read_file", arguments: { path: "README.md" } },
  { name: "read_file", arguments: { path: "build.gradle" } }
])
```

## Monitoring

Parallel execution shows progress:

```
▶ Parallel: [████████████████░░] 75% (3/4 tools) │ Running: grep
```

After completion:

```
✓ glob (45ms)
✓ grep (120ms)
✓ read_file (80ms)
✓ read_file (60ms)
```

## Configuration

Add to `~/.glm/config.toml`:

```toml
[parallel_execution]
enabled = true
max_parallel_tools = 10
thread_pool_size = 10
progress_display = true
```

## Troubleshooting

### Tools not executing in parallel?

Check:
- Are the tools truly independent?
- Are you calling them in separate turns?
- Is parallel_execution enabled in config?

### Performance not improving?

- Ensure tools are CPU-bound or I/O-bound (not both)
- Limit to 5-8 tools for best performance
- Check system resources (CPU, memory)

### Batch tool failing?

- Verify all tools are registered
- Check arguments format (must be objects)
- Ensure max 10 tools per batch
- Check for tool-specific errors in output

## API Reference

### BatchTool

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| tools | array | Yes | List of tool executions (1-10) |
| tools[].name | string | Yes | Tool name (e.g., "read_file") |
| tools[].arguments | object | Yes | Tool-specific parameters |

### ParallelExecutionConfig

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| enabled | boolean | true | Enable parallel execution |
| max_parallel_tools | integer | 10 | Max tools per batch |
| thread_pool_size | integer | 10 | Thread pool size |
| progress_display | boolean | true | Show progress bar |
