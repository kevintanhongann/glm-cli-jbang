package tests.integration

import core.CompactionTrigger
import core.HistoryPruner
import core.SessionCompactor
import core.SummaryGenerator
import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class SessionCompactionIntegrationTest extends Specification {
    
    @Subject
    SessionCompactor compactor = new SessionCompactor()
    
    def "should integrate compaction trigger with session compactor"() {
        given:
        def trigger = new CompactionTrigger(0.5, 5)  // Low thresholds for testing
        def messages = []
        10.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def shouldCompact = trigger.shouldCompact(messages, systemPrompt, maxContextTokens)
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == true
        result != null
        result.compactedHistory != null
        result.summary != null
        result.summary.length() > 0
        TokenCounter.estimateTotalContextTokens(result.compactedHistory, systemPrompt) <= maxContextTokens
    }
    
    def "should integrate summary generator with session compactor"() {
        given:
        def generator = new SummaryGenerator()
        def messages = [
            new Message("user", "What is the capital of France?"),
            new Message("assistant", "The capital of France is Paris."),
            new Message("user", "What about Germany?"),
            new Message("assistant", "The capital of Germany is Berlin.")
        ]
        
        when:
        def summary = generator.generateSummary(messages)
        def result = compactor.compactSession(messages, "You are a helpful assistant.", 1000)
        
        then:
        summary != null
        summary.length() > 0
        result.summary == summary
    }
    
    def "should integrate history pruner with session compactor"() {
        given:
        def pruner = new HistoryPruner()
        def messages = []
        20.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def targetTokens = 100
        
        when:
        def pruned = pruner.pruneHistory(messages, targetTokens)
        def result = compactor.compactSession(messages, "You are a helpful assistant.", 200)
        
        then:
        pruned != null
        pruned.size() < messages.size()
        TokenCounter.estimateHistoryTokens(pruned) <= targetTokens
        result.compactedHistory.size() < messages.size()
    }
    
    def "should handle complete compaction workflow"() {
        given:
        def messages = []
        30.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 500
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.compactedHistory != null
        result.summary != null
        result.summary.length() > 0
        TokenCounter.estimateTotalContextTokens(result.compactedHistory, systemPrompt) <= maxContextTokens
        result.compactedHistory.size() < messages.size()
    }
    
    def "should preserve recent messages during compaction"() {
        given:
        def messages = []
        20.times {
            messages << new Message("user", "Old message $it")
            messages << new Message("assistant", "Old response $it")
        }
        messages << new Message("user", "Recent message")
        messages << new Message("assistant", "Recent response")
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 200
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.compactedHistory.contains(messages[-1])  // Should contain most recent
        result.compactedHistory.contains(messages[-2])  // Should contain second most recent
    }
}