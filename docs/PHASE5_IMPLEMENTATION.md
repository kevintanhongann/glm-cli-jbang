# Phase 5 Implementation Plan: Advanced Features

This document provides a comprehensive implementation plan for GLM-CLI Phase 5 features: Web Search Tool (completed), RAG (Retrieval Augmented Generation), and TUI (Terminal User Interface).

## Status Overview

| Feature | Status | Files |
|---------|--------|-------|
| **Web Search Tool** | ✅ Completed | `models/WebSearchResponse.groovy`, `core/WebSearchClient.groovy`, `tools/WebSearchTool.groovy` |
| **RAG System** | 🔲 Not Started | TBD |
| **TUI Enhancement** | 🔲 Not Started | TBD |

---

## 1. Web Search Tool (✅ Completed)

### Implementation Summary

The web search tool has been fully implemented with the following components:

- **WebSearchResponse.groovy**: Response model with JSON parsing
- **WebSearchClient.groovy**: HTTP client for Z.AI Web Search API
- **WebSearchTool.groovy**: Tool interface implementation with parameter validation

### Remaining Tasks

- [ ] **Register tool with agent**: Update `Agent.groovy` to include `WebSearchTool`
- [ ] **Add configuration options**: Add web search settings to `~/.glm/config.toml`
- [ ] **Write integration tests**: Test tool in agent loop
- [ ] **Update documentation**: Update TOOLS.md and README.md

### Registration Example

```groovy
// In Agent.groovy or AgentCommand.groovy
String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
agent.registerTool(new WebSearchTool(apiKey))
```

---

## 2. RAG (Retrieval Augmented Generation)

### Overview

Implement embedding-based semantic search for large codebases, allowing the agent to find relevant code snippets based on meaning rather than exact text matches.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        RAG Pipeline                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────┐   ┌──────────┐   ┌───────────┐   ┌──────────────┐  │
│  │ Document │──▶│ Chunker  │──▶│ Embedding │──▶│ Vector Store │  │
│  │  Loader  │   │          │   │   Model   │   │              │  │
│  └──────────┘   └──────────┘   └───────────┘   └──────────────┘  │
│                                                        │          │
│                                                        ▼          │
│  ┌──────────┐   ┌──────────┐   ┌───────────┐   ┌──────────────┐  │
│  │ Response │◀──│ Augment  │◀──│ Retriever │◀──│    Query     │  │
│  │          │   │  Prompt  │   │           │   │              │  │
│  └──────────┘   └──────────┘   └───────────┘   └──────────────┘  │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Dependencies

Add to `glm.groovy`:

```groovy
//DEPS dev.langchain4j:langchain4j-easy-rag:1.10.0
//DEPS dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.10.0
//DEPS dev.langchain4j:langchain4j-document-parser-apache-tika:1.10.0
```

**Alternative (for production with persistent storage):**

```groovy
//DEPS dev.langchain4j:langchain4j-pgvector:1.10.0
```

### Implementation Components

#### 2.1 Document Loader

**File:** `rag/CodebaseLoader.groovy`

