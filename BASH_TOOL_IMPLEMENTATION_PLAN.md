# Bash Tool Implementation Plan for GLM-CLI

This document outlines the implementation plan for the bash tool in GLM-CLI, based on analysis of the SST OpenCode codebase and proposed improvements for concurrency and error handling.

## Research Findings from SST OpenCode

### Architecture Overview

SST OpenCode implements a robust bash tool with the following key components:

```
┌─────────────────────────────────────────────────────────────┐
│                     Bash Tool Execution                      │
├─────────────────────────────────────────────────────────────┤
│  1. Parameter Validation (Zod schema)                        │
│  2. AST Parsing (tree-sitter bash grammar)                   │
│  3. Security Analysis (path & command checks)                │
│  4. Permission System (wildcard pattern matching)            │
│  5. Process Spawning (Node.js spawn with detached mode)      │
│  6. Output Streaming (real-time via metadata callbacks)      │
│  7. Timeout Management (configurable with graceful shutdown) │
│  8. Process Cleanup (SIGTERM → SIGKILL two-phase kill)       │
└─────────────────────────────────────────────────────────────┘
```

### Key Implementation Patterns

| Component | OpenCode Approach | Notes |
|-----------|-------------------|-------|
| **Shell Selection** | Fallback hierarchy: user's SHELL → zsh → bash → sh | Blacklists fish, nu for compatibility |
| **Command Parsing** | tree-sitter WASM parser | Builds AST for security analysis |
| **Permissions** | Wildcard pattern matching with allow/deny/ask | Most specific pattern wins |
| **Process Control** | Detached process groups (Unix) | Enables proper tree cleanup |
| **Timeout** | Configurable default (2 min), per-call override | 100ms buffer before force kill |
| **Output Limit** | 30KB default, configurable | Truncates with metadata note |
| **Abort Support** | AbortSignal integration | Allows cancellation from agent |

### Security Layers

1. **External Directory Check** - Validates paths stay within project
2. **Dangerous Command Detection** - Flags `rm`, `mv`, `chmod`, etc.
3. **Permission Pattern Matching** - Configurable allow/deny rules
4. **AST-based Analysis** - Parses commands before execution

---

## GLM-CLI Implementation Plan

### Phase 1: Core Bash Tool Structure

#### 1.1 Tool Definition

```groovy
class BashTool implements Tool {
    static final String NAME = "bash"
    static final int DEFAULT_TIMEOUT_MS = 2 * 60 * 1000  // 2 minutes
    static final int MAX_OUTPUT_LENGTH = 30_000          // 30KB
    
    @Override
    String getName() { return NAME }
    
    @Override
    String getDescription() {
        return """Execute a bash command in the project directory.
        |Commands run in: ${System.getProperty("user.dir")}
        |
        |Guidelines:
        |- Do NOT chain commands with ; or && (make separate calls)
        |- Do NOT use interactive commands (REPLs, editors, password prompts)
        |- Do NOT use background processes with &
        |- Output is truncated to ${MAX_OUTPUT_LENGTH} characters
        """.stripMargin()
    }
    
    @Override
    Map<String, Object> getParametersSchema() {
        return [
            type: "object",
            properties: [
                command: [type: "string", description: "The command to execute"],
                timeout: [type: "integer", description: "Timeout in milliseconds (optional)"],
                workdir: [type: "string", description: "Working directory (optional)"],
                description: [type: "string", description: "What the command does"]
            ],
            required: ["command", "description"]
        ]
    }
}
```

#### 1.2 Shell Selection

```groovy
class ShellSelector {
    static final Set<String> BLACKLIST = ["fish", "nu", "pwsh"] as Set
    
    static String getAcceptableShell() {
        String userShell = System.getenv("SHELL")
        
        if (userShell && !BLACKLIST.contains(new File(userShell).name)) {
            return userShell
        }
        
        // Fallback hierarchy
        if (isWindows()) {
            return findWindowsShell()
        }
        
        // Unix fallback: bash → sh
        ["/bin/bash", "/usr/bin/bash", "/bin/sh"].find { new File(it).exists() }
    }
    
    private static String findWindowsShell() {
        // Try Git Bash first
        String gitBash = "C:\\Program Files\\Git\\bin\\bash.exe"
        if (new File(gitBash).exists()) return gitBash
        return "cmd.exe"
    }
    
    private static boolean isWindows() {
        System.getProperty("os.name").toLowerCase().contains("windows")
    }
}
```

### Phase 2: Process Execution with Concurrency Support

#### 2.1 Process Manager

