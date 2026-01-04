package tools

import core.FileTime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

class ReadFileTool implements Tool {

    private static final int MAX_LINES = 2000
    private static final int MAX_LINE_LENGTH = 2000

    private static final Set<String> BINARY_EXTENSIONS = [
        'zip', 'tar', 'gz', 'bz2', 'xz', '7z', 'rar',
        'exe', 'dll', 'so', 'dylib', 'lib', 'a',
        'class', 'jar', 'war', 'ear',
        'bin', 'dat',
        'o', 'obj',
        'pdb', 'idb',
        'iso', 'img', 'dmg',
        'mp3', 'mp4', 'avi', 'mov', 'wav', 'flac', 'ogg',
        'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'ico',
        'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
        'odt', 'ods', 'odp'
    ] as Set

    private static final Set<String> IMAGE_EXTENSIONS = [
        'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico'
    ] as Set

    private static final Set<String> PDF_EXTENSIONS = ['pdf'] as Set

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
- Binary files are blocked (images, PDFs are returned as base64 attachments)

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
                limit: [type: 'integer', description: "Number of lines to read (default: all, max ${MAX_LINES})".toString()]
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
            return "Error: File not found: ${pathStr}\n\nSuggestion: Use glob to find the file or check the path."
        }

        // Track file read for concurrent modification detection
        try {
            FileTime.instance.read(path.toAbsolutePath().toString())
        } catch (Exception e) {
            // Don't fail read if tracking fails
        }

        String fileName = path.fileName.toString()
        String extension = getExtension(fileName).toLowerCase()

        // Handle image files
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return readImageFile(path)
        }

        // Handle PDF files
        if (PDF_EXTENSIONS.contains(extension)) {
            return readPdfFile(path)
        }

        // Block binary files
        if (BINARY_EXTENSIONS.contains(extension)) {
            return "Binary file detected: ${pathStr}\n\nFile exists but cannot be displayed. Binary files (archives, executables, media, etc.) are blocked for safety."
        }

        // Check if it's a binary file by reading a few bytes
        try {
            def bytes = Files.readAllBytes(path)
            if (isBinaryContent(bytes)) {
                return "Binary file detected: ${pathStr}\n\nFile exists but cannot be displayed. Binary files are blocked for safety."
            }
        } catch (Exception e) {
            return "Error checking file type: ${e.message}"
        }

        // Read text file
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

            def result = new StringBuilder()
            if (startLine > 0 || endLine < totalLines) {
                result.append("# Showing lines ${startLine + 1}-${endLine} of ${totalLines} total\n\n")
            }

            // Add line numbers (cat -n format)
            selectedLines.eachWithIndex { line, idx ->
                def lineNum = startLine + idx + 1
                def truncatedLine = line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) + "..." : line
                result.append(String.format("%5d\t%s\n", lineNum, truncatedLine))
            }

            if (endLine < totalLines) {
                result.append("\n# ... ${totalLines - endLine} more lines (use offset=${endLine} to continue)")
            }

            return result.toString().trim()
        } catch (Exception e) {
            return "Error reading file: ${e.message}"
        }
    }

    private String getExtension(String fileName) {
        def lastDot = fileName.lastIndexOf('.')
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return ''
        }
        return fileName.substring(lastDot + 1)
    }

    private String readImageFile(Path path) {
        try {
            def bytes = Files.readAllBytes(path)
            def base64 = Base64.getEncoder().encodeToString(bytes)
            def mimeType = getMimeType(path)
            
            return """Image file: ${path}

This is an image file (${mimeType}). It has been attached as base64 data.

Data URL: data:${mimeType};base64,${base64.substring(0, 100)}... (truncated)

Image dimensions and metadata can be extracted using bash tool with 'file' or 'identify' commands.
"""
        } catch (Exception e) {
            return "Error reading image file: ${e.message}"
        }
    }

    private String readPdfFile(Path path) {
        try {
            def bytes = Files.readAllBytes(path)
            def base64 = Base64.getEncoder().encodeToString(bytes)
            def fileSize = Files.size(path)
            
            return """PDF file: ${path}

This is a PDF document (${(fileSize / 1024.0).round(2)} KB). It has been attached as base64 data.

Data URL: data:application/pdf;base64,${base64.substring(0, 100)}... (truncated)

For PDF text extraction, use bash tool with 'pdftotext' or 'pdf2txt' commands.
"""
        } catch (Exception e) {
            return "Error reading PDF file: ${e.message}"
        }
    }

    private String getMimeType(Path path) {
        def ext = getExtension(path.fileName.toString()).toLowerCase()
        def mimeTypes = [
            'jpg': 'image/jpeg',
            'jpeg': 'image/jpeg',
            'png': 'image/png',
            'gif': 'image/gif',
            'bmp': 'image/bmp',
            'webp': 'image/webp',
            'svg': 'image/svg+xml',
            'ico': 'image/x-icon'
        ]
        return mimeTypes.get(ext, 'application/octet-stream')
    }

    private boolean isBinaryContent(byte[] bytes) {
        if (bytes.length == 0) return false
        
        def nullCount = 0
        def checkLength = Math.min(8000, bytes.length)
        
        for (int i = 0; i < checkLength; i++) {
            if (bytes[i] == 0) nullCount++
        }
        
        // If more than 0.3% null bytes, consider it binary
        return (nullCount / checkLength) > 0.003
    }

}
