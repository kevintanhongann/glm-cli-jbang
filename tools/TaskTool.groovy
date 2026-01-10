package tools

import core.SubagentPool
import core.AgentConfig
import core.SubagentResultSynthesizer
import tui.shared.AnsiColors
import tui.shared.OutputFormatter

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
                ],
                parallel_tasks: [
                    type: "array",
                    items: [
                        type: "object",
                        properties: [
                            agent_type: [
                                type: "string",
                                enum: ["explore", "plan", "build", "general"]
                            ],
                            task: [
                                type: "string"
                            ],
                            max_turns: [
                                type: "integer"
                            ]
                        ],
                        required: ["agent_type", "task"]
                    ],
                    description: "Launch multiple subagents in parallel"
                ]
            ],
            required: []
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            if (args.containsKey("parallel_tasks")) {
                List taskDefs = args.get("parallel_tasks")
                return executeParallelTasks(taskDefs)
            }

            String agentType = args.get("agent_type")
            String task = args.get("task")

            if (!agentType || !task) {
                return "Error: agent_type and task are required (or use parallel_tasks)"
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
            def output = agent.execute(task)

            OutputFormatter.printSuccess("Subagent completed in ${agent.turn} turns")

            return """**${agentType.toUpperCase()} AGENT RESULT:**

${output.content}
""".trim()

        } catch (Exception e) {
            return "Error executing subagent: ${e.message}"
        }
    }

    private Object executeParallelTasks(List taskDefs) {
        OutputFormatter.printSection("Parallel Subagents")

        List<SubagentPool.AgentTask> tasks = []
        taskDefs.eachWithIndex { taskDef, idx ->
            String agentType = taskDef.agent_type
            String task = taskDef.task
            int maxTurns = taskDef.max_turns ?: -1

            AgentConfig config = AgentConfig.forName(agentType)
            if (maxTurns > 0) {
                config.maxTurns = maxTurns
            }

            tasks.add(new SubagentPool.AgentTask(config, task))

            String truncatedTask = task.length() > 60 ? task.take(60) + "..." : task
            println("  ${idx + 1}. ${AnsiColors.bold(agentType.toUpperCase())}: ${AnsiColors.cyan(truncatedTask)}")
        }

        println()
        OutputFormatter.printInfo("Executing ${tasks.size()} agents in parallel...")
        println()

        def results = pool.spawnAgents(tasks)

        OutputFormatter.printSeparator()

        results.eachWithIndex { result, idx ->
            String icon = result.success ? "✓" : "✗"
            String type = tasks[idx].config.name.toUpperCase()
            String color = result.success ? "green" : "red"

            println("${AnsiColors[color](icon)} ${AnsiColors.bold(type)} agent (${result.duration}ms)")

            if (result.history) {
                int turns = result.history.size() / 2
                println("   Turns: ${turns}")
            }

            if (result.result) {
                String truncated = result.result.length() > 150 ? result.result.take(150) + "..." : result.result
                println("   ${AnsiColors.dim(truncated)}")
            }

            if (result.error) {
                println("   ${AnsiColors.red('Error:')} ${result.error}")
            }

            println()
        }

        def stats = SubagentResultSynthesizer.getSummaryStats(results)
        OutputFormatter.printSeparator()
        println("Summary:")
        println("  Total: ${stats.total} agents")
        println("  ${AnsiColors.green('Success:')} ${stats.success}")
        println("  ${AnsiColors.red('Failed:')} ${stats.failed}")
        println("  Total duration: ${stats.totalDuration}ms")
        println("  Avg duration: ${String.format('%.0f', stats.avgDuration)}ms")
        println("  Total turns: ${stats.totalTurns}")
        println()

        OutputFormatter.printSuccess("All subagents completed")

        return SubagentResultSynthesizer.formatForLLM(results)
    }
}
