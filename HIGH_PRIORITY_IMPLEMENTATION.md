# High Priority Implementation Plan

This document outlines the implementation plan for three high-priority features that address the gap between Opencode and GLM-CLI JBang:
1. Reactive State Management + Event Bus
2. Session Compaction Mechanism
3. Improved Doom Loop Detection with Thresholds

---

## 1. Reactive State Management + Event Bus

### Overview

Implement a reactive state management system inspired by Opencode's `Instance.state()` pattern combined with an event bus for cross-component communication. This will enable decoupled components to communicate efficiently and allow the TUI to reactively update when state changes.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Event Bus (Singleton)                     │
│  - Publish/Subscribe pattern                                 │
│  - Event types: StateChange, ToolExecution, AgentProgress   │
│  - Supports synchronous and asynchronous events             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 Reactive State Manager                       │
│  - Central state registry                                   │
│  - Observable state containers                              │
│  - Change notifications                                     │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐       ┌──────────┐       ┌──────────┐
    │ TUI      │       │ Agent    │       │ Session  │
    │ Layer    │       │ Engine   │       │ Manager  │
    └──────────┘       └──────────┘       └──────────┘
```

### Implementation Files

#### 1.1 Event Bus (`core/EventBus.groovy`)

```groovy
package glm.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

enum EventType {
    STATE_CHANGED,
    TOOL_EXECUTION_STARTED,
    TOOL_EXECUTION_COMPLETED,
    TOOL_EXECUTION_ERROR,
    AGENT_STEP_STARTED,
    AGENT_STEP_COMPLETED,
    AGENT_LOOP_COMPLETED,
    SESSION_CREATED,
    SESSION_UPDATED,
    MESSAGE_ADDED,
    ERROR_OCCURRED,
    PROGRESS_UPDATE
}

@Singleton(strict = false)
class EventBus {
    private final Map<EventType, CopyOnWriteArrayList<Consumer<Event>>> listeners =
        new ConcurrentHashMap<>()

    void subscribe(EventType type, Consumer<Event> handler) {
        listeners.computeIfAbsent(type, { new CopyOnWriteArrayList<>() })
                 .add(handler)
    }

    void unsubscribe(EventType type, Consumer<Event> handler) {
        listeners.getOrDefault(type, new CopyOnWriteArrayList<>())
                 .remove(handler)
    }

    void publish(Event event) {
        listeners.getOrDefault(event.type, new CopyOnWriteArrayList<>())
                 .each { it.accept(event) }
    }

    void publish(EventType type, Map<String, Object> data = [:]) {
        publish(new Event(type, data))
    }
}

class Event {
    final EventType type
    final Map<String, Object> data
    final Instant timestamp

    Event(EventType type, Map<String, Object> data = [:]) {
        this.type = type
        this.data = data
        this.timestamp = Instant.now()
    }
}
```

#### 1.2 Reactive State Container (`core/ReactiveState.groovy`)

```groovy
package glm.core

import java.util.function.Consumer
import java.util.function.Function

class ReactiveState<T> {
    private T value
    private final List<Consumer<StateChange<T>>> listeners = new ArrayList<>()
    private final String name

    ReactiveState(String name, T initialValue = null) {
        this.name = name
        this.value = initialValue
    }

    T get() {
        return value
    }

    void set(T newValue) {
        T oldValue = this.value
        if (oldValue == newValue && oldValue != null) {
            return
        }
        this.value = newValue
        notifyListeners(oldValue, newValue)
    }

    void update(Function<T, T> updater) {
        set(updater.apply(value))
    }

    void subscribe(Consumer<StateChange<T>> listener) {
        listeners.add(listener)
    }

    void unsubscribe(Consumer<StateChange<T>> listener) {
        listeners.remove(listener)
    }

    private void notifyListeners(T oldValue, T newValue) {
        StateChange<T> change = new StateChange<>(name, oldValue, newValue)
        listeners.each { it.accept(change) }
        EventBus.instance.publish(EventType.STATE_CHANGED, [name: name, change: change])
    }
}

class StateChange<T> {
    final String propertyName
    final T oldValue
    final T newValue
    final Instant timestamp