```groovy
import java.util.concurrent.*

class ProcessManager {
    private final ExecutorService executor = Executors.newCachedThreadPool()
    private final ConcurrentHashMap<String, ManagedProcess> activeProcesses = new ConcurrentHashMap<>()
    
    static class ManagedProcess {
        Process process
        String id
        long startTime
        CompletableFuture<ProcessResult> future
        volatile boolean aborted = false
        volatile boolean timedOut = false
    }
    
    static class ProcessResult {
        String output
        int exitCode
        boolean timedOut
        boolean aborted
        boolean truncated
        long durationMs
    }
    
    CompletableFuture<ProcessResult> executeAsync(String command, ExecutionOptions options) {
        String processId = UUID.randomUUID().toString()
        
        return CompletableFuture.supplyAsync({
            executeWithManagement(processId, command, options)
        }, executor)
    }
    
    void abortProcess(String processId) {
        ManagedProcess mp = activeProcesses.get(processId)
        if (mp) {
            mp.aborted = true
            killProcessTree(mp.process)
        }
    }
    
    void abortAll() {
        activeProcesses.values().each { mp ->
            mp.aborted = true
            killProcessTree(mp.process)
        }
    }
}
```

#### 2.2 Execution Options

```groovy
class ExecutionOptions {
    String workdir = System.getProperty("user.dir")
    int timeoutMs = BashTool.DEFAULT_TIMEOUT_MS
    int maxOutputLength = BashTool.MAX_OUTPUT_LENGTH
    boolean streamOutput = true
    Consumer<String> outputCallback = null
    Runnable abortSignal = null
}
```

### Phase 3: Enhanced Error Handling

#### 3.1 Error Classification

```groovy
enum BashErrorType {
    TIMEOUT,
    PERMISSION_DENIED,
    COMMAND_NOT_FOUND,
    EXTERNAL_PATH_ACCESS,
    DANGEROUS_COMMAND,
    PROCESS_KILLED,
    INVALID_WORKDIR,
    SHELL_NOT_FOUND,
    UNKNOWN
}

class BashError extends RuntimeException {
    final BashErrorType type
    final String command
    final Map<String, Object> metadata
    
    BashError(BashErrorType type, String message, String command, Map<String, Object> metadata = [:]) {
        super(message)
        this.type = type
        this.command = command
        this.metadata = metadata
    }
    
    static BashError timeout(String command, int timeoutMs) {
        new BashError(
            BashErrorType.TIMEOUT,
            "Command timed out after ${timeoutMs}ms",
            command,
            [timeout: timeoutMs]
        )
    }
    
    static BashError permissionDenied(String command, String pattern) {
        new BashError(
            BashErrorType.PERMISSION_DENIED,
            "Command denied by permission rule: ${pattern}",
            command,
            [deniedPattern: pattern]
        )
    }
}
```

#### 3.2 Retry Strategy

```groovy
class RetryStrategy {
    int maxRetries = 3
    List<Integer> backoffMs = [1000, 3000, 5000]  // Exponential backoff
    Set<BashErrorType> retryableErrors = [
        BashErrorType.TIMEOUT,
        BashErrorType.UNKNOWN
    ] as Set
    
    ProcessResult executeWithRetry(Closure<ProcessResult> action) {
        int attempt = 0
        BashError lastError = null
        
        while (attempt <= maxRetries) {
            try {
                return action.call()
            } catch (BashError e) {
                lastError = e
                
                if (!retryableErrors.contains(e.type) || attempt >= maxRetries) {
                    throw e
                }
                
                int delayMs = backoffMs[Math.min(attempt, backoffMs.size() - 1)]
                Thread.sleep(delayMs)
                attempt++
            }
        }
        
        throw lastError
    }
}
```

### Phase 4: Process Cleanup & Kill Strategy

#### 4.1 Two-Phase Kill (SIGTERM → SIGKILL)

```groovy
class ProcessKiller {
    static final int SIGTERM_GRACE_MS = 200
    
    static void killProcessTree(Process process) {
        if (!process.isAlive()) return
        
        long pid = process.pid()
        
        if (isWindows()) {
            killWindowsProcessTree(pid)
        } else {
            killUnixProcessTree(pid, process)
        }
    }
    
    private static void killUnixProcessTree(long pid, Process process) {
        try {
            // Phase 1: SIGTERM (graceful)
            Runtime.runtime.exec(["kill", "-TERM", "-${pid}"] as String[])
            
            // Wait for graceful shutdown
            boolean exited = process.waitFor(SIGTERM_GRACE_MS, TimeUnit.MILLISECONDS)
            
            if (!exited) {
                // Phase 2: SIGKILL (force)
                Runtime.runtime.exec(["kill", "-KILL", "-${pid}"] as String[])
                process.waitFor(100, TimeUnit.MILLISECONDS)
            }
        } catch (Exception e) {
            // Fallback: destroy forcibly
            process.destroyForcibly()
        }
    }
    
    private static void killWindowsProcessTree(long pid) {
        try {
            // Windows: taskkill with /T for tree, /F for force
            Process killer = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                .redirectErrorStream(true)
                .start()
            killer.waitFor(5, TimeUnit.SECONDS)
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
```

