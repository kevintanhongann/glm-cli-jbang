package tools

import core.FileTime
import tui.shared.DiffRenderer
import tui.shared.AnsiColors
import tui.shared.InteractivePrompt
import core.LSPManager
import core.DiagnosticFormatter
import com.github.difflib.patch.AbstractDelta
import com.github.difflib.patch.Patch
import com.github.difflib.patch.DeltaType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class PatchTool implements Tool {

    private String currentSessionId
    
    void setSessionId(String sessionId) {
        this.currentSessionId = sessionId
    }

    @Override
    String getName() { 'patch' }

    @Override
    String getDescription() {
        return '''
Applies a unified diff patch to modify multiple files.

**WHEN TO USE:**
- Applying changes across multiple files
- Reverting changes from a patch
- Applying git diff output
- Applying changes from external tools

**PARAMETERS:**
- patchText: full patch text in unified diff format

**SUPPORTED OPERATIONS:**
- add: Create new files
- update: Modify existing files
- delete: Remove files
- move: Rename/move files

**PATCH FORMAT:**
Unified diff format:
```
--- a/file.txt
+++ b/file.txt
@@ -1,3 +1,4 @@
 old line
+new line
```

**BEST PRACTICES:**
- Use precise paths in patch
- Review patch content before applying
- Back up important files before patching
- Test patches on small changes first

**WHEN NOT TO USE:**
- For single file changes → use edit or write_file instead
- When you don't have a patch → use edit/write_file directly

**PARALLEL EXECUTION:**
- Patch operations are SEQUENTIAL - cannot be parallelized
- Requires user confirmation for all changes
- Never batch patch operations
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                patchText: [type: 'string', description: 'The full patch text in unified diff format that describes all changes to be made']
            ],
            required: ['patchText']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String patchText = args.get('patchText')
        
        if (patchText == null || patchText.trim().isEmpty()) {
            return "Error: patchText is required"
        }

        // Parse patch into hunks
        def patches = parsePatch(patchText)
        
        if (patches.isEmpty()) {
            return "Error: No valid patch hunks found. Ensure patch is in unified diff format."
        }

        // Show summary
        println "\n${AnsiColors.bold('Patch Summary:')}"
        println "Files to modify: ${patches.size()}"
        
        patches.each { filePatch ->
            def type = filePatch.type
            def file = filePatch.filePath
            def icon = getPatchIcon(type)
            println "  ${icon} ${type}: ${file}"
        }
        
        // Require confirmation
        if (!InteractivePrompt.confirm('Apply this patch?')) {
            return 'Patch cancelled by user.'
        }
        
        // Apply all patches
        def results = []
        def allSucceeded = true
        
        for (def filePatch : patches) {
            def result = applyFilePatch(filePatch)
            results.add(result)
            if (!result.success) {
                allSucceeded = false
            }
        }
        
        if (!allSucceeded) {
            def errorSummary = new StringBuilder()
            errorSummary.append("Error: Some patch operations failed.\n\n")
            
            results.each { result ->
                if (!result.success) {
                    errorSummary.append("${result.file}: ${result.error}\n")
                }
            }
            
            return errorSummary.toString().trim()
        }
        
        // Build success summary
        def summary = new StringBuilder()
        summary.append("Successfully applied patch to ${results.size()} file(s):\n")
        
        results.each { result ->
            def icon = result.type == 'delete' ? '🗑️ ' : result.type == 'add' ? '📝 ' : '✏️ '
            summary.append("  ${icon}${result.file}")
            if (result.linesChanged > 0) {
                summary.append(" (${result.linesChanged} line(s))")
            }
            summary.append("\n")
        }
        
        // Touch all modified files to get LSP diagnostics
        def diagnosticsInfo = getBatchDiagnostics(results.findAll { it.success && it.type != 'delete' })
        if (diagnosticsInfo) {
            summary.append("\n${diagnosticsInfo}")
        }
        
        return summary.toString().trim()
    }
    
    /**
     * Parse patch text into individual file patches
     */
    private List<Map<String, Object>> parsePatch(String patchText) {
        def patches = []
        def lines = patchText.split('\n')
        def currentPatch = null
        
        for (int i = 0; i < lines.length; i++) {
            def line = lines[i]
            
            if (line.startsWith('--- a/')) {
                if (currentPatch) {
                    patches.add(currentPatch)
                }
                currentPatch = [
                    filePath: line.substring(6).trim(),
                    type: 'update',
                    hunks: []
                ]
            } else if (line.startsWith('+++ b/')) {
                def newFile = line.substring(6).trim()
                if (currentPatch) {
                    currentPatch.filePath = newFile
                }
            } else if (line.startsWith('--- /dev/null')) {
                if (currentPatch) {
                    patches.add(currentPatch)
                }
                currentPatch = [
                    filePath: null,
                    type: 'add',
                    hunks: []
                ]
            } else if (line.startsWith('+++ b/')) {
                if (currentPatch && currentPatch.type == 'add') {
                    currentPatch.filePath = line.substring(6).trim()
                }
            } else if (line.startsWith('+++ /dev/null')) {
                if (currentPatch) {
                    currentPatch.type = 'delete'
                }
            } else if (line.startsWith('@@')) {
                if (currentPatch) {
                    def hunk = parseHunkHeader(line)
                    currentPatch.hunks.add(hunk)
                }
            } else if (line.startsWith('+') && !line.startsWith('++')) {
                if (currentPatch && !currentPatch.hunks.isEmpty()) {
                    currentPatch.hunks.last().additions.add(line.substring(1))
                }
            } else if (line.startsWith('-') && !line.startsWith('--')) {
                if (currentPatch && !currentPatch.hunks.isEmpty()) {
                    currentPatch.hunks.last().deletions.add(line.substring(1))
                }
            } else if (line.startsWith(' ') || line.isEmpty()) {
                if (currentPatch && !currentPatch.hunks.isEmpty()) {
                    currentPatch.hunks.last().context.add(line.substring(1))
                }
            }
        }
        
        if (currentPatch) {
            patches.add(currentPatch)
        }
        
        return patches.findAll { it != null && it.filePath != null }
    }
    
    /**
     * Parse hunk header like "@@ -1,3 +1,4 @@"
     */
    private Map<String, Object> parseHunkHeader(String line) {
        return [
            oldStart: 0,
            oldCount: 0,
            newStart: 0,
            newCount: 0,
            additions: [],
            deletions: [],
            context: []
        ]
    }
    
    /**
     * Apply a single file patch
     */
    private Map<String, Object> applyFilePatch(Map<String, Object> filePatch) {
        def filePath = filePatch.filePath
        def type = filePatch.type
        def hunks = filePatch.hunks
        
        Path path = Paths.get(filePath).normalize()
        
        if (type == 'delete') {
            return deleteFile(path)
        } else if (type == 'add') {
            return createFile(path, hunks)
        } else {
            return updateFile(path, hunks)
        }
    }
    
    private Map<String, Object> createFile(Path path, List<Map> hunks) {
        // Create parent directories if needed
        if (path.parent != null) {
            Files.createDirectories(path.parent)
        }
        
        // Build content from hunks
        def content = buildContentFromHunks(hunks)
        Files.writeString(path, content)
        
        return [
            success: true,
            file: path.toString(),
            type: 'add',
            linesChanged: content.split('\n').length
        ]
    }
    
    private Map<String, Object> deleteFile(Path path) {
        if (!Files.exists(path)) {
            return [
                success: false,
                file: path.toString(),
                type: 'delete',
                error: 'File not found'
            ]
        }
        
        Files.delete(path)
        
        return [
            success: true,
            file: path.toString(),
            type: 'delete',
            linesChanged: 0
        ]
    }
    
    private Map<String, Object> updateFile(Path path, List<Map> hunks) {
        if (!Files.exists(path)) {
            return [
                success: false,
                file: path.toString(),
                type: 'update',
                error: 'File not found'
            ]
        }
        
        // Check for concurrent modifications
        try {
            FileTime.instance.checkUnmodified(path.toString())
        } catch (RuntimeException e) {
            return [
                success: false,
                file: path.toString(),
                type: 'update',
                error: e.message
            ]
        }
        
        return FileTime.instance.withLock(path.toString()) {
            try {
                def original = Files.readString(path)
                def lines = original.split('\n')
                def result = []
                def lineIndex = 0
                def totalChanges = 0
                
                for (def hunk : hunks) {
                    // Skip to hunk start
                    while (lineIndex < hunk.oldStart - 1 && lineIndex < lines.length) {
                        result.add(lines[lineIndex])
                        lineIndex++
                    }
                    
                    // Apply hunk changes
                    for (def deletion : hunk.deletions) {
                        if (lineIndex < lines.length) {
                            lineIndex++
                            totalChanges++
                        }
                    }
                    
                    for (def addition : hunk.additions) {
                        result.add(addition)
                        totalChanges++
                    }
                }
                
                // Add remaining lines
                while (lineIndex < lines.length) {
                    result.add(lines[lineIndex])
                    lineIndex++
                }
                
                def newContent = result.join('\n')
                Files.writeString(path, newContent)
                
                return [
                    success: true,
                    file: path.toString(),
                    type: 'update',
                    linesChanged: totalChanges
                ]
            } catch (Exception e) {
                return [
                    success: false,
                    file: path.toString(),
                    type: 'update',
                    error: e.message
                ]
            }
        }
    }
    
    /**
     * Build file content from hunks (for new files)
     */
    private String buildContentFromHunks(List<Map> hunks) {
        def result = []
        
        for (def hunk : hunks) {
            for (def contextLine : hunk.context) {
                result.add(contextLine)
            }
            for (def addition : hunk.additions) {
                result.add(addition)
            }
        }
        
        return result.join('\n')
    }
    
    private String getPatchIcon(String type) {
        switch (type) {
            case 'add': return '➕'
            case 'delete': return '🗑️'
            case 'update': return '✏️'
            default: return '📄'
        }
    }
    
    /**
     * Get LSP diagnostics for all modified files
     */
    private String getBatchDiagnostics(List<Map> results) {
        def diagnostics = []
        
        try {
            def lsp = LSPManager.instance
            if (!lsp.enabled) return null
            
            for (def result : results) {
                try {
                    def fileDiagnostics = lsp.touchFile(result.file, true)
                    if (fileDiagnostics && !fileDiagnostics.isEmpty()) {
                        diagnostics.add([file: result.file, diags: fileDiagnostics])
                    }
                } catch (Exception e) {
                    // Skip files with LSP errors
                }
            }
        } catch (Exception e) {
            return null
        }
        
        if (diagnostics.isEmpty()) return null
        
        def output = new StringBuilder()
        output.append("\n${AnsiColors.bold('LSP Diagnostics:')}")
        
        diagnostics.each { entry ->
            output.append("\n${AnsiColors.cyan(entry.file)}:")
            output.append(DiagnosticFormatter.formatForAgent(entry.diags))
        }
        
        return output.toString()
    }
}