```groovy
package rag

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.TextDocumentParser
import java.nio.file.Path
import java.nio.file.Files

class CodebaseLoader {
    
    private static final Set<String> SUPPORTED_EXTENSIONS = [
        '.groovy', '.java', '.kt', '.scala',     // JVM languages
        '.py', '.rb', '.js', '.ts', '.jsx', '.tsx',  // Scripting
        '.go', '.rs', '.c', '.cpp', '.h',        // Systems
        '.md', '.txt', '.json', '.yaml', '.toml', // Config/docs
        '.sh', '.bash', '.zsh'                    // Shell
    ] as Set
    
    private static final Set<String> IGNORE_PATTERNS = [
        '.git', 'node_modules', 'target', 'build', '.gradle',
        '__pycache__', '.idea', '.vscode', 'dist', 'out'
    ] as Set
    
    List<Document> loadCodebase(String path) {
        Path basePath = Path.of(path).toAbsolutePath()
        List<Document> documents = []
        
        Files.walk(basePath)
            .filter { shouldInclude(it, basePath) }
            .forEach { filePath ->
                try {
                    String content = Files.readString(filePath)
                    String relativePath = basePath.relativize(filePath).toString()
                    
                    Document doc = Document.from(content, [
                        "file_path": relativePath,
                        "file_name": filePath.fileName.toString(),
                        "file_type": getExtension(filePath),
                        "absolute_path": filePath.toString()
                    ])
                    
                    documents.add(doc)
                } catch (Exception e) {
                    System.err.println("Warning: Could not load ${filePath}: ${e.message}")
                }
            }
        
        return documents
    }
    
    private boolean shouldInclude(Path path, Path basePath) {
        if (Files.isDirectory(path)) return false
        
        // Check ignore patterns
        String pathStr = path.toString()
        for (String pattern : IGNORE_PATTERNS) {
            if (pathStr.contains("/${pattern}/") || pathStr.endsWith("/${pattern}")) {
                return false
            }
        }
        
        // Check extension
        String ext = getExtension(path)
        return SUPPORTED_EXTENSIONS.contains(ext)
    }
    
    private String getExtension(Path path) {
        String name = path.fileName.toString()
        int dotIndex = name.lastIndexOf('.')
        return dotIndex > 0 ? name.substring(dotIndex) : ''
    }
}
```

#### 2.2 Document Chunker

**File:** `rag/CodeChunker.groovy`

```groovy
package rag

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.segment.TextSegment

class CodeChunker {
    
    private final int maxChunkSize
    private final int overlap
    
    CodeChunker(int maxChunkSize = 500, int overlap = 50) {
        this.maxChunkSize = maxChunkSize
        this.overlap = overlap
    }
    
    List<TextSegment> chunk(Document document) {
        String content = document.text()
        Map<String, String> metadata = document.metadata().asMap()
        String fileType = metadata.getOrDefault("file_type", "")
        
        // Use language-aware chunking for code files
        if (isCodeFile(fileType)) {
            return chunkCode(content, metadata)
        } else {
            return chunkText(content, metadata)
        }
    }
    
    private List<TextSegment> chunkCode(String content, Map<String, String> metadata) {
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
    
    private List<TextSegment> splitLargeChunk(String chunk, Map<String, String> metadata) {
        List<TextSegment> segments = []
        String[] lines = chunk.split('\n')
        StringBuilder current = new StringBuilder()
        
        lines.each { line ->
            if (current.length() + line.length() > maxChunkSize && current.length() > 0) {
                segments.add(TextSegment.from(current.toString(), metadata))
                // Keep overlap
                current = new StringBuilder()
                // Add last few lines for context
                int startLine = Math.max(0, segments.size() * (maxChunkSize / 50) - overlap)
            }
            current.append(line).append('\n')
        }
        
        if (current.length() > 0) {
            segments.add(TextSegment.from(current.toString(), metadata))
        }
        
        return segments
    }
    
    private List<TextSegment> chunkText(String content, Map<String, String> metadata) {
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
```

#### 2.3 Embedding Store

**File:** `rag/EmbeddingService.groovy`

```groovy
package rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
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
```

#### 2.4 RAG Pipeline

**File:** `rag/RAGPipeline.groovy`

```groovy
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
            def metadata = segment.metadata().asMap()
            String filePath = metadata.getOrDefault("file_path", "unknown")
            
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
```

#### 2.5 Code Search Tool

**File:** `tools/CodeSearchTool.groovy`

```groovy
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
```

### CLI Integration

Update `AgentCommand.groovy`:

