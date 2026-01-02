package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import core.Agent
import core.Auth
import core.Config
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool
import tools.GrepTool
import tools.GlobTool

@Command(name = "init", description = "Initialize or update AGENTS.md file", mixinStandardHelpOptions = true)
class InitCommand implements Runnable {

    @Option(names = ["--path"], description = "Path to project root (default: current directory)")
    String projectPath

    @Override
    void run() {
        def cwd = projectPath ?: System.getProperty("user.dir")
        def agentsFile = new File("${cwd}/AGENTS.md")
        def existingContent = agentsFile.exists() ? agentsFile.text : null

        def prompt = existingContent ?
            "Improve this AGENTS.md file:\n\n${existingContent}" :
            """Please analyze this codebase at ${cwd} and create an AGENTS.md file containing:
1. Build/lint/test commands - especially for running a single test
2. Code style guidelines including imports, formatting, types, naming conventions, error handling, etc.

The file you create will be given to agentic coding agents (such as yourself) that operate in this repository. Make it about 150 lines long.
If there are Cursor rules (in .cursor/rules/ or .cursorrules) or Copilot rules (in .github/copilot-instructions.md), make sure to include them."""

        Config config = Config.load()
        String apiKey = System.getenv("ZAI_API_KEY") ?: Auth.get("zai")?.key ?: config.api.key

        if (!apiKey) {
            System.err.println("Error: API Key required for /init command. Run 'glm auth login' or set ZAI_API_KEY environment variable.")
            return
        }

        println "Analyzing codebase and ${existingContent ? 'improving' : 'creating'} AGENTS.md..."

        Agent agent = new Agent(apiKey, "glm-4.7")
        agent.registerTool(new ReadFileTool())
        agent.registerTool(new ListFilesTool())
        agent.registerTool(new GrepTool())
        agent.registerTool(new GlobTool())
        agent.registerTool(new WriteFileTool())

        try {
            agent.run(prompt)
            println "\nAGENTS.md ${agentsFile.exists() ? 'updated' : 'created'} successfully at: ${agentsFile.absolutePath}"
        } finally {
            agent.shutdown()
        }
    }
}
