package tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

class ListFilesTool implements Tool {

    private static final Set<String> IGNORE_PATTERNS = [
        'node_modules',
        '.git',
        '.svn',
        '.hg',
        'dist',
        'build',
        'target',
        'out',
        '.next',
        '.nuxt',
        'coverage',
        '.idea',
        '.vscode',
        '.DS_Store',
        'Thumbs.db'
    ] as Set

    private static final int MAX_FILES = 100

    @Override
    String getName() { 'list_files' }

    @Override
    String getDescription() {
        return '''
List the contents of a directory with optional recursive search in tree format.

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
- Automatically ignores common directories (node_modules, .git, build, etc.)

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
                recursive: [type: 'boolean', description: 'List files recursively (default: false).']
            ],
            required: []
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get('path') ?: '.'
        boolean recursive = args.get('recursive') as Boolean ?: false
        Path dir = Paths.get(pathStr).normalize()

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return "Error: Directory not found: ${pathStr}"
        }

        try {
            def tree = new StringBuilder()
            def fileCount = [0]
            
            buildTree(dir, dir, recursive, 0, tree, fileCount)
            
            if (fileCount[0] >= MAX_FILES) {
                tree.append("\n... (truncated, showing first ${MAX_FILES} files)")
            }
            
            return tree.toString()
        } catch (Exception e) {
            return "Error listing files: ${e.message}"
        }
    }

    private void buildTree(Path root, Path current, boolean recursive, int depth, StringBuilder tree, List<Integer> fileCount) {
        if (fileCount[0] >= MAX_FILES) return

        def items = []
        try {
            Files.list(current).each { path ->
                if (path == current) return
                
                def name = path.fileName.toString()
                if (IGNORE_PATTERNS.contains(name)) return
                if (name.startsWith('.') && name.length() > 1 && !IGNORE_PATTERNS.contains(name)) return
                
                items << [
                    path: path,
                    name: name,
                    isDir: Files.isDirectory(path),
                    isFile: Files.isRegularFile(path),
                    attrs: Files.readAttributes(path, BasicFileAttributes.class)
                ]
            }
        } catch (Exception e) {
            return
        }

        items.sort { a, b ->
            if (a.isDir && !b.isDir) return -1
            if (!a.isDir && b.isDir) return 1
            return a.name.compareToIgnoreCase(b.name)
        }

        def prefix = "│   " * depth
        def connector = depth == 0 ? "" : "└── "

        items.each { item ->
            if (fileCount[0] >= MAX_FILES) return
            fileCount[0]++
            
            def relativePath = root.relativize(item.path)
            def icon = item.isDir ? "📁 " : "📄 "
            
            tree.append(prefix).append(connector).append(icon).append(item.name).append("\n")
            
            if (recursive && item.isDir && depth < 10) {
                buildTree(root, item.path, recursive, depth + 1, tree, fileCount)
            }
        }
    }

}
