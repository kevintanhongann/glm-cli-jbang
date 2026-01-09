package tests.integration

import core.DoomLoopAgent
import core.DoomLoopDetector
import core.PermissionManager
import core.PermissionPromptHandler
import models.Message
import spock.lang.Specification
import spock.lang.Subject

class DoomLoopIntegrationTest extends Specification {
    
    @Subject
    DoomLoopAgent agent = new DoomLoopAgent()
    
    def "should integrate doom loop detection with permission system"() {
        given:
        def detector = new DoomLoopDetector()
        def permissionManager = new PermissionManager()
        def permissionHandler = Mock(PermissionPromptHandler)
        permissionManager.promptHandler = permissionHandler
        
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
        1 * permissionHandler.promptPermission(_) >> true
        result == true
    }
    
    def "should integrate doom loop detection without permission"() {
        given:
        def detector = new DoomLoopDetector()
        def permissionManager = new PermissionManager()
        def permissionHandler = Mock(PermissionPromptHandler)
        permissionManager.promptHandler = permissionHandler
        
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
        0 * permissionHandler.promptPermission(_)
        result == false
    }
    
    def "should handle permission denied in doom loop detection"() {
        given:
        def detector = new DoomLoopDetector()
        def permissionManager = new PermissionManager()
        def permissionHandler = Mock(PermissionPromptHandler)
        permissionManager.promptHandler = permissionHandler
        
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
        1 * permissionHandler.promptPermission(_) >> false
        result == false
    }
    
    def "should integrate with different doom loop thresholds"() {
        given:
        def detector = new DoomLoopDetector(0.9, 5, 2)  // Stricter thresholds
        def permissionManager = new PermissionManager()
        def permissionHandler = Mock(PermissionPromptHandler)
        permissionManager.promptHandler = permissionHandler
        
        agent.detector = detector
        agent.permissionManager = permissionManager
        
        def messages = []
        4.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        
        when:
        def result = agent.handleDoomLoop(messages)
        
        then:
        1 * permissionHandler.promptPermission(_) >> true
        result == true
    }
    
    def "should handle null components gracefully"() {
        given:
        agent.detector = null
        agent.permissionManager = null
        
        def messages = []
        6.times {
            messages << new Message("user", "Same question")
            messages << new Message("assistant", "Same answer")
        }
        
        when:
        def result = agent.handleDoomLoop(messages)
        
        then:
        result == false
    }
}