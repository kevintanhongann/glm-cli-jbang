package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import tools.*
import core.Config
import core.Auth
import core.ModelCatalog
import core.SkillRegistry
import rag.RAGPipeline

class InitializeToolsCommand implements Command {

    private final String sessionId
    private final String providerId
    private final Config config

    InitializeToolsCommand(String sessionId, String providerId) {
        this.sessionId = sessionId
        this.providerId = providerId
        this.config = Config.load()
    }

    @Override
    Message execute() {
        try {
            List<Tool> tools = []

            def writeFileTool = new WriteFileTool()
            writeFileTool.setSessionId(sessionId)
            tools << writeFileTool
            tools << new ReadFileTool()
            tools << new ListFilesTool()
            tools << new GrepTool()
            tools << new GlobTool()

            if (config?.webSearch?.enabled) {
                def authCredential = Auth.get(providerId)
                if (authCredential) {
                    tools << new WebSearchTool(authCredential.key)
                }
            }

            if (config?.rag?.enabled) {
                try {
                    def ragPipeline = new RAGPipeline(config.rag.cacheDir)
                    tools << new CodeSearchTool(ragPipeline)
                } catch (Exception e) {
                }
            }

            def skillRegistry = new SkillRegistry()
            tools << new SkillTool(skillRegistry)

            return new ToolsInitializedMessage(tools)

        } catch (Exception e) {
            return new ErrorMessage("Tool initialization error: ${e.message}", e)
        }
    }
}