    StateChange(String propertyName, T oldValue, T newValue) {
        this.propertyName = propertyName
        this.oldValue = oldValue
        this.newValue = newValue
        this.timestamp = Instant.now()
    }
}
```

#### 1.3 State Registry (`core/StateRegistry.groovy`)

```groovy
package glm.core

@Singleton(strict = false)
class StateRegistry {
    private final Map<String, ReactiveState<?>> states = new ConcurrentHashMap<>()

    <T> ReactiveState<T> register(String name, T initialValue = null) {
        ReactiveState<T> state = new ReactiveState<>(name, initialValue)
        states.put(name, state)
        return state
    }

    <T> ReactiveState<T> get(String name) {
        return states.get(name) as ReactiveState<T>
    }

    <T> T getValue(String name) {
        ReactiveState<T> state = get(name)
        return state?.get()
    }

    <T> void setValue(String name, T value) {
        ReactiveState<T> state = get(name)
        if (state == null) {
            register(name, value)
        } else {
            state.set(value)
        }
    }

    Set<String> getStateNames() {
        return Collections.unmodifiableSet(states.keySet())
    }
}
```

#### 1.4 Agent State (`core/AgentState.groovy`)

```groovy
package glm.core

class AgentState {
    static final String CURRENT_STEP = "agent.currentStep"
    static final String MAX_STEPS = "agent.maxSteps"
    static final String IS_RUNNING = "agent.isRunning"
    static final String CURRENT_TOOL = "agent.currentTool"
    static final String PROGRESS = "agent.progress"
    static final String TOKENS_USED = "agent.tokensUsed"

    static ReactiveState<Integer> currentStep
    static ReactiveState<Integer> maxSteps
    static ReactiveState<Boolean> isRunning
    static ReactiveState<String> currentTool
    static ReactiveState<Double> progress
    static ReactiveState<Map<String, Integer>> tokensUsed

    static void initialize() {
        currentStep = StateRegistry.instance.register(CURRENT_STEP, 0)
        maxSteps = StateRegistry.instance.register(MAX_STEPS, 25)
        isRunning = StateRegistry.instance.register(IS_RUNNING, false)
        currentTool = StateRegistry.instance.register(CURRENT_TOOL, "")
        progress = StateRegistry.instance.register(PROGRESS, 0.0)
        tokensUsed = StateRegistry.instance.register(TOKENS_USED, [input: 0, output: 0])
    }

    static void reset() {
        currentStep.set(0)
        isRunning.set(false)
        currentTool.set("")
        progress.set(0.0)
    }
}
```

### Integration Points

1. **Agent Loop Integration** (`core/Agent.groovy`)
   - Emit `AGENT_STEP_STARTED` and `AGENT_STEP_COMPLETED` events
   - Update `AgentState.currentStep` on each iteration
   - Update `AgentState.progress` based on step/maxSteps

2. **Tool Execution Integration** (`tools/Tool.groovy`)
   - Emit `TOOL_EXECUTION_STARTED` before tool execution
   - Emit `TOOL_EXECUTION_COMPLETED` after execution
   - Update `AgentState.currentTool` with tool name

3. **TUI Integration** (`tui/lanterna/LanternaTUI.groovy`)
   - Subscribe to events for real-time UI updates
   - Update progress bar, status bar, activity log
   - Handle errors with event-driven notifications

### Testing Strategy

1. Unit tests for EventBus publish/subscribe
2. Unit tests for ReactiveState get/set/observe
3. Integration test for agent events
4. Manual TUI testing for real-time updates

---

## 2. Session Compaction Mechanism

### Overview

Implement a session compaction system that compresses conversation context when token limits are approached. This is critical for long-running sessions and prevents context overflow while preserving important information.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Session Compaction Manager                  │
│  - Monitors token usage                                     │
│  - Triggers compaction at threshold (e.g., 80%)             │
│  - Orchestrates compaction process                          │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐       ┌──────────┐       ┌──────────┐
    │ Token    │       │ Summary  │       │ History  │
    │ Counter  │       │ Generator│       │ Pruner   │
    └──────────┘       └──────────┘       └──────────┘
```

### Implementation Files

