package tools

import core.RipgrepHelper
import core.JavaSearchFallback
import java.nio.file.*

class GlobTool implements Tool {

    @Override
    String getName() {
        return 'glob'
    }

    @Override
    String getDescription() {
        return '''
Fast file pattern matching tool that works with any codebase size.

**WHEN TO USE:**
- First step in exploration: Start with glob to discover file structure
- Finding files by pattern: "**/*.groovy", "src/**/*.ts"
- Broad discovery before narrowing with grep
- Finding all files of a certain type in a directory

**COMMON PATTERNS:**
- "**/*.groovy" → all Groovy files recursively
- "src/**/*.java" → all Java files in src/
- "test/*Test.groovy" → test files matching pattern
- "**/{pom.xml,build.gradle,package.json}" → build files

**BEST PRACTICES:**
- Start broad, then narrow down
- Combine with grep in parallel for faster exploration
- Use with list_files for directory structure overview
- Returns files sorted by modification time (newest first)

**WHEN NOT TO USE:**
- When you know the exact file path → use read_file instead
- When searching file contents → use grep instead
- For complex multi-round exploration → use task tool with explore agent

**PARALLEL EXECUTION:**
- Can be combined with grep, list_files, or read_file in parallel
- Always batch independent searches together
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                pattern: [
                    type: 'string',
                    description: "Glob pattern to match files (e.g., '**/*.groovy', 'src/**/*.java')"
                ],
                path: [
                    type: 'string',
                    description: 'The directory to search in (default: current directory)'
                ]
            ],
            required: ['pattern']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pattern = args.get('pattern')
        String searchPath = args.get('path') ?: '.'

        if (!pattern) {
            return 'Error: pattern is required'
        }

        Path path = Paths.get(searchPath).normalize().toAbsolutePath()
        if (!Files.exists(path)) {
            return "Error: Directory not found: ${searchPath}"
        }

        try {
            def files = []

            if (RipgrepHelper.isAvailable()) {
                files = RipgrepHelper.files(pattern, path.toString())
            } else {
                files = JavaSearchFallback.files(pattern, path.toString())
            }

            if (files.isEmpty()) {
                return "No files found matching pattern: ${pattern}"
            }

            files = sortByModificationTime(files, path)

            def output = new StringBuilder()
            output.append("Found ${files.size()} files matching '${pattern}':\n\n")

            files.each { file ->
                output.append("${file}\n")
            }

            if (files.size() >= RipgrepHelper.MAX_RESULTS) {
                output.append("\n(Results truncated at ${RipgrepHelper.MAX_RESULTS} files)\n")
            }

            return output.toString()
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }

    private List<String> sortByModificationTime(List<String> files, Path basePath) {
        return files.sort { a, b ->
            try {
                def pathA = basePath.resolve(a)
                def pathB = basePath.resolve(b)
                def mtimeA = Files.exists(pathA) ? Files.getLastModifiedTime(pathA).toMillis() : 0
                def mtimeB = Files.exists(pathB) ? Files.getLastModifiedTime(pathB).toMillis() : 0
                return mtimeB <=> mtimeA
            } catch (Exception e) {
                return 0
            }
        }
    }

}
