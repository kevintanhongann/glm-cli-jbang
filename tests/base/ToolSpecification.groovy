package tests.base

import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Files
import java.nio.file.Path

abstract class ToolSpecification extends Specification {
    
    @TempDir Path tempDir
    
    /**
     * Create a test file with the given name and content
     */
    Path createTestFile(String name, String content = "") {
        def file = tempDir.resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }
    
    /**
     * Create a test directory with the given name
     */
    Path createTestDir(String name) {
        def dir = tempDir.resolve(name)
        Files.createDirectories(dir)
        return dir
    }
    
    /**
     * Resolve a path relative to tempDir
     */
    Path resolve(String relativePath) {
        return tempDir.resolve(relativePath)
    }
}