#### 2.1 Token Counter (`core/TokenCounter.groovy`)

```groovy
package glm.core

import glm.models.Message

class TokenCounter {
    private static final Map<String, Integer> TOKEN_ESTIMATES = [
        "english_word": 0.75,
        "chinese_char": 0.5,
        "code_token": 0.75,
        "special_char": 0.25
    ]

    static int estimateMessageTokens(Message message) {
        return estimateTextTokens(message.content ?: "")
    }

    static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0
        }
        int count = 0
        String[] words = text.split("\\s+")
        for (String word : words) {
            if (word.length() == 0) continue
            if (word ==~ /^[\u4e00-\u9fa5]$/) {
                count += TOKEN_ESTIMATES["chinese_char"]
            } else if (word ==~ /^[a-zA-Z]+$/) {
                count += TOKEN_ESTIMATES["english_word"]
            } else if (word ==~ /^[\{\}\[\]\(\)\.;,\"']+$/) {
                count += TOKEN_ESTIMATES["special_char"]
            } else {
                count += TOKEN_ESTIMATES["code_token"]
            }
        }
        return Math.ceil(count) as int
    }

    static int estimateToolCallTokens(String toolName, String arguments) {
        return Math.ceil((toolName.length() + arguments.length()) * 0.4) as int
    }

    static int estimateHistoryTokens(List<Message> history) {
        return history.sum { estimateMessageTokens(it) } ?: 0
    }

    static int estimateTotalContextTokens(List<Message> history, String systemPrompt) {
        int historyTokens = estimateHistoryTokens(history)
        int systemTokens = estimateTextTokens(systemPrompt)
        int overhead = 50  # Response formatting overhead
        return historyTokens + systemTokens + overhead
    }
}
```

#### 2.2 Compaction Trigger (`core/CompactionTrigger.groovy`)

```groovy
package glm.core

class CompactionTrigger {
    private static final double DEFAULT_WARNING_THRESHOLD = 0.75
    private static final double DEFAULT_CRITICAL_THRESHOLD = 0.90

    double warningThreshold
    double criticalThreshold
    int maxContextTokens

    CompactionTrigger(int maxContextTokens = 8000) {
        this.maxContextTokens = maxContextTokens
        this.warningThreshold = DEFAULT_WARNING_THRESHOLD
        this.criticalThreshold = DEFAULT_CRITICAL_THRESHOLD
    }

    enum TriggerLevel {
        NONE,
        WARNING,
        CRITICAL
    }

    TriggerLevel checkLevel(int currentTokens) {
        double ratio = (double) currentTokens / maxContextTokens
        if (ratio >= criticalThreshold) {
            return TriggerLevel.CRITICAL
        } else if (ratio >= warningThreshold) {
            return TriggerLevel.WARNING
        }
        return TriggerLevel.NONE
    }

    boolean shouldCompact(int currentTokens) {
        return checkLevel(currentTokens) != TriggerLevel.NONE
    }

    String getWarningMessage(int currentTokens) {
        def level = checkLevel(currentTokens)
        return switch(level) {
            case TriggerLevel.CRITICAL ->
                "Context at ${(currentTokens * 100 / maxContextTokens)}% - COMPACTING NOW"
            case TriggerLevel.WARNING ->
                "Context at ${(currentTokens * 100 / maxContextTokens)}% - Consider compacting"
            default -> ""
        }
    }
}
```

#### 2.3 Summary Generator (`core/SummaryGenerator.groovy`)

```groovy
package glm.core

import glm.core.GlmClient
import glm.models.ChatRequest
import glm.models.Message

class SummaryGenerator {
    private final GlmClient client
    private final String model

    SummaryGenerator(GlmClient client, String model = "zai/glm-4-flash") {
        this.client = client
        this.model = model
    }

    String generateSummary(List<Message> messages, int maxMessages = 10) {
        if (messages.size() <= maxMessages) {
            return "No compaction needed - conversation is within limits"
        }

        List<Message> recentMessages = messages.takeLast(maxMessages)
        StringBuilder context = new StringBuilder()
        context.append("Summarize the following conversation history into a concise paragraph (2-3 sentences):\n\n")

        recentMessages.each { msg ->
            context.append("[${msg.role.toUpperCase()}]\n")
            context.append(msg.content?.take(500) ?: "")
            context.append("\n\n")
        }

        String summaryPrompt = context.toString()

        try {
            ChatRequest request = new ChatRequest()
            request.model = model
            request.messages = [new Message("user", summaryPrompt)]
            request.maxTokens = 300

            String response = client.sendMessage(request)
            return extractSummary(response)
        } catch (Exception e) {
            return "Error generating summary: ${e.message}"
        }
    }

    private String extractSummary(String response) {
        def parsed = new groovy.json.JsonSlurper().parseText(response)
        return parsed.choices[0]?.message?.content ?: "Summary unavailable"
    }
}
```

