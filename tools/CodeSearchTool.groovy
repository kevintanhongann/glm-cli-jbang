package tools

import rag.RAGPipeline

class CodeSearchTool implements Tool {
    
    private final RAGPipeline pipeline
    
    CodeSearchTool(RAGPipeline pipeline) {
        this.pipeline = pipeline
    }
    
    @Override
    String getName() { "code_search" }
    
    @Override
    String getDescription() {
        "Search the indexed codebase for relevant code using semantic search. " +
        "Use natural language queries to find code by meaning, not just exact text matches. " +
        "Example: 'authentication logic' or 'database connection handling'"
    }
    
    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                query: [
                    type: "string",
                    description: "Natural language search query describing the code you're looking for"
                ],
                max_results: [
                    type: "integer",
                    description: "Maximum number of results to return (1-20, default: 5)"
                ]
            ],
            required: ["query"]
        ]
    }
    
    @Override
    Object execute(Map<String, Object> args) {
        try {
            if (!pipeline.isInitialized()) {
                return "Error: Codebase not indexed. Use --index-codebase flag to index first."
            }
            
            String query = args.get("query")
            if (query == null || query.trim().isEmpty()) {
                return "Error: query is required and cannot be empty"
            }
            
            int maxResults = 5
            if (args.containsKey("max_results")) {
                def mr = args.get("max_results")
                if (mr instanceof Number) {
                    maxResults = Math.min(20, Math.max(1, ((Number) mr).intValue()))
                }
            }
            
            return pipeline.searchAndFormat(query, maxResults)
        } catch (Exception e) {
            return "Error searching codebase: ${e.message}"
        }
    }
}
