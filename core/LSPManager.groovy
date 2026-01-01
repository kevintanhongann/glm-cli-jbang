package core

import models.Diagnostic
import groovy.transform.Synchronized
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton manager for all LSP clients.
 * Handles client lifecycle, server spawning, and diagnostic collection.
 */
@Singleton
class LSPManager {
    private final Map<String, LSPClient> clients = new ConcurrentHashMap<>()
    private final Set<String> brokenServers = ConcurrentHashMap.newKeySet()
    private final LSPServerRegistry registry = new LSPServerRegistry()
    private LSPConfig config = LSPConfig.load()
    
    /**
     * Get or create an LSP client for the given file.
     * @param filePath Absolute file path
     * @return LSPClient or null if no server available
     */
    @Synchronized
    LSPClient getClient(String filePath) {
        if (!config.enabled) return null
        
        def serverConfig = registry.getServerForFile(filePath)
        if (serverConfig == null) return null
        
        def serverId = serverConfig.id
        if (brokenServers.contains(serverId)) return null
        
        // Detect project root
        def root = serverConfig.rootDetector?.call(new File(filePath).parent) ?: new File(filePath).parent
        def clientKey = "${serverId}:${root}"
        
        def client = clients.get(clientKey)
        if (client != null && client.alive) {
            return client
        }
        
        // Try to spawn new client
        try {
            client = spawnClient(serverConfig, root)
            clients.put(clientKey, client)
            return client
        } catch (Exception e) {
            System.err.println("Failed to spawn LSP server '${serverId}': ${e.message}")
            brokenServers.add(serverId)
            return null
        }
    }
    
    /**
     * Touch a file (open + optionally wait for diagnostics).
     * @param filePath Absolute file path
     * @param waitForDiagnostics Whether to wait for diagnostics
     * @return List of diagnostics
     */
    List<Diagnostic> touchFile(String filePath, boolean waitForDiagnostics = true) {
        def client = getClient(filePath)
        if (client == null) return []
        
        try {
            def content = new File(filePath).text
            def uri = "file://${filePath}"
            
            // Check if file is already open
            def existingDiags = client.getDiagnostics(filePath)
            if (existingDiags != null) {
                // File already tracked, send change notification
                client.didChange(filePath, content)
                client.didSave(filePath, content)
            } else {
                // New file, open it
                client.didOpen(filePath, content)
            }
            
            if (waitForDiagnostics) {
                client.waitForDiagnostics(config.diagnosticTimeout)
            }
            
            return client.getDiagnostics(filePath) ?: []
        } catch (Exception e) {
            System.err.println("LSP touchFile error: ${e.message}")
            return []
        }
    }
    
    /**
     * Get all diagnostics across all clients.
     * @return Map of file URI to diagnostics
     */
    Map<String, List<Diagnostic>> getAllDiagnostics() {
        def result = [:]
        clients.values().each { client ->
            // Note: Would need to expose diagnostics map from client
        }
        return result
    }
    
    /**
     * Spawn a new LSP client.
     */
    private LSPClient spawnClient(LSPServerConfig serverConfig, String root) {
        def process = registry.spawn(serverConfig, root)
        def rpc = new JsonRpcHandler(process)
        def client = new LSPClient(rpc, serverConfig.id)
        
        // Initialize the client
        client.initialize(root)
        
        return client
    }
    
    /**
     * Shutdown all LSP servers.
     */
    void shutdown() {
        clients.values().each { client ->
            try {
                client.shutdown()
            } catch (Exception ignored) {}
        }
        clients.clear()
    }
    
    /**
     * Check if LSP is enabled.
     */
    boolean isEnabled() {
        return config.enabled
    }
    
    /**
     * Reload configuration.
     */
    void reloadConfig() {
        config = LSPConfig.load()
    }
}
