package tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ReadFileTool implements Tool {

    private static final int MAX_LINES = 2000

    @Override
    String getName() { 'read_file' }

    @Override
    String getDescription() {
        return """
Read the complete contents of a file from the local filesystem.

**WHEN TO USE:**
- When you know the exact file path to read
- After discovering files with glob or grep
- Reading configuration files, documentation, or code
- Examining specific implementation details

**PARAMETERS:**
- path: absolute file path to read
- offset: optional line number to start reading (0-based, default: 0)
- limit: optional number of lines to read (default: all, max 2000)

**BEST PRACTICES:**
- Use absolute paths only (no relative paths)
- Read multiple files simultaneously when possible
- Default reads up to 2000 lines, specify offset/limit for large files
- Files over 2000 lines should be read in chunks
- Use offset/limit for specific sections

**WHEN NOT TO USE:**
- When searching for files → use glob instead
- When searching file contents → use grep instead
- When path is unknown → use glob/grep first to find it

**PARALLEL EXECUTION:**
- ALWAYS read multiple files simultaneously when they're independent
- Batch reads of related files together
- Can be combined with glob or grep in parallel for faster exploration
""".stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                path: [type: 'string', description: 'Absolute path to the file to read.'],
                offset: [type: 'integer', description: 'Line number to start reading from (0-based, default: 0)'],
                limit: [type: 'integer', description: "Number of lines to read (default: all, max ${MAX_LINES})"]
            ],
            required: ['path']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String pathStr = args.get('path')
        Integer offset = args.get('offset') as Integer ?: 0
        Integer limit = args.get('limit') as Integer ?: MAX_LINES

        Path path = Paths.get(pathStr).normalize()
        if (!Files.exists(path)) {
            return "Error: File not found: ${pathStr}"
        }

        try {
            List<String> allLines = Files.readAllLines(path)
            int totalLines = allLines.size()

            // Handle offset and limit
            int startLine = Math.max(0, offset)
            int endLine = Math.min(totalLines, startLine + Math.min(limit, MAX_LINES))

            if (startLine >= totalLines) {
                return "Error: Offset ${offset} exceeds file length (${totalLines} lines)"
            }

            List<String> selectedLines = allLines.subList(startLine, endLine)
            String content = selectedLines.join("\n")

            def result = new StringBuilder()
            if (startLine > 0 || endLine < totalLines) {
                result.append("# Showing lines ${startLine + 1}-${endLine} of ${totalLines} total\n\n")
            }
            result.append(content)

            if (endLine < totalLines) {
                result.append("\n\n# ... ${totalLines - endLine} more lines (use offset=${endLine} to continue)")
            }

            return result.toString()
        } catch (Exception e) {
            return "Error reading file: ${e.message}"
        }
    }

}
