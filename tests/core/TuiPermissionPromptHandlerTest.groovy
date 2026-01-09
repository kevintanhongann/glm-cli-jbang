package tests.core

import core.TuiPermissionPromptHandler
import spock.lang.Specification
import spock.lang.Subject

class TuiPermissionPromptHandlerTest extends Specification {
    
    @Subject
    TuiPermissionPromptHandler handler
    
    def setup() {
        handler = new TuiPermissionPromptHandler()
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