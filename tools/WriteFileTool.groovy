package tools

import core.LSPManager
import core.DiagnosticFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class WriteFileTool implements Tool {
    @Override
    String getName() { "write_file" }

    @Override
    String getDescription() { "Write content to a file at the specified path. Overwrites existing content." }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                path: [type: "string", description: "The path to the file to write."],
                content: [type: "string", description: "The content to write to the file."]
            ],
            required: ["path", "content"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get("path")
        String content = args.get("content")
        Path path = Paths.get(pathStr).normalize()
        String absolutePath = path.toAbsolutePath().toString()
        
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent())
            }
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            
            def result = new StringBuilder()
            result.append("Successfully wrote ${content.length()} bytes to ${pathStr}")
            
            // Get LSP diagnostics if available
            try {
                def lsp = LSPManager.instance
                if (lsp.enabled) {
                    def diagnostics = lsp.touchFile(absolutePath, true)
                    if (diagnostics && !diagnostics.isEmpty()) {
                        result.append(DiagnosticFormatter.formatForAgent(diagnostics))
                    }
                }
            } catch (Exception lspError) {
                // LSP errors should not fail the write operation
                // Silently continue without diagnostics
            }
            
            return result.toString()
        } catch (Exception e) {
            return "Error writing file: ${e.message}"
        }
    }
}