### Phase 5: Output Streaming & Truncation

#### 5.1 Stream Handler

```groovy
class OutputStreamHandler {
    private final StringBuilder buffer = new StringBuilder()
    private final int maxLength
    private final Consumer<String> callback
    private volatile boolean truncated = false
    
    OutputStreamHandler(int maxLength, Consumer<String> callback) {
        this.maxLength = maxLength
        this.callback = callback
    }
    
    synchronized void append(String chunk) {
        if (buffer.length() >= maxLength) {
            truncated = true
            return
        }
        
        int remaining = maxLength - buffer.length()
        String toAppend = chunk.length() <= remaining ? chunk : chunk.substring(0, remaining)
        buffer.append(toAppend)
        
        if (chunk.length() > remaining) {
            truncated = true
        }
        
        if (callback) {
            callback.accept(buffer.toString())
        }
    }
    
    String getOutput() { buffer.toString() }
    boolean isTruncated() { truncated }
}
```

### Phase 6: Security & Permissions

#### 6.1 Permission Configuration

```groovy
// ~/.glm/config/permissions.json
{
    "bash": {
        "git status*": "allow",
        "git log*": "allow",
        "git diff*": "allow",
        "ls*": "allow",
        "cat*": "allow",
        "head*": "allow",
        "tail*": "allow",
        "grep*": "allow",
        "find*": "ask",
        "rm*": "ask",
        "mv*": "ask",
        "cp*": "ask",
        "chmod*": "deny",
        "chown*": "deny",
        "sudo*": "deny",
        "curl*": "ask",
        "wget*": "ask",
        "*": "ask"
    }
}
```

#### 6.2 Permission Matcher

```groovy
class PermissionMatcher {
    enum Action { ALLOW, DENY, ASK }
    
    private final Map<String, Action> patterns
    
    PermissionMatcher(Map<String, String> config) {
        this.patterns = config.collectEntries { pattern, action ->
            [(pattern): Action.valueOf(action.toUpperCase())]
        }
    }
    
    Action checkCommand(String command) {
        // Sort by specificity (longer patterns first)
        List<Map.Entry<String, Action>> sorted = patterns.entrySet()
            .sort { a, b -> b.key.length() <=> a.key.length() }
        
        for (entry in sorted) {
            if (matchesPattern(command, entry.key)) {
                return entry.value
            }
        }
        
        return Action.ASK  // Default: ask
    }
    
    private boolean matchesPattern(String command, String pattern) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
        return command.matches("^${regex}\$")
    }
}
```

---

## Concurrency Improvements

### 1. Parallel Tool Execution Queue

```groovy
class BashToolQueue {
    private final ExecutorService executor
    private final Semaphore concurrencyLimit
    private final BlockingQueue<QueuedCommand> queue = new LinkedBlockingQueue<>()
    
    BashToolQueue(int maxConcurrent = 5) {
        this.executor = Executors.newFixedThreadPool(maxConcurrent)
        this.concurrencyLimit = new Semaphore(maxConcurrent)
    }
    
    CompletableFuture<ProcessResult> submit(String command, ExecutionOptions options) {
        CompletableFuture<ProcessResult> future = new CompletableFuture<>()
        
        executor.submit {
            concurrencyLimit.acquire()
            try {
                ProcessResult result = executeCommand(command, options)
                future.complete(result)
            } catch (Exception e) {
                future.completeExceptionally(e)
            } finally {
                concurrencyLimit.release()
            }
        }
        
        return future
    }
    
    void shutdown(long timeoutMs = 5000) {
        executor.shutdown()
        executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
```

### 2. Abort Controller Pattern

```groovy
class AbortController {
    private final AtomicBoolean aborted = new AtomicBoolean(false)
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>()
    
    void abort() {
        if (aborted.compareAndSet(false, true)) {
            listeners.each { it.run() }
        }
    }
    
    boolean isAborted() { aborted.get() }
    
    void onAbort(Runnable listener) {
        if (aborted.get()) {
            listener.run()
        } else {
            listeners.add(listener)
        }
    }
    
    void removeListener(Runnable listener) {
        listeners.remove(listener)
    }
}
```

