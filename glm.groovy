///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:1.10.0
//DEPS info.picocli:picocli:4.7.7
//DEPS info.picocli:picocli-groovy:4.7.7   
//DEPS dev.langchain4j:langchain4j-zhipu-ai:0.36.2

// JWT for ZhipuAI authentication
//DEPS com.auth0:java-jwt:4.4.0

// Jackson for config parsing
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.15.2

// RAG Dependencies
//DEPS dev.langchain4j:langchain4j-easy-rag:1.0.0-beta2
//DEPS dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.0.0-beta2

// TUI Dependencies  
//DEPS org.fusesource.jansi:jansi:2.4.1

// Core sources
// TODO: ZaiCodingPlanClient needs to be updated for new LangChain4j API
// //SOURCES core/ZaiCodingPlanClient.groovy
//SOURCES commands/GlmCli.groovy
//SOURCES commands/ChatCommand.groovy
//SOURCES commands/AgentCommand.groovy
//SOURCES models/WebSearchResponse.groovy
//SOURCES core/WebSearchClient.groovy
//SOURCES tools/WebSearchTool.groovy

// RAG sources
//SOURCES rag/CodebaseLoader.groovy
//SOURCES rag/CodeChunker.groovy
//SOURCES rag/EmbeddingService.groovy
//SOURCES rag/RAGPipeline.groovy
//SOURCES tools/CodeSearchTool.groovy

// TUI sources
//SOURCES tui/AnsiColors.groovy
//SOURCES tui/DiffRenderer.groovy
//SOURCES tui/InteractivePrompt.groovy
//SOURCES tui/ProgressIndicator.groovy
//SOURCES tui/OutputFormatter.groovy

import picocli.CommandLine
import commands.GlmCli

new CommandLine(new GlmCli()).execute(args)

