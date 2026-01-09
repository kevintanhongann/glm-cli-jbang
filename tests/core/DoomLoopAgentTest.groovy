package tests.core

import core.DoomLoopAgent
import core.DoomLoopDetector
import core.PermissionManager
import core.PermissionPromptHandler
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class DoomLoopAgentTest extends Specification {
    
    @Subject
    DoomLoopAgent agent
    
    def setup() {
        agent = new DoomLoopAgent()
    }
    
    def "should detect doom loop and request permission"() {
        given:
        def detector = Mock(DoomLoopDetector)
        def permissionManager = Mock(PermissionManager)
        agent.detector = detector
        agent.permissionManager = permissionManager
        
        def messages = []
        6.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        
        when:
        def result = agent.handleDoomLoop(messages)
        
        then:
        1 * detector.isDoomLoop(messages) >> true
        1 * permissionManager.requestPermission(_) >> true
        result == true
    }
    
    def "should not handle when no doom loop detected"() {
        given:
        def detector = Mock(DoomLoopDetector)
        def permissionManager = Mock(PermissionManager)
        agent.detector = detector
        agent.permissionManager = permissionManager
        
        def messages = [
            new Message("user", "Question 1"),
            new Message("assistant", "Answer 1"),
            new Message("user", "Question 2"),
            new Message("assistant", "Answer 2")
        ]
        
        when:
        def result = agent.handleDoomLoop(messages)
        
        then:
        1 * detector.isDoomLoop(messages) >> false
        0 * permissionManager.requestPermission(_)
        result == false
    }
    
    def "should handle permission denied"() {
        given:
        def detector = Mock(DoomLoopDetector)
        def permissionManager = Mock(PermissionManager)
        agent.detector = detector
        agent.permissionManager = permissionManager
        
        def messages = []
        6.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        
        when:
        def result = agent.handleDoomLoop(messages)
        
        then:
        1 * detector.isDoomLoop(messages) >> true
        1 * permissionManager.requestPermission(_) >> false
        result == false
    }
    
    def "should handle null messages"() {
        given:
        def detector = Mock(DoomLoopDetector)
        def permissionManager = Mock(PermissionManager)
        agent.detector = detector
        agent.permissionManager = permissionManager
        
        when:
        def result = agent.handleDoomLoop(null)
        
        then:
        1 * detector.isDoomLoop(null) >> false
        0 * permissionManager.requestPermission(_)
        result == false
    }
    
    def "should handle empty messages"() {
        given:
        def detector = Mock(DoomLoopDetector)
        def permissionManager = Mock(PermissionManager)
        agent.detector = detector
        agent.permissionManager = permissionManager
        
        when:
        def result = agent.handleDoomLoop([])
        
        then:
        1 * detector.isDoomLoop([]) >> false
        0 * permissionManager.requestPermission(_)
        result == false
    }
    
    def "should use default components when not set"() {
        when:
        def result = agent.handleDoomLoop([])
        
        then:
        result == false  // Default behavior
    }
    
    def "should handle permission handler"() {
        given:
        def detector = Mock(DoomLoopDetector)
        def permissionHandler = Mock(PermissionPromptHandler)
        agent.detector = detector
        agent.permissionHandler = permissionHandler
        
        def messages = []
        6.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        
        when:
        def result = agent.handleDoomLoop(messages)
        
        then:
        1 * detector.isDoomLoop(messages) >> true
        1 * permissionHandler.promptPermission(_) >> true
        result == true
    }
}