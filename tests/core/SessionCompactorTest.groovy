package tests.core

import core.SessionCompactor
import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class SessionCompactorTest extends Specification {
    
    @Subject
    SessionCompactor compactor
    
    def setup() {
        compactor = new SessionCompactor()
    }
    
    def "should compact session history"() {
        given:
        def messages = []
        30.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.compactedHistory != null
        result.summary != null
        result.summary.length() > 0
        TokenCounter.estimateTotalContextTokens(result.compactedHistory, systemPrompt) <= maxContextTokens
    }
    
    def "should handle empty history"() {
        given:
        def messages = []
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.compactedHistory == []
        result.summary == "No conversation history available."
    }
    
    def "should handle history already under limit"() {
        given:
        def messages = [new Message("user", "Hello")]
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.compactedHistory == messages
        result.summary != null
    }
    
    def "should preserve recent messages in compacted history"() {
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
    
    def "should handle null inputs"() {
        when:
        def result = compactor.compactSession(null, null, 1000)
        
        then:
        result != null
        result.compactedHistory == []
        result.summary == "No conversation history available."
    }
    
    def "should handle zero max context tokens"() {
        given:
        def messages = [new Message("user", "Hello")]
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 0
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.compactedHistory == []
        result.summary != null
    }
    
    def "should generate meaningful summary"() {
        given:
        def messages = [
            new Message("user", "What is the capital of France?"),
            new Message("assistant", "The capital of France is Paris."),
            new Message("user", "What about Germany?"),
            new Message("assistant", "The capital of Germany is Berlin.")
        ]
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        when:
        def result = compactor.compactSession(messages, systemPrompt, maxContextTokens)
        
        then:
        result != null
        result.summary != null
        result.summary.length() > 0
        result.summary.contains("France") || result.summary.contains("Paris")
    }
}