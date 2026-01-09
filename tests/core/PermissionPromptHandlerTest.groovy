package tests.core

import core.PermissionPromptHandler
import spock.lang.Specification
import spock.lang.Subject

class PermissionPromptHandlerTest extends Specification {
    
    @Subject
    PermissionPromptHandler handler
    
    def setup() {
        handler = new PermissionPromptHandler()
    }
    
    def "should prompt for permission and return response"() {
        given:
        def prompt = "Test permission request"
        
        when:
        def result = handler.promptPermission(prompt)
        
        then:
        result == false  // Default implementation returns false
    }
    
    def "should handle empty prompt"() {
        when:
        def result = handler.promptPermission("")
        
        then:
        result == false
    }
    
    def "should handle null prompt"() {
        when:
        def result = handler.promptPermission(null)
        
        then:
        result == false
    }
    
    def "should handle long prompt"() {
        given:
        def longPrompt = "This is a very long permission request " * 10
        
        when:
        def result = handler.promptPermission(longPrompt)
        
        then:
        result == false
    }
}