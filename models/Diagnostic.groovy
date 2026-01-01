package models

import groovy.transform.Canonical

/**
 * LSP Diagnostic data class.
 * Represents a diagnostic item from a language server.
 */
@Canonical
class Diagnostic {
    /** File URI this diagnostic applies to */
    String uri
    
    /** Diagnostic range in the document */
    DiagnosticRange range
    
    /** Severity: 1=Error, 2=Warning, 3=Info, 4=Hint */
    int severity
    
    /** Diagnostic message */
    String message
    
    /** Source of the diagnostic (e.g., "typescript", "eslint") */
    String source
    
    /** Optional diagnostic code */
    String code
}

/**
 * Range within a document.
 */
@Canonical
class DiagnosticRange {
    Position start
    Position end
}

/**
 * Position in a document (0-indexed).
 */
@Canonical  
class Position {
    int line
    int character
}
