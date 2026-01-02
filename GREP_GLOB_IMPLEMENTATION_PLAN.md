# Grep and Glob Tools Implementation Plan

This document outlines the implementation plan for adding `grep` and `glob` file search tools to GLM-CLI, based on analysis of SST OpenCode and Google's Gemini CLI implementations.

## Overview

The goal is to implement two essential file search tools:
- **`grep`** - Search file contents using regular expressions (powered by ripgrep)
- **`glob`** - Find files matching glob patterns

Both tools should integrate seamlessly with the existing agent system and follow the established `Tool` interface pattern.

---

## Research Summary

### SST OpenCode Approach
- Uses ripgrep (`rg`) binary as the search backend
- Auto-downloads ripgrep for the platform if not found in `$PATH`
- Streams results using async generators for memory efficiency
- Returns up to 100 matches sorted by modification time
- Provides JSON-based structured output parsing

### Google Gemini CLI Approach
- Implements a **three-tier fallback strategy**: `git grep` → system `grep` → JavaScript fallback
- Auto-downloads ripgrep via `@joshua.litt/get-ripgrep` package
- Both `GrepTool` and `RipGrepTool` share the same tool name (`search_file_content`)
- Configuration-driven tool selection at startup
- Comprehensive file exclusion patterns (node_modules, .git, binary files, etc.)

### Key Insights from TOOLS.md
The existing documentation already outlines planned parameters:

```groovy
// grep (planned)
pattern: string   // Regex pattern to search
path: string      // Directory to search (default: current)
recursive: boolean // Search recursively (default: false)

// glob (planned)
pattern: string   // Glob pattern (e.g., **/*.groovy)
path: string      // Directory to search (default: current)
```

---

## Architecture Design

```
┌─────────────────────────────────────────────────────────────────┐
│                          Agent                                   │
│                                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │  GrepTool   │  │  GlobTool   │  │  Other Tools...         │ │
│  └──────┬──────┘  └──────┬──────┘  └─────────────────────────┘ │
│         │                │                                      │
│         └───────┬────────┘                                      │
│                 ▼                                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    RipgrepHelper                            ││
│  │  - Binary detection/download                                ││
│  │  - Platform-specific handling                               ││
│  │  - Command execution                                        ││
│  └─────────────────────────────────────────────────────────────┘│
│                 │                                               │
│                 ▼                                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              File System / Shell                            ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## Implementation Plan

### Phase 1: RipgrepHelper Utility

**File:** `core/RipgrepHelper.groovy`

Create a utility class to manage ripgrep binary detection, download, and execution.

```groovy
package core

import java.nio.file.*

class RipgrepHelper {
    static final String RIPGREP_VERSION = "14.1.0"
    static final Map<String, Map> PLATFORMS = [
        "linux-amd64": [
            platform: "x86_64-unknown-linux-musl",
            extension: "tar.gz"
        ],
        "linux-arm64": [
            platform: "aarch64-unknown-linux-gnu", 
            extension: "tar.gz"
        ],
        "darwin-amd64": [
            platform: "x86_64-apple-darwin",
            extension: "tar.gz"
        ],
        "darwin-arm64": [
            platform: "aarch64-apple-darwin",
            extension: "tar.gz"
        ],
        "windows-amd64": [
            platform: "x86_64-pc-windows-msvc",
            extension: "zip"
        ]
    ]
    
    private static String cachedPath = null
    
    /**
     * Get the path to ripgrep binary, downloading if necessary.
     */
    static String getRipgrepPath() {
        if (cachedPath) return cachedPath
        
        // Check if rg is in PATH
        def which = isWindows() ? "where rg" : "which rg"
        def proc = which.execute()
        proc.waitFor()
        if (proc.exitValue() == 0) {
            cachedPath = proc.text.trim().split("\n")[0]
            return cachedPath
        }
        
        // Check cached binary
        Path binDir = getGlmBinDir()
        String rgBinary = isWindows() ? "rg.exe" : "rg"
        Path rgPath = binDir.resolve(rgBinary)
        
        if (Files.exists(rgPath)) {
            cachedPath = rgPath.toString()
            return cachedPath
        }
        
        // Download ripgrep
        downloadRipgrep(binDir)
        cachedPath = rgPath.toString()
        return cachedPath
    }
    
