package core

import models.Diagnostic

/**
 * Formatter for presenting LSP diagnostics in agent-readable format.
 */
class DiagnosticFormatter {
    
    private static final Map<Integer, String> SEVERITY_NAMES = [
        1: 'ERROR',
        2: 'WARNING',
        3: 'INFO',
        4: 'HINT'
    ]
    
    /**
     * Format a single diagnostic.
     * @param diag The diagnostic
     * @return Formatted string
     */
    static String format(Diagnostic diag) {
        def severity = SEVERITY_NAMES[diag.severity] ?: 'UNKNOWN'
        def line = diag.range.start.line + 1  // Convert to 1-based
        def col = diag.range.start.character + 1
        def source = diag.source ? "[${diag.source}]" : ""
        def code = diag.code ? " (${diag.code})" : ""
        
        return "${severity} ${source} ${line}:${col}${code} - ${diag.message}"
    }
    
    /**
     * Format multiple diagnostics.
     * @param diagnostics List of diagnostics
     * @param limit Maximum number to format
     * @return Formatted string
     */
    static String formatAll(List<Diagnostic> diagnostics, int limit = 20) {
        if (!diagnostics || diagnostics.isEmpty()) {
            return ""
        }
        
        // Sort by severity (errors first) then by line
        def sorted = diagnostics.sort { a, b ->
            def severityCompare = a.severity <=> b.severity
            if (severityCompare != 0) return severityCompare
            return a.range.start.line <=> b.range.start.line
        }
        
        def formatted = sorted.take(limit).collect { format(it) }.join('\n')
        
        if (diagnostics.size() > limit) {
            formatted += "\n... and ${diagnostics.size() - limit} more diagnostics"
        }
        
        return formatted
    }
    
    /**
     * Format diagnostics as agent-friendly output.
     * @param diagnostics List of diagnostics
     * @return Formatted output with XML tags
     */
    static String formatForAgent(List<Diagnostic> diagnostics) {
        if (!diagnostics || diagnostics.isEmpty()) {
            return ""
        }
        
        def errors = diagnostics.findAll { it.severity == 1 }
        def warnings = diagnostics.findAll { it.severity == 2 }
        
        def result = new StringBuilder()
        
        if (errors) {
            result.append("\n\n⚠️ This file has ${errors.size()} error(s)")
            if (warnings) {
                result.append(" and ${warnings.size()} warning(s)")
            }
            result.append(", please fix:\n")
            result.append("<file_diagnostics>\n")
            result.append(formatAll(errors, 20))
            result.append("\n</file_diagnostics>")
        } else if (warnings) {
            result.append("\n\nNote: ${warnings.size()} warning(s) found:\n")
            result.append("<file_diagnostics>\n")
            result.append(formatAll(warnings, 10))
            result.append("\n</file_diagnostics>")
        }
        
        return result.toString()
    }
    
    /**
     * Get summary statistics.
     */
    static Map<String, Integer> summarize(List<Diagnostic> diagnostics) {
        def counts = [errors: 0, warnings: 0, info: 0, hints: 0]
        diagnostics.each { d ->
            switch (d.severity) {
                case 1: counts.errors++; break
                case 2: counts.warnings++; break
                case 3: counts.info++; break
                case 4: counts.hints++; break
            }
        }
        return counts
    }
}
