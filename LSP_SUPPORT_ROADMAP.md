# LSP Support Implementation Plan

## Executive Summary

This document provides a comprehensive plan for implementing full Language Server Protocol (LSP) support in GLM-CLI, enabling real-time diagnostics, code intelligence queries, and enhanced agent self-correction capabilities.

## Implementation Status

### ✅ Phase 1: Core Infrastructure (COMPLETED)
- [x] `core/JsonRpcHandler.groovy` - JSON-RPC 2.0 protocol handler
- [x] `models/Diagnostic.groovy` - Enhanced with `fromLsp()` factory method
- [x] Added `DiagnosticRelatedInformation` and `Location` classes

### ✅ Phase 2: LSP Client & Server Management (COMPLETED)
- [x] `core/LSPClient.groovy` - Full implementation with code intelligence methods
- [x] `core/LSPServerRegistry.groovy` - 11 language servers configured
- [x] `core/LSPManager.groovy` - Singleton manager with client lifecycle

### ✅ Phase 3: Tool Integration (COMPLETED)
- [x] `core/DiagnosticFormatter.groovy` - Formatting for agents and display
- [x] `tools/WriteFileTool.groovy` - LSP diagnostics on file writes

### ✅ Phase 4: Configuration System (COMPLETED)
- [x] `core/LSPConfig.groovy` - Configuration with JSON config support

### ✅ Phase 5: Agent Integration (COMPLETED)
- [x] `tools/LSPTool.groovy` - Full LSP query tool

## Current State Analysis

### Existing Infrastructure
- `Diagnostic.groovy` model already defined in `models/` directory
- `JsonRpcHandler.groovy` handles JSON-RPC protocol
- `LSPClient.groovy` manages communication with language servers
- `LSPServerRegistry.groovy` configures language servers
- `LSPManager.groovy` singleton manages all LSP clients
- `DiagnosticFormatter.groovy` formats diagnostics for display
- `LSPConfig.groovy` handles configuration
- `LSPTool.groovy` provides agent access to LSP features
- `WriteFileTool.groovy` integrates with LSP diagnostics

### Supported Languages
- TypeScript/JavaScript (.ts, .tsx, .js, .jsx, .mjs, .cjs)
- Python (.py, .pyw)
- Java (.java)
- Groovy (.groovy, .gvy, .gy)
- Go (.go)
- Rust (.rs)
- JSON (.json, .jsonc, .jsonl)
- YAML (.yaml, .yml)
- HTML (.html, .htm)
- CSS (.css, .scss, .sass, .less, .styl)
- Markdown (.md, .markdown)

### Code Intelligence Features
- **goToDefinition** - Find symbol definitions
- **findReferences** - Find all symbol references
- **hover** - Get documentation at position
- **documentSymbol** - List all symbols in file
- **workspaceSymbol** - Search symbols across workspace
- **completion** - Get completion suggestions

## Quick Start

```bash
# Enable LSP in ~/.glm/config.json:
# {
#   "lsp": {
#     "enabled": true,
#     "diagnosticTimeout": 3000
#   }
# }

# Agent can now use LSP operations:
# { "operation": "goToDefinition", "filePath": "src/main.ts", "position": {"line": 10, "character": 5} }
# { "operation": "diagnostics", "filePath": "src/main.ts" }
```

## Architecture

```
LSPTool (tools/LSPTool.groovy)
    ↓
LSPManager (core/LSPManager.groovy) [Singleton]
    ↓
LSPServerRegistry (core/LSPServerRegistry.groovy)
    ↓
LSPClient (core/LSPClient.groovy)
    ↓
JsonRpcHandler (core/JsonRpcHandler.groovy)
    ↓
Language Server Process (typescript-language-server, pyright, etc.)
```

## Configuration

### Enable LSP

Add to `~/.glm/config.json`:
```json
{
  "lsp": {
    "enabled": true,
    "diagnosticTimeout": 3000
  }
}
```

### Environment Variables
- `GLM_LSP_ENABLED` - Override enabled state (true/false)

## Troubleshooting

### Server Not Starting
1. Check if language server is installed (`which typescript-language-server`, etc.)
2. For npx-based servers, ensure npm/node is available
3. Check server logs for errors

### No Diagnostics
1. Verify file extension is supported
2. Check project root contains marker file (package.json, go.mod, etc.)
3. Increase diagnostic timeout in config

### Performance Issues
1. Reduce diagnostic timeout for faster feedback
2. Disable LSP for specific languages in config
3. Consider using faster language servers (pyright vs pylsp)

#### 1.2 Diagnostic Model Enhancement

**File:** `models/Diagnostic.groovy` (existing - enhance)

```groovy
class Diagnostic {
    String code
    String source
    Integer severity  // 1=Error, 2=Warning, 3=Info, 4=Hint
    String message
    Range range
    List<DiagnosticRelatedInformation> relatedInformation
    Map tags  // Unnecessary, Deprecated
    
    static class Range {
        Position start
        Position end
    }
    
    static class Position {
        int line      // 0-based
        int character // 0-based
    }
    
    static class DiagnosticRelatedInformation {
        Location location
        String message
    }
    
    static class Location {
        String uri
        Range range
    }
    
    static Diagnostic fromLsp(Map lsp) {
        def diag = new Diagnostic()
        diag.code = lsp.code
        diag.source = lsp.source
        diag.severity = lsp.severity
        diag.message = lsp.message
        diag.tags = lsp.tags
        
        if (lsp.range) {
            diag.range = new Range(
                start: new Position(
                    line: lsp.range.start.line,
                    character: lsp.range.start.character
                ),
                end: new Position(
                    line: lsp.range.end.line,
                    character: lsp.range.end.character
                )
            )
        }
        
        if (lsp.relatedInformation) {
            diag.relatedInformation = lsp.relatedInformation.collect { info ->
                new DiagnosticRelatedInformation(
                    location: new Location(
                        uri: info.location.uri,
                        range: new Range(
                            start: new Position(
                                line: info.location.range.start.line,
                                character: info.location.range.start.character
                            ),
                            end: new Position(
                                line: info.location.range.end.line,
                                character: info.location.range.end.character
                            )
                        )
                    ),
                    message: info.message
                )
            }
        }
        
        return diag
    }
}
```

