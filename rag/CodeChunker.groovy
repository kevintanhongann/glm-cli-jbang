package rag

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.data.document.Metadata

class CodeChunker {
    
    private final int maxChunkSize
    private final int overlap
    
    CodeChunker(int maxChunkSize = 500, int overlap = 50) {
        this.maxChunkSize = maxChunkSize
        this.overlap = overlap
    }
    
    List<TextSegment> chunk(Document document) {
        String content = document.text()
        Metadata metadata = document.metadata()
        String fileType = metadata.getString("file_type") ?: ""
        
        // Use language-aware chunking for code files
        if (isCodeFile(fileType)) {
            return chunkCode(content, metadata)
        } else {
            return chunkText(content, metadata)
        }
    }
    
    private List<TextSegment> chunkCode(String content, Metadata metadata) {
        List<TextSegment> segments = []
        
        // Split by function/class boundaries (simple heuristic)
        List<String> chunks = splitByCodeBlocks(content)
        
        chunks.each { chunk ->
            if (chunk.length() > maxChunkSize) {
                // Further split large chunks
                segments.addAll(splitLargeChunk(chunk, metadata))
            } else if (chunk.trim().length() > 20) {
                segments.add(TextSegment.from(chunk, metadata))
            }
        }
        
        return segments
    }
    
    private List<String> splitByCodeBlocks(String content) {
        // Split on common code block boundaries
        List<String> chunks = []
        StringBuilder current = new StringBuilder()
        int braceDepth = 0
        
        content.split('\n').each { line ->
            current.append(line).append('\n')
            braceDepth += line.count('{') - line.count('}')
            
            // At top level and reasonable size, create chunk
            if (braceDepth == 0 && current.length() > 100) {
                chunks.add(current.toString())
                current = new StringBuilder()
            }
        }
        
        if (current.length() > 0) {
            chunks.add(current.toString())
        }
        
        return chunks
    }
    
    private List<TextSegment> splitLargeChunk(String chunk, Metadata metadata) {
        List<TextSegment> segments = []
        String[] lines = chunk.split('\n')
        StringBuilder current = new StringBuilder()
        
        lines.each { line ->
            if (current.length() + line.length() > maxChunkSize && current.length() > 0) {
                segments.add(TextSegment.from(current.toString(), metadata))
                current = new StringBuilder()
            }
            current.append(line).append('\n')
        }
        
        if (current.length() > 0) {
            segments.add(TextSegment.from(current.toString(), metadata))
        }
        
        return segments
    }
    
    private List<TextSegment> chunkText(String content, Metadata metadata) {
        List<TextSegment> segments = []
        
        // Split by paragraphs for text files
        String[] paragraphs = content.split('\n\n+')
        StringBuilder current = new StringBuilder()
        
        paragraphs.each { para ->
            if (current.length() + para.length() > maxChunkSize && current.length() > 0) {
                segments.add(TextSegment.from(current.toString().trim(), metadata))
                current = new StringBuilder()
            }
            current.append(para).append('\n\n')
        }
        
        if (current.length() > 0) {
            segments.add(TextSegment.from(current.toString().trim(), metadata))
        }
        
        return segments
    }
    
    private boolean isCodeFile(String ext) {
        return ext in ['.groovy', '.java', '.kt', '.py', '.js', '.ts', '.go', '.rs', '.c', '.cpp']
    }
}
