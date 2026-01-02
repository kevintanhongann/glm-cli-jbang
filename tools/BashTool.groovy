package tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

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

class ShellSelector {
    static final Set<String> BLACKLIST = ["fish", "nu", "pwsh"] as Set

    static String getAcceptableShell() {
        String userShell = System.getenv("SHELL")

        if (userShell && !BLACKLIST.contains(new File(userShell).name)) {
            return userShell
        }

        if (isWindows()) {
            return findWindowsShell()
        }

        ["/bin/bash", "/usr/bin/bash", "/bin/sh"].find { new File(it).exists() } ?: "/bin/sh"
    }

    private static String findWindowsShell() {
        String gitBash = "C:\\Program Files\\Git\\bin\\bash.exe"
        if (new File(gitBash).exists()) return gitBash
        return "cmd.exe"
    }

    private static boolean isWindows() {
        System.getProperty("os.name").toLowerCase().contains("windows")
    }
}

class ExecutionOptions {
    String workdir = System.getProperty("user.dir")
    int timeoutMs
    int maxOutputLength
    boolean streamOutput = true
    Consumer<String> outputCallback = null
    Runnable abortSignal = null

    ExecutionOptions(int timeoutMs = 120000, int maxOutputLength = 30000) {
        this.timeoutMs = timeoutMs
        this.maxOutputLength = maxOutputLength
    }
}

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
            Runtime.runtime.exec(["kill", "-TERM", "-${pid}"] as String[])
            boolean exited = process.waitFor(SIGTERM_GRACE_MS, TimeUnit.MILLISECONDS)

            if (!exited) {
                Runtime.runtime.exec(["kill", "-KILL", "-${pid}"] as String[])
                process.waitFor(100, TimeUnit.MILLISECONDS)
            }
        } catch (Exception e) {
            process.destroyForcibly()
        }
    }

    private static void killWindowsProcessTree(long pid) {
        try {
            Process killer = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                .redirectErrorStream(true)
                .start()
            killer.waitFor(5, TimeUnit.SECONDS)
        } catch (Exception e) {
        }
    }

    private static boolean isWindows() {
        System.getProperty("os.name").toLowerCase().contains("windows")
    }
}

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

class RetryStrategy {
    int maxRetries = 3
    List<Integer> backoffMs = [1000, 3000, 5000]
    Set<BashErrorType> retryableErrors = [
        BashErrorType.TIMEOUT,
        BashErrorType.UNKNOWN
    ] as Set