### Phase 2: LSP Client & Server Management (Days 4-7)

#### 2.1 LSP Client Implementation

**File:** `core/LSPClient.groovy`

```groovy
class LSPClient {
    private final String serverId
    private final String rootPath
    private final Process process
    private final JsonRpcHandler rpc
    private final Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>()
    private final Map<String, Integer> documentVersions = new ConcurrentHashMap<>()
    private final String rootUri
    private final Semaphore initializationComplete = new Semaphore(0)
    private volatile boolean initialized = false
    private volatile boolean failed = false
    
    LSPClient(String serverId, String rootPath, Process process) {
        this.serverId = serverId
        this.rootPath = rootPath
        this.process = process
        this.rpc = new JsonRpcHandler(process)
        this.rootUri = pathToUri(rootPath)
    }
    
    Map initialize() {
        def capabilities = [
            textDocument: [
                synchronization: [
                    dynamicRegistration: false,
                    willSave: false,
                    didSave: true,
                    willSaveWaitUntil: false
                ],
                completion: [
                    dynamicRegistration: false,
                    completionItem: [
                        resolveSupport: [properties: ["documentation", "detail", "additionalTextEdits"]]
                    ]
                ],
                hover: [dynamicRegistration: false],
                definitions: [dynamicRegistration: false],
                references: [dynamicRegistration: false],
                documentSymbol: [dynamicRegistration: false],
                codeAction: [dynamicRegistration: false],
                diagnostics: [dynamicRegistration: false]
            ],
            workspace: [
                applyEdit: false,
                workspaceFolders: true
            ]
        ]
        
        def params = [
            processId: ProcessHandle.current().pid(),
            rootUri: rootUri,
            capabilities: capabilities,
            initializationOptions: getServerInitializationOptions()
        ]
        
        try {
            def result = rpc.sendRequest("initialize", params, 30000)
            rpc.sendNotification("initialized", [:])
            
            // Start notification loop for diagnostics
            startNotificationLoop()
            
            initialized = true
            return result
        } catch (Exception e) {
            failed = true
            throw new RuntimeException("Failed to initialize LSP server ${serverId}", e)
        }
    }
    
    private void startNotificationLoop() {
        Thread.start {
            try {
                rpc.startMessageLoop { message ->
                    handleNotification(message)
                }
            } catch (Exception e) {
                if (!failed) {
                    System.err.println "LSP notification loop error for ${serverId}: ${e.message}"
                }
            }
        }
    }
    
    private void handleNotification(Map message) {
        switch (message.method) {
            case "textDocument/publishDiagnostics":
                handlePublishDiagnostics(message.params)
                break
        }
    }
    
    private void handlePublishDiagnostics(Map params) {
        def uri = params.textDocument.uri
        def rawDiags = params.diagnostics ?: []
        def diagnostics = rawDiags.collect { Diagnostic.fromLsp(it) }
        this.diagnostics.put(uri, diagnostics)
        initializationComplete.release()
    }
    
    void didOpen(String filePath, String content) {
        if (!initialized) throw new IllegalStateException("Client not initialized")
        
        def uri = pathToUri(filePath)
        documentVersions.put(uri, (documentVersions.getOrDefault(uri, 0) ?: 0) + 1)
        
        def params = [
            textDocument: [
                uri: uri,
                languageId: getLanguageId(filePath),
                version: documentVersions.get(uri),
                text: content
            ]
        ]
        rpc.sendNotification("textDocument/didOpen", params)
    }
    
    void didChange(String filePath, String content) {
        if (!initialized) throw new IllegalStateException("Client not initialized")
        
        def uri = pathToUri(filePath)
        documentVersions.put(uri, (documentVersions.getOrDefault(uri, 0) ?: 0) + 1)
        
        def params = [
            textDocument: [
                uri: uri,
                version: documentVersions.get(uri)
            ],
            contentChanges: [
                [
                    text: content
                ]
            ]
        ]
        rpc.sendNotification("textDocument/didChange", params)
    }
    
    void didClose(String filePath) {
        def uri = pathToUri(filePath)
        def params = [textDocument: [uri: uri]]
        rpc.sendNotification("textDocument/didClose", params)
    }
    
    List<Diagnostic> getDiagnostics(String filePath) {
        def uri = pathToUri(filePath)
        return diagnostics.getOrDefault(uri, [])
    }
    
    List<Diagnostic> waitForDiagnostics(String filePath, long timeoutMs = 3000) {
        def uri = pathToUri(filePath)
        def startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            def diags = diagnostics.get(uri)
            if (diags != null) {
                return diags
            }
            Thread.sleep(100)
        }
        
        return diagnostics.getOrDefault(uri, [])
    }
    
    // Code intelligence queries (Phase 5)
    List<Location> goToDefinition(String filePath, int line, int character) {
        def uri = pathToUri(filePath)
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character]
        ]
        
        try {
            def result = rpc.sendRequest("textDocument/definition", params, 5000)
            if (result) {
                return flattenLocations(result)
            }
        } catch (Exception e) {
            System.err.println "Definition query failed: ${e.message}"
        }
        return []
    }
    
    List<Location> findReferences(String filePath, int line, int character) {
        def uri = pathToUri(filePath)
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character],
            context: [includeDeclaration: true]
        ]
        
        try {
            def result = rpc.sendRequest("textDocument/references", params, 5000)
            if (result) {
                return flattenLocations(result)
            }
        } catch (Exception e) {
            System.err.println "References query failed: ${e.message}"
        }
        return []
    }
    
    Map hover(String filePath, int line, int character) {
        def uri = pathToUri(filePath)
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character]
        ]
        
        try {
            return rpc.sendRequest("textDocument/hover", params, 5000)
        } catch (Exception e) {
            System.err.println "Hover query failed: ${e.message}"
        }
        return null
    }
    
    private List<Location> flattenLocations(result) {
        if (result instanceof List) {
            return result.collect { locationFromLsp(it) }
        } else if (result != null) {
            return [locationFromLsp(result)]
        }
        return []
    }
    
    private Location locationFromLsp(lspLocation) {
        return new Location(
            uri: lspLocation.uri,
            range: new Range(
                start: new Position(
                    line: lspLocation.range.start.line,
                    character: lspLocation.range.start.character
                ),
                end: new Position(
                    line: lspLocation.range.end.line,
                    character: lspLocation.range.end.character
                )
            )
        )
    }
    
    void shutdown() {
        try {
            rpc.sendRequest("shutdown", [:], 5000)
            rpc.sendNotification("exit", [:])
        } catch (Exception ignored) {
        }
        rpc.shutdown()
    }
    
    boolean isFailed() { return failed }
    
    private String pathToUri(String path) {
        return "file://" + new File(path).absolutePath
    }
    
    private String getLanguageId(String filePath) {
        def ext = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase()
        def map = [
            'ts': 'typescript', 'tsx': 'typescript',
            'js': 'javascript', 'jsx': 'javascript',
            'java': 'java', 'groovy': 'groovy',
            'py': 'python', 'go': 'go',
            'rs': 'rust', 'rb': 'ruby',
            'md': 'markdown', 'json': 'json',
            'yaml': 'yaml', 'yml': 'yaml',
            'xml': 'xml', 'html': 'html',
            'css': 'css', 'sql': 'sql'
        ]
        return map.get(ext, 'plaintext')
    }
    
    private Map getServerInitializationOptions() {
        return [:]  // Override in subclasses for server-specific options
    }
}
```

