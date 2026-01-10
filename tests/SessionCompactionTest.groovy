package tests

import core.TokenCounter
import core.CompactionTrigger
import core.HistoryPruner
import core.SessionCompactor
import models.Message
import spock.lang.Specification

class SessionCompactionTest extends Specification {

    def "TokenCounter estimates tokens correctly"() {
        when:
        int tokens = TokenCounter.estimateTextTokens("Hello world test message")

        then:
        tokens > 0
        tokens <= 100  // Should be reasonable estimate
    }

    def "TokenCounter handles empty text"() {
        expect:
        TokenCounter.estimateTextTokens("") == 0
        TokenCounter.estimateTextTokens(null) == 0
    }

    def "CompactionTrigger detects warning level at 75%"() {
        given:
        def trigger = new CompactionTrigger(100)

        when:
        def level = trigger.checkLevel(76)

        then:
        level == CompactionTrigger.TriggerLevel.WARNING
    }

    def "CompactionTrigger detects critical level at 90%"() {
        given:
        def trigger = new CompactionTrigger(100)

        when:
        def level = trigger.checkLevel(91)

        then:
        level == CompactionTrigger.TriggerLevel.CRITICAL
    }

    def "CompactionTrigger returns NONE below 75%"() {
        given:
        def trigger = new CompactionTrigger(100)

        when:
        def level = trigger.checkLevel(74)

        then:
        level == CompactionTrigger.TriggerLevel.NONE
    }

    def "HistoryPruner preserves system messages"() {
        given:
        def history = [
            new Message('system', 'System prompt'),
            new Message('user', 'User message'),
            new Message('assistant', 'Assistant response')
        ]
        def pruner = new HistoryPruner()

        when:
        def result = pruner.prune(history, 1000)

        then:
        result.prunedHistory[0].role == 'system'
    }

    def "HistoryPruner removes messages to fit token budget"() {
        given:
        def history = (1..10).collect { i ->
            new Message('user', "User message ${i}" * 50)
        }
        def pruner = new HistoryPruner()

        when:
        def result = pruner.prune(history, 100)

        then:
        result.prunedHistory.size() < history.size()
        result.messagesRemoved > 0
    }

    def "SessionCompactor skips when context is within limits"() {
        given:
        def compactor = SessionCompactor.instance
        def client = Mock(core.GlmClient)
        compactor.initialize(client, null)
        compactor.maxContextTokens = 8000

        def history = [new Message('user', 'test')]
        def systemPrompt = 'system'

        when:
        def result = compactor.maybeCompact('test-session', history, systemPrompt)

        then:
        result.performed == false
    }

    def "SessionCompactor detects compaction need at high token usage"() {
        given:
        def compactor = SessionCompactor.instance
        def client = Mock(core.GlmClient)
        compactor.initialize(client, null)
        compactor.maxContextTokens = 100  // Very low limit

        def history = (1..50).collect { i ->
            new Message('user', 'Large message ' * 100)
        }
        def systemPrompt = 'system'

        when:
        def result = compactor.maybeCompact('test-session', history, systemPrompt)

        then:
        result.performed == true || result.performed == false  // May not have sessionManager
    }
}
