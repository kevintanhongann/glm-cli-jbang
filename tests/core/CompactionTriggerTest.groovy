package tests.core

import core.CompactionTrigger
import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class CompactionTriggerTest extends Specification {
    
    @Subject
    CompactionTrigger trigger
    
    def setup() {
        trigger = new CompactionTrigger()
    }
    
    def "should trigger compaction when token usage is high"() {
        given:
        def history = []
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        // Add enough messages to exceed 80% threshold
        20.times {
            history << new Message("user", "This is a test message with some content.")
            history << new Message("assistant", "This is a response message.")
        }
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == true
    }
    
    def "should not trigger compaction when token usage is low"() {
        given:
        def history = [
            new Message("user", "Hello"),
            new Message("assistant", "Hi there")
        ]
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == false
    }
    
    def "should trigger compaction based on message count"() {
        given:
        def history = []
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        // Add many short messages
        50.times {
            history << new Message("user", "Hi")
            history << new Message("assistant", "Hello")
        }
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == true
    }
    
    def "should use default thresholds when not configured"() {
        given:
        def trigger = new CompactionTrigger()
        
        expect:
        trigger.tokenUsageThreshold == 0.8
        trigger.messageCountThreshold == 20
    }
    
    def "should use custom thresholds"() {
        given:
        def trigger = new CompactionTrigger(0.9, 30)
        
        expect:
        trigger.tokenUsageThreshold == 0.9
        trigger.messageCountThreshold == 30
    }
    
    def "should handle empty history"() {
        given:
        def history = []
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == false
    }
    
    def "should handle null history"() {
        given:
        def history = null
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == false
    }
    
    def "should handle null system prompt"() {
        given:
        def history = [new Message("user", "Hello")]
        def systemPrompt = null
        def maxContextTokens = 1000
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == false
    }
    
    def "should trigger compaction when both thresholds are met"() {
        given:
        def history = []
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        // Add messages to meet both thresholds
        25.times {
            history << new Message("user", "This is a moderately long test message.")
            history << new Message("assistant", "This is a moderately long response message.")
        }
        
        when:
        def shouldCompact = trigger.shouldCompact(history, systemPrompt, maxContextTokens)
        
        then:
        shouldCompact == true
    }
    
    def "should calculate token usage correctly"() {
        given:
        def history = [new Message("user", "Hello")]
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def tokenUsage = trigger.calculateTokenUsage(history, systemPrompt, maxContextTokens)
        
        then:
        tokenUsage > 0.0
        tokenUsage < 1.0
    }
}