#### 2.4 History Pruner (`core/HistoryPruner.groovy`)

```groovy
package glm.core

import glm.models.Message

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

        int recentToolCalls = toolMessages.takeLast(5)
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
```

#### 2.5 Session Compactor (`core/SessionCompactor.groovy`)

```groovy
package glm.core

import glm.core.GlmClient
import glm.core.SessionManager
import glm.models.Message

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
                                       messagesRemoved, "Compaction completed", durationMs)
        }
    }

    CompactionResult maybeCompact(String sessionId, List<Message> history,
                                  String systemPrompt) {
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
            name: "session.compaction",
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
```

### Integration Points

1. **Agent Loop Integration** (`core/Agent.groovy`)
   - Before each LLM call, check if compaction needed
   - If compaction triggered, compact history and continue

2. **SessionManager Integration** (`core/SessionManager.groovy`)
   - Add `compactSession()` method
   - Store compacted sessions with history

3. **Configuration** (`core/Config.groovy`)
   - Add compaction settings to BehaviorConfig

### Configuration Schema

```toml
[behavior]
# Session compaction settings
max_context_tokens = 8000       # Maximum context window
compaction_threshold = 75       # Percentage at which to trigger compaction
auto_compact = true             # Automatically compact when threshold reached
```

### Testing Strategy

1. Unit tests for TokenCounter estimates
2. Unit tests for CompactionTrigger thresholds
3. Unit tests for HistoryPruner preservation logic
4. Integration test for full compaction flow
5. Performance benchmark for compaction time

---

## 3. Improved Doom Loop Detection with Thresholds

### Overview

Implement sophisticated doom loop detection that tracks identical tool calls across multiple iterations and prompts the user for permission to continue. This prevents infinite loops while allowing legitimate repeated operations.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Doom Loop Detector                          │
│  - Tracks recent tool calls with fingerprints                │
│  - Maintains loop history window                            │
│  - Triggers permission prompts at threshold                 │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐       ┌──────────┐       ┌──────────┐
    │ Call     │       │ Pattern  │       │ Permission│
    │ Fingerprint│     │ Matcher  │       │ Manager  │
    └──────────┘       └──────────┘       └──────────┘
```

### Implementation Files

#### 3.1 Doom Loop Detector (`core/DoomLoopDetector.groovy`)

```groovy
package glm.core

import java.security.MessageDigest

class DoomLoopDetector {
    private static final int DEFAULT_LOOP_THRESHOLD = 3
    private static final int DEFAULT_WINDOW_SIZE = 10
    private static final int DEFAULT_COOLDOWN_MS = 5000

    int loopThreshold
    int windowSize
    long cooldownMs

    private final List<CallRecord> recentCalls = new ArrayList<>()
    private long lastPromptTime = 0
    private int consecutiveDenials = 0

    DoomLoopDetector(int loopThreshold = DEFAULT_LOOP_THRESHOLD,
                     int windowSize = DEFAULT_WINDOW_SIZE,
                     long cooldownMs = DEFAULT_COOLDOWN_MS) {
        this.loopThreshold = loopThreshold
        this.windowSize = windowSize
        this.cooldownMs = cooldownMs
    }

    static class CallRecord {
        String toolName
        String argumentsHash
        long timestamp
        int step

        static String hashArguments(Map<String, Object> args) {
            String argsStr = new TreeMap<>(args).toString()
            MessageDigest md = MessageDigest.getInstance("MD5")
            byte[] digest = md.digest(argsStr.bytes)
            return digest.encodeHex() as String
        }
    }

