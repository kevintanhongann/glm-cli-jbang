package core

import groovy.transform.Singleton

@Singleton(strict=false)
class SessionStatsManager {
    private final Map<String, SessionStat> sessionStats = [:]
    
    private SessionStatsManager() {}
    
    /**
     * Get or create session stats for a session ID
     */
    SessionStat getOrCreateStats(String sessionId) {
        if (!sessionStats[sessionId]) {
            sessionStats[sessionId] = new SessionStat(sessionId: sessionId)
        }
        return sessionStats[sessionId]
    }
    
    /**
     * Get session stats for a session ID (returns null if not exists)
     */
    SessionStat getStats(String sessionId) {
        return sessionStats[sessionId]
    }
    
    /**
     * Update token counts for a session
     */
    void updateTokenCount(String sessionId, int inputTokens, int outputTokens, BigDecimal cost = 0.0000) {
        def stats = getOrCreateStats(sessionId)
        stats.updateTokens(inputTokens, outputTokens, cost)
    }
    
    /**
     * Update LSP server status for a session
     */
    void updateLspStatus(String sessionId, String lspId, String status, String error = null, String root = null) {
        def stats = getOrCreateStats(sessionId)
        stats.addLspServer(lspId, status, error, root)
    }
    
    /**
     * Record a modified file for a session
     */
    void recordModifiedFile(String sessionId, String filePath, int additions = 0, int deletions = 0) {
        def stats = getOrCreateStats(sessionId)
        stats.addModifiedFile(filePath, additions, deletions)
    }
    
    /**
     * Remove a session's stats
     */
    void removeSession(String sessionId) {
        sessionStats.remove(sessionId)
    }
    
    /**
     * Get all session stats
     */
    Map<String, SessionStat> getAllStats() {
        return new HashMap<>(sessionStats)
    }
}