### 3. Resource Cleanup on Shutdown

```groovy
class BashToolShutdownHook {
    private static final Set<ManagedProcess> activeProcesses = 
        Collections.newSetFromMap(new ConcurrentHashMap<>())
    
    static {
        Runtime.runtime.addShutdownHook(new Thread({
            activeProcesses.each { mp ->
                try {
                    ProcessKiller.killProcessTree(mp.process)
                } catch (Exception ignored) {}
            }
        }))
    }
    
    static void register(ManagedProcess mp) {
        activeProcesses.add(mp)
    }
    
    static void unregister(ManagedProcess mp) {
        activeProcesses.remove(mp)
    }
}
```

---

## Error Handling Improvements

### 1. Structured Error Reporting

```groovy
class BashToolResult {
    boolean success
    String output
    int exitCode
    BashErrorType errorType
    String errorMessage
    Map<String, Object> metadata
    long durationMs
    
    String toAgentMessage() {
        StringBuilder sb = new StringBuilder()
        
        if (!success) {
            sb.append("<bash_error>\n")
            sb.append("Type: ${errorType}\n")
            sb.append("Message: ${errorMessage}\n")
            sb.append("</bash_error>\n\n")
        }
        
        sb.append(output)
        
        if (metadata.truncated) {
            sb.append("\n\n<bash_metadata>")
            sb.append("Output truncated at ${metadata.maxLength} characters")
            sb.append("</bash_metadata>")
        }
        
        return sb.toString()
    }
}
```

### 2. Comprehensive Logging

```groovy
class BashToolLogger {
    private static final Logger log = LoggerFactory.getLogger(BashTool)
    
    static void logExecution(String command, ExecutionOptions options) {
        log.debug("Executing: {} (timeout={}ms, workdir={})", 
            command, options.timeoutMs, options.workdir)
    }
    
    static void logCompletion(String command, ProcessResult result) {
        if (result.exitCode == 0) {
            log.debug("Completed: {} (exit=0, duration={}ms)", 
                command, result.durationMs)
        } else {
            log.warn("Failed: {} (exit={}, duration={}ms)", 
                command, result.exitCode, result.durationMs)
        }
    }
    
    static void logError(String command, BashError error) {
        log.error("Error executing '{}': {} (type={})", 
            command, error.message, error.type)
    }
}
```

### 3. Graceful Degradation

```groovy
class FallbackExecutor {
    static ProcessResult executeWithFallback(String command, ExecutionOptions options) {
        try {
            return executeNormal(command, options)
        } catch (BashError e) {
            switch (e.type) {
                case BashErrorType.TIMEOUT:
                    // Retry with longer timeout
                    options.timeoutMs *= 2
                    return executeNormal(command, options)
                    
                case BashErrorType.SHELL_NOT_FOUND:
                    // Try alternative shell
                    return executeWithAlternativeShell(command, options)
                    
                default:
                    throw e
            }
        }
    }
}
```

---

## Implementation Timeline

| Phase | Description | Duration | Priority |
|-------|-------------|----------|----------|
| 1 | Core tool structure & schema | 1 day | P0 |
| 2 | Process execution & management | 2 days | P0 |
| 3 | Error handling & classification | 1 day | P0 |
| 4 | Process cleanup & kill strategy | 1 day | P1 |
| 5 | Output streaming & truncation | 1 day | P1 |
| 6 | Security & permissions | 2 days | P1 |
| 7 | Concurrency improvements | 2 days | P2 |
| 8 | Testing & documentation | 2 days | P0 |

**Total Estimated Time: 12 days**

---

## Testing Checklist

- [ ] Basic command execution (ls, echo, etc.)
- [ ] Timeout handling with long-running commands
- [ ] Process tree cleanup on abort
- [ ] Output truncation at limit
- [ ] Permission deny/allow/ask flow
- [ ] Cross-platform compatibility (Linux, macOS, Windows)
- [ ] Concurrent command execution
- [ ] Error recovery and retry logic
- [ ] Shutdown hook cleanup
- [ ] Stream output callback

---

## References

- [SST OpenCode bash.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/tool/bash.ts)
- [SST OpenCode shell.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/shell/shell.ts)
- [SST OpenCode permission/index.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/permission/index.ts)
- [SST OpenCode wildcard.ts](https://github.com/sst/opencode/blob/main/packages/opencode/src/util/wildcard.ts)