    Object executeWithRetry(Closure<Object> action) {
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

class BashTool implements Tool {
    static final String NAME = "bash"
    static final int DEFAULT_TIMEOUT_MS = 2 * 60 * 1000
    static final int MAX_OUTPUT_LENGTH = 30_000
    private final ProcessManager processManager = new ProcessManager()
    private final PermissionMatcher permissionMatcher = new PermissionMatcher()
    private final RetryStrategy retryStrategy = new RetryStrategy()

    @Override
    String getName() { return NAME }

    @Override
    String getDescription() {
        """Execute a bash command in the project directory.
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
    Map<String, Object> getParameters() {
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

    @Override
    Object execute(Map<String, Object> args) {
        try {
            return executeCommand(args)
        } catch (BashError e) {
            return formatError(e)
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }

    private Object executeCommand(Map<String, Object> args) {
        String command = args.get("command")
        String desc = args.get("description", "")
        Integer timeoutArg = args.get("timeout") as Integer
        String workdirArg = args.get("workdir") as String

        if (!command || command.trim().isEmpty()) {
            throw new BashError(BashErrorType.UNKNOWN, "Command cannot be empty", command)
        }

        ExecutionOptions options = new ExecutionOptions(
            timeoutArg ?: DEFAULT_TIMEOUT_MS,
            MAX_OUTPUT_LENGTH
        )

        if (workdirArg) {
            Path workdir = Paths.get(workdirArg).normalize()
            if (!Files.exists(workdir)) {
                throw new BashError(BashErrorType.INVALID_WORKDIR, "Working directory does not exist: ${workdirArg}", command)
            }
            options.workdir = workdir.toString()
        }

        PermissionMatcher.Action permission = permissionMatcher.checkCommand(command)
        if (permission == PermissionMatcher.Action.DENY) {
            throw BashError.permissionDenied(command, "Permission rule")
        }

        ProcessManager.ProcessResult result = retryStrategy.executeWithRetry {
            processManager.execute(command, options)
        }

        StringBuilder sb = new StringBuilder()

        if (!result.success) {
            if (result.timedOut) {
                sb.append("<bash_error>\n")
                sb.append("Type: TIMEOUT\n")
                sb.append("Message: Command timed out after ${options.timeoutMs}ms\n")
                sb.append("</bash_error>\n\n")
            } else if (result.aborted) {
                sb.append("<bash_error>\n")
                sb.append("Type: PROCESS_KILLED\n")
                sb.append("Message: Process was aborted\n")
                sb.append("</bash_error>\n\n")
            } else if (result.exitCode != 0) {
                sb.append("<bash_error>\n")
                sb.append("Type: COMMAND_FAILED\n")
                sb.append("Exit Code: ${result.exitCode}\n")
                sb.append("</bash_error>\n\n")
            }
        }

        sb.append(result.output)

        if (result.truncated) {
            sb.append("\n\n<bash_metadata>")
            sb.append("Output truncated at ${options.maxOutputLength} characters")
            sb.append("</bash_metadata>")
        }

        return sb.toString()
    }

    private String formatError(BashError error) {
        StringBuilder sb = new StringBuilder()
        sb.append("<bash_error>\n")
        sb.append("Type: ${error.type}\n")
        sb.append("Message: ${error.message}\n")
        sb.append("</bash_error>\n")
        return sb.toString()
    }

    void shutdown() {
        processManager.shutdown()
    }
}

class ProcessManager {
    private final ExecutorService executor = Executors.newCachedThreadPool()
    private final ConcurrentHashMap<String, ManagedProcess> activeProcesses = new ConcurrentHashMap<>()
    private final Set<ManagedProcess> allProcesses = Collections.newSetFromMap(new ConcurrentHashMap<>())
    private final Thread shutdownHook

    ProcessManager() {
        shutdownHook = new Thread({
            allProcesses.each { mp ->
                try {
                    ProcessKiller.killProcessTree(mp.process)
                } catch (Exception ignored) {}
            }
        })
        Runtime.runtime.addShutdownHook(shutdownHook)
    }

    static class ManagedProcess {
        Process process
        String id
        long startTime
        volatile boolean aborted = false
        volatile boolean timedOut = false
    }

    static class ProcessResult {
        String output
        int exitCode
        boolean success
        boolean timedOut
        boolean aborted
        boolean truncated
        long durationMs
    }

    ProcessResult execute(String command, ExecutionOptions options) {
        ProcessResult result
        try {
            result = executeInternal(command, options)
        } catch (InterruptedException e) {
            throw new BashError(BashErrorType.UNKNOWN, "Execution interrupted", command)
        } catch (Exception e) {
            throw new BashError(BashErrorType.UNKNOWN, e.message, command)
        }
        return result
    }

    private ProcessResult executeInternal(String command, ExecutionOptions options) {
        long startTime = System.currentTimeMillis()
        String shell = ShellSelector.getAcceptableShell()
        if (!shell) {
            throw new BashError(BashErrorType.SHELL_NOT_FOUND, "No acceptable shell found", command)
        }

        ProcessBuilder pb = new ProcessBuilder(shell, "-c", command)
        pb.directory(new File(options.workdir))
        pb.redirectErrorStream(true)

        AbortController abortController = new AbortController()
        Process process = pb.start()
        ManagedProcess mp = new ManagedProcess(
            process: process,
            id: UUID.randomUUID().toString(),
            startTime: startTime
        )

        activeProcesses.put(mp.id, mp)
        allProcesses.add(mp)

        OutputStreamHandler outputHandler = new OutputStreamHandler(options.maxOutputLength, options.outputCallback)
        StringBuilder output = new StringBuilder()

        try {
            Thread outputReader = new Thread({
                def reader = new BufferedReader(new InputStreamReader(process.inputStream))
                String line
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n")
                    outputHandler.append(line + "\n")
                }
            })
            outputReader.daemon = true
            outputReader.start()

            boolean completed = process.waitFor(options.timeoutMs, TimeUnit.MILLISECONDS)

            if (!completed) {
                mp.timedOut = true
                ProcessKiller.killProcessTree(process)
                process.waitFor(100, TimeUnit.MILLISECONDS)
            }

            outputReader.join(1000)

            ProcessResult result = new ProcessResult(
                output: output.toString(),
                exitCode: process.exitValue(),
                success: completed && process.exitValue() == 0,
                timedOut: mp.timedOut,
                aborted: mp.aborted,
                truncated: outputHandler.isTruncated(),
                durationMs: System.currentTimeMillis() - startTime
            )

            return result
        } finally {
            activeProcesses.remove(mp.id)
            allProcesses.remove(mp)
        }
    }

    void abortAll() {
        activeProcesses.values().each { mp ->
            mp.aborted = true
            ProcessKiller.killProcessTree(mp.process)
        }
    }

    void shutdown() {
        try {
            Runtime.runtime.removeShutdownHook(shutdownHook)
        } catch (Exception e) {
        }
        abortAll()
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (InterruptedException e) {
        }
    }
}

class PermissionMatcher {
    enum Action { ALLOW, DENY, ASK }

    private final Map<String, Action> patterns

    PermissionMatcher() {
        this.patterns = loadDefaultPatterns()
    }

    private Map<String, Action> loadDefaultPatterns() {
        def defaultPatterns = [
            "git status*": Action.ALLOW,
            "git log*": Action.ALLOW,
            "git diff*": Action.ALLOW,
            "git show*": Action.ALLOW,
            "ls*": Action.ALLOW,
            "cat*": Action.ALLOW,
            "head*": Action.ALLOW,
            "tail*": Action.ALLOW,
            "grep*": Action.ALLOW,
            "find*": Action.ASK,
            "rm*": Action.ASK,
            "mv*": Action.ASK,
            "cp*": Action.ASK,
            "chmod*": Action.DENY,
            "chown*": Action.DENY,
            "sudo*": Action.DENY,
            "curl*": Action.ASK,
            "wget*": Action.ASK,
            "*": Action.ASK
        ]
        return defaultPatterns
    }

    Action checkCommand(String command) {
        List<Map.Entry<String, Action>> sorted = patterns.entrySet()
            .sort { a, b -> b.key.length() <=> a.key.length() }

        for (entry in sorted) {
            if (matchesPattern(command, entry.key)) {
                return entry.value
            }
        }

        return Action.ASK
    }

    private boolean matchesPattern(String command, String pattern) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
        return command.matches("^${regex}\$")
    }
}