#### 2.2 LSP Server Registry

**File:** `core/LSPServerRegistry.groovy`

```groovy
class LSPServerConfig {
    String id
    String displayName
    List<String> command
    List<String> extensions
    List<String> filenameMarkers
    Map<String, String> env
    Map initializationOptions
    boolean autoInstall = false
    String installCommand
    String installDescription
    
    LSPServerConfig() {
        this.env = [:]
        this.initializationOptions = [:]
        this.extensions = []
        this.filenameMarkers = []
    }
    
    static LSPServerConfig typescript() {
        def config = new LSPServerConfig(
            id: 'typescript',
            displayName: 'TypeScript/JavaScript',
            command: ['npx', '-y', 'typescript-language-server', '--stdio'],
            extensions: ['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs'],
            filenameMarkers: ['package.json', 'tsconfig.json', 'jsconfig.json'],
            autoInstall: true,
            installCommand: 'npx -y typescript-language-server --stdio',
            installDescription: 'Run: npx -y typescript-language-server --stdio'
        )
        return config
    }
    
    static LSPServerConfig python() {
        def config = new LSPServerConfig(
            id: 'python',
            displayName: 'Python (Pyright)',
            command: ['npx', '-y', 'pyright-langserver', '--stdio'],
            extensions: ['.py', '.pyw'],
            filenameMarkers: ['pyproject.toml', 'setup.py', 'requirements.txt', 'setup.cfg', 'pyrightconfig.json'],
            autoInstall: true,
            installCommand: 'npx -y pyright-langserver --stdio',
            installDescription: 'Run: npx -y pyright-langserver --stdio'
        )
        return config
    }
    
    static LSPServerConfig java() {
        def config = new LSPServerConfig(
            id: 'java',
            displayName: 'Java (Eclipse JDTLS)',
            command: ['jdtls'],
            extensions: ['.java'],
            filenameMarkers: ['pom.xml', 'build.gradle', 'settings.gradle', 'build.gradle.kts', 'settings.gradle.kts'],
            autoInstall: false,
            installDescription: 'Install Eclipse JDTLS from https://github.com/eclipse/eclipse.jdt.ls'
        )
        config.env.putAll([
            'JDTLS_JAVA_HOME': System.getProperty('java.home'),
            'JDTLS_HOME': System.getenv('JDTLS_HOME') ?: ''
        ])
        return config
    }
    
    static LSPServerConfig groovy() {
        def config = new LSPServerConfig(
            id: 'groovy',
            displayName: 'Groovy',
            command: ['groovy-language-server'],
            extensions: ['.groovy', '.gvy', '.gy'],
            filenameMarkers: ['build.gradle', 'settings.gradle', 'gradle.properties', '.groovylintrc.json'],
            autoInstall: false,
            installDescription: 'Install groovy-language-server from https://github.com/prominic/groovy-language-server'
        )
        return config
    }
    
    static LSPServerConfig go() {
        def config = new LSPServerConfig(
            id: 'go',
            displayName: 'Go (gopls)',
            command: ['gopls'],
            extensions: ['.go'],
            filenameMarkers: ['go.mod', 'go.sum', 'go.work'],
            autoInstall: true,
            installCommand: 'go install golang.org/x/tools/gopls@latest',
            installDescription: 'Run: go install golang.org/x/tools/gopls@latest'
        )
        return config
    }
    
    static LSPServerConfig rust() {
        def config = new LSPServerConfig(
            id: 'rust',
            displayName: 'Rust (rust-analyzer)',
            command: ['rust-analyzer'],
            extensions: ['.rs'],
            filenameMarkers: ['Cargo.toml', 'Cargo.lock', 'rust-toolchain.toml'],
            autoInstall: false,
            installDescription: 'Install rust-analyzer from https://rust-analyzer.github.io'
        )
        return config
    }
    
    static LSPServerConfig json() {
        def config = new LSPServerConfig(
            id: 'json',
            displayName: 'JSON',
            command: ['npx', '-y', 'vscode-json-languageserver', '--stdio'],
            extensions: ['.json', '.jsonc', '.jsonl'],
            filenameMarkers: ['package.json', 'tsconfig.json', '.eslintrc.json'],
            autoInstall: true,
            installCommand: 'npx -y vscode-json-languageserver --stdio',
            installDescription: 'Run: npx -y vscode-json-languageserver --stdio'
        )
        return config
    }
    
    static LSPServerConfig yaml() {
        def config = new LSPServerConfig(
            id: 'yaml',
            displayName: 'YAML',
            command: ['npx', '-y', 'yaml-language-server', '--stdio'],
            extensions: ['.yaml', '.yml'],
            filenameMarkers: ['.yaml-lint', '.yamllint'],
            autoInstall: true,
            installCommand: 'npx -y yaml-language-server --stdio',
            installDescription: 'Run: npx -y yaml-language-server --stdio'
        )
        return config
    }
    
    static LSPServerConfig html() {
        def config = new LSPServerConfig(
            id: 'html',
            displayName: 'HTML',
            command: ['npx', '-y', 'vscode-html-languageserver', '--stdio'],
            extensions: ['.html', '.htm'],
            filenameMarkers: [],
            autoInstall: true,
            installCommand: 'npx -y vscode-html-languageserver --stdio',
            installDescription: 'Run: npx -y vscode-html-languageserver --stdio'
        )
        return config
    }
    
    static LSPServerConfig css() {
        def config = new LSPServerConfig(
            id: 'css',
            displayName: 'CSS',
            command: ['npx', '-y', 'vscode-css-languageserver', '--stdio'],
            extensions: ['.css', '.scss', '.sass', '.less', '.styl'],
            filenameMarkers: [],
            autoInstall: true,
            installCommand: 'npx -y vscode-css-languageserver --stdio',
            installDescription: 'Run: npx -y vscode-css-languageserver --stdio'
        )
        return config
    }
}

class LSPServerRegistry {
    private static final Map<String, LSPServerConfig> SERVERS = [
        'typescript': LSPServerConfig.typescript(),
        'python': LSPServerConfig.python(),
        'java': LSPServerConfig.java(),
        'groovy': LSPServerConfig.groovy(),
        'go': LSPServerConfig.go(),
        'rust': LSPServerConfig.rust(),
        'json': LSPServerConfig.json(),
        'yaml': LSPServerConfig.yaml(),
        'html': LSPServerConfig.html(),
        'css': LSPServerConfig.css()
    ]
    
    LSPServerConfig getServerForFile(String filePath) {
        def ext = getExtension(filePath)
        
        for (def server : SERVERS.values()) {
            if (server.extensions.any { ext.equalsIgnoreCase(it.substring(1)) }) {
                return server
            }
        }
        
        return null
    }
    
    String findProjectRoot(String startDir, LSPServerConfig config) {
        def dir = new File(startDir).absoluteFile
        def maxDepth = 10
        def depth = 0
        
        while (dir != null && depth < maxDepth) {
            for (def marker : config.filenameMarkers) {
                if (new File(dir, marker).exists()) {
                    return dir.absolutePath
                }
            }
            dir = dir.parentFile
            depth++
        }
        
        return startDir
    }
    
    Process spawnServer(LSPServerConfig config, String rootPath) {
        def pb = new ProcessBuilder(config.command)
        pb.directory(new File(rootPath))
        
        config.env.each { key, value ->
            pb.environment().put(key, value)
        }
        
        pb.redirectErrorStream(true)
        return pb.start()
    }
    
    List<LSPServerConfig> getAvailableServers() {
        return new ArrayList<>(SERVERS.values())
    }
    
    LSPServerConfig getServer(String id) {
        return SERVERS.get(id)
    }
    
    private String getExtension(String filePath) {
        def idx = filePath.lastIndexOf('.')
        return idx > 0 ? filePath.substring(idx).toLowerCase() : ''
    }
}
```