    enum LoopStatus {
        NONE,
        SUSPICIOUS,
        LIKELY_LOOP,
        CONFIRMED_LOOP
    }

    static class LoopCheckResult {
        LoopStatus status
        int occurrenceCount
        String message
        boolean shouldPrompt

        static LoopCheckResult none() {
            return new LoopCheckResult(LoopStatus.NONE, 0, "", false)
        }

        static LoopCheckResult suspicious(String toolName, int count) {
            return new LoopCheckResult(LoopStatus.SUSPICIOUS, count,
                "Warning: ${toolName} called ${count} times recently", false)
        }

        static LoopCheckResult likelyLoop(String toolName, int count) {
            return new LoopCheckResult(LoopStatus.LIKELY_LOOP, count,
                "Potential loop detected: ${toolName} called ${count} times", true)
        }

        static LoopCheckResult confirmedLoop(String toolName, int count) {
            return new LoopCheckResult(LoopStatus.CONFIRMED_LOOP, count,
                "Looping detected: ${toolName} called ${count} times - please confirm",
                true)
        }
    }

    LoopCheckResult checkForLoop(String toolName, Map<String, Object> args) {
        String argsHash = CallRecord.hashArguments(args)

        pruneOldRecords()

        List<CallRecord> matchingCalls = recentCalls.findAll {
            it.toolName == toolName && it.argumentsHash == argsHash
        }

        int occurrenceCount = matchingCalls.size()

        if (occurrenceCount == 0) {
            recordCall(toolName, argsHash, 0)
            return LoopCheckResult.none()
        }

        updateSteps(matchingCalls)

        LoopStatus status = determineStatus(occurrenceCount)

        if (status == LoopStatus.NONE) {
            recordCall(toolName, argsHash, matchingCalls[0]?.step ?: 0)
        }

        return new LoopCheckResult(status, occurrenceCount,
            generateMessage(toolName, occurrenceCount, status),
            status.ordinal() >= LoopStatus.LIKELY_LOOP.ordinal())
    }

    void recordCall(String toolName, String argsHash, int currentStep) {
        recentCalls.add(new CallRecord(
            toolName: toolName,
            argumentsHash: argsHash,
            timestamp: System.currentTimeMillis(),
            step: currentStep
        ))

        if (recentCalls.size() > windowSize) {
            recentCalls.remove(0)
        }
    }

    void recordCall(String toolName, Map<String, Object> args, int currentStep) {
        recordCall(toolName, CallRecord.hashArguments(args), currentStep)
    }

    void reset() {
        recentCalls.clear()
    }

    void resetForTool(String toolName) {
        recentCalls.removeAll { it.toolName == toolName }
    }

    boolean shouldPromptUser() {
        if (recentCalls.empty) {
            return false
        }

        long now = System.currentTimeMillis()
        if (now - lastPromptTime < cooldownMs) {
            return false
        }

        return hasHighFrequencyCalls()
    }

    List<CallRecord> getRecentCalls() {
        return Collections.unmodifiableList(recentCalls)
    }

    int getRecentCallCount(String toolName) {
        return recentCalls.count { it.toolName == toolName }
    }

    private void pruneOldRecords() {
        long cutoff = System.currentTimeMillis() - (cooldownMs * 2)
        recentCalls.removeAll { it.timestamp < cutoff }
    }

    private void updateSteps(List<CallRecord> matchingCalls) {
        int maxStep = recentCalls.max { it.step }?.step ?: 0
        matchingCalls.each { it.step = maxStep }
    }

    private LoopStatus determineStatus(int occurrenceCount) {
        if (occurrenceCount < loopThreshold) {
            return LoopStatus.NONE
        } else if (occurrenceCount == loopThreshold) {
            return LoopStatus.SUSPICIOUS
        } else if (occurrenceCount < loopThreshold * 2) {
            return LoopStatus.LIKELY_LOOP
        } else {
            return LoopStatus.CONFIRMED_LOOP
        }
    }

