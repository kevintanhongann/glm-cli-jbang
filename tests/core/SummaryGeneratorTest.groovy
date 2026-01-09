package tests.core

import core.SummaryGenerator
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class SummaryGeneratorTest extends Specification {
    
    @Subject
    SummaryGenerator generator
    
    def setup() {
        generator = new SummaryGenerator()
    }
    
    def "should generate summary for conversation"() {
        given:
        def messages = [
            new Message("user", "What is the capital of France?"),
            new Message("assistant", "The capital of France is Paris."),
            new Message("user", "What about Germany?"),
            new Message("assistant", "The capital of Germany is Berlin.")
        ]
        
        when:
        def summary = generator.generateSummary(messages)
        
        then:
        summary != null
        summary.length() > 0
        summary.contains("France") || summary.contains("Paris")
        summary.contains("Germany") || summary.contains("Berlin")
    }
    
    def "should handle empty conversation"() {
        given:
        def messages = []
        
        when:
        def summary = generator.generateSummary(messages)
        
        then:
        summary == "No conversation history available."
    }
    
    def "should handle single message"() {
        given:
        def messages = [new Message("user", "Hello")]
        
        when:
        def summary = generator.generateSummary(messages)
        
        then:
        summary != null
        summary.length() > 0
    }
    
    def "should handle null messages"() {
        when:
        def summary = generator.generateSummary(null)
        
        then:
        summary == "No conversation history available."
    }
    
    def "should generate concise summary"() {
        given:
        def messages = []
        10.times {
            messages << new Message("user", "This is message number $it with some content.")
            messages << new Message("assistant", "This is the response to message number $it.")
        }
        
        when:
        def summary = generator.generateSummary(messages)
        
        then:
        summary != null
        summary.length() < 500  // Should be reasonably concise
    }
    
    def "should handle mixed message types"() {
        given:
        def messages = [
            new Message("user", "Question 1"),
            new Message("assistant", "Answer 1"),
            new Message("user", "Question 2"),
            new Message("assistant", "Answer 2"),
            new Message("user", "Question 3")
        ]
        
        when:
        def summary = generator.generateSummary(messages)
        
        then:
        summary != null
        summary.length() > 0
    }
    
    def "should handle long messages"() {
        given:
        def longContent = "This is a very long message " * 100
        def messages = [new Message("user", longContent)]
        
        when:
        def summary = generator.generateSummary(messages)
        
        then:
        summary != null
        summary.length() > 0
    }
}