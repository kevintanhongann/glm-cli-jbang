package tests.core

import core.DoomLoopDetector
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class DoomLoopDetectorTest extends Specification {
    
    @Subject
    DoomLoopDetector detector
    
    def setup() {
        detector = new DoomLoopDetector()
    }
    
    def "should detect doom loop with repeated messages"() {
        given:
        def messages = []
        6.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        
        then:
        isLoop == true
    }
    
    def "should not detect doom loop with varied messages"() {
        given:
        def messages = []
        6.times { i ->
            messages << new Message("user", "Question $i")
            messages << new Message("assistant", "Answer $i")
        }
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        
        then:
        isLoop == false
    }
    
    def "should handle empty history"() {
        given:
        def messages = []
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        
        then:
        isLoop == false
    }
    
    def "should handle null history"() {
        when:
        def isLoop = detector.isDoomLoop(null)
        
        then:
        isLoop == false
    }
    
    def "should handle short history"() {
        given:
        def messages = [
            new Message("user", "Hello"),
            new Message("assistant", "Hi")
        ]
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        
        then:
        isLoop == false
    }
    
    def "should detect loop with different message patterns"() {
        given:
        def messages = []
        4.times {
            messages << new Message("user", "What is 2+2?")
            messages << new Message("assistant", "4")
        }
        
        when:
        def isLoop = detector.isDoomLoop(messages)
        
        then:
        isLoop == true
    }
    
    def "should use default thresholds"() {
        expect:
        detector.similarityThreshold == 0.8
        detector.minMessagesForDetection == 10
        detector.consecutiveSimilarThreshold == 3
    }
    
    def "should use custom thresholds"() {
        given:
        def detector = new DoomLoopDetector(0.9, 5, 2)
        
        expect:
        detector.similarityThreshold == 0.9
        detector.minMessagesForDetection == 5
        detector.consecutiveSimilarThreshold == 2
    }
    
    def "should calculate similarity correctly"() {
        given:
        def msg1 = "Hello world"
        def msg2 = "Hello world"
        
        when:
        def similarity = detector.calculateSimilarity(msg1, msg2)
        
        then:
        similarity == 1.0
    }
    
    def "should handle completely different messages"() {
        given:
        def msg1 = "Hello"
        def msg2 = "Goodbye"
        
        when:
        def similarity = detector.calculateSimilarity(msg1, msg2)
        
        then:
        similarity == 0.0
    }
    
    def "should handle partial similarity"() {
        given:
        def msg1 = "Hello world"
        def msg2 = "Hello there"
        
        when:
        def similarity = detector.calculateSimilarity(msg1, msg2)
        
        then:
        similarity > 0.0
        similarity < 1.0
    }
}