```groovy
@Option(names = ["--index-codebase", "-i"], description = "Path to codebase to index for RAG")
String codebasePath

@Option(names = ["--rag"], description = "Enable RAG-based code search")
boolean enableRag = false

// In run() method:
RAGPipeline ragPipeline = null
if (codebasePath || enableRag) {
    ragPipeline = new RAGPipeline()
    if (codebasePath) {
        ragPipeline.indexCodebase(codebasePath)
    }
    agent.registerTool(new CodeSearchTool(ragPipeline))
}
```

### Usage Examples

```bash
# Index and search codebase
./glm.groovy agent --index-codebase ./src "Find authentication handling code"

# Use RAG with existing index
./glm.groovy agent --rag "How does the tool registration work?"
```

---

## 3. TUI (Terminal User Interface)

### Overview

Enhance the terminal interface with rich diff displays, syntax highlighting, and interactive features using Lanterna and Jansi.

### Dependencies

Add to `glm.groovy`:

```groovy
//DEPS com.googlecode.lanterna:lanterna:3.1.2
//DEPS org.fusesource.jansi:jansi:2.4.1
```

### Implementation Components

#### 3.1 ANSI Color Utilities

**File:** `tui/AnsiColors.groovy`

```groovy
package tui

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole

class AnsiColors {
    
    static void install() {
        AnsiConsole.systemInstall()
    }
    
    static void uninstall() {
        AnsiConsole.systemUninstall()
    }
    
    static String red(String text) {
        Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString()
    }
    
    static String green(String text) {
        Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString()
    }
    
    static String yellow(String text) {
        Ansi.ansi().fg(Ansi.Color.YELLOW).a(text).reset().toString()
    }
    
    static String blue(String text) {
        Ansi.ansi().fg(Ansi.Color.BLUE).a(text).reset().toString()
    }
    
    static String cyan(String text) {
        Ansi.ansi().fg(Ansi.Color.CYAN).a(text).reset().toString()
    }
    
    static String magenta(String text) {
        Ansi.ansi().fg(Ansi.Color.MAGENTA).a(text).reset().toString()
    }
    
    static String bold(String text) {
        Ansi.ansi().bold().a(text).reset().toString()
    }
    
    static String dim(String text) {
        Ansi.ansi().fgBrightBlack().a(text).reset().toString()
    }
}
```

#### 3.2 Diff Renderer

**File:** `tui/DiffRenderer.groovy`