#### 2.3 LSP Manager

**File:** `core/LSPManager.groovy`

```groovy
@Singleton
class LSPManager {
    private final Map<String, LSPClient> clients = new ConcurrentHashMap<>()
    private final Set<String> brokenServers = new CopyOnWriteArraySet<>()
    private final int maxRetries = 3
    private final int diagnosticTimeout = 3000
    private final registry = new LSPServerRegistry()
    private volatile boolean enabled = true
    
    LSPClient getClient(String filePath) {
        if (!enabled) return null
        
        def config = registry.getServerForFile(filePath)
        if (config == null) {
            return null
        }
        
        if (brokenServers.contains(config.id)) {
            return null
        }
        
        def root = registry.findProjectRoot(filePath, config)
        def key = "${config.id}:${root}"
        
        return clients.computeIfAbsent(key) { k ->
            createClient(config, root)
        }
    }
    
    private LSPClient createClient(LSPServerConfig config, String root) {
        def process = registry.spawnServer(config, root)
        def client = new LSPClient(config.id, root, process)
        
        try {
            client.initialize()
            return client
        } catch (Exception e) {
            System.err.println "Failed to start LSP server ${config.id}: ${e.message}"
            brokenServers.add(config.id)
            process.destroy()
            return null
        }
    }
    
    List<Diagnostic> touchFile(String filePath, boolean waitForDiagnostics = true) {
        def client = getClient(filePath)
        if (client == null) {
            return []
        }
        
        def content = new File(filePath).text
        client.didOpen(filePath, content)
        
        if (waitForDiagnostics) {
            return client.waitForDiagnostics(filePath, diagnosticTimeout)
        }
        
        return client.getDiagnostics(filePath)
    }
    
    Map<String, List<Diagnostic>> getAllDiagnostics() {
        def result = new HashMap<String, List<Diagnostic>>()
        
        clients.each { key, client ->
            if (client != null && !client.isFailed()) {
                // This would require additional API - for now return empty
            }
        }
        
        return result
    }
    
    void onFileModified(String filePath, String newContent) {
        def client = getClient(filePath)
        if (client != null) {
            client.didChange(filePath, newContent)
        }
    }
    
    void onFileClosed(String filePath) {
        def client = getClient(filePath)
        if (client != null) {
            client.didClose(filePath)
        }
    }
    
    void shutdown() {
        clients.each { key, client ->
            if (client != null) {
                try {
                    client.shutdown()
                } catch (Exception ignored) {
                }
            }
        }
        clients.clear()
    }
    
    void setEnabled(boolean value) {
        this.enabled = value
    }
    
    boolean isEnabled() {
        return enabled
    }
    
    List<String> getBrokenServers() {
        return new ArrayList<>(brokenServers)
    }
    
    void clearBrokenServer(String serverId) {
        brokenServers.remove(serverId)
    }
    
    void retryServer(String serverId) {
        brokenServers.remove(serverId)
        // Next file access will recreate the client
    }
}
```

