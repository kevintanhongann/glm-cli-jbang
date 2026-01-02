package tests.tools

import tools.GrepTool
import tests.base.ToolSpecification
import spock.lang.Unroll

class GrepToolTest extends ToolSpecification {
    
    def "should search for simple pattern in file"() {
        given:
        createTestFile("test.txt", "Hello World\nGoodbye World\nHello Universe")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "Hello"])
        
        then:
        result.contains("Found 2 matches")
        result.contains("Hello")
    }
    
    def "should search with regex pattern"() {
        given:
        createTestFile("code.groovy", "def foo()\ndef bar()\ndef baz()")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "def \\w+\\(\\)"])
        
        then:
        result.contains("Found 3 matches")
        result.contains("def foo()")
        result.contains("def bar()")
        result.contains("def baz()")
    }
    
    def "should return error when pattern is missing"() {
        given:
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([:])
        
        then:
        result.startsWith("Error: pattern is required")
    }
    
    def "should return error for invalid regex pattern"() {
        given:
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "[invalid("])
        
        then:
        result.startsWith("Error: Invalid regex pattern")
    }
    
    def "should return no matches message when pattern not found"() {
        given:
        createTestFile("test.txt", "Hello World")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "Goodbye"])
        
        then:
        result.contains("No matches found")
    }
    
    def "should search in specific directory"() {
        given:
        def subdir = createTestDir("subdir")
        def testFile = subdir.resolve("test.txt")
        java.nio.file.Files.writeString(testFile, "Hello World")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "Hello", path: subdir.toString()])
        
        then:
        result.contains("Found 1 matches")
    }
    
    def "should filter files by glob pattern"() {
        given:
        createTestFile("code.groovy", "Hello")
        createTestFile("code.java", "Hello")
        createTestFile("code.txt", "Hello")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "Hello", include: "*.groovy"])
        
        then:
        result.contains("code.groovy")
        !result.contains("code.java")
    }
    
    def "should handle case-insensitive search"() {
        given:
        createTestFile("test.txt", "HELLO world hello World")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "hello"])
        
        then:
        result.contains("Found 3 matches")
    }
    
    def "should search across multiple files"() {
        given:
        createTestFile("file1.txt", "Hello World")
        createTestFile("file2.txt", "Hello Universe")
        createTestFile("file3.txt", "Goodbye")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "Hello"])
        
        then:
        result.contains("Found 2 matches")
        result.contains("file1.txt")
        result.contains("file2.txt")
    }
    
    def "should handle empty directory"() {
        given:
        def emptyDir = createTestDir("empty")
        def tool = new GrepTool()
        
        when:
        def result = tool.execute([pattern: "test", path: emptyDir.toString()])
        
        then:
        result.contains("No matches found")
    }
}