```groovy
package tui

class DiffRenderer {
    
    static String renderDiff(String original, String modified) {
        List<String> origLines = original.split('\n').toList()
        List<String> modLines = modified.split('\n').toList()
        
        StringBuilder output = new StringBuilder()
        output.append(AnsiColors.bold("=== DIFF ===\n"))
        
        List<DiffLine> diffLines = computeDiff(origLines, modLines)
        
        diffLines.each { line ->
            switch (line.type) {
                case DiffType.CONTEXT:
                    output.append(AnsiColors.dim("  ${line.content}\n"))
                    break
                case DiffType.ADDITION:
                    output.append(AnsiColors.green("+ ${line.content}\n"))
                    break
                case DiffType.DELETION:
                    output.append(AnsiColors.red("- ${line.content}\n"))
                    break
            }
        }
        
        return output.toString()
    }
    
    static String renderUnifiedDiff(String original, String modified, String fileName) {
        List<String> origLines = original.split('\n').toList()
        List<String> modLines = modified.split('\n').toList()
        
        StringBuilder output = new StringBuilder()
        output.append(AnsiColors.bold("--- a/${fileName}\n"))
        output.append(AnsiColors.bold("+++ b/${fileName}\n"))
        
        List<DiffLine> diffLines = computeDiff(origLines, modLines)
        
        // Group into hunks
        List<List<DiffLine>> hunks = groupIntoHunks(diffLines, 3)
        
        hunks.each { hunk ->
            int startLine = hunk[0].lineNumber
            output.append(AnsiColors.cyan("@@ -${startLine},${hunk.size()} +${startLine},${hunk.size()} @@\n"))
            
            hunk.each { line ->
                switch (line.type) {
                    case DiffType.CONTEXT:
                        output.append(" ${line.content}\n")
                        break
                    case DiffType.ADDITION:
                        output.append(AnsiColors.green("+${line.content}\n"))
                        break
                    case DiffType.DELETION:
                        output.append(AnsiColors.red("-${line.content}\n"))
                        break
                }
            }
        }
        
        return output.toString()
    }
    
    private static List<DiffLine> computeDiff(List<String> origLines, List<String> modLines) {
        List<DiffLine> result = []
        
        // Simple LCS-based diff (can be replaced with more sophisticated algorithm)
        int i = 0, j = 0
        int lineNum = 1
        
        while (i < origLines.size() || j < modLines.size()) {
            if (i >= origLines.size()) {
                // Remaining lines are additions
                while (j < modLines.size()) {
                    result.add(new DiffLine(DiffType.ADDITION, modLines[j], lineNum++))
                    j++
                }
            } else if (j >= modLines.size()) {
                // Remaining lines are deletions
                while (i < origLines.size()) {
                    result.add(new DiffLine(DiffType.DELETION, origLines[i], lineNum++))
                    i++
                }
            } else if (origLines[i] == modLines[j]) {
                // Lines match - context
                result.add(new DiffLine(DiffType.CONTEXT, origLines[i], lineNum++))
                i++
                j++
            } else {
                // Lines differ
                result.add(new DiffLine(DiffType.DELETION, origLines[i], lineNum))
                result.add(new DiffLine(DiffType.ADDITION, modLines[j], lineNum++))
                i++
                j++
            }
        }
        
        return result
    }
    
    private static List<List<DiffLine>> groupIntoHunks(List<DiffLine> lines, int contextLines) {
        List<List<DiffLine>> hunks = []
        List<DiffLine> currentHunk = []
        int contextCount = 0
        
        lines.each { line ->
            if (line.type != DiffType.CONTEXT) {
                currentHunk.add(line)
                contextCount = 0
            } else {
                currentHunk.add(line)
                contextCount++
                
                if (contextCount > contextLines * 2 && currentHunk.size() > contextLines) {
                    // Split hunk
                    hunks.add(currentHunk)
                    currentHunk = []
                    contextCount = 0
                }
            }
        }
        
        if (!currentHunk.isEmpty()) {
            hunks.add(currentHunk)
        }
        
        return hunks
    }
    
    static enum DiffType {
        CONTEXT, ADDITION, DELETION
    }
    
    static class DiffLine {
        DiffType type
        String content
        int lineNumber
        
        DiffLine(DiffType type, String content, int lineNumber) {
            this.type = type
            this.content = content
            this.lineNumber = lineNumber
        }
    }
}
```

#### 3.3 Interactive Prompt

**File:** `tui/InteractivePrompt.groovy`

```groovy
package tui

import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class InteractivePrompt {
    
    static boolean confirm(String message) {
        print "${AnsiColors.yellow("?")} ${message} ${AnsiColors.dim("[y/N]")} "
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String response = reader.readLine()?.trim()?.toLowerCase()
        
        return response in ['y', 'yes']
    }
    
    static String prompt(String message, String defaultValue = null) {
        String prompt = "${AnsiColors.cyan("?")} ${message}"
        if (defaultValue) {
            prompt += " ${AnsiColors.dim("(${defaultValue})")}"
        }
        prompt += " "
        
        print prompt
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String response = reader.readLine()?.trim()
        
        return response?.isEmpty() ? defaultValue : response
    }
    
    static int select(String message, List<String> options) {
        println "${AnsiColors.cyan("?")} ${message}"
        
        options.eachWithIndex { option, i ->
            println "  ${AnsiColors.bold("${i + 1}.")} ${option}"
        }
        
        print "${AnsiColors.dim("Enter number:")} "
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String response = reader.readLine()?.trim()
        
        try {
            int selection = Integer.parseInt(response) - 1
            if (selection >= 0 && selection < options.size()) {
                return selection
            }
        } catch (NumberFormatException e) {
            // Invalid input
        }
        
        return -1
    }
}
```

