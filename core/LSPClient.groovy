package core

import models.Diagnostic
import models.DiagnosticRange
import models.Position
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * LSP Client for communication with a single language server.
 * Handles initialization, document sync, and diagnostics.
 */
class LSPClient {
    private final JsonRpcHandler rpc
    private final String serverName
    private String rootPath
    private final Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>()
    private final Map<String, Integer> fileVersions = new ConcurrentHashMap<>()
    private volatile CountDownLatch diagnosticLatch
    private volatile boolean initialized = false
    private volatile String status = "connected"
    
    LSPClient(JsonRpcHandler rpc, String serverName) {
        this.rpc = rpc
        this.serverName = serverName
        
        // Handle notifications from server
        rpc.onNotification = { method, params ->
            if (method == "textDocument/publishDiagnostics") {
                handlePublishDiagnostics(params)
            }
        }
    }
    
    /**
     * Get the project root path for this LSP client.
     */
    String getRootPath() {
        return rootPath
    }
    
    /**
     * Get the current status of this LSP client.
     */
    String getStatus() {
        return status
    }
    
    /**
     * Get the total number of diagnostics across all files.
     */
    int getTotalDiagnosticCount() {
        int count = 0
        diagnostics.values().each { diags ->
            count += diags.size()
        }
        return count
    }
    
    /**
     * Get the number of files with diagnostics.
     */
    int getFileCountWithDiagnostics() {
        return diagnostics.size()
    }
    
    /**
     * Get a summary of diagnostics (counts by severity).
     */
    Map<String, Integer> getDiagnosticSummary() {
        Map<String, Integer> summary = [
            error: 0,
            warning: 0,
            info: 0
        ]
        
        diagnostics.values().each { diags ->
            diags.each { diag ->
                if (diag.severity == 1) summary.error++
                else if (diag.severity == 2) summary.warning++
                else summary.info++
            }
        }
        
        return summary
    }
    
    /**
     * Initialize the LSP connection.
     * @param rootPath Project root path
     * @return Server capabilities
     */
    Map initialize(String rootPath) {
        if (initialized) return [:]
        
        this.rootPath = rootPath
        this.status = "initializing"
        
        try {
            rpc.startMessageLoop()
            
            def result = rpc.sendRequest("initialize", [
                processId: ProcessHandle.current().pid(),
                rootUri: "file://${rootPath}",
                rootPath: rootPath,
                capabilities: [
                    textDocument: [
                        synchronization: [
                            dynamicRegistration: false,
                            willSave: false,
                            willSaveWaitUntil: false,
                            didSave: true
                        ],
                        publishDiagnostics: [
                            relatedInformation: true
                        ]
                    ],
                    workspace: [
                        workspaceFolders: true
                    ]
                ],
                workspaceFolders: [
                    [uri: "file://${rootPath}", name: new File(rootPath).name]
                ]
            ], 10000) as Map
            
            // Send initialized notification
            rpc.sendNotification("initialized", [:])
            initialized = true
            this.status = "connected"
            
            return result
        } catch (Exception e) {
            this.status = "error"
            throw e
        }
    }
    
    /**
     * Notify server that a file was opened.
     * @param filePath Absolute file path
     * @param content File content
     */
    void didOpen(String filePath, String content) {
        def uri = "file://${filePath}"
        fileVersions.put(uri, 1)
        
        rpc.sendNotification("textDocument/didOpen", [
            textDocument: [
                uri: uri,
                languageId: detectLanguageId(filePath),
                version: 1,
                text: content
            ]
        ])
    }
    
    /**
     * Notify server that a file changed.
     * @param filePath Absolute file path
     * @param content New content
     */
    void didChange(String filePath, String content) {
        def uri = "file://${filePath}"
        def version = (fileVersions.get(uri) ?: 0) + 1
        fileVersions.put(uri, version)
        
        rpc.sendNotification("textDocument/didChange", [
            textDocument: [
                uri: uri,
                version: version
            ],
            contentChanges: [
                [text: content]
            ]
        ])
    }
    
    /**
     * Notify server that a file was saved.
     * @param filePath Absolute file path
     * @param content File content
     */
    void didSave(String filePath, String content) {
        def uri = "file://${filePath}"
        
        rpc.sendNotification("textDocument/didSave", [
            textDocument: [uri: uri],
            text: content
        ])
    }
    
    /**
     * Notify server that a file was closed.
     * @param filePath Absolute file path
     */
    void didClose(String filePath) {
        def uri = "file://${filePath}"
        fileVersions.remove(uri)
        
        rpc.sendNotification("textDocument/didClose", [
            textDocument: [uri: uri]
        ])
    }
    
    /**
     * Get current diagnostics for a file.
     * @param filePath Absolute file path
     * @return List of diagnostics
     */
    List<Diagnostic> getDiagnostics(String filePath) {
        def uri = "file://${filePath}"
        return diagnostics.getOrDefault(uri, [])
    }
    
