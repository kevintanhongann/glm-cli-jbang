package commands

import core.Agent
import core.Config
import core.PlanMode
import core.SubagentPool
import core.AgentConfig
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option
import tui.shared.AnsiColors
import tui.shared.OutputFormatter
import tui.shared.InteractivePrompt
import tui.shared.PlanPrompt
import core.SubagentPool.AgentTask

@Command(name = "plan", description = "Enter plan mode to explore codebase and create detailed plans before execution")
class PlanCommand implements Runnable {

    @Parameters(index = "0", description = "Task to plan for")
    String task

    @Option(names = ["--output", "-o"], description = "Plan file output path")
    String outputFile = ".opencode/plan/plan.md"

    @Option(names = ["--thoroughness"], description = "Exploration thoroughness: quick, medium, or very thorough")
    String thoroughness = "medium"

    private PlanMode planMode = new PlanMode()
    private Agent agent
    private Config config

    void run() {
        def config = Config.load()
        agent = new Agent(config.api.key, config.behavior.defaultModel)
        this.config = config

        def outFile = new File(outputFile)
        outFile.parentFile?.mkdirs()

        planMode.planFilePath = outputFile
        planMode.start(task)
        planMode.thoroughness = thoroughness

        println """
${AnsiColors.bold("╔══════════════════════════════════════════════════════════╗")}
${AnsiColors.bold("║")}${" PLAN MODE ".center(56)}${AnsiColors.bold("║")}
${AnsiColors.bold("╚══════════════════════════════════════════════════════════╝")}

${AnsiColors.cyan("Task:")} ${task}
${AnsiColors.cyan("Thoroughness:")} ${thoroughness}
${AnsiColors.cyan("Output:")} ${outputFile}

${AnsiColors.yellow("Plan mode will guide you through:")}
  ${AnsiColors.dim("1.")} Understanding the codebase
  ${AnsiColors.dim("2.")} Creating a detailed plan
  ${AnsiColors.dim("3.")} Reviewing with you
  ${AnsiColors.dim("4.")} Getting approval before execution
"""

        try {
            executePlanMode()
        } catch (InterruptedException e) {
            OutputFormatter.printWarning("Plan cancelled: ${e.message}")
        } finally {
            agent.shutdown()
        }
    }

    private void executePlanMode() {
        while (planMode.active) {
            switch(planMode.phase) {
                case 1:
                    phase1Understanding()
                    break
                case 2:
                    phase2Planning()
                    break
                case 3:
                    phase3Synthesis()
                    break
                case 4:
                    phase4Execution()
                    break
                case 5:
                    phase5Complete()
                    return
            }
        }
    }

    private void phase1Understanding() {
        OutputFormatter.printSection("Phase 1: Understanding")
        OutputFormatter.printInfo("Exploring the codebase to understand task...")

        def numAgents = determineAgentCount()
        def exploreTasks = createExploreTasks(numAgents)

        OutputFormatter.printInfo("Launching ${numAgents} explore agent(s)...")

        def pool = new SubagentPool(agent.client, agent.tools)
        def results = pool.spawnAgents(exploreTasks)

        synthesizeFindings(results)

        OutputFormatter.printSuccess("Understanding phase complete")
        OutputFormatter.printInfo("Key findings:")
        planMode.keyFindings.each { finding ->
            println "  • ${finding}"
        }

        planMode.phase = 2
    }

    private void phase2Planning() {
        OutputFormatter.printSection("Phase 2: Planning")

        def planTask = new AgentTask(
            AgentConfig.plan(),
            """
Based on the following task and key findings, create a detailed implementation plan:

Task: ${planMode.currentTask}

Key Findings:
${planMode.keyFindings.collect { "• ${it}" }.join('\n')}

Critical Files:
${planMode.criticalFiles.collect { "• ${it}" }.join('\n')}

Please create a comprehensive plan that includes:
1. Overview
2. Recommended approach with rationale
3. Key files to modify
4. Step-by-step implementation
5. Testing strategy
6. Risks and considerations
7. Any questions for the user
""".stripIndent()
        )

        def pool = new SubagentPool(agent.client, agent.tools)
        def results = pool.spawnAgents([planTask])

        planMode.proposedApproach = results[0].result

        OutputFormatter.printSuccess("Planning phase complete")

        planMode.phase = 3
    }

