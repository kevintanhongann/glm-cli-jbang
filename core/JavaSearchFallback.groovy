package core

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class JavaSearchFallback {
    
    static final int MAX_RESULTS = 100
    static final Set<String> EXCLUDED_DIRS = [
        ".git", "node_modules", ".svn", ".hg",
        "build", "dist", "target", "__pycache__",
        ".gradle", "vendor", ".idea", ".vscode"
    ] as Set
    
    static final Set<String> BINARY_EXTENSIONS = [
        ".exe", ".dll", ".so", ".dylib", ".bin",
        ".zip", ".tar", ".gz", ".jar", ".war",
        ".png", ".jpg", ".jpeg", ".gif", ".ico",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx",
        ".mp3", ".mp4", ".avi", ".mov"
    ] as Set
    
    static List<GrepMatch> search(String regex, String searchPath, String includeGlob = null) {
        def matches = []
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        Path root = Paths.get(searchPath).toAbsolutePath()
        
        PathMatcher globMatcher = null
        if (includeGlob) {
            try {
                globMatcher = FileSystems.getDefault()
                    .getPathMatcher("glob:${includeGlob}")
            } catch (Exception e) {
            }
        }
        
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.fileName.toString()
                if (EXCLUDED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
            
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matches.size() >= MAX_RESULTS) {
                    return FileVisitResult.TERMINATE
                }
                
                if (globMatcher && !globMatcher.matches(file.fileName)) {
                    return FileVisitResult.CONTINUE
                }
                
                if (isBinaryFile(file)) {
                    return FileVisitResult.CONTINUE
                }
                
                try {
                    def lines = Files.readAllLines(file)
                    lines.eachWithIndex { line, idx ->
                        if (matches.size() >= MAX_RESULTS) return
                        if (pattern.matcher(line).find()) {
                            matches << new GrepMatch(
                                filePath: root.relativize(file).toString(),
                                lineNumber: idx + 1,
                                lineText: line
                            )
                        }
                    }
                } catch (Exception e) {
                }
                
                return FileVisitResult.CONTINUE
            }
        })
        
        return matches
    }
    
    static List<String> files(String globPattern, String searchPath) {
        def results = []
        Path root = Paths.get(searchPath).toAbsolutePath()
        
        PathMatcher matcher = null
        try {
            matcher = FileSystems.getDefault()
                .getPathMatcher("glob:${globPattern}")
        } catch (Exception e) {
            return results
        }
        
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.fileName.toString()
                if (EXCLUDED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
            
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (results.size() >= MAX_RESULTS) {
                    return FileVisitResult.TERMINATE
                }
                
                Path relativePath = root.relativize(file)
                if (matcher.matches(relativePath)) {
                    results << relativePath.toString()
                }
                
                return FileVisitResult.CONTINUE
            }
        })
        
        return results
    }
    
    private static boolean isBinaryFile(Path file) {
        def fileName = file.fileName.toString().toLowerCase()
        return BINARY_EXTENSIONS.any { fileName.endsWith(it) }
    }
}
