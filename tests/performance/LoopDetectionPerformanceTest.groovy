package tests.performance

import core.DoomLoopDetector
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class LoopDetectionPerformanceTest extends Specification {
    
    @Subject
    DoomLoopDetector detector = new DoomLoopDetector()
    
    def "should handle large history loop detection efficiently"() {
        given:
        def messages = []
        10000.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        def startTime = System.currentTimeMillis()
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        isLoop == true
        duration < 5000  // Should complete in under 5 seconds
    }
    
    def "should handle varied history loop detection efficiently"() {
        given:
        def messages = []
        5000.times { i ->
            messages << new Message("user", "Question $i")
            messages << new Message("assistant", "Answer $i")
        }
        def startTime = System.currentTimeMillis()
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        isLoop == false
        duration < 5000  // Should complete in under 5 seconds
    }
    
    def "should handle similarity calculation efficiently"() {
        given:
        def text1 = "This is a test message with some content. " * 1000
        def text2 = "This is a test message with some content. " * 1000
        def startTime = System.currentTimeMillis()
        
        when:
        def similarity = detector.calculateSimilarity(text1, text2)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        similarity > 0.8
        duration < 1000  // Should complete in under 1 second
    }
    
    def "should handle concurrent loop detection"() {
        given:
        def threadCount = 10
        def messagesPerThread = 1000
        def startTime = System.currentTimeMillis()
        def results = []
        
        when:
        def threads = []
        threadCount.times {
            threads << Thread.start {
                def messages = []
                messagesPerThread.times { j ->
                    messages << new Message("user", "Same question")
                    messages << new Message("assistant", "Same answer")
                }
                def isLoop = detector.isDoomLoop(messages)
                results << isLoop
            }
        }
        threads*.join()
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        results.size() == threadCount
        results.every { it == true }
        duration < 10000  // Should complete in under 10 seconds
    }
    
    def "should handle edge cases efficiently"() {
        given:
        def emptyMessages = []
        def singleMessage = [new Message("user", "Hello")]
        def nullMessages = null
        def startTime = System.currentTimeMillis()
        
        when:
        def result1 = detector.isDoomLoop(emptyMessages)
        def result2 = detector.isDoomLoop(singleMessage)
        def result3 = detector.isDoomLoop(nullMessages)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        result1 == false
        result2 == false
        result3 == false
        duration < 100  // Should complete very quickly
    }
}