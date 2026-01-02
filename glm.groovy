///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:1.10.0
//DEPS ./jexer-2.0.0-full.jar
//DEPS info.picocli:picocli:4.7.7
//DEPS info.picocli:picocli-groovy:4.7.7   
//DEPS dev.langchain4j:langchain4j-zhipu-ai:0.36.2

// Groovy JSON module (required for JsonSlurper/JsonOutput in Groovy 4.x)
//DEPS org.apache.groovy:groovy-json:4.0.27

// JWT for ZhipuAI authentication
//DEPS com.auth0:java-jwt:4.4.0

// Jackson for config parsing
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.15.2

// RAG Dependencies
//DEPS dev.langchain4j:langchain4j-easy-rag:1.0.0-beta2
//DEPS dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.0.0-beta2

// TUI Dependencies
//DEPS org.fusesource.jansi:jansi:2.4.1
//DEPS com.googlecode.lanterna:lanterna:3.1.2

// Core sources
// TODO: ZaiCodingPlanClient needs to be updated for new LangChain4j API
// //SOURCES core/ZaiCodingPlanClient.groovy
//SOURCES commands/GlmCli.groovy
//SOURCES commands/ChatCommand.groovy
//SOURCES commands/AgentCommand.groovy
//SOURCES commands/AuthCommand.groovy
//SOURCES models/WebSearchResponse.groovy
//SOURCES core/WebSearchClient.groovy
//SOURCES core/GlmClient.groovy
//SOURCES core/Config.groovy
//SOURCES core/RipgrepHelper.groovy
//SOURCES core/JavaSearchFallback.groovy
//SOURCES tools/WebSearchTool.groovy
//SOURCES models/Auth.groovy
//SOURCES models/ChatRequest.groovy
//SOURCES models/ChatResponse.groovy
//SOURCES models/Message.groovy
//SOURCES core/Auth.groovy

// RAG sources
//SOURCES rag/CodebaseLoader.groovy
//SOURCES rag/CodeChunker.groovy
//SOURCES rag/EmbeddingService.groovy
//SOURCES rag/RAGPipeline.groovy

// Tool sources
//SOURCES tools/Tool.groovy
//SOURCES tools/ReadFileTool.groovy
//SOURCES tools/WriteFileTool.groovy
//SOURCES tools/ListFilesTool.groovy
//SOURCES tools/FetchUrlTool.groovy
//SOURCES tools/WebSearchTool.groovy
//SOURCES tools/CodeSearchTool.groovy
//SOURCES tools/GrepTool.groovy
//SOURCES tools/GlobTool.groovy

// Agent source
//SOURCES core/Agent.groovy

// TUI sources (Jexer based)
//SOURCES tui/AnsiColors.groovy
//SOURCES tui/DiffRenderer.groovy
//SOURCES tui/InteractivePrompt.groovy
//SOURCES tui/ProgressIndicator.groovy
//SOURCES tui/OutputFormatter.groovy
//SOURCES tui/JexerTUI.groovy
//SOURCES tui/AutocompleteItem.groovy
//SOURCES tui/AutocompleteField.groovy
//SOURCES tui/AutocompletePopup.groovy
//SOURCES tui/FileProvider.groovy
//SOURCES tui/CommandProvider.groovy

// TUI sources (Lanterna based)
//SOURCES tui/LanternaTUI.groovy
//SOURCES tui/LanternaTheme.groovy
//SOURCES tui/ActivityLogPanel.groovy
//SOURCES tui/CommandInputPanel.groovy

// LSP sources
//SOURCES models/Diagnostic.groovy
//SOURCES core/RootDetector.groovy
//SOURCES core/JsonRpcHandler.groovy
//SOURCES core/LSPConfig.groovy
//SOURCES core/LSPServerRegistry.groovy
//SOURCES core/LSPClient.groovy
//SOURCES core/LSPManager.groovy
//SOURCES core/DiagnosticFormatter.groovy

import picocli.CommandLine
import commands.GlmCli

new CommandLine(new GlmCli()).execute(args)

