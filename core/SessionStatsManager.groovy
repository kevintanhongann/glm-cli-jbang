package core

class SessionStatsManager {
    private static final SessionStatsManager INSTANCE = new SessionStatsManager()
    private final Map<String, SessionStat> sessionStats = [:]

    private SessionStatsManager() {}

    static SessionStatsManager getInstance() {
        return INSTANCE
    }

    SessionStat getOrCreateStats(String sessionId) {
        if (!sessionStats[sessionId]) {
            sessionStats[sessionId] = new SessionStat(sessionId: sessionId)
        }
        return sessionStats[sessionId]
    }

    SessionStat getStats(String sessionId) {
        return sessionStats[sessionId]
    }

    void updateTokenCount(String sessionId, int inputTokens, int outputTokens, BigDecimal cost = 0.0000) {
        def stats = getOrCreateStats(sessionId)
        stats.updateTokens(inputTokens, outputTokens, cost)
    }

    void updateLspStatus(String sessionId, String lspId, String status, String error = null, String root = null) {
        def stats = getOrCreateStats(sessionId)
        stats.addLspServer(lspId, status, error, root)
    }

    void recordModifiedFile(String sessionId, String filePath, int additions = 0, int deletions = 0) {
        def stats = getOrCreateStats(sessionId)
        stats.addModifiedFile(filePath, additions, deletions)
    }

    void removeSession(String sessionId) {
        sessionStats.remove(sessionId)
    }

    Map<String, SessionStat> getAllStats() {
        return new HashMap<>(sessionStats)
    }
}