    private String generateMessage(String toolName, int count, LoopStatus status) {
        return switch(status) {
            case LoopStatus.SUSPICIOUS ->
                "${toolName} called ${count} times - this might be intentional"
            case LoopStatus.LIKELY_LOOP ->
                "${toolName} called ${count} times - consider if this is correct"
            case LoopStatus.CONFIRMED_LOOP ->
                "${toolName} called ${count} times - this appears to be a loop"
            default -> ""
        }
    }

    private boolean hasHighFrequencyCalls() {
        Map<String, Integer> toolCounts = recentCalls.countBy { it.toolName }
        return toolCounts.values().any { it >= loopThreshold }
    }
}
```

#### 3.2 Permission Manager (`core/PermissionManager.groovy`)

```groovy
package glm.core

class PermissionManager {
    private static final Map<String, Action> DEFAULT_POLICY = [
        "read_file": Action.ALLOW,
        "write_file": Action.ALLOW,
        "list_files": Action.ALLOW,
        "glob": Action.ALLOW,
        "grep": Action.ALLOW,
        "edit_file": Action.ASK,
        "multi_edit_file": Action.ASK,
        "bash": Action.ASK,
        "web_search": Action.ASK,
        "task": Action.ASK
    ]

    enum Action {
        ALLOW,
        ASK,
        DENY
    }

    enum PermissionRequest {
        READ,
        WRITE,
        EXECUTE,
        DELETE,
        NETWORK
    }

    static class PermissionResult {
        Action action
        boolean remembered
        int rememberedDurationMinutes

        static Action ask() {
            return new PermissionResult(Action.ASK, false, 0)
        }

        static Action allow(boolean remember = false, int durationMinutes = 30) {
            return new PermissionResult(Action.ALLOW, remember, durationMinutes)
        }

        static Action deny(boolean remember = false, int durationMinutes = 30) {
            return new PermissionResult(Action.DENY, remember, durationMinutes)
        }

        boolean isAllowed() {
            return action == Action.ALLOW
        }

        boolean isDenied() {
            return action == Action.DENY
        }

        boolean shouldAsk() {
            return action == Action.ASK
        }
    }

    private final Map<String, PermissionResult> rememberedPermissions =
        new ConcurrentHashMap<>()

    private final Map<String, Action> toolPolicies =
        new ConcurrentHashMap<>(DEFAULT_POLICY)

    private DoomLoopDetector doomLoopDetector

    PermissionManager(DoomLoopDetector doomLoopDetector = null) {
        this.doomLoopDetector = doomLoopDetector ?: new DoomLoopDetector()
    }

    PermissionResult checkPermission(String toolName, Map<String, Object> args = [:]) {
        if (doomLoopDetector != null) {
            DoomLoopDetector.LoopCheckResult loopCheck =
                doomLoopDetector.checkForLoop(toolName, args)

            if (loopCheck.shouldPrompt) {
                return PermissionResult.ask()
            }
        }

        PermissionResult remembered = rememberedPermissions.get(toolName)
        if (remembered != null) {
            return remembered
        }

        Action defaultAction = toolPolicies.getOrDefault(toolName, Action.ASK)
        return PermissionResult.ask()
    }

    boolean shouldPromptForLoop(String toolName, Map<String, Object> args) {
        if (doomLoopDetector == null) {
            return false
        }

        DoomLoopDetector.LoopCheckResult result =
            doomLoopDetector.checkForLoop(toolName, args)

        return result.shouldPrompt
    }

    void rememberPermission(String toolName, PermissionResult result) {
        if (result.remembered) {
            rememberedPermissions.put(toolName, result)
        }
    }

    void forgetPermission(String toolName) {
        rememberedPermissions.remove(toolName)
    }

    void forgetAllPermissions() {
        rememberedPermissions.clear()
    }

    void setDefaultPolicy(String toolName, Action action) {
        toolPolicies.put(toolName, action)
    }

    void resetDoomLoopHistory() {
        doomLoopDetector?.reset()
    }

    Map<String, PermissionResult> getRememberedPermissions() {
        return Collections.unmodifiableMap(rememberedPermissions)
    }
}
```

#### 3.3 Permission Prompt Handler (`core/PermissionPrompt.groovy`)

```groovy
package glm.core

