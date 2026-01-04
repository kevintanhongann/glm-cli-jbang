package tools

import core.FileTime
import tui.shared.DiffRenderer
import tui.shared.AnsiColors
import tui.shared.InteractivePrompt
import core.LSPManager
import core.DiagnosticFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MultiEditTool implements Tool {

    private String currentSessionId
    
    void setSessionId(String sessionId) {
        this.currentSessionId = sessionId
    }

    @Override
    String getName() { 'multiedit' }

    @Override
    String getDescription() {
        return '''
Performs multiple edits to a single file atomically.

**WHEN TO USE:**
- Making multiple related changes to one file
- Applying a series of related refactors
- Updating multiple sections of code simultaneously
- When edits should be applied together or not at all

**PARAMETERS:**
- filePath: absolute path to the file to modify
- edits: array of edit operations, each with:
  - oldString: text to replace
  - newString: text to replace it with (must be different from oldString)
  - replaceAll: optional - replace all occurrences (default: false)

**BEHAVIOR:**
- All edits must succeed or none are applied (atomic)
- Edits are applied sequentially in the order provided
- Each edit uses the same matching strategies as the edit tool:
  1. Simple - exact match
  2. LineTrimmed - ignores indentation
  3. WhitespaceNormalized - normalizes spaces/tabs
  4. IndentationFlexible - removes consistent indentation

**BEST PRACTICES:**
- Ensure edits don't conflict with each other
- Use when edits are related and should succeed/fail together
- Order edits from least specific to most specific
- Read file first to understand context

**WHEN NOT TO USE:**
- For single edits → use edit instead
- For different files → use multiple edit calls or write_file
- When edits are independent → use separate edit calls

**PARALLEL EXECUTION:**
- Multi-edits are SEQUENTIAL - cannot be parallelized
- Requires user confirmation for all edits together
- Never batch multi-edit operations
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                filePath: [type: 'string', description: 'The absolute path to the file to modify'],
                edits: [
                    type: 'array',
                    items: [
                        type: 'object',
                        properties: [
                            oldString: [type: 'string', description: 'The text to replace'],
                            newString: [type: 'string', description: 'The text to replace it with (must be different from oldString)'],
                            replaceAll: [type: 'boolean', description: 'Replace all occurrences of oldString (default: false)']
                        ],
                        required: ['oldString', 'newString']
                    ],
                    description: 'Array of edit operations to perform sequentially on the file'
                ]
            ],
            required: ['filePath', 'edits']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String filePathStr = args.get('filePath')
        List<Map<String, Object>> edits = args.get('edits') as List<Map<String, Object>>

        if (edits == null || edits.isEmpty()) {
            return "Error: edits array must contain at least one edit"
        }

        // Validate edits
        for (int i = 0; i < edits.size(); i++) {
            def edit = edits[i]
            def oldString = edit.get('oldString')
            def newString = edit.get('newString')
            
            if (oldString == newString) {
                return "Error: Edit ${i + 1} - oldString and newString must be different"
            }
            
            if (oldString == null || oldString.isEmpty()) {
                return "Error: Edit ${i + 1} - oldString is required"
            }
            
            if (newString == null) {
                return "Error: Edit ${i + 1} - newString is required"
            }
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
                String currentContent = originalContent
                def results = []
                def allSucceeded = true
                
                // Apply all edits
                for (int i = 0; i < edits.size(); i++) {
                    def edit = edits[i]
                    def oldString = edit.get('oldString') as String
                    def newString = edit.get('newString') as String
                    def replaceAll = edit.get('replaceAll') as Boolean ?: false
                    
                    // Normalize line endings
                    def normalizedOld = normalizeLineEndings(oldString)
                    def normalizedNew = normalizeLineEndings(newString)
                    
                    // Try to apply edit
                    def editResult = tryApplyEdit(currentContent, normalizedOld, normalizedNew, replaceAll)
                    
                    if (editResult.error) {
                        allSucceeded = false
                        results.add([
                            index: i,
                            success: false,
                            error: editResult.error
                        ])
                    } else {
                        currentContent = editResult.newContent
                        results.add([
                            index: i,
                            success: true,
                            linesChanged: editResult.matches,
                            strategy: editResult.strategy
                        ])
                    }
                }
                
                if (!allSucceeded) {
                    def errorSummary = new StringBuilder()
                    errorSummary.append("Error: Some edits failed. No changes were applied.\n\n")
                    
                    results.each { result ->
                        if (!result.success) {
                            errorSummary.append("Edit ${result.index + 1}: ${result.error}\n")
                        }
                    }
                    
                    return errorSummary.toString().trim()
                }
                
                // Show diff preview
                def diff = DiffRenderer.renderUnifiedDiff(originalContent, currentContent, filePathStr)
                println diff
                
                // Show summary
                println "\n${AnsiColors.bold('Edit Summary:')}"
                results.each { result ->
                    println "  Edit ${result.index + 1}: ${result.linesChanged} line(s) changed (strategy: ${result.strategy})"
                }
                
                // Require confirmation
                if (!InteractivePrompt.confirm('Apply all these changes?')) {
                    return 'Multi-edit cancelled by user.'
                }
                
                // Write new content
                Files.writeString(path, currentContent)
                
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
                
                def totalLines = results.sum { it.linesChanged }
                def summary = new StringBuilder()
                summary.append("Successfully applied ${results.size()} edits to ${filePathStr} (${totalLines} total lines changed)")
                summary.append(diagnosticsOutput)
                
                return summary.toString()
                
            } catch (Exception e) {
                return "Error editing file: ${e.message}"
            }
        }
    }
    
    /**
     * Try to apply a single edit using multiple matching strategies
     */
    private Map tryApplyEdit(String content, String oldString, String newString, boolean replaceAll) {
        // Simple exact match
        if (content.contains(oldString)) {
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
                    def newLinesContent = newString.split('\n')
                    for (int j = 0; j < oldLines.length; j++) {
                        def indent = lines[i + j].takeWhile { it == ' ' || it == '\t' }
                        newLines[i + j] = indent + newLinesContent[j]
                    }
                    return [error: null, newContent: newLines.join('\n'), matches: 1, strategy: 'LineTrimmed']
                }
            }
        }
        
        return [error: "oldString not found. Tried Simple and LineTrimmed strategies.", newContent: null, matches: 0, strategy: null]
    }
    
    private String normalizeLineEndings(String text) {
        return text.replaceAll('\r\n', '\n')
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
}
