package rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import dev.langchain4j.data.embedding.Embedding
import java.nio.file.*

class EmbeddingService {
    
    private final EmbeddingModel embeddingModel
    private final EmbeddingStore<TextSegment> embeddingStore
    private final Path cacheDir
    
    EmbeddingService(String cacheLocation = null) {
        this.embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel()
        this.embeddingStore = new InMemoryEmbeddingStore<>()
        this.cacheDir = cacheLocation ? 
            Path.of(cacheLocation) : 
            Path.of(System.getProperty("user.home"), ".glm", "embeddings")
        
        Files.createDirectories(cacheDir)
    }
    
    void ingest(List<TextSegment> segments) {
        println "Ingesting ${segments.size()} segments..."
        
        segments.eachWithIndex { segment, i ->
            Embedding embedding = embeddingModel.embed(segment.text()).content()
            embeddingStore.add(embedding, segment)
            
            if ((i + 1) % 100 == 0) {
                println "  Processed ${i + 1}/${segments.size()} segments"
            }
        }
        
        println "Ingestion complete."
    }
    
    List<TextSegment> search(String query, int maxResults = 5, double minScore = 0.5) {
        Embedding queryEmbedding = embeddingModel.embed(query).content()
        
        def results = embeddingStore.findRelevant(queryEmbedding, maxResults, minScore)
        
        return results.collect { it.embedded() }
    }
    
    EmbeddingStore<TextSegment> getStore() {
        return embeddingStore
    }
    
    EmbeddingModel getModel() {
        return embeddingModel
    }
}