interface PermissionPromptHandler {
    PermissionManager.PermissionResult prompt(
        String message,
        String toolName,
        Map<String, Object> args
    )
}

class ConsolePermissionPromptHandler implements PermissionPromptHandler {
    private final Scanner scanner = new Scanner(System.in)
    private final PrintStream out = System.out

    @Override
    PermissionManager.PermissionResult prompt(String message, String toolName,
                                               Map<String, Object> args) {
        out.println("\n" + "=" * 60)
        out.println("PERMISSION REQUIRED")
        out.println("=" * 60)
        out.println(message)
        out.println()
        out.println("Tool: ${toolName}")
        out.println("Arguments: ${formatArgs(args)}")
        out.println()
        out.println("Options:")
        out.println("  [a]llow - Allow this call")
        out.println("  [A]lways - Allow this tool permanently")
        out.println("  [d]eny - Deny this call")
        out.println("  [D]eny always - Deny this tool permanently")
        out.println("  [c]ontinue - Allow and continue without prompting")
        out.println("  [s]top - Stop execution")
        out.println()
        out.print("Your choice: ")

        String choice = scanner.nextLine()?.trim()?.toLowerCase() ?: ""

        return switch(choice) {
            case "a", "allow" -> PermissionManager.PermissionResult.allow(false)
            case "A", "always" -> PermissionManager.PermissionResult.allow(true, 60)
            case "d", "deny" -> PermissionManager.PermissionResult.deny(false)
            case "D", "deny always" -> PermissionManager.PermissionResult.deny(true, 60)
            case "c", "continue" -> PermissionManager.PermissionResult.allow(false)
            case "s", "stop" -> throw new PermissionDeniedException("User stopped execution")
            default -> {
                out.println("Invalid choice, defaulting to ask...")
                PermissionManager.PermissionResult.ask()
            }
        }
    }

    private String formatArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "(none)"
        }
        return args.findAll { it.value != null }.take(5)
            .collect { "${it.key}: ${it.value}" }
            .join(", ") + (args.size() > 5 ? " ..." : "")
    }
}

class PermissionDeniedException extends RuntimeException {
    PermissionDeniedException(String message) {
        super(message)
    }
}
```

#### 3.4 Enhanced Agent Integration (`core/DoomLoopAgent.groovy`)

```groovy
package glm.core

class DoomLoopAgent {
    private DoomLoopDetector detector
    private PermissionManager permissionManager
    private PermissionPromptHandler promptHandler

    DoomLoopAgent(
        DoomLoopDetector detector = null,
        PermissionManager permissionManager = null,
        PermissionPromptHandler promptHandler = null
    ) {
        this.detector = detector ?: new DoomLoopDetector()
        this.permissionManager = permissionManager ?: new PermissionManager(detector)
        this.promptHandler = promptHandler ?: new ConsolePermissionPromptHandler()
    }

    static class ExecutionDecision {
        enum Decision {
            PROCEED,
            SKIP,
            STOP
        }

        Decision decision
        String message
        boolean loopDetected

        static ExecutionDecision proceed() {
            return new ExecutionDecision(Decision.PROCEED, "Proceeding", false)
        }

        static ExecutionDecision skip(String message) {
            return new ExecutionDecision(Decision.SKIP, message, false)
        }

        static ExecutionDecision stop(String message) {
            return new ExecutionDecision(Decision.STOP, message, true)
        }
    }

    ExecutionDecision beforeToolExecution(String toolName, Map<String, Object> args,
                                           int currentStep) {
        detector.recordCall(toolName, args, currentStep)

        DoomLoopDetector.LoopCheckResult loopCheck =
            detector.checkForLoop(toolName, args)

        if (loopCheck.status == DoomLoopDetector.LoopStatus.NONE) {
            return ExecutionDecision.proceed()
        }

        if (loopCheck.status == DoomLoopDetector.LoopStatus.SUSPICIOUS) {
            EventBus.instance.publish(EventType.PROGRESS_UPDATE,
                                     [message: loopCheck.message])
            return ExecutionDecision.proceed()
        }

        return handlePotentialLoop(toolName, args, loopCheck)
    }