    /**
     * Execute ripgrep with arguments and return output.
     */
    static ProcessResult executeRipgrep(List<String> args, String cwd = ".") {
        String rgPath = getRipgrepPath()
        if (!rgPath) {
            return new ProcessResult(exitCode: 1, error: "Ripgrep not available")
        }
        
        def command = [rgPath] + args
        def proc = command.execute(null, new File(cwd))
        def stdout = new StringBuilder()
        def stderr = new StringBuilder()
        
        proc.consumeProcessOutput(stdout, stderr)
        proc.waitFor()
        
        return new ProcessResult(
            exitCode: proc.exitValue(),
            output: stdout.toString(),
            error: stderr.toString()
        )
    }
    
    /**
     * Search for pattern in files (grep mode).
     */
    static List<GrepMatch> search(String pattern, String path = ".", 
                                   String include = null, int limit = 100) {
        def args = [
            "--json",           // JSON output for structured parsing
            "--regexp", pattern
        ]
        
        if (include) {
            args += ["--glob", include]
        }
        
        args += [path]
        
        def result = executeRipgrep(args, path)
        if (result.exitCode != 0 && !result.output) {
            return []
        }
        
        return parseJsonOutput(result.output, limit)
    }
    
    /**
     * List files matching glob pattern.
     */
    static List<String> files(String pattern, String path = ".", int limit = 100) {
        def args = [
            "--files",
            "--glob", pattern,
            "--glob", "!.git/*"
        ]
        
        def result = executeRipgrep(args, path)
        if (result.exitCode != 0) {
            return []
        }
        
        return result.output
            .split("\n")
            .findAll { it.trim() }
            .take(limit)
    }
    
    // ... helper methods for download, extraction, platform detection
}

class ProcessResult {
    int exitCode
    String output
    String error
}

class GrepMatch {
    String filePath
    int lineNumber
    String lineText
    List<SubMatch> submatches = []
}

class SubMatch {
    String text
    int start
    int end
}
```

### Phase 2: GrepTool Implementation

**File:** `tools/GrepTool.groovy`

```groovy
package tools

import core.RipgrepHelper
import java.nio.file.*

class GrepTool implements Tool {
    
    @Override
    String getName() {
        return "grep"
    }
    
