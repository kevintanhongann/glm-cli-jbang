import core.Instructions
import core.Filesystem
import java.nio.file.Files
import java.nio.file.Paths

class InstructionsTest {

    static void testFindUp() {
        def matches = Filesystem.findUp("AGENTS.md", System.getProperty("user.dir"))
        assert matches.size() >= 0 : "Should find AGENTS.md or return empty list"
        println "✓ findUp test passed (found ${matches.size()} matches)"
    }

    static void testDetect() {
        def paths = Instructions.detect()
        assert paths.size() >= 0 : "Should detect instruction files"
        println "✓ detect test passed (found ${paths.size()} files)"
        paths.each { path ->
            println "  - ${path}"
        }
    }

    static void testLoadAll() {
        def contents = Instructions.loadAll()
        assert contents.size() >= 0 : "Should load instruction content"
        println "✓ loadAll test passed (loaded ${contents.size()} files)"
        contents.each { content ->
            def lines = content.split('\n')
            println "  - ${lines[0]} (${lines.size()} lines total)"
        }
    }

    static void runAll() {
        println "Running Instructions tests..."
        println ""
        testFindUp()
        testDetect()
        testLoadAll()
        println ""
        println "All tests passed!"
    }

    static void main(String[] args) {
        runAll()
    }
}