    private void phase3Synthesis() {
        OutputFormatter.printSection("Phase 3: Synthesis")

        writePlanFile()

        println "\n${new File(planMode.planFilePath).text}\n"

        if (!planMode.userQuestions.isEmpty()) {
            OutputFormatter.printInfo("I have some questions about your preferences:")
            planMode.userQuestions.eachWithIndex { question, i ->
                def answer = PlanPrompt.prompt("Q${i+1}: ${question}")
            }
        }

        if (PlanPrompt.confirmPlan(planMode.planFilePath)) {
            planMode.approved = true
            planMode.phase = 4
            OutputFormatter.printSuccess("Plan approved! Proceeding to execution...")
        } else {
            OutputFormatter.printWarning("Plan not approved. Let me revise...")
            planMode.phase = 2
        }
    }

    private void phase4Execution() {
        OutputFormatter.printSection("Phase 4: Execution")

        def buildTask = """
Execute the following plan step by step:

${planMode.proposedApproach}

Important:
- Follow the steps in order
- Report progress after each step
- Ask for clarification if needed
- Create backups before modifying files
- Test after major changes
""".stripIndent()

        def buildAgent = new Agent(config.api.key, agent.model ?: config.behavior.defaultModel)
        buildAgent.run(buildTask)

        planMode.phase = 5
    }

    private void phase5Complete() {
        OutputFormatter.printSection("Phase 5: Complete")

        OutputFormatter.printSuccess("Plan execution completed!")

        println """
${AnsiColors.bold("Summary:")}
${AnsiColors.dim("─" * 40)}

Task: ${planMode.currentTask}
Plan file: ${planMode.planFilePath}
Status: ${planMode.approved ? "✓ Approved and executed" : "✗ Not approved"}

${AnsiColors.cyan("Next steps:")}
- Review the changes
- Run tests
- Update documentation
"""

        planMode.exit()
    }

    private int determineAgentCount() {
        def complexity = planMode.currentTask.length()
        if (complexity < 100) return 1
        if (complexity < 300) return 2
        return 3
    }

    private List<AgentTask> createExploreTasks(int count) {
        def tasks = []

        if (count >= 1) {
            tasks.add(new AgentTask(
                AgentConfig.explore(),
                """
Explore the codebase for: ${planMode.currentTask}

Thoroughness level: ${planMode.thoroughness}

Focus on:
- Finding relevant files and code
- Understanding current implementation
- Identifying patterns and conventions
- Locating related components

Return:
- List of relevant files with brief descriptions
- Key code patterns discovered
- Architecture insights
- Any potential blockers
""".stripIndent()
            ))
        }

        if (count >= 2) {
            tasks.add(new AgentTask(
                AgentConfig.explore(),
                """
Investigate testing and documentation for: ${planMode.currentTask}

Focus on:
- Existing test files and patterns
- Documentation for related features
- Configuration files
- Dependencies and imports

Return:
- Test files found
- Documentation locations
- Key dependencies
- Testing conventions
""".stripIndent()
            ))
        }

        if (count >= 3) {
            tasks.add(new AgentTask(
                AgentConfig.explore(),
                """
Analyze the architecture and dependencies for: ${planMode.currentTask}

Focus on:
- Code structure and organization
- Module dependencies
- Configuration and settings
- External integrations

Return:
- Architecture overview
- Dependency graph (text representation)
- Configuration locations
- Integration points
""".stripIndent()
            ))
        }

        return tasks
    }

    private void synthesizeFindings(List results) {
        planMode.keyFindings = []
        planMode.criticalFiles = []

        results.each { result ->
            if (result.success && result.result) {
                def lines = result.result.split('\n')
                lines.each { line ->
                    if (line.startsWith('•') || line.startsWith('-')) {
                        planMode.keyFindings.add(line.replaceFirst(/^[•-]\s*/, ''))
                    }
                }

                def fileMatches = result.result =~ /`[^`]+\.\w+`/
                fileMatches.each { match ->
                    def filePath = match[0].replaceAll(/`/, '')
                    if (filePath.contains('.') && !planMode.criticalFiles.contains(filePath)) {
                        planMode.criticalFiles.add(filePath)
                    }
                }
            }
        }
    }

    private void writePlanFile() {
        def planContent = """
# Plan: ${planMode.currentTask}

## Overview
This plan outlines the approach for: ${planMode.currentTask}

## Approach
${planMode.proposedApproach}

## Key Findings
${planMode.keyFindings.collect { "- ${it}" }.join('\n')}

## Critical Files
${planMode.criticalFiles.collect { "- `${it}`" }.join('\n')}

## Implementation Steps
*To be filled by plan agent*

## Testing Strategy
*To be filled by plan agent*

## Risks & Considerations
*To be filled by plan agent*

## Questions for User
${planMode.userQuestions.collect { "- ${it}" }.join('\n')}

---
*Plan generated by GLM-CLI Plan Mode*
""".stripIndent()

        new File(planMode.planFilePath).text = planContent
    }
}
