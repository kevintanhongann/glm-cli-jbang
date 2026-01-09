package tests.core

import core.PermissionManager
import core.PermissionPromptHandler
import spock.lang.Specification
import spock.lang.Subject

class PermissionManagerTest extends Specification {
    
    @Subject
    PermissionManager manager
    
    def setup() {
        manager = new PermissionManager()
    }
    
    def "should request permission and handle response"() {
        given:
        def promptHandler = Mock(PermissionPromptHandler)
        manager.promptHandler = promptHandler
        
        when:
        def result = manager.requestPermission("Test permission")
        
        then:
        1 * promptHandler.promptPermission("Test permission") >> true
        result == true
    }
    
    def "should deny permission when handler returns false"() {
        given:
        def promptHandler = Mock(PermissionPromptHandler)
        manager.promptHandler = promptHandler
        
        when:
        def result = manager.requestPermission("Test permission")
        
        then:
        1 * promptHandler.promptPermission("Test permission") >> false
        result == false
    }
    
    def "should handle null prompt handler"() {
        given:
        manager.promptHandler = null
        
        when:
        def result = manager.requestPermission("Test permission")
        
        then:
        result == false
    }
    
    def "should handle empty permission request"() {
        given:
        def promptHandler = Mock(PermissionPromptHandler)
        manager.promptHandler = promptHandler
        
        when:
        def result = manager.requestPermission("")
        
        then:
        1 * promptHandler.promptPermission("") >> true
        result == true
    }
    
    def "should handle null permission request"() {
        given:
        def promptHandler = Mock(PermissionPromptHandler)
        manager.promptHandler = promptHandler
        
        when:
        def result = manager.requestPermission(null)
        
        then:
        1 * promptHandler.promptPermission(null) >> true
        result == true
    }
    
    def "should handle multiple permission requests"() {
        given:
        def promptHandler = Mock(PermissionPromptHandler)
        manager.promptHandler = promptHandler
        
        when:
        def result1 = manager.requestPermission("Permission 1")
        def result2 = manager.requestPermission("Permission 2")
        
        then:
        1 * promptHandler.promptPermission("Permission 1") >> true
        1 * promptHandler.promptPermission("Permission 2") >> false
        result1 == true
        result2 == false
    }
}