#### 3.4 Progress Indicator

**File:** `tui/ProgressIndicator.groovy`

```groovy
package tui

class ProgressIndicator {
    
    private static final String[] SPINNER_FRAMES = ['⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏']
    private int frame = 0
    private boolean running = false
    private Thread spinnerThread
    private String message
    
    void start(String message) {
        this.message = message
        this.running = true
        this.frame = 0
        
        spinnerThread = Thread.start {
            while (running) {
                print "\r${AnsiColors.cyan(SPINNER_FRAMES[frame])} ${message}"
                frame = (frame + 1) % SPINNER_FRAMES.length
                Thread.sleep(80)
            }
        }
    }
    
    void stop(boolean success = true) {
        running = false
        spinnerThread?.join()
        
        String icon = success ? AnsiColors.green("✓") : AnsiColors.red("✗")
        println "\r${icon} ${message}"
    }
    
    static void withSpinner(String message, Closure action) {
        ProgressIndicator spinner = new ProgressIndicator()
        spinner.start(message)
        
        try {
            action.call()
            spinner.stop(true)
        } catch (Exception e) {
            spinner.stop(false)
            throw e
        }
    }
}
```

#### 3.5 Output Formatter

**File:** `tui/OutputFormatter.groovy`

```groovy
package tui

class OutputFormatter {
    
    static void printHeader(String title) {
        int width = 60
        String border = "═" * width
        
        println AnsiColors.cyan("╔${border}╗")
        println AnsiColors.cyan("║") + AnsiColors.bold(title.center(width)) + AnsiColors.cyan("║")
        println AnsiColors.cyan("╚${border}╝")
    }
    
    static void printSection(String title) {
        println "\n${AnsiColors.bold(AnsiColors.blue("▶ ${title}"))}"
        println AnsiColors.dim("─" * 40)
    }
    
    static void printSuccess(String message) {
        println "${AnsiColors.green("✓")} ${message}"
    }
    
    static void printError(String message) {
        println "${AnsiColors.red("✗")} ${message}"
    }
    
    static void printWarning(String message) {
        println "${AnsiColors.yellow("⚠")} ${message}"
    }
    
    static void printInfo(String message) {
        println "${AnsiColors.blue("ℹ")} ${message}"
    }
    
    static void printCode(String code, String language = null) {
        println AnsiColors.dim("```${language ?: ''}")
        code.split('\n').each { line ->
            println "  ${line}"
        }
        println AnsiColors.dim("```")
    }
    
    static void printTable(List<List<String>> rows, List<String> headers = null) {
        if (rows.isEmpty()) return
        
        int cols = headers?.size() ?: rows[0].size()
        List<Integer> widths = (0..<cols).collect { col ->
            int maxWidth = headers ? headers[col].length() : 0
            rows.each { row ->
                if (col < row.size()) {
                    maxWidth = Math.max(maxWidth, row[col].length())
                }
            }
            return maxWidth + 2
        }
        
        // Print header
        if (headers) {
            String headerRow = headers.withIndex().collect { h, i ->
                h.padRight(widths[i])
            }.join("│")
            println AnsiColors.bold(headerRow)
            println "─" * widths.sum()
        }
        
        // Print rows
        rows.each { row ->
            String dataRow = row.withIndex().collect { cell, i ->
                (cell ?: "").padRight(widths[i])
            }.join("│")
            println dataRow
        }
    }
}
```

### Integration with WriteFileTool

Update `tools/WriteFileTool.groovy` to use the new diff renderer:

```groovy
import tui.DiffRenderer
import tui.InteractivePrompt
import tui.AnsiColors