    /**
     * Wait for diagnostics to arrive.
     * @param timeoutMs Timeout in milliseconds
     */
    void waitForDiagnostics(long timeoutMs = 3000) {
        diagnosticLatch = new CountDownLatch(1)
        try {
            diagnosticLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (InterruptedException ignored) {}
    }
    
    /**
     * Handle publishDiagnostics notification from server.
     */
    private void handlePublishDiagnostics(Map params) {
        def uri = params.uri as String
        def diagList = (params.diagnostics as List)?.collect { d ->
            def range = d.range as Map
            def start = range?.start as Map
            def end = range?.end as Map
            
            new Diagnostic(
                uri: uri,
                range: new DiagnosticRange(
                    start: new Position(
                        line: (start?.line ?: 0) as int,
                        character: (start?.character ?: 0) as int
                    ),
                    end: new Position(
                        line: (end?.line ?: 0) as int,
                        character: (end?.character ?: 0) as int
                    )
                ),
                severity: (d.severity ?: 1) as int,
                message: d.message as String ?: "",
                source: d.source as String ?: serverName,
                code: d.code?.toString()
            )
        } ?: []
        
        diagnostics.put(uri, diagList)
        diagnosticLatch?.countDown()
    }
    
    /**
     * Detect language ID from file extension.
     */
    private String detectLanguageId(String filePath) {
        def ext = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase()
        switch (ext) {
            case 'ts': return 'typescript'
            case 'tsx': return 'typescriptreact'
            case 'js': return 'javascript'
            case 'jsx': return 'javascriptreact'
            case 'java': return 'java'
            case 'groovy': return 'groovy'
            case 'py': return 'python'
            case 'go': return 'go'
            case 'rs': return 'rust'
            case 'json': return 'json'
            case 'yaml': case 'yml': return 'yaml'
            case 'md': return 'markdown'
            default: return 'plaintext'
        }
    }
    
    /**
     * Shutdown the LSP connection.
     */
    void shutdown() {
        try {
            status = "disconnected"
            rpc.sendRequest("shutdown", [:], 2000)
            rpc.sendNotification("exit", [:])
        } catch (Exception ignored) {}
        rpc.stop()
    }
    
    boolean isAlive() {
        return rpc.alive
    }
    
    // ============ Code Intelligence Methods ============
    
    /**
     * Go to definition of symbol at position.
     */
    List<Map> goToDefinition(String filePath, int line, int character) {
        def uri = "file://${filePath}"
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character]
        ]
        
        try {
            def result = rpc.sendRequest("textDocument/definition", params, 5000)
            return flattenLocations(result)
        } catch (Exception e) {
            System.err.println("goToDefinition failed: ${e.message}")
            return []
        }
    }
    
    /**
     * Find all references to symbol at position.
     */
    List<Map> findReferences(String filePath, int line, int character) {
        def uri = "file://${filePath}"
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character],
            context: [includeDeclaration: true]
        ]
        
        try {
            def result = rpc.sendRequest("textDocument/references", params, 5000)
            return flattenLocations(result)
        } catch (Exception e) {
            System.err.println("findReferences failed: ${e.message}")
            return []
        }
    }
    /**
     * Get hover information at position.
     */
    Map hover(String filePath, int line, int character) {
        def uri = "file://${filePath}"
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character]
        ]
        
        try {
            return rpc.sendRequest("textDocument/hover", params, 5000) as Map
        } catch (Exception e) {
            System.err.println("hover failed: ${e.message}")
            return null
        }
    }
    
    /**
     * Get document symbols.
     */
    List<Map> documentSymbol(String filePath) {
        def uri = "file://${filePath}"
        def params = [textDocument: [uri: uri]]
        
        try {
            def result = rpc.sendRequest("textDocument/documentSymbol", params, 5000)
            if (result instanceof List) {
                return result
            }
            return []
        } catch (Exception e) {
            System.err.println("documentSymbol failed: ${e.message}")
            return []
        }
    }
    
    /**
     * Search workspace symbols.
     */
    List<Map> workspaceSymbol(String query) {
        def params = [query: query]
        
        try {
            def result = rpc.sendRequest("workspace/symbol", params, 5000)
            if (result instanceof List) {
                return result
            }
            return []
        } catch (Exception e) {
            System.err.println("workspaceSymbol failed: ${e.message}")
            return []
        }
    }
    
    /**
     * Get completions at position.
     */
    List<Map> completion(String filePath, int line, int character) {
        def uri = "file://${filePath}"
        def params = [
            textDocument: [uri: uri],
            position: [line: line, character: character]
        ]
        
        try {
            def result = rpc.sendRequest("textDocument/completion", params, 3000)
            if (result instanceof List) {
                return result
            }
            return []
        } catch (Exception e) {
            System.err.println("completion failed: ${e.message}")
            return []
        }
    }
    
    /**
     * Flatten LSP location responses to a consistent format.
     */
    private List<Map> flattenLocations(result) {
        if (result == null) {
            return []
        }
        if (result instanceof List) {
            return result.collect { locationFromLsp(it) }
        }
        return [locationFromLsp(result)]
    }
    
    /**
     * Convert LSP location to consistent format.
     */
    private Map locationFromLsp(lspLocation) {
        return [
            uri: lspLocation.uri,
            range: [
                start: [line: lspLocation.range?.start?.line, character: lspLocation.range?.start?.character],
                end: [line: lspLocation.range?.end?.line, character: lspLocation.range?.end?.character]
            ]
        ]
    }
}
