package tools

import core.LSPManager
import core.DiagnosticFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class WriteFileTool implements Tool {

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
- Combine related edits into a single write

**WHEN NOT TO USE:**
- For small edits to specific sections → consider read + rewrite
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
