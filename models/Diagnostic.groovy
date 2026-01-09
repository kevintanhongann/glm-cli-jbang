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
    
    /** Optional tags (e.g., Unnecessary, Deprecated) */
    List<String> tags = []
    
    /** Related information for the diagnostic */
    List<DiagnosticRelatedInformation> relatedInformation = []
    
    static Diagnostic fromLsp(Map lsp) {
        def diag = new Diagnostic()
        diag.uri = lsp.uri
        diag.severity = lsp.severity ?: 1
        diag.message = lsp.message ?: ""
        diag.source = lsp.source
        diag.code = lsp.code?.toString()
        diag.tags = lsp.tags ?: []
        
        if (lsp.range) {
            diag.range = new DiagnosticRange(
                start: new Position(
                    line: lsp.range.start?.line ?: 0,
                    character: lsp.range.start?.character ?: 0
                ),
                end: new Position(
                    line: lsp.range.end?.line ?: 0,
                    character: lsp.range.end?.character ?: 0
                )
            )
        }
        
        if (lsp.relatedInformation) {
            diag.relatedInformation = lsp.relatedInformation.collect { info ->
                new DiagnosticRelatedInformation(
                    location: new Location(
                        uri: info.location?.uri,
                        range: new DiagnosticRange(
                            start: new Position(
                                line: info.location?.range?.start?.line ?: 0,
                                character: info.location?.range?.start?.character ?: 0
                            ),
                            end: new Position(
                                line: info.location?.range?.end?.line ?: 0,
                                character: info.location?.range?.end?.character ?: 0
                            )
                        )
                    ),
                    message: info.message
                )
            }
        }
        
        return diag
    }
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

/**
 * Related diagnostic information.
 */
@Canonical
class DiagnosticRelatedInformation {
    Location location
    String message
}

/**
 * Location in a document.
 */
@Canonical
class Location {
    String uri
    DiagnosticRange range
}
