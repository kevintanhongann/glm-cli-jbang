package core

import groovy.transform.Singleton

@Singleton(strict=false)
class LspManager {
    private final Map<String, Map<String, LspClientInfo>> activeLsps = [:]
    private final Map<String, Map<String, Integer>> lastDiagnosticCounts = [:]
    
    private LspManager() {}
    
    /**
     * Register an LSP client for a session
     */
    void registerLsp(String sessionId, String serverName, LSPClient client, String rootPath = null) {
        if (!activeLsps[sessionId]) {
            activeLsps[sessionId] = [:]
        }
        activeLsps[sessionId][serverName] = new LspClientInfo(
            serverName: serverName,
            client: client,
            status: client.isAlive() ? "connected" : "disconnected",
            root: rootPath,
            lastUpdated: new Date()
        )
        
        // Update session stats
        SessionStatsManager.instance.updateLspStatus(
            sessionId, serverName, 
            client.isAlive() ? "connected" : "disconnected",
            null, rootPath
        )
    }
    
    /**
     * Update LSP status for a session
     */
    void updateLspStatus(String sessionId, String serverName, String status, String error = null) {
        def sessionLsps = activeLsps[sessionId]
        if (sessionLsps && sessionLsps[serverName]) {
            sessionLsps[serverName].status = status
            sessionLsps[serverName].error = error
            sessionLsps[serverName].lastUpdated = new Date()
        }
        
        // Update session stats
        SessionStatsManager.instance.updateLspStatus(sessionId, serverName, status, error)
    }
    
    /**
     * Remove an LSP client from a session
     */
    void removeLsp(String sessionId, String serverName) {
        def sessionLsps = activeLsps[sessionId]
        if (sessionLsps) {
            sessionLsps.remove(serverName)
        }
    }
    
    /**
     * Remove all LSP clients for a session
     */
    void removeSession(String sessionId) {
        activeLsps.remove(sessionId)
    }
    
    /**
     * Get LSP info for a session
     */
    List<LspClientInfo> getLspInfo(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        return sessionLsps ? new ArrayList<LspClientInfo>(sessionLsps.values()) : []
    }
    
    /**
     * Check if session has active LSP connections
     */
    boolean hasActiveLsps(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return false
        return sessionLsps.values().any { it.status == "connected" }
    }
    
    /**
     * Get count of connected LSP servers
     */
    int getConnectedLspCount(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return 0
        return sessionLsps.values().count { it.status == "connected" }
    }
    
    /**
     * Get count of LSP servers with errors
     */
    int getErrorLspCount(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return 0
        return sessionLsps.values().count { it.status == "error" }
    }
    
    /**
     * Update LSP diagnostic counts - call this periodically or when UI refreshes
     */
    void updateDiagnosticCounts(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return
        
        if (!lastDiagnosticCounts[sessionId]) {
            lastDiagnosticCounts[sessionId] = [:]
        }
        
        sessionLsps.each { serverId, lspInfo ->
            if (lspInfo.client && lspInfo.status == "connected") {
                try {
                    int currentCount = lspInfo.client.getTotalDiagnosticCount()
                    int lastCount = lastDiagnosticCounts[sessionId].getOrDefault(serverId, 0)
                    
                    if (currentCount != lastCount) {
                        // Diagnostic count changed, update status
                        if (currentCount > 0) {
                            lspInfo.error = "${currentCount} diagnostics"
                        } else {
                            lspInfo.error = null
                        }
                        lastDiagnosticCounts[sessionId][serverId] = currentCount
                        lspInfo.lastUpdated = new Date()
                    }
                } catch (Exception e) {
                    // Ignore errors when accessing client
                }
            }
        }
    }
    
    /**
     * Get all LSP info for a session (for sidebar display).
     */
    List<LspServerInfo> getLspInfoForSidebar(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return []
        
        return sessionLsps.values().collect { lspInfo ->
            // Get diagnostic counts
            String error = lspInfo.error
            if (lspInfo.status == "connected" && lspInfo.client) {
                try {
                    int diagCount = lspInfo.client.getTotalDiagnosticCount()
                    if (diagCount > 0 && !error?.contains("diagnostics")) {
                        error = "${diagCount} diagnostics"
                    }
                } catch (Exception e) {
                }
            }
            
            return new LspServerInfo(
                lspId: lspInfo.serverName,
                status: lspInfo.status,
                error: error,
                root: lspInfo.root,
                lastUpdated: lspInfo.lastUpdated
            )
        }
    }
}

class LspClientInfo {
    String serverName
    LSPClient client
    String status // "connected", "error", "disconnected"
    String error
    String root
    Date lastUpdated
}
