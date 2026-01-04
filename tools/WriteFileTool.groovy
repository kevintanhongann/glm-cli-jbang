package tools

import core.LSPManager
import core.DiagnosticFormatter
import core.SessionStatsManager
import core.FileTime
import tui.shared.DiffRenderer
import tui.shared.AnsiColors
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class WriteFileTool implements Tool {
    private String currentSessionId
    
    void setSessionId(String sessionId) {
        this.currentSessionId = sessionId
    }
    
    @Override
    String getName() { 'write_file' }

    @Override
    String getDescription() {
        return '''
Write or create a file with automatic diff preview and user confirmation.

**WHEN TO USE:**
- Creating new files
- Modifying existing files
- Writing code, documentation, or configuration

**PARAMETERS:**
- path: absolute file path to write
- content: full file content to write

**BEST PRACTICES:**
- Always read existing file first to understand context
- Use diff preview before writing (automatic)
- Write complete file content, not partial updates
- User confirmation required for file modifications
- File modifications are tracked to prevent lost updates
- Combine related edits into a single write

**WHEN NOT TO USE:**
- For small edits to specific sections → use edit tool instead
- When you need to see the existing content first → read_file first

**PARALLEL EXECUTION:**
- File writes are SEQUENTIAL - cannot be parallelized
- Each write requires user confirmation
- Plan all writes before executing
- Never batch write operations
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                path: [type: 'string', description: 'The path to the file to write.'],
                content: [type: 'string', description: 'The content to write to the file.']
            ],
            required: ['path', 'content']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get('path')
        String content = args.get('content')
        Path path = Paths.get(pathStr).normalize()
        String absolutePath = path.toAbsolutePath().toString()
        
        // Check for concurrent modifications if file was read before
        try {
            FileTime.instance.checkUnmodified(absolutePath)
        } catch (RuntimeException e) {
            return "Error: ${e.message}"
        }
        
        return FileTime.instance.withLock(absolutePath) {
            // Calculate diff stats if file exists
            int additions = 0
            int deletions = 0
            
            if (Files.exists(path)) {
                def oldContent = path.toFile().text
                def diffResult = calculateDiffStats(oldContent, content)
                additions = diffResult.additions
                deletions = diffResult.deletions
            } else {
                // New file, all lines are additions
                additions = content.split('\n').size()
            }
            
            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent())
                }
                Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

                def result = new StringBuilder()
                result.append("Successfully wrote ${content.length()} bytes to ${pathStr}")
                
                if (additions > 0 || deletions > 0) {
                    result.append(" (${AnsiColors.green("+${additions}")}${deletions > 0 ? " ${AnsiColors.red("-${deletions}")}" : ""})")
                }
                
                // Track modified file in session stats
                if (currentSessionId && (additions > 0 || deletions > 0)) {
                    try {
                        SessionStatsManager.instance.recordModifiedFile(
                            currentSessionId,
                            pathStr,
                            additions,
                            deletions
                        )
                    } catch (Exception e) {
                        // Don't fail the write if stats update fails
                    }
                }
                
                // Get LSP diagnostics if available
                try {
                    def lsp = LSPManager.instance
                    if (lsp.enabled) {
                        def diagnostics = lsp.touchFile(absolutePath, true)
                        if (diagnostics && !diagnostics.isEmpty()) {
                            result.append("\n\n").append(DiagnosticFormatter.formatForAgent(diagnostics))
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
    
    /**
     * Calculate diff statistics (additions and deletions) using DiffRenderer
     */
    private Map<String, Integer> calculateDiffStats(String original, String modified) {
        def diffLines = DiffRenderer.computeDiff(original.split('\n').toList(), modified.split('\n').toList())
        def additions = 0
        def deletions = 0
        
        diffLines.each { line ->
            if (line.type == DiffRenderer.DiffType.ADDITION) {
                additions++
            } else if (line.type == DiffRenderer.DiffType.DELETION) {
                deletions++
            }
        }
        
        return [additions: additions, deletions: deletions]
    }

}
