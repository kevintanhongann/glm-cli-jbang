package core

class LspManager {
    private static final LspManager INSTANCE = new LspManager()
    private final Map<String, Map<String, LspClientInfo>> activeLsps = [:]
    private final Map<String, Map<String, Integer>> lastDiagnosticCounts = [:]

    private LspManager() {}

    static LspManager getInstance() {
        return INSTANCE
    }

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

        SessionStatsManager.getInstance().updateLspStatus(
            sessionId, serverName,
            client.isAlive() ? "connected" : "disconnected",
            null, rootPath
        )
    }

    void updateLspStatus(String sessionId, String serverName, String status, String error = null) {
        def sessionLsps = activeLsps[sessionId]
        if (sessionLsps && sessionLsps[serverName]) {
            sessionLsps[serverName].status = status
            sessionLsps[serverName].error = error
            sessionLsps[serverName].lastUpdated = new Date()
        }

        SessionStatsManager.getInstance().updateLspStatus(sessionId, serverName, status, error)
    }

    void removeLsp(String sessionId, String serverName) {
        def sessionLsps = activeLsps[sessionId]
        if (sessionLsps) {
            sessionLsps.remove(serverName)
        }
    }

    void removeSession(String sessionId) {
        activeLsps.remove(sessionId)
    }

    List<LspClientInfo> getLspInfo(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        return sessionLsps ? new ArrayList<LspClientInfo>(sessionLsps.values()) : []
    }

    boolean hasActiveLsps(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return false
        return sessionLsps.values().any { it.status == "connected" }
    }

    int getConnectedLspCount(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return 0
        return sessionLsps.values().count { it.status == "connected" }
    }

    int getErrorLspCount(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return 0
        return sessionLsps.values().count { it.status == "error" }
    }

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
                        if (currentCount > 0) {
                            lspInfo.error = "${currentCount} diagnostics"
                        } else {
                            lspInfo.error = null
                        }
                        lastDiagnosticCounts[sessionId][serverId] = currentCount
                        lspInfo.lastUpdated = new Date()
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    List<LspServerInfo> getLspInfoForSidebar(String sessionId) {
        def sessionLsps = activeLsps[sessionId]
        if (!sessionLsps) return []

        return sessionLsps.values().collect { lspInfo ->
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
    String status
    String error
    String root
    Date lastUpdated
}
