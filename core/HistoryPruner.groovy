package core

import models.Message

class HistoryPruner {
    private static final List<String> TOOL_MESSAGE_ROLES = ["tool", "function", "tool_results"]

    enum PreservationPriority {
        SYSTEM_PROMPT(1),
        LATEST_USER_MESSAGE(2),
        LATEST_ASSISTANT_MESSAGE(3),
        RECENT_TOOL_CALLS(4),
        OLD_USER_MESSAGES(5),
        OLD_ASSISTANT_MESSAGES(6),
        TOOL_RESULTS(7),
        MIDDLE_CONTENT(8)

        final int priority
        PreservationPriority(int priority) { this.priority = priority }
    }

    static class PruneResult {
        List<Message> prunedHistory
        int messagesRemoved
        String summary
        int tokensBefore
        int tokensAfter

        PruneResult(List<Message> prunedHistory, int messagesRemoved,
                    String summary, int tokensBefore, int tokensAfter) {
            this.prunedHistory = prunedHistory
            this.messagesRemoved = messagesRemoved
            this.summary = summary
            this.tokensBefore = tokensBefore
            this.tokensAfter = tokensAfter
        }
    }

    PruneResult prune(List<Message> history, int targetTokenCount,
                      SummaryGenerator summaryGenerator = null) {
        if (history.empty) {
            return new PruneResult([], 0, "", 0, 0)
        }

        int tokensBefore = TokenCounter.estimateHistoryTokens(history)
        List<Message> grouped = groupByConversations(history)

        List<Message> systemMessages = grouped.findAll { it.role == "system" }
        List<Message> userMessages = grouped.findAll { it.role == "user" }
        List<Message> assistantMessages = grouped.findAll { it.role == "assistant" }
        List<Message> toolMessages = grouped.findAll { TOOL_MESSAGE_ROLES.contains(it.role) }

        List<Message> preserved = []

        preserved.addAll(systemMessages)

        int currentTokens = TokenCounter.estimateHistoryTokens(preserved)
        if (currentTokens >= targetTokenCount) {
            return compactSystemOnly(preserved, tokensBefore)
        }

        int latestUserIndex = userMessages.size() - 1
        if (latestUserIndex >= 0) {
            preserved.add(userMessages[latestUserIndex])
            currentTokens = TokenCounter.estimateHistoryTokens(preserved)
        }

        int latestAssistantIndex = assistantMessages.size() - 1
        if (latestAssistantIndex >= 0 && currentTokens < targetTokenCount) {
            preserved.add(assistantMessages[latestAssistantIndex])
            currentTokens = TokenCounter.estimateHistoryTokens(preserved)
        }

        List<Message> recentToolCalls = toolMessages.takeLast(5)
        preserved.addAll(recentToolCalls)

        currentTokens = TokenCounter.estimateHistoryTokens(preserved)
        int remainingBudget = targetTokenCount - currentTokens

        if (remainingBudget > 0) {
            List<Message> toRemove = findMessagesToRemove(
                userMessages, assistantMessages, latestUserIndex, latestAssistantIndex,
                remainingBudget
            )
            preserved.addAll(toRemove)
        }

        List<Message> finalHistory = preserved.sort { a, b ->
            indexOfInOriginal(history, a) <=> indexOfInOriginal(history, b)
        }

        String summary = ""
        if (summaryGenerator != null) {
            summary = summaryGenerator.generateSummary(history, 10)
            summary = "Earlier conversation summarized: ${summary}"
            Message summaryMessage = new Message("system", summary)
            finalHistory.add(0, summaryMessage)
        }

        int tokensAfter = TokenCounter.estimateHistoryTokens(finalHistory)

        return new PruneResult(finalHistory, history.size() - finalHistory.size(),
                              summary, tokensBefore, tokensAfter)
    }

    private List<Message> groupByConversations(List<Message> history) {
        List<Message> grouped = []
        int i = 0
        while (i < history.size()) {
            Message msg = history[i]
            if (TOOL_MESSAGE_ROLES.contains(msg.role)) {
                grouped.add(msg)
            } else {
                Message combined = combineUserAssistant(msg, history, i)
                grouped.add(combined)
                i += 2
                continue
            }
            i++
        }
        return grouped
    }

    private Message combineUserAssistant(Message userMsg, List<Message> history, int index) {
        if (index + 1 < history.size()) {
            Message nextMsg = history[index + 1]
            if (nextMsg.role == "assistant") {
                return new Message("assistant", "${userMsg.content}\n\nAssistant: ${nextMsg.content}")
            }
        }
        return userMsg
    }

    private List<Message> findMessagesToRemove(List<Message> userMessages,
                                                List<Message> assistantMessages,
                                                int latestUserIndex, int latestAssistantIndex,
                                                int remainingBudget) {
        List<Message> toRemove = []

        List<Message> middleUserMessages = userMessages.subList(0, Math.max(0, latestUserIndex))
        List<Message> middleAssistantMessages = assistantMessages.subList(0, Math.max(0, latestAssistantIndex))

        int budgetPerPair = remainingBudget / Math.max(1, middleUserMessages.size() + middleAssistantMessages.size())

        middleUserMessages.eachWithIndex { msg, idx ->
            if (TokenCounter.estimateMessageTokens(msg) < budgetPerPair) {
                toRemove.add(msg)
            }
        }

        middleAssistantMessages.eachWithIndex { msg, idx ->
            if (TokenCounter.estimateMessageTokens(msg) < budgetPerPair) {
                toRemove.add(msg)
            }
        }

        return toRemove
    }

    private int indexOfInOriginal(List<Message> original, Message target) {
        for (int i = 0; i < original.size(); i++) {
            if (original[i] == target) return i
        }
        return original.size()
    }

    private PruneResult compactSystemOnly(List<Message> systemMessages, int tokensBefore) {
        return new PruneResult(systemMessages, 0, "", tokensBefore,
                              TokenCounter.estimateHistoryTokens(systemMessages))
    }
}