### Phase 3: Tool Integration (Days 8-10)

#### 3.1 Enhanced WriteFileTool with LSP Diagnostics

**File:** Update existing `tools/WriteFileTool.groovy`

```groovy
class WriteFileTool implements Tool {
    private static final DiagnosticFormatter diagnosticFormatter = new DiagnosticFormatter()
    
    String name = "write"
    String description = """Write content to a file, optionally waiting for LSP diagnostics.
    
Parameters:
- path: Target file path (required)
- content: Content to write (required)
- waitForDiagnostics: Whether to wait for LSP feedback (default: true)
    
Returns:
- Success message with file path and size
- LSP diagnostics (errors, warnings) if waitForDiagnostics=true and LSP is active"""
    
    Object execute(Map<String, Object> args) {
        String path = args.path
        String content = args.content
        boolean waitForDiagnostics = args.getOrDefault('waitForDiagnostics', true) as Boolean
        
        if (!path) {
            return errorResult("Missing required parameter: path")
        }
        if (content == null) {
            return errorResult("Missing required parameter: content")
        }
        
        try {
            def file = new File(path)
            file.parentFile.mkdirs()
            file.text = content
            
            def result = buildSuccessResult(path, content)
            
            // Get LSP diagnostics
            if (waitForDiagnostics) {
                def diagnostics = LSPManager.instance.touchFile(path, true)
                result = addDiagnosticsToResult(result, diagnostics)
            }
            
            return result
            
        } catch (Exception e) {
            return errorResult("Failed to write file: ${e.message}")
        }
    }
    
    private Map buildSuccessResult(String path, String content) {
        [
            success: true,
            path: path,
            bytes: content.bytes.length,
            characters: content.length(),
            lines: content.readLines().size()
        ]
    }
    
    private Map addDiagnosticsToResult(Map result, List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return result + [lspDiagnostics: [enabled: true, count: 0]]
        }
        
        def errors = diagnostics.findAll { it.severity == 1 }
        def warnings = diagnostics.findAll { it.severity == 2 }
        def info = diagnostics.findAll { it.severity == 3 }
        
        def formattedDiagnostics = diagnostics.take(30).collect { diag ->
            diagnosticFormatter.format(diag)
        }
        
        result + [
            lspDiagnostics: [
                enabled: true,
                count: diagnostics.size(),
                errors: errors.size(),
                warnings: warnings.size(),
                info: info.size(),
                diagnostics: formattedDiagnostics,
                hasBlockingErrors: !errors.isEmpty()
            ]
        ]
    }
    
    private Map errorResult(String message) {
        [success: false, error: message]
    }
}
```

#### 3.2 Diagnostic Formatter

**File:** `core/DiagnosticFormatter.groovy`

