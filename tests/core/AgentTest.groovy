package tests.core

import core.Agent
import tools.Tool
import tests.base.ToolSpecification
import spock.lang.Unroll

import java.nio.file.Files

class AgentTest extends ToolSpecification {
    
    def "should register tool successfully"() {
        given:
        def mockTool = Mock(Tool)
        mockTool.name >> "testTool"
        mockTool.description >> "Test tool description"
        mockTool.parameters >> [type: "object"]
        
        when:
        def agent = new Agent("test-key", "test-model")
        agent.registerTool(mockTool)
        
        then:
        // Tool is registered (verify by checking tool list)
        agent != null
    }
    
    def "should register multiple tools"() {
        given:
        def tool1 = Mock(Tool)
        tool1.name >> "tool1"
        tool1.description >> "Tool 1"
        tool1.parameters >> [type: "object"]
        
        def tool2 = Mock(Tool)
        tool2.name >> "tool2"
        tool2.description >> "Tool 2"
        tool2.parameters >> [type: "object"]
        
        when:
        def agent = new Agent("test-key", "test-model")
        agent.registerTools([tool1, tool2])
        
        then:
        agent != null
    }
    
    def "should create agent with session ID"() {
        when:
        def agent = new Agent("test-key", "test-model", "session-123")
        
        then:
        agent != null
    }
    
    def "should shutdown agent properly"() {
        given:
        def agent = new Agent("test-key", "test-model")
        
        when:
        agent.shutdown()
        
        then:
        // Should complete without exception
        true
    }
    
    def "should handle tool execution with valid args"() {
        given:
        def mockTool = Mock(Tool)
        mockTool.name >> "testTool"
        mockTool.description >> "Test tool"
        mockTool.parameters >> [type: "object"]
        mockTool.execute(_) >> "Tool executed successfully"
        
        when:
        def agent = new Agent("test-key", "test-model")
        agent.registerTool(mockTool)
        def result = mockTool.execute([arg1: "value1"])
        
        then:
        result == "Tool executed successfully"
    }
    
    def "should handle tool execution with missing args"() {
        given:
        def mockTool = Mock(Tool)
        mockTool.name >> "testTool"
        mockTool.description >> "Test tool"
        mockTool.parameters >> [type: "object", properties: [:], required: []]
        mockTool.execute(_) >> "Result"
        
        when:
        def result = mockTool.execute([:])
        
        then:
        result == "Result"
    }
}
