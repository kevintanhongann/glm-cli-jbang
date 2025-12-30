package rag

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.segment.TextSegment

class RAGPipeline {
    
    private final CodebaseLoader loader
    private final CodeChunker chunker
    private final EmbeddingService embeddingService
    private boolean initialized = false
    
    RAGPipeline(String cacheLocation = null) {
        this.loader = new CodebaseLoader()
        this.chunker = new CodeChunker()
        this.embeddingService = new EmbeddingService(cacheLocation)
    }
    
    void indexCodebase(String path) {
        println "Loading codebase from: ${path}"
        List<Document> documents = loader.loadCodebase(path)
        println "Loaded ${documents.size()} files"
        
        println "Chunking documents..."
        List<TextSegment> allSegments = []
        documents.each { doc ->
            allSegments.addAll(chunker.chunk(doc))
        }
        println "Created ${allSegments.size()} chunks"
        
        embeddingService.ingest(allSegments)
        initialized = true
    }
    
    List<TextSegment> search(String query, int maxResults = 5) {
        if (!initialized) {
            throw new IllegalStateException("RAG pipeline not initialized. Call indexCodebase() first.")
        }
        return embeddingService.search(query, maxResults)
    }
    
    String searchAndFormat(String query, int maxResults = 5) {
        List<TextSegment> results = search(query, maxResults)
        
        if (results.isEmpty()) {
            return "No relevant code found for: '${query}'"
        }
        
        StringBuilder sb = new StringBuilder()
        sb.append("Found ${results.size()} relevant code segments:\n\n")
        
        results.eachWithIndex { segment, i ->
            def metadata = segment.metadata()
            String filePath = metadata.getString("file_path") ?: "unknown"
            
            sb.append("--- ${i + 1}. ${filePath} ---\n")
            sb.append(segment.text())
            sb.append("\n\n")
        }
        
        return sb.toString()
    }
    
    boolean isInitialized() {
        return initialized
    }
}
