package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import core.Agent
import core.Config
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool

@Command(name = "agent", description = "Run an autonomous agent task", mixinStandardHelpOptions = true)
class AgentCommand implements Runnable {

    @Parameters(index = "0", description = "The task to perform")
    String task

    @Override
    void run() {
        Config config = Config.load()
        String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
        
        if (!apiKey) {
            System.err.println("Error: API Key not found. Set ZAI_API_KEY env var or configure ~/.glm/config.toml")
            return
        }

        Agent agent = new Agent(apiKey, "glm-4") // Default to glm-4 for agent, or config.behavior.defaultModel
        // Ideally agent uses a smarter model by default

        
        // Register standard tools
        agent.registerTool(new ReadFileTool())
        agent.registerTool(new WriteFileTool())
        agent.registerTool(new ListFilesTool())
        
        try {
            agent.run(task)
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
