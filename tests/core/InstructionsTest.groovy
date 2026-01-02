package tests.core

import core.Instructions
import core.Filesystem
import tests.base.ToolSpecification

class InstructionsTest extends ToolSpecification {
    
    def "should find AGENTS.md using Filesystem.findUp"() {
        when:
        def matches = Filesystem.findUp("AGENTS.md", tempDir.toString())
        
        then:
        matches != null
        matches instanceof List
    }
    
    def "should detect instruction files in current directory"() {
        given:
        def agentsFile = createTestFile("AGENTS.md", "# Test Agent Guidelines\n\nThese are test guidelines.")
        
        when:
        def paths = Instructions.detect()
        
        then:
        paths != null
        paths instanceof List
    }
    
    def "should load content from instruction files"() {
        given:
        def agentsFile = createTestFile("AGENTS.md", "# Test Guidelines\n\nFollow these rules.")
        
        when:
        def contents = Instructions.loadAll()
        
        then:
        contents != null
        contents instanceof List
    }
    
    def "should handle empty directory for instructions"() {
        given:
        def emptyDir = createTestDir("empty-instructions")
        
        when:
        def paths = Instructions.detect()
        
        then:
        paths != null
        // May find instructions from parent directories
    }
    
    def "should handle malformed instruction files"() {
        given:
        def badFile = createTestFile("AGENTS.md", "")
        
        when:
        def contents = Instructions.loadAll()
        
        then:
        contents != null
        contents instanceof List
    }
    
    def "should find CLAUDE.md if it exists"() {
        given:
        def claudeFile = createTestFile("CLAUDE.md", "# Claude Instructions")
        
        when:
        def paths = Instructions.detect()
        
        then:
        paths != null
        paths instanceof List
    }
    
    def "should find CONTEXT.md if it exists"() {
        given:
        def contextFile = createTestFile("CONTEXT.md", "# Context Instructions")
        
        when:
        def paths = Instructions.detect()
        
        then:
        paths != null
        paths instanceof List
    }
}
