package tests.core

import core.TokenCounter
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class TokenCounterTest extends Specification {
    
    @Subject
    TokenCounter tokenCounter = new TokenCounter()
    
    def "should estimate tokens for English text"() {
        expect:
        TokenCounter.estimateTextTokens("hello world") == 2
        TokenCounter.estimateTextTokens("this is a test") == 4
        TokenCounter.estimateTextTokens("Hello, world!") == 3
    }
    
    def "should estimate tokens for Chinese text"() {
        expect:
        TokenCounter.estimateTextTokens("你好") == 1
        TokenCounter.estimateTextTokens("中文测试") == 2
        TokenCounter.estimateTextTokens("这是一个测试") == 4
    }
    
    def "should estimate tokens for code"() {
        expect:
        TokenCounter.estimateTextTokens("function test() { return true; }") == 6
        TokenCounter.estimateTextTokens("const x = 10;") == 4
        TokenCounter.estimateTextTokens("if (x > 0) { console.log(x); }") == 7
    }
    
    def "should estimate tokens for mixed content"() {
        expect:
        TokenCounter.estimateTextTokens("Hello 世界 test 测试") == 5
        TokenCounter.estimateTextTokens("function 测试() { return 'hello'; }") == 7
    }
    
    def "should handle empty and null text"() {
        expect:
        TokenCounter.estimateTextTokens("") == 0
        TokenCounter.estimateTextTokens(null) == 0
    }
    
    def "should estimate message tokens"() {
        given:
        def message = new Message("user", "Hello world")
        
        expect:
        TokenCounter.estimateMessageTokens(message) == 2
    }
    
    def "should estimate tool call tokens"() {
        expect:
        TokenCounter.estimateToolCallTokens("read_file", '{"path": "test.txt"}') == 10
        TokenCounter.estimateToolCallTokens("write_file", '{"path": "test.txt", "content": "test"}') == 18
    }
    
    def "should estimate history tokens"() {
        given:
        def messages = [
            new Message("user", "Hello"),
            new Message("assistant", "Hi there"),
            new Message("user", "How are you?")
        ]
        
        expect:
        TokenCounter.estimateHistoryTokens(messages) == 5
    }
    
    def "should estimate total context tokens"() {
        given:
        def history = [
            new Message("user", "Hello"),
            new Message("assistant", "Hi there")
        ]
        def systemPrompt = "You are a helpful assistant."
        
        expect:
        TokenCounter.estimateTotalContextTokens(history, systemPrompt) == 10
    }
    
    def "should estimate conversation tokens"() {
        given:
        def messages = [
            new Message("user", "Hello"),
            new Message("assistant", "Hi there"),
            new Message("user", "How are you?")
        ]
        
        expect:
        TokenCounter.estimateConversationTokens(messages) == 17  // 5 tokens + 12 role tokens
    }
    
    def "should estimate context usage"() {
        given:
        def history = [new Message("user", "Hello")]
        def systemPrompt = "You are a helpful assistant."
        def maxContextTokens = 1000
        
        expect:
        TokenCounter.estimateContextUsage(history, systemPrompt, maxContextTokens) == 10
    }
    
    def "should calculate token usage percentage"() {
        expect:
        TokenCounter.calculateTokenUsagePercentage(500, 1000) == 50.0
        TokenCounter.calculateTokenUsagePercentage(750, 1000) == 75.0
        TokenCounter.calculateTokenUsagePercentage(0, 1000) == 0.0
    }
    
    def "should handle special characters"() {
        expect:
        TokenCounter.estimateTextTokens("{}[]()") == 6
        TokenCounter.estimateTextTokens(".,;:'\"") == 6
        TokenCounter.estimateTextTokens("Hello, world!") == 3
    }
    
    def "should handle whitespace correctly"() {
        expect:
        TokenCounter.estimateTextTokens("  hello   world  ") == 2
        TokenCounter.estimateTextTokens("\t\nhello\n\t") == 1
    }
}