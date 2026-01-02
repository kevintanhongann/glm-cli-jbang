package tools

import core.SubagentPool
import core.AgentConfig
import tui.AnsiColors
import tui.OutputFormatter

class TaskTool implements Tool {
    private final SubagentPool pool

    TaskTool(SubagentPool pool) {
        this.pool = pool
    }

    @Override
    String getName() {
        return "task"
    }

    @Override
    String getDescription() {
        return "Launch a specialized subagent to handle a complex, multi-step task. " +
               "Use this when you need to delegate work that requires multiple tool calls " +
               "or independent exploration. Specify the agent type: 'explore' for " +
               "codebase exploration, 'plan' for read-only analysis, or 'build' for " +
               "full development work."
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                agent_type: [
                    type: "string",
                    enum: ["explore", "plan", "build", "general"],
                    description: "Type of subagent to launch"
                ],
                task: [
                    type: "string",
                    description: "Detailed task description for the subagent"
                ],
                max_turns: [
                    type: "integer",
                    description: "Maximum number of turns for this subagent (default depends on agent type)"
                ]
            ],
            required: ["agent_type", "task"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String agentType = args.get("agent_type")
            String task = args.get("task")

            if (!agentType || !task) {
                return "Error: agent_type and task are required"
            }

            AgentConfig config = switch(agentType) {
                case "explore" -> AgentConfig.explore()
                case "plan" -> AgentConfig.plan()
                case "build" -> AgentConfig.build()
                case "general" -> AgentConfig.general()
                default -> {
                    OutputFormatter.printWarning("Unknown agent type: ${agentType}, using 'general'")
                    yield AgentConfig.general()
                }
            }

            if (args.containsKey("max_turns")) {
                config.maxTurns = ((Number) args.get("max_turns")).intValue()
            }

            OutputFormatter.printSection("Subagent: ${AnsiColors.bold(agentType)}")
            OutputFormatter.printInfo(task)

            def agent = pool.createAgent(config)
            def result = agent.execute(task)

            OutputFormatter.printSuccess("Subagent completed in ${agent.turn} turns")

            return """**${agentType.toUpperCase()} AGENT RESULT:**

${result}
""".trim()

        } catch (Exception e) {
            return "Error executing subagent: ${e.message}"
        }
    }
}
