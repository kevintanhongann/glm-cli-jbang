package rag

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.Metadata
import java.nio.file.Path
import java.nio.file.Files

class CodebaseLoader {
    
    private static final Set<String> SUPPORTED_EXTENSIONS = [
        '.groovy', '.java', '.kt', '.scala',     // JVM languages
        '.py', '.rb', '.js', '.ts', '.jsx', '.tsx',  // Scripting
        '.go', '.rs', '.c', '.cpp', '.h',        // Systems
        '.md', '.txt', '.json', '.yaml', '.toml', // Config/docs
        '.sh', '.bash', '.zsh'                    // Shell
    ] as Set
    
    private static final Set<String> IGNORE_PATTERNS = [
        '.git', 'node_modules', 'target', 'build', '.gradle',
        '__pycache__', '.idea', '.vscode', 'dist', 'out'
    ] as Set
    
    List<Document> loadCodebase(String path) {
        Path basePath = Path.of(path).toAbsolutePath()
        List<Document> documents = []
        
        Files.walk(basePath)
            .filter { shouldInclude(it, basePath) }
            .forEach { filePath ->
                try {
                    String content = Files.readString(filePath)
                    String relativePath = basePath.relativize(filePath).toString()
                    
                    Metadata metadata = new Metadata()
                    metadata.put("file_path", relativePath)
                    metadata.put("file_name", filePath.fileName.toString())
                    metadata.put("file_type", getExtension(filePath))
                    metadata.put("absolute_path", filePath.toString())
                    
                    Document doc = Document.from(content, metadata)
                    documents.add(doc)
                } catch (Exception e) {
                    System.err.println("Warning: Could not load ${filePath}: ${e.message}")
                }
            }
        
        return documents
    }
    
    private boolean shouldInclude(Path path, Path basePath) {
        if (Files.isDirectory(path)) return false
        
        // Check ignore patterns
        String pathStr = path.toString()
        for (String pattern : IGNORE_PATTERNS) {
            if (pathStr.contains("/${pattern}/") || pathStr.endsWith("/${pattern}")) {
                return false
            }
        }
        
        // Check extension
        String ext = getExtension(path)
        return SUPPORTED_EXTENSIONS.contains(ext)
    }
    
    private String getExtension(Path path) {
        String name = path.fileName.toString()
        int dotIndex = name.lastIndexOf('.')
        return dotIndex > 0 ? name.substring(dotIndex) : ''
    }
}
