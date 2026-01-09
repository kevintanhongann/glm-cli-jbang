package tests.performance

import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class TokenCounterPerformanceTest extends Specification {
    
    @Subject
    TokenCounter tokenCounter = new TokenCounter()
    
    def "should handle large text token estimation efficiently"() {
        given:
        def largeText = "This is a test message with some content. " * 10000
        def startTime = System.currentTimeMillis()
        
        when:
        def tokenCount = TokenCounter.estimateTextTokens(largeText)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        tokenCount > 0
        duration < 1000  // Should complete in under 1 second
    }
    
    def "should handle large message history efficiently"() {
        given:
        def messages = []
        1000.times {
            messages << new Message("user", "This is a test message with some content.")
            messages << new Message("assistant", "This is a response message with some content.")
        }
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 100000
        def startTime = System.currentTimeMillis()
        
        when:
        def tokenUsage = TokenCounter.estimateContextUsage(messages, systemPrompt, maxContextTokens)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        tokenUsage > 0
        duration < 2000  // Should complete in under 2 seconds
    }
    
    def "should handle concurrent token counting"() {
        given:
        def threadCount = 10
        def messagesPerThread = 100
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
                def tokenCount = TokenCounter.estimateHistoryTokens(messages)
                results << tokenCount
            }
        }
        threads*.join()
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        results.size() == threadCount
        results.every { it > 0 }
        duration < 3000  // Should complete in under 3 seconds
    }
    
    def "should handle mixed content token estimation"() {
        given:
        def mixedContent = """
            This is English text with some Chinese: 你好世界
            And some code: function test() { return true; }
            And special characters: {}[]()<>.,;:'"
            This is repeated many times to make it large: 
        """ * 1000
        def startTime = System.currentTimeMillis()
        
        when:
        def tokenCount = TokenCounter.estimateTextTokens(mixedContent)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        tokenCount > 0
        duration < 1000  // Should complete in under 1 second
    }
    
    def "should handle tool call token estimation efficiently"() {
        given:
        def toolName = "read_file"
        def arguments = '{"path": "test.txt", "content": "This is test content"}' * 1000
        def startTime = System.currentTimeMillis()
        
        when:
        def tokenCount = TokenCounter.estimateToolCallTokens(toolName, arguments)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        tokenCount > 0
        duration < 100  // Should complete very quickly
    }
}