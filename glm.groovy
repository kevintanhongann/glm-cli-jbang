///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:1.10.0
//DEPS io.gitlab.autumnmeowmeow:jexer:2.0.0
//DEPS info.picocli:picocli:4.7.7
//DEPS info.picocli:picocli-groovy:4.7.7
//DEPS dev.langchain4j:langchain4j-zhipu-ai:0.36.2

// Groovy JSON module (required for JsonSlurper/JsonOutput in Groovy 4.x)
//DEPS org.apache.groovy:groovy-json:4.0.27

// Groovy SQL module for H2 database support
//DEPS org.apache.groovy:groovy-sql:4.0.27

// JWT for ZhipuAI authentication
//DEPS com.auth0:java-jwt:4.4.0

// Jackson for config parsing
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.15.2

// Java Diff Utils for patch operations
//DEPS io.github.java-diff-utils:java-diff-utils:4.12

// RAG Dependencies
//DEPS dev.langchain4j:langchain4j-easy-rag:1.0.0-beta2
//DEPS dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.0.0-beta2

// TUI Dependencies
//DEPS org.fusesource.jansi:jansi:2.4.1
//DEPS com.googlecode.lanterna:lanterna:3.1.2
//DEPS com.williamcallahan:tui4j:0.2.5

// H2 Database for session persistence
//DEPS com.h2database:h2:2.2.224

// Core sources
// TODO: ZaiCodingPlanClient needs to be updated for new LangChain4j API
// //SOURCES core/ZaiCodingPlanClient.groovy
//SOURCES commands/GlmCli.groovy
//SOURCES commands/ChatCommand.groovy
//SOURCES commands/AgentCommand.groovy
//SOURCES commands/AuthCommand.groovy
//SOURCES commands/SessionCommand.groovy
//SOURCES commands/ModelsCommand.groovy
//SOURCES commands/PlanCommand.groovy
//SOURCES commands/InitCommand.groovy
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
//SOURCES core/AgentRegistry.groovy
//SOURCES core/AgentConfig.groovy
//SOURCES core/AgentType.groovy
//SOURCES core/Instructions.groovy
//SOURCES core/SessionStats.groovy
//SOURCES core/SessionStatsManager.groovy
//SOURCES core/LspManager.groovy
//SOURCES core/LSPClient.groovy
//SOURCES core/ModelCatalog.groovy
//SOURCES core/TokenTracker.groovy
//SOURCES core/Subagent.groovy
//SOURCES core/SubagentPool.groovy
//SOURCES core/LSPManager.groovy
//SOURCES tools/ReadFileTool.groovy
//SOURCES tools/WriteFileTool.groovy
//SOURCES tools/ListFilesTool.groovy
//SOURCES tools/FetchUrlTool.groovy
//SOURCES tools/CodeSearchTool.groovy
//SOURCES tools/GrepTool.groovy
//SOURCES tools/GlobTool.groovy
//SOURCES tools/BatchTool.groovy
//SOURCES tools/Tool.groovy
//SOURCES tools/TaskTool.groovy

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
//SOURCES tools/BatchTool.groovy
//SOURCES tools/EditTool.groovy
//SOURCES tools/MultiEditTool.groovy
//SOURCES tools/PatchTool.groovy
//SOURCES tools/LSPTool.groovy

// Agent source
//SOURCES core/Agent.groovy
//SOURCES core/SessionManager.groovy
//SOURCES core/MessageStore.groovy
//SOURCES core/TokenTracker.groovy
//SOURCES core/FileTime.groovy
//SOURCES core/ParallelExecutor.groovy
//SOURCES core/ParallelProgressMonitor.groovy

// Model sources
//SOURCES models/Session.groovy
//SOURCES models/TokenStats.groovy

// TUI sources (Shared)
//SOURCES tui/shared/AnsiColors.groovy
//SOURCES tui/shared/DiffRenderer.groovy
//SOURCES tui/shared/InteractivePrompt.groovy
//SOURCES tui/shared/ProgressIndicator.groovy
//SOURCES tui/shared/OutputFormatter.groovy
//SOURCES tui/shared/AutocompleteItem.groovy
//SOURCES tui/shared/FileProvider.groovy
//SOURCES tui/shared/CommandProvider.groovy
//SOURCES tui/shared/PlanPrompt.groovy

// TUI sources (Jexer based)
//SOURCES tui/JexerTUI.groovy
// //SOURCES tui/JexerTUIEnhanced.groovy
//SOURCES tui/JexerTheme.groovy
//SOURCES tui/jexer/widgets/JexerActivityLog.groovy
//SOURCES tui/jexer/widgets/JexerCommandInput.groovy
//SOURCES tui/jexer/widgets/JexerAutocompletePopup.groovy
//SOURCES tui/jexer/widgets/JexerStatusBar.groovy
//SOURCES tui/jexer/widgets/JexerSidebar.groovy
//SOURCES tui/jexer/sidebar/JexerSessionInfoSection.groovy
//SOURCES tui/jexer/sidebar/JexerTokenSection.groovy
//SOURCES tui/jexer/sidebar/JexerLspSection.groovy
//SOURCES tui/jexer/sidebar/JexerModifiedFilesSection.groovy

// TUI sources (Lanterna based)
//SOURCES tui/LanternaTUI.groovy
//SOURCES tui/LanternaTheme.groovy
//SOURCES tui/lanterna/widgets/ActivityLogPanel.groovy
//SOURCES tui/lanterna/widgets/CommandInputPanel.groovy
//SOURCES tui/lanterna/widgets/LanternaAutocompletePopup.groovy
//SOURCES tui/lanterna/widgets/AutocompleteField.groovy
//SOURCES tui/lanterna/widgets/AutocompletePopup.groovy
//SOURCES tui/lanterna/widgets/SidebarPanel.groovy
//SOURCES tui/lanterna/widgets/ModelSelectionDialog.groovy
//SOURCES tui/lanterna/widgets/Tooltip.groovy
//SOURCES tui/lanterna/sidebar/SessionInfoSection.groovy
//SOURCES tui/lanterna/sidebar/TokenSection.groovy
//SOURCES tui/lanterna/sidebar/LspSection.groovy
//SOURCES tui/lanterna/sidebar/ModifiedFilesSection.groovy

// TUI sources (TUI4J based)
//SOURCES tui/Tui4jTUI.groovy
//SOURCES tui/tui4j/Tui4jTheme.groovy
//SOURCES tui/tui4j/messages/Messages.groovy
//SOURCES tui/tui4j/commands/SendChatCommand.groovy
//SOURCES tui/tui4j/commands/ExecuteToolCommand.groovy
//SOURCES tui/tui4j/components/ConversationView.groovy
//SOURCES tui/tui4j/components/SidebarView.groovy
//
//// LSP sources
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