    @Override
    String getDescription() {
        return """Fast content search tool that works with any codebase size.
Searches file contents using regular expressions.
- Supports full regex syntax (e.g., "log.*Error", "function\\s+\\w+")
- Filter files by pattern with the include parameter (e.g., "*.groovy", "*.{java,kt}")
- Returns file paths and line numbers sorted by modification time
- Limited to 100 matches by default"""
    }
    
    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                pattern: [
                    type: "string",
                    description: "The regular expression pattern to search for"
                ],
                path: [
                    type: "string",
                    description: "The directory to search in (default: current directory)"
                ],
                include: [
                    type: "string",
                    description: "Glob pattern to filter files (e.g., '*.groovy', '*.{java,kt}')"
                ]
            ],
            required: ["pattern"]
        ]
    }
    
    @Override
    Object execute(Map<String, Object> args) {
        String pattern = args.get("pattern")
        String searchPath = args.get("path") ?: "."
        String include = args.get("include")
        
        // Validate pattern
        if (!pattern) {
            return "Error: pattern is required"
        }
        
        // Validate regex
        try {
            java.util.regex.Pattern.compile(pattern)
        } catch (Exception e) {
            return "Error: Invalid regex pattern: ${e.message}"
        }
        
        // Validate path
        Path path = Paths.get(searchPath).normalize().toAbsolutePath()
        if (!Files.exists(path)) {
            return "Error: Directory not found: ${searchPath}"
        }
        
        try {
            def matches = RipgrepHelper.search(pattern, path.toString(), include)
            
            if (matches.isEmpty()) {
                return "No matches found for pattern: ${pattern}"
            }
            
            // Sort by modification time (newest first)
            matches = sortByModificationTime(matches)
            
            // Format output
            def output = new StringBuilder()
            output.append("Found ${matches.size()} matches for pattern '${pattern}':\n\n")
            
            def groupedByFile = matches.groupBy { it.filePath }
            groupedByFile.each { filePath, fileMatches ->
                output.append("${filePath}:\n")
                fileMatches.each { match ->
                    output.append("  L${match.lineNumber}: ${match.lineText.take(200)}\n")
                }
                output.append("\n")
            }
            
            if (matches.size() >= 100) {
                output.append("(Results truncated at 100 matches)\n")
            }
            
            return output.toString()
            
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }
    
    private List<GrepMatch> sortByModificationTime(List<GrepMatch> matches) {
        def fileTimes = [:]
        matches.each { match ->
            if (!fileTimes.containsKey(match.filePath)) {
                try {
                    def mtime = Files.getLastModifiedTime(Paths.get(match.filePath))
                    fileTimes[match.filePath] = mtime.toMillis()
                } catch (Exception e) {
                    fileTimes[match.filePath] = 0
                }
            }
        }
        
        return matches.sort { a, b ->
            (fileTimes[b.filePath] ?: 0) <=> (fileTimes[a.filePath] ?: 0)
        }
    }
}
```

### Phase 3: GlobTool Implementation

**File:** `tools/GlobTool.groovy`

```groovy
package tools

import core.RipgrepHelper
import java.nio.file.*

class GlobTool implements Tool {
    
    @Override
    String getName() {
        return "glob"
    }
    
