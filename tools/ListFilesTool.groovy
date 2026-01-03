package tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ListFilesTool implements Tool {

    @Override
    String getName() { 'list_files' }

    @Override
    String getDescription() {
        return '''
List the contents of a directory with optional recursive search.

**WHEN TO USE:**
- Understanding directory structure
- Finding what files exist in a specific location
- Exploring project layout
- Checking if a directory exists before operations

**PARAMETERS:**
- path: directory path to list (default: current directory)
- recursive: whether to list subdirectories (default: false)

**BEST PRACTICES:**
- Use for directory structure overview
- Combine with glob for pattern matching
- Use recursive=true for full tree view
- Returns directories and files separately

**WHEN NOT TO USE:**
- When searching by pattern → use glob instead
- When searching contents → use grep instead
- For large codebases, use glob with patterns instead

**PARALLEL EXECUTION:**
- Can be combined with other list_files calls in parallel
- Can be combined with glob or grep in parallel
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                path: [type: 'string', description: 'The directory path (default: .).'],
                recursive: [type: 'boolean', description: 'List files recursively.']
            ],
            required: ['path']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get('path') ?: '.'
        Path dir = Paths.get(pathStr).normalize()

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return "Error: Directory not found: ${pathStr}"
        }

        try {
            def files = []
            Files.walk(dir, 1).forEach { p ->
                files.add(p.toString())
            }
            return files.join("\n")
        } catch (Exception e) {
            return "Error listing files: ${e.message}"
        }
    }

}
