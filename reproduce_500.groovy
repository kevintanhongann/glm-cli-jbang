///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j:1.10.0
//DEPS io.gitlab.autumnmeowmeow:jexer:2.0.0
//DEPS info.picocli:picocli:4.7.7
//DEPS info.picocli:picocli-groovy:4.7.7
//DEPS dev.langchain4j:langchain4j-zhipu-ai:0.36.2
//DEPS org.apache.groovy:groovy-json:4.0.27
//DEPS org.apache.groovy:groovy-sql:4.0.27
//DEPS com.auth0:java-jwt:4.4.0
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.15.2
//DEPS io.github.java-diff-utils:java-diff-utils:4.12
//DEPS dev.langchain4j:langchain4j-easy-rag:1.0.0-beta2
//DEPS dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.0.0-beta2
//DEPS org.fusesource.jansi:jansi:2.4.1
//DEPS com.googlecode.lanterna:lanterna:3.1.2
//DEPS com.h2database:h2:2.2.224

//SOURCES core/GlmClient.groovy
//SOURCES core/Config.groovy
//SOURCES core/Auth.groovy
//SOURCES core/ModelCatalog.groovy
//SOURCES core/RipgrepHelper.groovy
//SOURCES core/JavaSearchFallback.groovy
//SOURCES core/Filesystem.groovy
//SOURCES models/*.groovy
//SOURCES tools/Tool.groovy
//SOURCES tools/ReadFileTool.groovy
//SOURCES tools/WriteFileTool.groovy
//SOURCES tools/ListFilesTool.groovy
//SOURCES tools/EditTool.groovy
//SOURCES tools/MultiEditTool.groovy
//SOURCES tools/PatchTool.groovy
//SOURCES tools/LSPTool.groovy
//SOURCES tools/GrepTool.groovy
//SOURCES tools/GlobTool.groovy
//SOURCES tools/BashTool.groovy
//SOURCES core/LSPManager.groovy
//SOURCES core/LSPClient.groovy
//SOURCES core/LSPServerRegistry.groovy
//SOURCES core/DiagnosticFormatter.groovy
//SOURCES core/JsonRpcHandler.groovy
//SOURCES core/RootDetector.groovy
//SOURCES core/LSPConfig.groovy
//SOURCES models/Diagnostic.groovy
//SOURCES core/SessionStats.groovy
//SOURCES core/SessionStatsManager.groovy
//SOURCES tui/AnsiColors.groovy
//SOURCES tui/OutputFormatter.groovy
//SOURCES tui/JexerTheme.groovy

import core.*
import models.*
import tools.*
import com.fasterxml.jackson.databind.ObjectMapper

class Reproducer {

    static void main(String[] args) {
        println 'Starting reproduction script...'

        Config config = Config.load()
        String apiKey = System.getenv('ZAI_API_KEY') ?: config.api.key

        if (!apiKey) {
            System.err.println('API Key not found!')
            System.exit(1)
        }

        println "API Key found (length: ${apiKey.length()})"

        // Pass explicit arguments to avoid ambiguity
        GlmClient client = new GlmClient(apiKey, null, 'jwt')

        def tools = [
            new ReadFileTool(),
            new WriteFileTool(),
            new ListFilesTool(),
            new EditTool(),
            new MultiEditTool(),
            new PatchTool(),
            new LSPTool(),
            new GrepTool(),
            new GlobTool(),
            new BashTool()
        ]

        println "Registered ${tools.size()} tools."

        ChatRequest req = new ChatRequest()
        req.model = 'glm-4.7'
        req.stream = false
        req.messages = [
            new Message('system', 'You are GLM-CLI...'),
            new Message('user', 'tell me more about the codebase')
        ]

        req.tools = tools.collect { tool ->
            [
                type: 'function',
                function: [
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.parameters
                ]
            ]
        }

        println "Sending request to ${req.model}..."

        try {
            String response = client.sendMessage(req)
            println 'Response: ' + response
        } catch (Exception e) {
            println 'Caught exception: ' + e.message
            e.printStackTrace()
        }
    }

}