    @Override
    String getDescription() {
        return """Fast file pattern matching tool that works with any codebase size.
- Supports glob patterns like "**/*.groovy" or "src/**/*.java"
- Returns matching file paths sorted by modification time
- Use this tool when you need to find files by name patterns"""
    }
    
    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                pattern: [
                    type: "string",
                    description: "Glob pattern to match files (e.g., '**/*.groovy', 'src/**/*.java')"
                ],
                path: [
                    type: "string",
                    description: "The directory to search in (default: current directory)"
                ]
            ],
            required: ["pattern"]
        ]
    }
    
    @Override
    Object execute(Map<String, Object> args) {
        String pattern = args.get("pattern")
        String searchPath = args.get("path") ?: "."
        
        // Validate pattern
        if (!pattern) {
            return "Error: pattern is required"
        }
        
        // Validate path
        Path path = Paths.get(searchPath).normalize().toAbsolutePath()
        if (!Files.exists(path)) {
            return "Error: Directory not found: ${searchPath}"
        }
        
        try {
            def files = RipgrepHelper.files(pattern, path.toString())
            
            if (files.isEmpty()) {
                return "No files found matching pattern: ${pattern}"
            }
            
            // Sort by modification time (newest first)
            files = sortByModificationTime(files, path)
            
            // Format output
            def output = new StringBuilder()
            output.append("Found ${files.size()} files matching '${pattern}':\n\n")
            
            files.each { file ->
                output.append("${file}\n")
            }
            
            if (files.size() >= 100) {
                output.append("\n(Results truncated at 100 files)\n")
            }
            
            return output.toString()
            
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }
    
    private List<String> sortByModificationTime(List<String> files, Path basePath) {
        return files.sort { a, b ->
            try {
                def pathA = basePath.resolve(a)
                def pathB = basePath.resolve(b)
                def mtimeA = Files.exists(pathA) ? Files.getLastModifiedTime(pathA).toMillis() : 0
                def mtimeB = Files.exists(pathB) ? Files.getLastModifiedTime(pathB).toMillis() : 0
                return mtimeB <=> mtimeA
            } catch (Exception e) {
                return 0
            }
        }
    }
}
```

### Phase 4: Fallback Strategy (No Ripgrep)

For environments where ripgrep cannot be installed, implement Java-based fallbacks.

**File:** `core/JavaSearchFallback.groovy`

```groovy
package core

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class JavaSearchFallback {
    
    static final int MAX_RESULTS = 100
    static final Set<String> EXCLUDED_DIRS = [
        ".git", "node_modules", ".svn", ".hg", 
        "build", "dist", "target", "__pycache__"
    ] as Set
    
    /**
     * Search files for pattern using pure Java.
     */
    static List<GrepMatch> search(String regex, String searchPath, 
                                   String includeGlob = null) {
        def matches = []
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        Path root = Paths.get(searchPath).toAbsolutePath()
        
        PathMatcher globMatcher = null
        if (includeGlob) {
            globMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:${includeGlob}")
        }
        
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (EXCLUDED_DIRS.contains(dir.fileName.toString())) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
            
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matches.size() >= MAX_RESULTS) {
                    return FileVisitResult.TERMINATE
                }
                
                if (globMatcher && !globMatcher.matches(file.fileName)) {
                    return FileVisitResult.CONTINUE
                }
                
                // Skip binary files
                if (isBinaryFile(file)) {
                    return FileVisitResult.CONTINUE
                }
                
                try {
                    def lines = Files.readAllLines(file)
                    lines.eachWithIndex { line, idx ->
                        if (matches.size() >= MAX_RESULTS) return
                        if (pattern.matcher(line).find()) {
                            matches << new GrepMatch(
                                filePath: root.relativize(file).toString(),
                                lineNumber: idx + 1,
                                lineText: line
                            )
                        }
                    }
                } catch (Exception e) {
                    // Skip unreadable files
                }
                
                return FileVisitResult.CONTINUE
            }
        })
        
        return matches
    }
    
    /**
     * Find files matching glob pattern using pure Java.
     */
    static List<String> files(String globPattern, String searchPath) {
        def results = []
        Path root = Paths.get(searchPath).toAbsolutePath()
        PathMatcher matcher = FileSystems.getDefault()
            .getPathMatcher("glob:${globPattern}")
        
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (EXCLUDED_DIRS.contains(dir.fileName.toString())) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
            
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (results.size() >= MAX_RESULTS) {
                    return FileVisitResult.TERMINATE
                }
                
                Path relativePath = root.relativize(file)
                if (matcher.matches(relativePath)) {
                    results << relativePath.toString()
                }
                
                return FileVisitResult.CONTINUE
            }
        })
        
        return results
    }
    
    private static boolean isBinaryFile(Path file) {
        def binaryExtensions = [
            ".exe", ".dll", ".so", ".dylib", ".bin",
            ".zip", ".tar", ".gz", ".jar", ".war",
            ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx"
        ]
        def fileName = file.fileName.toString().toLowerCase()
        return binaryExtensions.any { fileName.endsWith(it) }
    }
}
```

### Phase 5: Tool Registration

Update `core/Agent.groovy` to register the new tools:

```groovy
// In Agent.groovy constructor or initialization
void registerDefaultTools() {
    registerTool(new ReadFileTool())
    registerTool(new WriteFileTool())
    registerTool(new ListFilesTool())
    registerTool(new GrepTool())   // NEW
    registerTool(new GlobTool())   // NEW
}
```

Update `glm.groovy` to include new source files:

```groovy
//SOURCES tools/GrepTool.groovy
//SOURCES tools/GlobTool.groovy
//SOURCES core/RipgrepHelper.groovy
//SOURCES core/JavaSearchFallback.groovy
```

---

## Implementation Timeline

| Phase | Description | Estimated Effort |
|-------|-------------|------------------|
| 1 | RipgrepHelper utility class | 4-6 hours |
| 2 | GrepTool implementation | 2-3 hours |
| 3 | GlobTool implementation | 2-3 hours |
| 4 | Java fallback implementation | 3-4 hours |
| 5 | Tool registration & testing | 2-3 hours |
| **Total** | | **13-19 hours** |

---

## Testing Strategy

### Unit Tests

```groovy
// test/tools/GrepToolTest.groovy
class GrepToolTest {
    
