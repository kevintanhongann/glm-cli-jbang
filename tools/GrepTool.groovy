package tools

import core.RipgrepHelper
import core.JavaSearchFallback
import java.nio.file.*

class GrepTool implements Tool {

    @Override
    String getName() {
        return 'grep'
    }

    @Override
    String getDescription() {
        return '''
Fast content search tool that works with any codebase size using regex.

**WHEN TO USE:**
- Searching file contents with specific patterns
- Finding where a function/class is defined
- Locating usage of a specific variable or method
- Narrowing down after glob for content search

**COMMON PATTERNS:**
- "class.*Controller" → find Controller classes
- "def myFunction|def my_method" → find function definitions
- "@Autowired|@Inject" → find dependency injection points
- "TODO|FIXME|HACK" → find code comments

**PARAMETERS:**
- pattern: regex pattern to search for
- path: optional directory to search (default: current)
- include: optional file filter (e.g., "*.groovy", "*.{ts,tsx}")

**BEST PRACTICES:**
- Use glob first to narrow search scope, then grep
- Combine with glob in parallel for faster exploration
- Returns max 100 matches with file paths and line numbers
- Sorted by modification time (newest first)
- Use include parameter to filter by file type

**WHEN NOT TO USE:**
- When searching file names → use glob instead
- When you know the exact file → use read_file instead
- For complex multi-round exploration → use task tool with explore agent

**PARALLEL EXECUTION:**
- Can be combined with glob, read_file, or other grep calls in parallel
- Multiple grep calls with different patterns in parallel
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
                    description: 'The regular expression pattern to search for'
                ],
                path: [
                    type: 'string',
                    description: 'The directory to search in (default: current directory)'
                ],
                include: [
                    type: 'string',
                    description: "Glob pattern to filter files (e.g., '*.groovy', '*.{java,kt}')"
                ]
            ],
            required: ['pattern']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pattern = args.get('pattern')
        String searchPath = args.get('path') ?: '.'
        String include = args.get('include')

        if (!pattern) {
            return 'Error: pattern is required'
        }

        try {
            java.util.regex.Pattern.compile(pattern)
        } catch (Exception e) {
            return "Error: Invalid regex pattern: ${e.message}"
        }

        Path path = Paths.get(searchPath).normalize().toAbsolutePath()
        if (!Files.exists(path)) {
            return "Error: Directory not found: ${searchPath}"
        }

        try {
            def matches = []

            if (RipgrepHelper.isAvailable()) {
                matches = RipgrepHelper.search(pattern, path.toString(), include)
            } else {
                matches = JavaSearchFallback.search(pattern, path.toString(), include)
            }

            if (matches.isEmpty()) {
                return "No matches found for pattern: ${pattern}"
            }

            matches = sortByModificationTime(matches)

            def output = new StringBuilder()
            output.append("Found ${matches.size()} matches for pattern '${pattern}':\n\n")

            def groupedByFile = matches.groupBy { it.filePath }
            groupedByFile.each { filePath, fileMatches ->
                output.append("${filePath}:\n")
                fileMatches.each { match ->
                    output.append("  L${match.lineNumber}: ${match.lineText.take(200)}\n")
                }
                output.append("\n")
            }

            if (matches.size() >= RipgrepHelper.MAX_RESULTS) {
                output.append("(Results truncated at ${RipgrepHelper.MAX_RESULTS} matches)\n")
            }

            return output.toString()
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }

    private List sortByModificationTime(List matches) {
        def fileTimes = [:]
        matches.each { match ->
            if (!fileTimes.containsKey(match.filePath)) {
                try {
                    def mtime = Files.getLastModifiedTime(Paths.get(match.filePath))
                    fileTimes[match.filePath] = mtime.toMillis()
                } catch (Exception e) {
                    fileTimes[match.filePath] = 0
                }
            }
        }

        return matches.sort { a, b ->
            (fileTimes[b.filePath] ?: 0) <=> (fileTimes[a.filePath] ?: 0)
        }
    }

}
