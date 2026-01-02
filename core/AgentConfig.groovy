package core

import groovy.transform.Canonical
import tools.Tool

@Canonical
class AgentConfig {
    String name
    AgentType type
    String description
    List<String> allowedTools = []
    List<String> deniedTools = []
    int maxTurns = 10
    boolean hidden = false
    String model = "glm-4.7"
    Map<String, Object> options = [:]

    List<Tool> filterTools(List<Tool> availableTools) {
        return availableTools.findAll { tool ->
            def toolName = tool.name

            if (toolName in deniedTools) {
                return false
            }

            if (allowedTools.isEmpty()) {
                return true
            }

            return toolName in allowedTools
        }
    }

    boolean isToolAllowed(String toolName) {
        if (toolName in deniedTools) return false
        if (allowedTools.isEmpty()) return true
        return toolName in allowedTools
    }

    String loadPrompt() {
        def promptFile = new File("prompts/${type.name().toLowerCase()}.txt")
        if (promptFile.exists()) {
            return promptFile.text
        }
        return description
    }

    static AgentConfig build() {
        new AgentConfig(
            name: "build",
            type: AgentType.BUILD,
            description: "Full access agent for development work. Can read and write files.",
            maxTurns: 50
        )
    }

    static AgentConfig plan() {
        new AgentConfig(
            name: "plan",
            type: AgentType.PLAN,
            description: "Read-only agent for analysis and code exploration. Can only read files.",
            deniedTools: ["write_file", "edit_file"],
            maxTurns: 30
        )
    }

    static AgentConfig explore() {
        new AgentConfig(
            name: "explore",
            type: AgentType.EXPLORE,
            description: "Fast agent specialized for exploring codebases. Use glob and grep to find files and code patterns.",
            deniedTools: ["write_file", "edit_file", "todo_write", "todo_read"],
            allowedTools: ["read_file", "glob", "grep", "list_files"],
            maxTurns: 15,
            hidden: true
        )
    }

    static AgentConfig general() {
        new AgentConfig(
            name: "general",
            type: AgentType.GENERAL,
            description: "Multi-step task execution subagent.",
            deniedTools: ["todo_write", "todo_read"],
            maxTurns: 20,
            hidden: true
        )
    }

    static AgentConfig forType(AgentType type) {
        switch (type) {
            case AgentType.BUILD:
                return build()
            case AgentType.PLAN:
                return plan()
            case AgentType.EXPLORE:
                return explore()
            case AgentType.GENERAL:
                return general()
            default:
                return build()
        }
    }

    static AgentConfig forName(String name) {
        switch (name.toLowerCase()) {
            case "build":
                return build()
            case "plan":
                return plan()
            case "explore":
                return explore()
            case "general":
                return general()
            default:
                return build()
        }
    }

    static List<AgentType> getVisibleAgentTypes() {
        return AgentType.values().findAll { type ->
            !forType(type).hidden
        }
    }
}