```groovy
class DiagnosticFormatter {
    private static final Map<Integer, String> SEVERITY_NAMES = [
        1: "ERROR",
        2: "WARNING",
        3: "INFO",
        4: "HINT"
    ]
    
    private static final Map<Integer, String> SEVERITY_COLORS = [
        1: "red",
        2: "yellow",
        3: "blue",
        4: "gray"
    ]
    
    String format(Diagnostic diag) {
        def severity = SEVERITY_NAMES.get(diag.severity, "UNKNOWN")
        def line = diag.range?.start?.line ?: 0
        def col = diag.range?.start?.character ?: 0
        def code = diag.code ? "[${diag.code}] " : ""
        
        return "${severity}: ${code}${diag.message} (${formatLocation(line, col)})"
    }
    
    String formatLocation(int line, int column) {
        return "line ${line + 1}, column ${column + 1}"
    }
    
    String formatCompact(Diagnostic diag) {
        def severity = SEVERITY_NAMES.get(diag.severity, "?")
        def line = diag.range?.start?.line ?: 0
        return "${severity}@${line + 1}: ${diag.message.take(80)}"
    }
    
    String formatAll(List<Diagnostic> diagnostics, int limit = 50) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return ""
        }
        
        def sb = new StringBuilder()
        sb.append("\n")
        sb.append("═".repeat(60)).append("\n")
        sb.append(" LSP Diagnostics (").append(diagnostics.size()).append(" issues)\n")
        sb.append("═".repeat(60)).append("\n")
        
        diagnostics.take(limit).each { diag ->
            sb.append("  ").append(format(diag)).append("\n")
        }
        
        if (diagnostics.size() > limit) {
            sb.append("  ... and ").append(diagnostics.size() - limit).append(" more\n")
        }
        
        sb.append("═".repeat(60)).append("\n")
        
        return sb.toString()
    }
    
    String formatSummary(List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "No issues"
        }
        
        def errors = diagnostics.findAll { it.severity == 1 }.size()
        def warnings = diagnostics.findAll { it.severity == 2 }.size()
        def info = diagnostics.findAll { it.severity >= 3 }.size()
        
        def parts = []
        if (errors > 0) parts.add("${errors} error${errors > 1 ? 's' : ''}")
        if (warnings > 0) parts.add("${warnings} warning${warnings > 1 ? 's' : ''}")
        if (info > 0) parts.add("${info} info")
        
        return parts.join(", ")
    }
    
    List<String> formatForAgent(List<Diagnostic> diagnostics) {
        diagnostics.collect { diag ->
            def line = diag.range?.start?.line ?: 0
            def col = diag.range?.start?.character ?: 0
            def severity = SEVERITY_NAMES.get(diag.severity, "UNKNOWN")
            "${severity} at line ${line + 1}, col ${col + 1}: ${diag.message}"
        }
    }
}
```

### Phase 4: Configuration System (Day 11)

**File:** `core/LSPConfig.groovy`

```groovy
class LSPConfig {
    boolean enabled = true
    int diagnosticTimeout = 3000
    boolean autoEnable = true
    Set<String> disabledLanguages = new HashSet<>()
    Set<String> enabledLanguages = new HashSet<>()  // If non-empty, only these are enabled
    Map<String, Map<String, Object>> serverOverrides = [:]
    boolean showWarnings = true
    boolean showInfo = false
    
    static LSPConfig load() {
        def configPath = getConfigPath()
        if (configPath.exists()) {
            try {
                def slurper = new groovy.json.JsonSlurper()
                def json = slurper.parse(configPath)
                return fromJson(json)
            } catch (Exception e) {
                System.err.println "Failed to load LSP config: ${e.message}"
            }
        }
        return new LSPConfig()
    }
    
    private static File getConfigPath() {
        def home = System.getProperty("user.home")
        return new File(home, ".glm/config.json")
    }
    
    private static LSPConfig fromJson(Map json) {
        def config = new LSPConfig()
        
        if (json.lsp instanceof Map) {
            def lsp = json.lsp
            config.enabled = lsp.getOrDefault("enabled", true)
            config.diagnosticTimeout = lsp.getOrDefault("diagnosticTimeout", 3000) as Integer
            config.autoEnable = lsp.getOrDefault("autoEnable", true)
            
            if (lsp.disabledLanguages instanceof List) {
                config.disabledLanguages = new HashSet<>(lsp.disabledLanguages)
            }
            if (lsp.enabledLanguages instanceof List) {
                config.enabledLanguages = new HashSet<>(lsp.enabledLanguages)
            }
            if (lsp.serverOverrides instanceof Map) {
                config.serverOverrides = lsp.serverOverrides
            }
        }
        
        return config
    }
    
    void save() {
        def configPath = getConfigPath()
        def json = toJson()
        configPath.text = json
    }
    
    private String toJson() {
        def sb = new StringBuilder("{\n  \"lsp\": {\n")
        sb.append("    \"enabled\": ${enabled},\n")
        sb.append("    \"diagnosticTimeout\": ${diagnosticTimeout},\n")
        sb.append("    \"autoEnable\": ${autoEnable},\n")
        
        if (!disabledLanguages.isEmpty()) {
            def langs = disabledLanguages.collect { "\"${it}\"" }.join(", ")
            sb.append("    \"disabledLanguages\": [${langs}],\n")
        }
        
        if (!enabledLanguages.isEmpty()) {
            def langs = enabledLanguages.collect { "\"${it}\"" }.join(", ")
            sb.append("    \"enabledLanguages\": [${langs}],\n")
        }
        
        sb.append("    \"showWarnings\": ${showWarnings},\n")
        sb.append("    \"showInfo\": ${showInfo}\n")
        sb.append("  }\n}")
        return sb.toString()
    }
    
    boolean isLanguageEnabled(String languageId) {
        if (!enabled) return false
        if (!enabledLanguages.isEmpty()) {
            return enabledLanguages.contains(languageId)
        }
        return !disabledLanguages.contains(languageId)
    }
}
```

