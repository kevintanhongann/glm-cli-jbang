package core

import models.Message
import core.GlmClient
import core.SessionManager

@Singleton(strict = false)
class SessionCompactor {

    private CompactionTrigger trigger
    private SessionManager sessionManager
    private SummaryGenerator summaryGenerator
    private HistoryPruner historyPruner

    int maxContextTokens = 8000
    int compactionThresholdPercent = 75

    void initialize(GlmClient client, SessionManager sessionManager) {
        this.trigger = new CompactionTrigger(maxContextTokens)
        this.summaryGenerator = new SummaryGenerator(client)
        this.historyPruner = new HistoryPruner()
        this.sessionManager = sessionManager
    }

    static class CompactionResult {

        boolean performed
        int tokensBefore
        int tokensAfter
        int messagesRemoved
        String reason
        long durationMs

        static CompactionResult skipped(String reason) {
            return new CompactionResult(false, 0, 0, 0, reason, 0)
        }

        static CompactionResult success(int tokensBefore, int tokensAfter,
                                         int messagesRemoved, long durationMs) {
            return new CompactionResult(true, tokensBefore, tokensAfter,
                                       messagesRemoved, 'Compaction completed', durationMs)
                                         }

    }

    CompactionResult maybeCompact(String sessionId, List<Message> history,
                                  String systemPrompt) {
        // Ensure initialized
        if (trigger == null) {
            // Fallback if initialize wasn't called or triggers missing
            trigger = new CompactionTrigger(maxContextTokens)
        }
        if (historyPruner == null) historyPruner = new HistoryPruner()

        int currentTokens = TokenCounter.estimateTotalContextTokens(history, systemPrompt)
        CompactionTrigger.TriggerLevel level = trigger.checkLevel(currentTokens)

        if (level == CompactionTrigger.TriggerLevel.NONE) {
            return CompactionResult.skipped("Context within limits (${currentTokens}/${maxContextTokens} tokens)")
        }

        EventBus.instance.publish(EventType.PROGRESS_UPDATE,
                                 [message: "Context at ${(currentTokens * 100 / maxContextTokens as int)}% - compacting"])

        return compact(sessionId, history, systemPrompt, level)
                                  }

    private CompactionResult compact(String sessionId, List<Message> history,
                                     String systemPrompt, CompactionTrigger.TriggerLevel level) {
        long startTime = System.currentTimeMillis()

        int targetTokens = level == CompactionTrigger.TriggerLevel.CRITICAL ?
            (maxContextTokens * 0.6) as int : (maxContextTokens * 0.8) as int

        int tokensBefore = TokenCounter.estimateTotalContextTokens(history, systemPrompt)

        // Ensure summary generator is available if needed, though pruner handles null
        HistoryPruner.PruneResult pruneResult = historyPruner.prune(
            history, targetTokens, summaryGenerator
        )

        List<Message> compactedHistory = pruneResult.prunedHistory

        if (sessionManager != null) {
            sessionManager.compactSession(sessionId, compactedHistory, pruneResult.summary)
        }

        int tokensAfter = TokenCounter.estimateTotalContextTokens(compactedHistory, systemPrompt)
        long duration = System.currentTimeMillis() - startTime

        EventBus.instance.publish(EventType.STATE_CHANGED, [
            name: 'session.compaction',
            data: [tokensBefore: tokensBefore, tokensAfter: tokensAfter,
                   messagesRemoved: pruneResult.messagesRemoved]
        ])

        return CompactionResult.success(tokensBefore, tokensAfter,
                                       pruneResult.messagesRemoved, duration)
                                     }

    CompactionTrigger getTrigger() {
        return trigger
    }

    void setCompactionThresholdPercent(int percent) {
        this.compactionThresholdPercent = percent
        this.trigger = new CompactionTrigger(maxContextTokens)
    }

}
