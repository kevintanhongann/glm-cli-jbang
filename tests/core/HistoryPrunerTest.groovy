package tests.core

import core.HistoryPruner
import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class HistoryPrunerTest extends Specification {
    
    @Subject
    HistoryPruner pruner
    
    def setup() {
        pruner = new HistoryPruner()
    }
    
    def "should prune history to target token count"() {
        given:
        def messages = []
        20.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def targetTokens = 100
        
        when:
        def pruned = pruner.pruneHistory(messages, targetTokens)
        
        then:
        pruned != null
        pruned.size() < messages.size()
        TokenCounter.estimateHistoryTokens(pruned) <= targetTokens
    }
    
    def "should handle empty history"() {
        given:
        def messages = []
        def targetTokens = 100
        
        when:
        def pruned = pruner.pruneHistory(messages, targetTokens)
        
        then:
        pruned == []
    }
    
    def "should handle history already under target"() {
        given:
        def messages = [new Message("user", "Hello")]
        def targetTokens = 1000
        
        when:
        def pruned = pruner.pruneHistory(messages, targetTokens)
        
        then:
        pruned == messages
    }
    
    def "should preserve recent messages"() {
        given:
        def messages = []
        10.times {
            messages << new Message("user", "Old message $it")
            messages << new Message("assistant", "Old response $it")
        }
        messages << new Message("user", "Recent message")
        messages << new Message("assistant", "Recent response")
        
        when:
        def pruned = pruner.pruneHistory(messages, 50)
        
        then:
        pruned.contains(messages[-1])  // Should contain most recent
        pruned.contains(messages[-2])  // Should contain second most recent
    }
    
    def "should handle null history"() {
        when:
        def pruned = pruner.pruneHistory(null, 100)
        
        then:
        pruned == null
    }
    
    def "should handle zero target tokens"() {
        given:
        def messages = [new Message("user", "Hello")]
        
        when:
        def pruned = pruner.pruneHistory(messages, 0)
        
        then:
        pruned == []
    }
    
    def "should handle negative target tokens"() {
        given:
        def messages = [new Message("user", "Hello")]
        
        when:
        def pruned = pruner.pruneHistory(messages, -1)
        
        then:
        pruned == []
    }
    
    def "should maintain message order"() {
        given:
        def messages = []
        5.times {
            messages << new Message("user", "Message $it")
            messages << new Message("assistant", "Response $it")
        }
        
        when:
        def pruned = pruner.pruneHistory(messages, 20)
        
        then:
        pruned != null
        // Check that order is preserved
        def userMessages = pruned.findAll { it.role == "user" }
        def assistantMessages = pruned.findAll { it.role == "assistant" }
        userMessages.size() == assistantMessages.size()
    }
}