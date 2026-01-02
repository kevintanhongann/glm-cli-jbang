package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.Agent
import core.Auth
import core.Config
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool
import tools.WebSearchTool
import tools.CodeSearchTool
import tools.GrepTool
import tools.GlobTool
import rag.RAGPipeline

@Command(name = "agent", description = "Run an autonomous agent task", mixinStandardHelpOptions = true)
class AgentCommand implements Runnable {

    @Parameters(index = "0", description = "The task to perform")
    String task

    @Option(names = ["--index-codebase", "-i"], description = "Path to codebase to index for RAG semantic search")
    String codebasePath

    @Option(names = ["--rag"], description = "Enable RAG-based code search (requires prior indexing)")
    boolean enableRag = false

    @Override
    void run() {
        Config config = Config.load()
        
        // Priority: 1) env var, 2) auth.json (from `glm auth login`), 3) config.toml
        String apiKey = System.getenv("ZAI_API_KEY")
        if (!apiKey) {
            def authCredential = Auth.get("zai")
            apiKey = authCredential?.key
        }
        if (!apiKey) {
            apiKey = config.api.key
        }
        
        if (!apiKey) {
            System.err.println("Error: API Key not found. Run 'glm auth login', set ZAI_API_KEY env var, or configure ~/.glm/config.toml")
            return
        }

        Agent agent = new Agent(apiKey, "glm-4.7") // Default to glm-4 for agent, or config.behavior.defaultModel

        // Register standard tools
        agent.registerTool(new ReadFileTool())
        agent.registerTool(new WriteFileTool())
        agent.registerTool(new ListFilesTool())
        agent.registerTool(new GrepTool())
        agent.registerTool(new GlobTool())
        
        if (config.webSearch.enabled) {
            agent.registerTool(new WebSearchTool(apiKey))
        }
        
        // RAG integration
        RAGPipeline ragPipeline = null
        if (codebasePath || enableRag || config.rag.enabled) {
            ragPipeline = new RAGPipeline(config.rag.cacheDir)
            if (codebasePath) {
                println "Indexing codebase at: ${codebasePath}"
                ragPipeline.indexCodebase(codebasePath)
            }
            agent.registerTool(new CodeSearchTool(ragPipeline))
        }
        
        try {
            agent.run(task)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}