    private ExecutionDecision handlePotentialLoop(String toolName,
                                                   Map<String, Object> args,
                                                   DoomLoopDetector.LoopCheckResult loopCheck) {
        PermissionManager.PermissionResult permission =
            permissionManager.checkPermission(toolName, args)

        if (permission.shouldAsk()) {
            PermissionManager.PermissionResult result =
                promptHandler.prompt(loopCheck.message, toolName, args)

            permissionManager.rememberPermission(toolName, result)

            if (result.isDenied()) {
                return ExecutionDecision.stop("Permission denied for ${toolName}")
            }

            if (result.isAllowed()) {
                return ExecutionDecision.proceed()
            }
        }

        if (permission.isAllowed()) {
            return ExecutionDecision.proceed()
        }

        if (permission.isDenied()) {
            return ExecutionDecision.skip("Permission denied for ${toolName}")
        }

        return ExecutionDecision.proceed()
    }

    void reset() {
        detector.reset()
        permissionManager.resetDoomLoopHistory()
    }

    void resetForTool(String toolName) {
        detector.resetForTool(toolName)
    }

    DoomLoopDetector getDetector() {
        return detector
    }

    PermissionManager getPermissionManager() {
        return permissionManager
    }
}
```

### Configuration Schema

```toml
[behavior]
# Doom loop detection settings
doom_loop_threshold = 3           # Number of identical calls before warning
doom_loop_window_size = 10        # How many recent calls to track
doom_loop_cooldown_ms = 5000      # Cooldown between prompts
auto_prompt_for_loops = true      # Automatically prompt on potential loops
```

### Integration Points

1. **Agent Loop Integration** (`core/Agent.groovy`)
   - Wrap tool execution with `DoomLoopAgent.beforeToolExecution()`
   - Handle SKIP/STOP decisions appropriately

2. **Tool Execution** (`tools/Tool.groovy`)
   - Integrate with permission checking

3. **TUI Integration** (`tui/lanterna/LanternaTUI.groovy`)
   - Create TUI-specific `PermissionPromptHandler`
   - Display permission dialogs in terminal

### Testing Strategy

1. Unit tests for DoomLoopDetector loop detection
2. Unit tests for PermissionManager remembered permissions
3. Unit tests for DoomLoopAgent execution decisions
4. Integration test with mock prompt handler
5. Edge case tests for similar but different args

---

## Implementation Order

### Phase 1: Reactive State + Event Bus (2-3 days)
1. Implement `EventBus.groovy`
2. Implement `ReactiveState.groovy`
3. Implement `StateRegistry.groovy`
4. Implement `AgentState.groovy`
5. Integrate with Agent loop
6. Integrate with TUI

### Phase 2: Session Compaction (3-4 days)
1. Implement `TokenCounter.groovy`
2. Implement `CompactionTrigger.groovy`
3. Implement `SummaryGenerator.groovy`
4. Implement `HistoryPruner.groovy`
5. Implement `SessionCompactor.groovy`
6. Integrate with Agent loop
7. Add configuration options

### Phase 3: Doom Loop Detection (2-3 days)
1. Implement `DoomLoopDetector.groovy`
2. Implement `PermissionManager.groovy`
3. Implement `PermissionPrompt.groovy`
4. Implement `DoomLoopAgent.groovy`
5. Integrate with Agent loop
6. Add TUI prompt handler
7. Add configuration options

### Total Estimated Time: 7-10 days

---

## Dependencies Between Components

```
Phase 1 (State + Event Bus)
├── No dependencies
└── Enables: Phase 2, Phase 3

Phase 2 (Session Compaction)
├── Requires: Phase 1 (EventBus for notifications)
└── Enables: Better long-running session handling

Phase 3 (Doom Loop Detection)
├── Requires: Phase 1 (EventBus for progress updates)
└── Independent of Phase 2
```

---

## Testing Requirements

All three components require:

1. **Unit Tests** - Test each class in isolation
2. **Integration Tests** - Test component interactions
3. **End-to-End Tests** - Test full workflows
4. **Performance Tests** - Ensure no significant overhead
5. **Manual Testing** - Verify TUI interactions

Run tests with:
```bash
./glm.groovy --test
```