### Phase 5: Agent Integration & LSP Query Tool (Days 12-14)

#### 5.1 LSP Query Tool

**File:** `tools/LSPTool.groovy`

```groovy
class LSPTool implements Tool {
    private static final DiagnosticFormatter formatter = new DiagnosticFormatter()
    
    String name = "lsp"
    String description = """Query language server for code intelligence.
    
Operations:
- diagnostics: Get all diagnostics for a file
- goto_definition: Find where a symbol is defined
- references: Find all references to a symbol
- hover: Get type/documentation info at position
- document_symbols: List all symbols in a file
- completion: Get completion suggestions (experimental)
    
Parameters:
- operation: The query type (required)
- filePath: Target file (required for most operations)
- line: Line number (1-based, required for goto_definition, references, hover)
- character: Column number (1-based, required for goto_definition, references, hover)
    
Example:
{ "operation": "diagnostics", "filePath": "src/main.ts" }
{ "operation": "goto_definition", "filePath": "src/main.ts", "line": 10, "character": 15 }"""
    
    Object execute(Map<String, Object> args) {
        String operation = args.operation
        String filePath = args.filePath
        Integer line = args.line
        Integer character = args.character
        
        if (!operation) {
            return [success: false, error: "Missing required parameter: operation"]
        }
        
        if (!filePath) {
            return [success: false, error: "Missing required parameter: filePath"]
        }
        
        def file = new File(filePath)
        if (!file.exists()) {
            return [success: false, error: "File not found: ${filePath}"]
        }
        
        def client = LSPManager.instance.getClient(filePath)
        if (client == null) {
            return [success: false, error: "No LSP server available for ${filePath}", 
                    suggestion: "Ensure the language server is installed and configured"]
        }
        
        switch (operation) {
            case "diagnostics":
                return handleDiagnostics(client, filePath)
            case "goto_definition":
                return handleGotoDefinition(client, filePath, line, character)
            case "references":
                return handleReferences(client, filePath, line, character)
            case "hover":
                return handleHover(client, filePath, line, character)
            case "document_symbols":
                return handleDocumentSymbols(client, filePath)
            case "completion":
                return handleCompletion(client, filePath, line, character)
            default:
                return [success: false, error: "Unknown operation: ${operation}",
                        validOperations: ["diagnostics", "goto_definition", "references", "hover", "document_symbols", "completion"]]
        }
    }
    
    private Map handleDiagnostics(LSPClient client, String filePath) {
        def diagnostics = client.getDiagnostics(filePath)
        
        def errors = diagnostics.findAll { it.severity == 1 }
        def warnings = diagnostics.findAll { it.severity == 2 }
        
        return [
            success: true,
            operation: "diagnostics",
            filePath: filePath,
            count: diagnostics.size(),
            summary: formatter.formatSummary(diagnostics),
            errors: errors.size(),
            warnings: warnings.size(),
            diagnostics: diagnostics.collect { diag ->
                [
                    severity: diag.severity,
                    message: diag.message,
                    line: diag.range?.start?.line + 1,
                    column: diag.range?.start?.character + 1,
                    code: diag.code,
                    formatted: formatter.format(diag)
                ]
            }
        ]
    }
    
    private Map handleGotoDefinition(LSPClient client, String filePath, Integer line, Integer character) {
        if (line == null || character == null) {
            return [success: false, error: "line and character are required for goto_definition"]
        }
        
        def locations = client.goToDefinition(filePath, line - 1, character - 1)
        
        if (locations.isEmpty()) {
            return [success: true, operation: "goto_definition", 
                    message: "No definition found"]
        }
        
        return [
            success: true,
            operation: "goto_definition",
            count: locations.size(),
            locations: locations.collect { loc ->
                [
                    uri: loc.uri,
                    filePath: uriToPath(loc.uri),
                    line: loc.range.start.line + 1,
                    column: loc.range.start.character + 1,
                    endLine: loc.range.end.line + 1,
                    endColumn: loc.range.end.character + 1
                ]
            }
        ]
    }
    
    private Map handleReferences(LSPClient client, String filePath, Integer line, Integer character) {
        if (line == null || character == null) {
            return [success: false, error: "line and character are required for references"]
        }
        
        def locations = client.findReferences(filePath, line - 1, character - 1)
        
        if (locations.isEmpty()) {
            return [success: true, operation: "references",
                    message: "No references found"]
        }
        
        return [
            success: true,
            operation: "references",
            count: locations.size(),
            locations: locations.collect { loc ->
                [
                    uri: loc.uri,
                    filePath: uriToPath(loc.uri),
                    line: loc.range.start.line + 1,
                    column: loc.range.start.character + 1
                ]
            }
        ]
    }
    
    private Map handleHover(LSPClient client, String filePath, Integer line, Integer character) {
        if (line == null || character == null) {
            return [success: false, error: "line and character are required for hover"]
        }
        
        def result = client.hover(filePath, line - 1, character - 1)
        
        if (result == null || result.contents == null) {
            return [success: true, operation: "hover", message: "No hover information available"]
        }
        
        def content = result.contents
        String textContent = null
        
        if (content instanceof List && !content.isEmpty()) {
            textContent = content.find { it instanceof String } ?: content[0]?.value
        } else if (content instanceof Map) {
            textContent = content.value
        } else if (content instanceof String) {
            textContent = content
        }
        
        return [
            success: true,
            operation: "hover",
            content: textContent,
            range: result.range ? [
                startLine: result.range.start.line + 1,
                startColumn: result.range.start.character + 1,
                endLine: result.range.end.line + 1,
                endColumn: result.range.end.character + 1
            ] : null
        ]
    }
    
    private Map handleDocumentSymbols(LSPClient client, String filePath) {
        // Would need additional client method implementation
        return [success: false, error: "document_symbols not yet implemented"]
    }
    
    private Map handleCompletion(LSPClient client, String filePath, Integer line, Integer character) {
        return [success: false, error: "completion not yet implemented",
                suggestion: "Use editor completion instead"]
    }
    
    private String uriToPath(String uri) {
        if (uri.startsWith("file://")) {
            return uri.substring(7)
        }
        return uri
    }
}
```

