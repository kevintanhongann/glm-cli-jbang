package core

import groovy.transform.Canonical
import java.util.concurrent.ConcurrentHashMap

class SubagentSessionManager {
    private static final SubagentSessionManager INSTANCE = new SubagentSessionManager()

    static SubagentSessionManager getInstance() { return INSTANCE }

    private ConcurrentHashMap<String, SubagentSession> activeSessions = [:]

    synchronized String registerSession(String sessionId, String agentType, String task) {
        SubagentSession session = new SubagentSession(
            sessionId: sessionId,
            agentType: agentType,
            task: task,
            status: "pending",
            startTime: System.currentTimeMillis(),
            lastUpdate: System.currentTimeMillis()
        )
        activeSessions[sessionId] = session
        return sessionId
    }

    synchronized void updateSession(String sessionId, String status) {
        if (activeSessions.containsKey(sessionId)) {
            activeSessions[sessionId].status = status
            activeSessions[sessionId].lastUpdate = System.currentTimeMillis()
        }
    }

    synchronized void completeSession(String sessionId, boolean success, String result) {
        if (activeSessions.containsKey(sessionId)) {
            activeSessions[sessionId].status = "completed"
            activeSessions[sessionId].completedAt = System.currentTimeMillis()
            activeSessions[sessionId].success = success
            activeSessions[sessionId].result = result
            activeSessions[sessionId].lastUpdate = System.currentTimeMillis()
        }
    }

    synchronized SubagentSession getSession(String sessionId) {
        return activeSessions[sessionId]
    }

    synchronized List<SubagentSession> getActiveSessions() {
        return activeSessions.values().findAll { it.status in ["pending", "running"] }
    }

    synchronized int getActiveCount() {
        return activeSessions.values().count { it.status in ["pending", "running"] }
    }

    synchronized List<SubagentSession> getAllSessions() {
        return new ArrayList<>(activeSessions.values())
    }

    synchronized void clearCompleted(int olderThanSeconds = 300) {
        long cutoff = System.currentTimeMillis() - (olderThanSeconds * 1000)
        activeSessions = activeSessions.findAll {
            it.value.status in ["pending", "running"] ||
            it.value.lastUpdate > cutoff
        }
    }

    @Canonical
    static class SubagentSession {
        String sessionId
        String agentType
        String task
        String status = "pending"
        long startTime
        long completedAt = 0
        long lastUpdate
        int turns = 0
        boolean success = false
        String result = null
    }
}
