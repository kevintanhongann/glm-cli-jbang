package tools

import core.FileTime
import tui.DiffRenderer
import tui.AnsiColors
import tui.InteractivePrompt
import core.LSPManager
import core.DiagnosticFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EditTool implements Tool {

    private String currentSessionId
    
    void setSessionId(String sessionId) {
        this.currentSessionId = sessionId
    }

    @Override
    String getName() { 'edit' }

    @Override
    String getDescription() {
        return '''
Performs precise string replacements in files with multiple matching strategies.

**WHEN TO USE:**
- Making targeted edits to specific sections of code
- Updating function implementations
- Modifying configuration values
- Replacing specific text patterns

**PARAMETERS:**
- filePath: absolute path to the file to modify
- oldString: the exact text to replace
- newString: the text to replace it with (must be different from oldString)
- replaceAll: optional - replace all occurrences of oldString (default: false)

**MATCHING STRATEGIES:**
The tool tries multiple strategies in order:
1. Simple - exact match
2. LineTrimmed - ignores line indentation
3. WhitespaceNormalized - normalizes spaces and tabs
4. IndentationFlexible - removes consistent indentation

**BEST PRACTICES:**
- Use sufficient context in oldString for unique matches
- Avoid matching only common patterns
- Use replaceAll for global replacements with caution
- Always read the file first to understand context
- Use withLock to prevent concurrent modifications

**WHEN NOT TO USE:**
- For creating new files → use write_file instead
- For replacing entire file content → use write_file instead
- When context is insufficient → use write_file after reading

**PARALLEL EXECUTION:**
- File edits are SEQUENTIAL - cannot be parallelized
- Each edit requires user confirmation
- Never batch edit operations
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                filePath: [type: 'string', description: 'The absolute path to the file to modify'],
                oldString: [type: 'string', description: 'The text to replace'],
                newString: [type: 'string', description: 'The text to replace it with (must be different from oldString)'],
                replaceAll: [type: 'boolean', description: 'Replace all occurrences of oldString (default: false)']
            ],
            required: ['filePath', 'oldString', 'newString']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String filePathStr = args.get('filePath')
        String oldString = args.get('oldString')
        String newString = args.get('newString')
        Boolean replaceAll = args.get('replaceAll') as Boolean ?: false

        if (oldString == newString) {
            return "Error: oldString and newString must be different"
        }

        Path path = Paths.get(filePathStr).normalize()
        if (!Files.exists(path)) {
            return "Error: File not found: ${filePathStr}"
        }

        // Check for concurrent modifications
        try {
            FileTime.instance.checkUnmodified(filePathStr)
        } catch (RuntimeException e) {
            return "Error: ${e.message}"
        }

        return FileTime.instance.withLock(filePathStr) {
            try {
                String originalContent = Files.readString(path)
                
                // Normalize line endings
                def normalizedOriginal = normalizeLineEndings(originalContent)
                def normalizedOld = normalizeLineEndings(oldString)
                def normalizedNew = normalizeLineEndings(newString)
                
                // Apply matching strategies
                def result = applyEditStrategies(
                    normalizedOriginal, 
                    normalizedOld, 
                    normalizedNew, 
                    replaceAll,
                    filePathStr
                )
                
                if (result.error) {
                    return "Error: ${result.error}"
                }
                
                // Show diff preview
                def diff = DiffRenderer.renderUnifiedDiff(originalContent, result.newContent, filePathStr)
                println diff
                
                // Require confirmation
                if (!InteractivePrompt.confirm('Apply these changes?')) {
                    return 'Edit cancelled by user.'
                }
                
                // Write the new content
                Files.writeString(path, result.newContent)
                
                // Get LSP diagnostics if available
                def diagnosticsOutput = ''
                try {
                    def lsp = LSPManager.instance
                    if (lsp.enabled) {
                        def diagnostics = lsp.touchFile(filePathStr, true)
                        if (diagnostics && !diagnostics.isEmpty()) {
                            diagnosticsOutput = '\n\n' + DiagnosticFormatter.formatForAgent(diagnostics)
                        }
                    }
                } catch (Exception e) {
                    // LSP errors should not fail the edit
                }
                
                def linesChanged = result.matches
                def status = linesChanged == 1 ? '1 line changed' : "${linesChanged} lines changed"
                return "Successfully edited ${filePathStr}: ${status}${diagnosticsOutput}"
                
            } catch (Exception e) {
                return "Error editing file: ${e.message}"
            }
        }
    }
    
    /**
     * Apply multiple matching strategies to find and replace text
     */
    private Map applyEditStrategies(String content, String oldString, String newString, boolean replaceAll, String filePath) {
        def strategies = [
            [name: 'Simple', matcher: { s, o -> s == o },
             replacer: { s, o, n -> replaceAll ? s.replace(o, n) : s.replaceFirst(o, n) }],
            
            [name: 'LineTrimmed', matcher: { s, o -> s.trim() == o.trim() },
             replacer: { s, o, n -> replaceAll ? replaceAllLineTrimmed(s, o, n) : replaceFirstLineTrimmed(s, o, n) }],
            
            [name: 'WhitespaceNormalized', matcher: { s, o -> normalizeWhitespace(s) == normalizeWhitespace(o) },
             replacer: { s, o, n -> replaceAll ? replaceAllWhitespaceNormalized(s, o, n) : replaceFirstWhitespaceNormalized(s, o, n) }],
            
            [name: 'IndentationFlexible', matcher: { s, o -> removeCommonIndentation(s) == removeCommonIndentation(o) },
             replacer: { s, o, n -> replaceAll ? replaceAllIndentationFlexible(s, o, n) : replaceFirstIndentationFlexible(s, o, n) }]
        ]
        
        for (def strategy in strategies) {
            if (content.contains(oldString)) {
                // Simple exact match - use original
                def newContent = replaceAll ? content.replace(oldString, newString) : content.replaceFirst(Pattern.quote(oldString), Matcher.quoteReplacement(newString))
                def matches = countMatches(content, oldString)
                return [error: null, newContent: newContent, matches: replaceAll ? matches : 1, strategy: 'Simple']
            }
            
            // Try line-trimmed match
            def lines = content.split('\n')
            def oldLines = oldString.split('\n')
            if (lines.length >= oldLines.length) {
                for (int i = 0; i <= lines.length - oldLines.length; i++) {
                    def match = true
                    for (int j = 0; j < oldLines.length; j++) {
                        if (lines[i + j].trim() != oldLines[j].trim()) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        def newLines = lines.clone()
                        for (int j = 0; j < oldLines.length; j++) {
                            newLines[i + j] = newLines[i + j].replaceFirst(Pattern.quote(oldLines[j].trim()), Matcher.quoteReplacement(newString.split('\n')[j]))
                        }
                        return [error: null, newContent: newLines.join('\n'), matches: 1, strategy: 'LineTrimmed']
                    }
                }
            }
        }
        
        return [error: "oldString not found in file. Tried Simple and LineTrimmed strategies. Ensure the text exists exactly as provided.", newContent: null, matches: 0, strategy: null]
    }
    
    private String normalizeLineEndings(String text) {
        return text.replaceAll('\r\n', '\n')
    }
    
    private String normalizeWhitespace(String text) {
        return text.replaceAll('\\s+', ' ').trim()
    }
    
    private String removeCommonIndentation(String text) {
        def lines = text.split('\n')
        if (lines.length == 0) return text
        
        // Find minimum indentation
        def minIndent = Integer.MAX_VALUE
        for (def line in lines) {
            if (line.trim().isEmpty()) continue
            def indent = line.takeWhile { it == ' ' || it == '\t' }.length()
            minIndent = Math.min(minIndent, indent)
        }
        
        if (minIndent == Integer.MAX_VALUE) return text
        
        // Remove minimum indentation from all lines
        return lines.collect { line ->
            if (line.trim().isEmpty()) return line
            line.substring(minIndent)
        }.join('\n')
    }
    
    private int countMatches(String text, String pattern) {
        def count = 0
        def index = 0
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++
            index += pattern.length()
        }
        return count
    }
    
    private String replaceFirstLineTrimmed(String content, String oldString, String newString) {
        def oldLines = oldString.split('\n')
        def newLines = newString.split('\n')
        def lines = content.split('\n')
        
        for (int i = 0; i <= lines.length - oldLines.length; i++) {
            def match = true
            for (int j = 0; j < oldLines.length; j++) {
                if (lines[i + j].trim() != oldLines[j].trim()) {
                    match = false
                    break
                }
            }
            if (match) {
                def result = lines.clone()
                for (int j = 0; j < oldLines.length; j++) {
                    def trimmed = oldLines[j].trim()
                    def line = result[i + j]
                    def indent = line.takeWhile { it == ' ' || it == '\t' }
                    result[i + j] = indent + newLines[j]
                }
                return result.join('\n')
            }
        }
        return content
    }
    
    private String replaceAllLineTrimmed(String content, String oldString, String newString) {
        def result = content
        while (true) {
            def newResult = replaceFirstLineTrimmed(result, oldString, newString)
            if (newResult == result) break
            result = newResult
        }
        return result
    }
    
    private String replaceFirstWhitespaceNormalized(String content, String oldString, String newString) {
        def normalizedOld = normalizeWhitespace(oldString)
        def words = content.split('\\s+')
        def oldWords = normalizedOld.split(' ')
        
        if (words.length < oldWords.length) return content
        
        for (int i = 0; i <= words.length - oldWords.length; i++) {
            def match = true
            for (int j = 0; j < oldWords.length; j++) {
                if (words[i + j] != oldWords[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                def result = words.clone()
                for (int j = 0; j < oldWords.length; j++) {
                    result[i + j] = newString.split('\\s+')[j] ?: newString
                }
                return result.join(' ')
            }
        }
        return content
    }
    
    private String replaceAllWhitespaceNormalized(String content, String oldString, String newString) {
        def result = content
        while (true) {
            def newResult = replaceFirstWhitespaceNormalized(result, oldString, newString)
            if (newResult == result) break
            result = newResult
        }
        return result
    }
    
    private String replaceFirstIndentationFlexible(String content, String oldString, String newString) {
        def oldLines = oldString.split('\n')
        def newLines = newString.split('\n')
        def lines = content.split('\n')
        
        def oldNoIndent = removeCommonIndentation(oldString).split('\n')
        
        for (int i = 0; i <= lines.length - oldLines.length; i++) {
            def match = true
            for (int j = 0; j < oldLines.length; j++) {
                if (removeCommonIndentation(lines[i + j]) != oldNoIndent[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                def result = lines.clone()
                for (int j = 0; j < oldLines.length; j++) {
                    def line = result[i + j]
                    def oldIndent = line.takeWhile { it == ' ' || it == '\t' }
                    result[i + j] = oldIndent + newLines[j]
                }
                return result.join('\n')
            }
        }
        return content
    }
    
    private String replaceAllIndentationFlexible(String content, String oldString, String newString) {
        def result = content
        while (true) {
            def newResult = replaceFirstIndentationFlexible(result, oldString, newString)
            if (newResult == result) break
            result = newResult
        }
        return result
    }
}