    void testBasicSearch() {
        def tool = new GrepTool()
        def result = tool.execute([pattern: "class", path: "src"])
        assert result.contains("Found")
        assert !result.startsWith("Error:")
    }
    
    void testInvalidRegex() {
        def tool = new GrepTool()
        def result = tool.execute([pattern: "[invalid"])
        assert result.startsWith("Error:")
        assert result.contains("Invalid regex")
    }
    
    void testNoMatches() {
        def tool = new GrepTool()
        def result = tool.execute([
            pattern: "xyznonexistentpattern123xyz"
        ])
        assert result.contains("No matches found")
    }
    
    void testWithIncludeFilter() {
        def tool = new GrepTool()
        def result = tool.execute([
            pattern: "def",
            path: ".",
            include: "*.groovy"
        ])
        assert result.contains(".groovy")
    }
}
```

```groovy
// test/tools/GlobToolTest.groovy
class GlobToolTest {
    
    void testFindGroovyFiles() {
        def tool = new GlobTool()
        def result = tool.execute([pattern: "**/*.groovy"])
        assert result.contains("Found")
        assert result.contains(".groovy")
    }
    
    void testNoMatches() {
        def tool = new GlobTool()
        def result = tool.execute([pattern: "**/*.nonexistent"])
        assert result.contains("No files found")
    }
    
    void testSpecificDirectory() {
        def tool = new GlobTool()
        def result = tool.execute([
            pattern: "*.groovy",
            path: "tools"
        ])
        assert result.contains("Tool.groovy")
    }
}
```

### Integration Tests

```groovy
// Test agent using grep tool
void testAgentGrepIntegration() {
    Agent agent = new Agent(apiKey, model)
    agent.registerTool(new GrepTool())
    
    // Simulate agent using grep
    def result = agent.executeTool("grep", [
        pattern: "implements Tool",
        path: "tools"
    ])
    
    assert result.contains("Found")
}
```

---

## File Exclusion Patterns

Default patterns to exclude from searches:

```groovy
static final List<String> DEFAULT_EXCLUDES = [
    // Version control
    ".git",
    ".svn",
    ".hg",
    
    // Dependencies
    "node_modules",
    "vendor",
    ".gradle",
    
    // Build artifacts
    "build",
    "dist",
    "target",
    "out",
    
    // IDE
    ".idea",
    ".vscode",
    "*.iml",
    
    // Cache
    "__pycache__",
    ".cache",
    
    // Binary/large files
    "*.jar",
    "*.war",
    "*.zip",
    "*.tar",
    "*.gz"
]
```

---

## Configuration Options

Add to `~/.glm/config.toml`:

```toml
[search]
# Maximum results per search
max_results = 100

# Use ripgrep if available (faster)
use_ripgrep = true

# Additional exclude patterns
exclude_patterns = ["*.log", "*.tmp"]

# Include hidden files in search
include_hidden = false
```

---

## References

- [SST OpenCode - ripgrep.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/file/ripgrep.ts)
- [SST OpenCode - grep.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/tool/grep.ts)
- [SST OpenCode - glob.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/tool/glob.ts)
- [Gemini CLI - ripGrep.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/tools/ripGrep.ts)
- [Gemini CLI - grep.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/tools/grep.ts)
- [Gemini CLI - glob.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/tools/glob.ts)
- [Ripgrep Releases](https://github.com/BurntSushi/ripgrep/releases)
- [GLM-CLI TOOLS.md](./TOOLS.md)
- [GLM-CLI AGENTS.md](./AGENTS.md)