// In execute() method, before writing:
if (Files.exists(filePath)) {
    String original = Files.readString(filePath)
    println DiffRenderer.renderUnifiedDiff(original, content, path)
    
    if (!InteractivePrompt.confirm("Apply these changes?")) {
        return "Operation cancelled by user"
    }
}
```

---

## 4. Implementation Timeline

### Week 1: Web Search Integration & Testing

| Day | Tasks |
|-----|-------|
| 1 | Register WebSearchTool with agent, add config options |
| 2 | Write integration tests for web search in agent loop |
| 3 | Update documentation (TOOLS.md, README.md, USAGE.md) |

### Week 2: RAG Implementation

| Day | Tasks |
|-----|-------|
| 1 | Add langchain4j dependencies, implement CodebaseLoader |
| 2 | Implement CodeChunker and EmbeddingService |
| 3 | Implement RAGPipeline and CodeSearchTool |
| 4 | Integrate with AgentCommand, add CLI options |
| 5 | Test with real codebases, optimize chunking |

### Week 3: TUI Enhancement

| Day | Tasks |
|-----|-------|
| 1 | Add Lanterna/Jansi dependencies, implement AnsiColors |
| 2 | Implement DiffRenderer with unified diff support |
| 3 | Implement InteractivePrompt and ProgressIndicator |
| 4 | Integrate with WriteFileTool for rich diff preview |
| 5 | Polish and test on different terminals |

---

## 5. Testing Strategy

### Unit Tests

```groovy
// Test WebSearchTool
class WebSearchToolTest {
    void testBasicSearch() {
        // Mock WebSearchClient
        // Verify response formatting
    }
    
    void testInvalidQuery() {
        // Test empty query handling
    }
}

// Test RAGPipeline
class RAGPipelineTest {
    void testCodebaseIndexing() {
        // Test with sample codebase
    }
    
    void testSemanticSearch() {
        // Verify relevant results returned
    }
}

// Test DiffRenderer
class DiffRendererTest {
    void testAdditions() {
        // Verify additions shown in green
    }
    
    void testDeletions() {
        // Verify deletions shown in red
    }
}
```

### Integration Tests

```bash
# Test web search integration
./glm.groovy agent "Search for Java 21 features"

# Test RAG
./glm.groovy agent --index-codebase . "Find tool registration code"

# Test TUI diff
./glm.groovy agent "Add a hello method to HelloWorld.java"
```

---

## 6. Configuration

Add to `~/.glm/config.toml`:

```toml
[web_search]
enabled = true
default_count = 10
default_recency = "noLimit"

[rag]
enabled = false
cache_dir = "~/.glm/embeddings"
max_chunk_size = 500
min_score = 0.5

[tui]
colors_enabled = true
diff_context_lines = 3
spinner_style = "dots"
```

---

## 7. Success Criteria

### Web Search (Completed)
- [x] WebSearchResponse model created
- [x] WebSearchClient implemented
- [x] WebSearchTool created
- [ ] Tool registered with agent
- [ ] Configuration options added
- [ ] Documentation updated
- [ ] Integration tests passing

### RAG
- [ ] Codebase indexing works for 10K+ files
- [ ] Semantic search returns relevant results
- [ ] Query response time < 500ms
- [ ] Embeddings cached for reuse
- [ ] CLI flags working correctly

### TUI
- [ ] Diff display shows colors correctly
- [ ] Works on Linux, macOS, Windows
- [ ] Fallback to plain text if ANSI unsupported
- [ ] Interactive prompts work correctly
- [ ] Progress indicators animate smoothly

---

## References

- [LangChain4j Documentation](https://docs.langchain4j.dev)
- [LangChain4j Easy RAG](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-easy-rag)
- [Lanterna Library](https://github.com/mabe02/lanterna)
- [Jansi Library](https://github.com/fusesource/jansi)
- [Z.AI Web Search API](https://docs.z.ai/guides/tools/web-search)
- [WEB_SEARCH_IMPLEMENTATION.md](../WEB_SEARCH_IMPLEMENTATION.md)

---

**Document Version:** 1.0  
**Created:** 2025-12-30  
**Last Updated:** 2025-12-30
