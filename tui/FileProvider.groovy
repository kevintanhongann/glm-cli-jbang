package tui

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Provides file suggestions for @ mentions.
 * Supports fuzzy matching and line range syntax (#L1-L10).
 */
class FileProvider {

    private static final int MAX_RESULTS = 50
    private static final Set<String> IGNORED_DIRS = [
        '.git', 'node_modules', '.gradle', 'build', 'target', 
        '.idea', '.vscode', '__pycache__', '.cache', 'dist'
    ] as Set

    private static final Set<String> CODE_EXTENSIONS = [
        '.groovy', '.java', '.kt', '.scala',
        '.js', '.ts', '.jsx', '.tsx', '.vue', '.svelte',
        '.py', '.rb', '.go', '.rs', '.c', '.cpp', '.h',
        '.json', '.yaml', '.yml', '.toml', '.xml',
        '.md', '.txt', '.sh', '.bash', '.zsh',
        '.html', '.css', '.scss', '.less'
    ] as Set

    /**
     * Get file suggestions based on query.
     * Supports partial path matching and line ranges.
     * 
     * Examples:
     *   @README.md
     *   @src/main.groovy
     *   @config.yaml#L10
     *   @config.yaml#L10-L50
     */
    static List<AutocompleteItem> getFiles(String workingDir, String query) {
        List<AutocompleteItem> results = []
        
        // Parse line range from query
        def parsed = extractLineRange(query)
        String baseQuery = parsed.baseQuery
        
        Path root = Paths.get(workingDir ?: System.getProperty('user.dir'))
        
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption), 5, new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName()?.toString() ?: ''
                    if (IGNORED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    
                    // Add directory to results if it matches query
                    if (results.size() < MAX_RESULTS) {
                        String relativePath = root.relativize(dir).toString()
                        if (relativePath && !relativePath.isEmpty() && matchesQuery(relativePath, baseQuery)) {
                            results << AutocompleteItem.directory(relativePath)
                        }
                    }
                    
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE
                    }
                    
                    String fileName = file.getFileName()?.toString() ?: ''
                    String relativePath = root.relativize(file).toString()
                    
                    // Check if it's a code file and matches query
                    if (isCodeFile(fileName) && matchesQuery(relativePath, baseQuery)) {
                        results << AutocompleteItem.file(relativePath)
                    }
                    
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (Exception e) {
            // Ignore file system errors
        }
        
        // Sort by relevance
        return sortByRelevance(results, baseQuery)
    }

    /**
     * Extract line range from query.
     * Examples: file.txt#L10 -> {baseQuery: "file.txt", startLine: 10}
     *           file.txt#L10-L50 -> {baseQuery: "file.txt", startLine: 10, endLine: 50}
     */
    static Map extractLineRange(String query) {
        if (!query || !query.contains('#')) {
            return [baseQuery: query ?: '']
        }
        
        int hashIndex = query.lastIndexOf('#')
        String baseName = query.substring(0, hashIndex)
        String linePart = query.substring(hashIndex + 1)
        
        // Match patterns like L10, L10-L50, 10, 10-50
        def matcher = linePart =~ /^L?(\d+)(?:-L?(\d+))?$/
        if (matcher.matches()) {
            int startLine = Integer.parseInt(matcher.group(1))
            Integer endLine = matcher.group(2) ? Integer.parseInt(matcher.group(2)) : null
            
            return [
                baseQuery: baseName,
                startLine: startLine,
                endLine: endLine
            ]
        }
        
        return [baseQuery: query]
    }

    private static boolean matchesQuery(String path, String query) {
        if (!query || query.isEmpty()) {
            return true
        }
        
        String lowerPath = path.toLowerCase()
        String lowerQuery = query.toLowerCase()
        
        // Exact prefix match
        if (lowerPath.startsWith(lowerQuery)) {
            return true
        }
        
        // Contains match
        if (lowerPath.contains(lowerQuery)) {
            return true
        }
        
        // Fuzzy match - all query chars appear in order
        return fuzzyMatch(lowerPath, lowerQuery)
    }

    private static boolean fuzzyMatch(String text, String pattern) {
        int patternIdx = 0
        for (int i = 0; i < text.length() && patternIdx < pattern.length(); i++) {
            if (text.charAt(i) == pattern.charAt(patternIdx)) {
                patternIdx++
            }
        }
        return patternIdx == pattern.length()
    }

    private static boolean isCodeFile(String fileName) {
        String lower = fileName.toLowerCase()
        return CODE_EXTENSIONS.any { lower.endsWith(it) }
    }

    private static List<AutocompleteItem> sortByRelevance(List<AutocompleteItem> items, String query) {
        if (!query || query.isEmpty()) {
            return items.sort { a, b -> a.label <=> b.label }
        }
        
        String lowerQuery = query.toLowerCase()
        
        return items.sort { a, b ->
            String aLower = a.label.toLowerCase()
            String bLower = b.label.toLowerCase()
            
            // Prefer exact prefix matches
            boolean aPrefix = aLower.startsWith(lowerQuery)
            boolean bPrefix = bLower.startsWith(lowerQuery)
            if (aPrefix != bPrefix) return aPrefix ? -1 : 1
            
            // Prefer filename matches over path matches
            boolean aFileName = aLower.split('/').last().contains(lowerQuery)
            boolean bFileName = bLower.split('/').last().contains(lowerQuery)
            if (aFileName != bFileName) return aFileName ? -1 : 1
            
            // Shorter paths first
            a.label.length() <=> b.label.length()
        }
    }
}
