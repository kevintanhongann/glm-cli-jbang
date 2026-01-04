package tools

import core.LSPManager
import core.LSPConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LSPTool implements Tool {

    @Override
    String getName() { 'lsp' }

    @Override
    String getDescription() {
        return '''
Language Server Protocol operations for code intelligence.

**WHEN TO USE:**
- Finding symbol definitions (goToDefinition)
- Finding all references to a symbol (findReferences)
- Getting documentation/type info (hover)
- Getting all symbols in a document (documentSymbol)
- Searching symbols across workspace (workspaceSymbol)

**PARAMETERS:**
- operation: the LSP operation to perform
- filePath: absolute path to the file
- position: cursor position with line and column (0-indexed)
- query: search query (for workspaceSymbol)

**SUPPORTED OPERATIONS:**
- goToDefinition: Find where a symbol is defined
- findReferences: Find all usages of a symbol
- hover: Get documentation and type information
- documentSymbol: Get all symbols in the current file
- workspaceSymbol: Search symbols across all files

**POSITION FORMAT:**
```json
{
  "line": 10,
  "character": 5
}
```

**BEST PRACTICES:**
- Ensure LSP server is configured for the file type
- Use goToDefinition to understand symbol origins
- Use findReferences to see symbol usage
- Use hover for quick documentation
- Use workspaceSymbol for finding symbols without knowing the file

**WHEN NOT TO USE:**
- For file operations → use file tools instead
- When LSP is not configured for the language

**REQUIREMENTS:**
- LSP must be enabled in configuration
- Language server must be configured for the file type
- File must be part of a valid project
'''.stripIndent().trim()
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                operation: [
                    type: 'string',
                    enum: ['goToDefinition', 'findReferences', 'hover', 'documentSymbol', 'workspaceSymbol'],
                    description: 'The LSP operation to perform'
                ],
                filePath: [type: 'string', description: 'Absolute path to the file'],
                position: [
                    type: 'object',
                    properties: [
                        line: [type: 'integer', description: 'Line number (0-indexed)'],
                        character: [type: 'integer', description: 'Column number (0-indexed)']
                    ],
                    required: ['line', 'character'],
                    description: 'Cursor position (required for goToDefinition, findReferences, hover)'
                ],
                query: [type: 'string', description: 'Search query (required for workspaceSymbol)']
            ],
            required: ['operation', 'filePath']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String operation = args.get('operation')
        String filePath = args.get('filePath')
        
        def lsp = LSPManager.instance
        if (!lsp.enabled) {
            return "Error: LSP is not enabled. Enable it in your configuration."
        }
        
        Path path = Paths.get(filePath).normalize()
        if (!Files.exists(path)) {
            return "Error: File not found: ${filePath}"
        }
        
        def client = lsp.getClient(filePath)
        if (client == null) {
            return "Error: No LSP server available for this file type. Check your LSP configuration."
        }
        
        if (!client.alive) {
            return "Error: LSP server is not responding."
        }
        
        switch (operation) {
            case 'goToDefinition':
                return goToDefinition(client, filePath, args.get('position'))
            case 'findReferences':
                return findReferences(client, filePath, args.get('position'))
            case 'hover':
                return hover(client, filePath, args.get('position'))
            case 'documentSymbol':
                return documentSymbol(client, filePath)
            case 'workspaceSymbol':
                return workspaceSymbol(client, args.get('query'))
            default:
                return "Error: Unknown operation '${operation}'"
        }
    }
    
    private String goToDefinition(def client, String filePath, Map<String, Object> position) {
        if (position == null) {
            return "Error: position parameter is required for goToDefinition"
        }
        
        try {
            def line = position.get('line') as Integer
            def character = position.get('character') as Integer
            
            if (line == null || character == null) {
                return "Error: position must include 'line' and 'character' fields"
            }
            
            def definitions = client.goToDefinition(filePath, line, character)
            
            if (definitions == null || definitions.isEmpty()) {
                return "No definition found at position ${line}:${character}"
            }
            
            def result = new StringBuilder()
            result.append("Found ${definitions.size()} definition(s):\n\n")
            
            definitions.eachWithIndex { defn, idx ->
                result.append("${idx + 1}. ${defn.uri ?: 'unknown file'}")
                if (defn.range) {
                    result.append(" at line ${defn.range.start.line}")
                }
                result.append("\n")
            }
            
            return result.toString().trim()
        } catch (Exception e) {
            return "Error executing goToDefinition: ${e.message}"
        }
    }
    
    private String findReferences(def client, String filePath, Map<String, Object> position) {
        if (position == null) {
            return "Error: position parameter is required for findReferences"
        }
        
        try {
            def line = position.get('line') as Integer
            def character = position.get('character') as Integer
            
            if (line == null || character == null) {
                return "Error: position must include 'line' and 'character' fields"
            }
            
            def references = client.findReferences(filePath, line, character)
            
            if (references == null || references.isEmpty()) {
                return "No references found at position ${line}:${character}"
            }
            
            def result = new StringBuilder()
            result.append("Found ${references.size()} reference(s):\n\n")
            
            references.eachWithIndex { ref, idx ->
                result.append("${idx + 1}. ${ref.uri ?: 'unknown file'}")
                if (ref.range) {
                    result.append(" at line ${ref.range.start.line}")
                }
                result.append("\n")
            }
            
            return result.toString().trim()
        } catch (Exception e) {
            return "Error executing findReferences: ${e.message}"
        }
    }
    
    private String hover(def client, String filePath, Map<String, Object> position) {
        if (position == null) {
            return "Error: position parameter is required for hover"
        }
        
        try {
            def line = position.get('line') as Integer
            def character = position.get('character') as Integer
            
            if (line == null || character == null) {
                return "Error: position must include 'line' and 'character' fields"
            }
            
            def hoverResult = client.hover(filePath, line, character)
            
            if (hoverResult == null || hoverResult.isEmpty()) {
                return "No hover information available at position ${line}:${character}"
            }
            
            return hoverResult
        } catch (Exception e) {
            return "Error executing hover: ${e.message}"
        }
    }
    
    private String documentSymbol(def client, String filePath) {
        try {
            def symbols = client.documentSymbol(filePath)
            
            if (symbols == null || symbols.isEmpty()) {
                return "No symbols found in file"
            }
            
            def result = new StringBuilder()
            result.append("Found ${symbols.size()} symbol(s) in file:\n\n")
            
            symbols.each { symbol ->
                def indent = "  " * (symbol.kind ?: 0)
                result.append("${indent}${symbol.name}")
                if (symbol.kind) {
                    result.append(" (${symbol.kind})")
                }
                if (symbol.range) {
                    result.append(" [line ${symbol.range.start.line}]")
                }
                result.append("\n")
            }
            
            return result.toString().trim()
        } catch (Exception e) {
            return "Error executing documentSymbol: ${e.message}"
        }
    }
    
    private String workspaceSymbol(def client, String query) {
        if (query == null || query.trim().isEmpty()) {
            return "Error: query parameter is required for workspaceSymbol"
        }
        
        try {
            def symbols = client.workspaceSymbol(query)
            
            if (symbols == null || symbols.isEmpty()) {
                return "No symbols found matching '${query}'"
            }
            
            def result = new StringBuilder()
            result.append("Found ${symbols.size()} symbol(s) matching '${query}':\n\n")
            
            symbols.eachWithIndex { symbol, idx ->
                result.append("${idx + 1}. ${symbol.name}")
                if (symbol.containerName) {
                    result.append(" in ${symbol.containerName}")
                }
                if (symbol.kind) {
                    result.append(" (${symbol.kind})")
                }
                if (symbol.location?.uri) {
                    result.append("\n   File: ${symbol.location.uri}")
                    if (symbol.location.range) {
                        result.append(" at line ${symbol.location.range.start.line}")
                    }
                }
                result.append("\n\n")
            }
            
            return result.toString().trim()
        } catch (Exception e) {
            return "Error executing workspaceSymbol: ${e.message}"
        }
    }
}
