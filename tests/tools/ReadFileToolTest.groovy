package tests.tools

import tools.ReadFileTool
import tests.base.ToolSpecification
import spock.lang.Unroll

class ReadFileToolTest extends ToolSpecification {
    
    def "should read existing file successfully"() {
        given:
        def file = createTestFile("test.txt", "Hello World")
        def tool = new ReadFileTool()
        
        when:
        def result = tool.execute([path: file.toString()])
        
        then:
        result == "Hello World"
    }
    
    def "should read file with multiple lines"() {
        given:
        def content = """Line 1
Line 2
Line 3"""
        def file = createTestFile("multiline.txt", content)
        def tool = new ReadFileTool()
        
        when:
        def result = tool.execute([path: file.toString()])
        
        then:
        result == content
    }
    
    def "should return error for non-existent file"() {
        given:
        def tool = new ReadFileTool()
        
        when:
        def result = tool.execute([path: "/nonexistent/file.txt"])
        
        then:
        result.startsWith("Error:")
        result.contains("not found")
    }
    
    def "should return error when path parameter is missing"() {
        given:
        def tool = new ReadFileTool()
        
        when:
        def result = tool.execute([:])
        
        then:
        result == null
    }
    
    def "should normalize paths correctly"() {
        given:
        def file = createTestFile("subdir/test.txt", "content")
        def tool = new ReadFileTool()
        
        expect:
        tool.execute([path: file.toString()]) == "content"
    }
    
    def "should handle empty file"() {
        given:
        def file = createTestFile("empty.txt", "")
        def tool = new ReadFileTool()
        
        when:
        def result = tool.execute([path: file.toString()])
        
        then:
        result == ""
    }
    
    def "should handle special characters in file content"() {
        given:
        def content = "Special chars: \"quotes\", 'apostrophes', \$dollar, @at, #hash"
        def file = createTestFile("special.txt", content)
        def tool = new ReadFileTool()
        
        when:
        def result = tool.execute([path: file.toString()])
        
        then:
        result == content
    }
}
