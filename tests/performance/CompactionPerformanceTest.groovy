package tests.performance

import core.CompactionTrigger
import core.HistoryPruner
import core.SessionCompactor
import core.SummaryGenerator
import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class CompactionPerformanceTest extends Specification {
    
    @Subject
    SessionCompactor compactor = new SessionCompactor()
    
    def "should handle large history compaction efficiently"() {
        given:
        def messages = []
        5000.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 10000
        def startTime = System.currentTimeMillis()
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        result != null
        result.compactedHistory != null
        result.summary != null
        result.summary.length() > 0
        duration < 10000  // Should complete in under 10 seconds
    }
    
    def "should handle compaction trigger efficiently"() {
        given:
        def trigger = new CompactionTrigger()
        def messages = []
        1000.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 5000
        def startTime = System.currentTimeMillis()
        
        when:
        def shouldCompact = trigger.shouldCompact(messages, systemPrompt, maxContextTokens)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        shouldCompact == true
        duration < 1000  // Should complete in under 1 second
    }
    
    def "should handle summary generation efficiently"() {
        given:
        def generator = new SummaryGenerator()
        def messages = []
        1000.times {
            messages << new Message("user", "Question $it")
            messages << new Message("assistant", "Answer $it")
        }
        def startTime = System.currentTimeMillis()
        
        when:
        def summary = generator.generateSummary(messages)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        summary != null
        summary.length() > 0
        duration < 5000  // Should complete in under 5 seconds
    }
    
    def "should handle history pruning efficiently"() {
        given:
        def pruner = new HistoryPruner()
        def messages = []
        2000.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def targetTokens = 1000
        def startTime = System.currentTimeMillis()
        
        when:
        def pruned = pruner.pruneHistory(messages, targetTokens)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        pruned != null
        pruned.size() < messages.size()
        TokenCounter.estimateHistoryTokens(pruned) <= targetTokens
        duration < 5000  // Should complete in under 5 seconds
    }
    
    def "should handle concurrent compaction operations"() {
        given:
        def threadCount = 5
        def messagesPerThread = 1000
        def startTime = System.currentTimeMillis()
        def results = []
        
        when:
        def threads = []
        threadCount.times {
            threads << Thread.start {
                def messages = []
                messagesPerThread.times { j ->
                    messages << new Message("user", "Test message $j")
                    messages << new Message("assistant", "Response $j")
                }
                def result = compactor.compactSession(messages, "You are a helpful assistant.", 2000)
                results << result
            }
        }
        threads*.join()
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        results.size() == threadCount
        results.every { it != null && it.compactedHistory != null }
        duration < 15000  // Should complete in under 15 seconds
    }
}