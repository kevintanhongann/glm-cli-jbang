package tests.tools

import tools.WriteFileTool
import tests.base.ToolSpecification
import spock.lang.Unroll
import java.nio.file.Files

class WriteFileToolTest extends ToolSpecification {
    
    def "should write content to new file"() {
        given:
        def file = resolve("newfile.txt")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString(), content: "Hello World"])
        
        then:
        result.contains("Successfully")
        Files.exists(file)
        Files.readString(file) == "Hello World"
    }
    
    def "should overwrite existing file when allowed"() {
        given:
        def file = createTestFile("existing.txt", "Original")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString(), content: "Updated"])
        
        then:
        result.contains("Successfully")
        Files.readString(file) == "Updated"
    }
    
    def "should create intermediate directories"() {
        given:
        def file = resolve("deep/nested/path/file.txt")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString(), content: "content"])
        
        then:
        result.contains("Successfully")
        Files.exists(file)
        Files.readString(file) == "content"
    }
    
    def "should return error when path parameter is missing"() {
        given:
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([content: "content"])
        
        then:
        result == null
    }
    
    def "should return error when content parameter is missing"() {
        given:
        def file = resolve("file.txt")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString()])
        
        then:
        result == null
    }
    
    def "should write multi-line content"() {
        given:
        def content = """Line 1
Line 2
Line 3"""
        def file = resolve("multiline.txt")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString(), content: content])
        
        then:
        result.contains("Successfully")
        Files.readString(file) == content
    }
    
    def "should handle empty content"() {
        given:
        def file = resolve("empty.txt")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString(), content: ""])
        
        then:
        result.contains("Successfully")
        Files.exists(file)
        Files.readString(file) == ""
    }
    
    def "should handle special characters in content"() {
        given:
        def content = "Special chars: \"quotes\", 'apostrophes', \$dollar, @at, #hash"
        def file = resolve("special.txt")
        def tool = new WriteFileTool(safetyMode: "always_allow")
        
        when:
        def result = tool.execute([path: file.toString(), content: content])
        
        then:
        result.contains("Successfully")
        Files.readString(file) == content
    }
}