#### 5.2 Agent Loop Integration

**File:** `core/LSPIntegration.groovy` (new mixin/trait)

```groovy
trait LSPIntegration {
    private static final DiagnosticFormatter diagnosticFormatter = new DiagnosticFormatter()
    
    String checkForLSPDiagnostics(String filePath) {
        try {
            def diagnostics = LSPManager.instance.touchFile(filePath, true)
            
            if (diagnostics.isEmpty()) {
                return null
            }
            
            def errors = diagnostics.findAll { it.severity == 1 }
            if (!errors.isEmpty()) {
                return "LSP Errors detected:\n" + 
                       diagnosticFormatter.formatAll(errors)
            }
            
            def warnings = diagnostics.findAll { it.severity == 2 }
            if (!warnings.isEmpty()) {
                return "LSP Warnings:\n" +
                       diagnosticFormatter.formatAll(warnings)
            }
            
            return null
            
        } catch (Exception e) {
            System.err.println "LSP diagnostic check failed: ${e.message}"
            return null
        }
    }
    
    boolean shouldWaitForDiagnostics(String lastTool) {
        return lastTool in ["write", "edit"]
    }
}
```

### Phase 6: TUI Integration (Days 15-17)

#### 6.1 Diagnostic Panel for TUI

**File:** `tui/lsp/DiagnosticPanel.groovy` (for TUI frameworks)

```groovy
class DiagnosticPanel {
    private List<Diagnostic> currentDiagnostics = []
    private String currentFile
    private boolean expanded = false
    
    void showDiagnostics(String filePath, List<Diagnostic> diagnostics) {
        this.currentFile = filePath
        this.currentDiagnostics = diagnostics
    }
    
    String render() {
        if (currentDiagnostics.isEmpty()) {
            return ""
        }
        
        def sb = new StringBuilder()
        sb.append("\n╔").append("═".repeat(58)).append("╗\n")
        sb.append("║ LSP Diagnostics ").append(currentFile ? " - ${currentFile}" : "").append(" ".repeat(Math.max(0, 50 - (currentFile?.size() ?: 0)))).append("║\n")
        sb.append("╠").append("═".repeat(58)).append("╣\n")
        
        currentDiagnostics.each { diag ->
            def line = diag.range?.start?.line + 1
            def severity = diag.severity == 1 ? "ERROR" : diag.severity == 2 ? "WARN" : "INFO"
            def icon = diag.severity == 1 ? "✗" : diag.severity == 2 ? "⚠" : "ℹ"
            def message = diag.message.take(50)
            
            sb.append("║ ${icon} [${severity}] ${message.padEnd(40)} ${"L${line}".padEnd(6)} ║\n")
        }
        
        sb.append("╚").append("═".repeat(58)).append("╝\n")
        
        return sb.toString()
    }
    
    void toggleExpanded() {
        expanded = !expanded
    }
}
```

## File Structure Summary

```
glm-cli-jbang/
├── core/
│   ├── LSPManager.groovy           # Singleton managing all LSP clients
│   ├── LSPClient.groovy            # JSON-RPC communication with server
│   ├── LSPServerRegistry.groovy    # Server definitions and spawning
│   ├── JsonRpcHandler.groovy       # Low-level JSON-RPC protocol
│   ├── DiagnosticFormatter.groovy  # Format diagnostics for display
│   ├── LSPConfig.groovy            # Configuration handling
│   └── LSPIntegration.groovy       # Agent integration mixin
├── models/
│   └── Diagnostic.groovy           # Diagnostic data model (enhanced)
├── tools/
│   ├── WriteFileTool.groovy        # Modified to include diagnostics
│   ├── EditFileTool.groovy         # Modified to include diagnostics
│   └── LSPTool.groovy              # Direct LSP queries
└── tui/
    └── lsp/
        └── DiagnosticPanel.groovy  # TUI component for diagnostics
```

## Testing Strategy

### Unit Tests
- JSON-RPC message parsing and serialization
- Root detection algorithm
- Server registry matching
- Diagnostic model conversion

### Integration Tests
- Full LSP lifecycle (spawn, initialize, diagnose, shutdown)
- Multiple concurrent LSP servers
- Server unavailability handling
- Timeout behavior

### Agent Tests
- Write → diagnostic → self-correct loop
- LSP query tool responses
- Performance under load

## Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| Diagnostic feedback | < 3s after write | Automated timing tests |
| Supported languages | ≥ 8 | Server registry count |
| Error self-correction | 80% pass rate | Agent test suite |
| Server crash recovery | Graceful | Manual testing |
| Memory usage | < 100MB per server | Profiling |

## Next Steps

1. Create core directory structure
2. Implement JsonRpcHandler
3. Implement LSPClient with basic diagnostics
4. Implement LSPServerRegistry
5. Implement LSPManager singleton
6. Modify WriteFileTool to integrate LSP
7. Add configuration system
8. Implement LSPTool for queries
9. Add TUI diagnostic display
10. Write comprehensive tests
