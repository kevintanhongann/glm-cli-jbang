package tools

import core.RipgrepHelper
import core.JavaSearchFallback
import java.nio.file.*

class GlobTool implements Tool {
    
    @Override
    String getName() {
        return "glob"
    }
    
    @Override
    String getDescription() {
        return """Fast file pattern matching tool that works with any codebase size.
- Supports glob patterns like "**/*.groovy" or "src/**/*.java"
- Returns matching file paths sorted by modification time
- Use this tool when you need to find files by name patterns"""
    }
    
    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                pattern: [
                    type: "string",
                    description: "Glob pattern to match files (e.g., '**/*.groovy', 'src/**/*.java')"
                ],
                path: [
                    type: "string",
                    description: "The directory to search in (default: current directory)"
                ]
            ],
            required: ["pattern"]
        ]
    }
    
    @Override
    Object execute(Map<String, Object> args) {
        String pattern = args.get("pattern")
        String searchPath = args.get("path") ?: "."
        
        if (!pattern) {
            return "Error: pattern is required"
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
