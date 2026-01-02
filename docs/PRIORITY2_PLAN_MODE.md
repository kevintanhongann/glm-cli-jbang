# Priority 2: Plan Mode Implementation Plan

## Overview

Implement an explicit planning phase before execution, allowing the agent to understand the codebase, create detailed plans, and get user approval before making changes.

## Status

- **Status:** Not Started
- **Priority:** High
- **Estimated Effort:** 6-8 days
- **Dependencies:** Priority 1 (Subagent Support) recommended but not required

---

## Problem Statement

Currently, GLM-CLI jumps directly from user request to execution. This leads to:
- No pre-execution exploration of the codebase
- No structured planning phase
- User cannot review approach before changes are made
- Higher risk of incorrect or incomplete implementations
- No way to understand tradeoffs before committing

OpenCode's Plan Mode solves this by:
1. Exploring the codebase with read-only agents
2. Creating detailed plans
3. Getting user approval on approach
4. Only then executing changes

---

## Design

### Plan Mode Workflow

```
User Request
    │
    ├─► Phase 1: Understanding
    │   ├─► Launch 1-3 Explore agents in parallel
    │   ├─► Each agent investigates different aspects
    │   └─► Synthesize findings
    │
    ├─► Phase 2: Planning
    │   ├─► Launch Plan agent
    │   ├─► Create detailed implementation plan
    │   └─► Identify critical files
    │
    ├─► Phase 3: Synthesis
    │   ├─► Review plan with user
    │   ├─► Ask clarifying questions
    │   └─► Get approval
    │
    ├─► Phase 4: Execution
    │   ├─► Execute approved plan
    │   ├─► Use Build agent with full tool access
    │   └─► Track progress
    │
    └─► Phase 5: Completion
        ├─► Summary of changes
        └─► Follow-up tasks
```

### Plan File Structure

```markdown
# Plan: [Task Title]

## Overview
[Brief description of what needs to be done]

## Approach
[Recommended approach with rationale]

## Key Findings
[Important discoveries from exploration]

## Critical Files
- `path/to/file1.groovy` - [Why it's important]
- `path/to/file2.groovy` - [Why it's important]

## Implementation Steps
1. [Step 1 description]
2. [Step 2 description]
3. [Step 3 description]

## Testing Strategy
[How to verify the implementation]

## Risks & Considerations
- [Risk 1]
- [Risk 2]

## Questions for User
- [Question 1]
- [Question 2]
```

---

## Implementation Plan

### Phase 1: Plan Mode Command Structure (Days 1-2)

#### 1.1 Create Plan Mode State

**File:** `core/PlanMode.groovy`

```groovy
package core

@Canonical
class PlanMode {
    boolean active = false
    int phase = 0  // 0=off, 1=understanding, 2=planning, 3=synthesis, 4=execution, 5=complete
    String currentTask = ""
    String planFilePath = ""
    List<String> keyFindings = []
    List<String> criticalFiles = []
    String proposedApproach = ""
    List<String> userQuestions = []
    boolean approved = false

    void start(String task) {
        this.active = true
        this.phase = 1
        this.currentTask = task
        this.approved = false
    }

    void exit() {
        this.active = false
        this.phase = 0
        reset()
    }

    void reset() {
        this.keyFindings = []
        this.criticalFiles = []
        this.proposedApproach = ""
        this.userQuestions = []
    }

    String getPhaseName() {
        switch(phase) {
            case 1: return "Understanding"
            case 2: return "Planning"
            case 3: return "Synthesis"
            case 4: return "Execution"
            case 5: return "Complete"
            default: return "Off"
        }
    }

    boolean isInteractive() {
        return phase in [1, 2, 3]
    }

    boolean canExecute() {
        return phase == 4 && approved
    }
}
```

#### 1.2 Add Plan Command

**File:** `commands/PlanCommand.groovy`

```groovy
package commands

import core.Agent
import core.Config
import core.PlanMode
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option

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

    void run() {
        def config = Config.load()
        agent = new Agent(config.api.key, config.model)

        // Create output directory if needed
        def outFile = new File(outputFile)
        outFile.parentFile?.mkdirs()

        // Start plan mode
        planMode.start(task)

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
        OutputFormatter.printInfo("Exploring the codebase to understand the task...")

        // Determine how many explore agents to launch
        def numAgents = determineAgentCount()
        def exploreTasks = createExploreTasks(numAgents)

        // Launch agents in parallel
        def pool = new SubagentPool(agent.client, agent.tools)
        def results = pool.spawnAgents(exploreTasks)

        // Synthesize findings
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

        // Launch plan agent
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

        // Write plan file
        writePlanFile()

        // Show plan to user
        println "\n${new File(planMode.planFilePath).text}\n"

        // Ask questions if any
        if (!planMode.userQuestions.isEmpty()) {
            OutputFormatter.printInfo("I have some questions about your preferences:")
            planMode.userQuestions.eachWithIndex { question, i ->
                def answer = InteractivePrompt.prompt("Q${i+1}: ${question}")
                // Store answers for later
            }
        }

        // Get approval
        if (InteractivePrompt.confirm("Does this plan look good?")) {
            planMode.approved = true
            planMode.phase = 4
            OutputFormatter.printSuccess("Plan approved! Proceeding to execution...")
        } else {
            OutputFormatter.printWarning("Plan not approved. Let me revise...")
            planMode.phase = 2  // Go back to planning
        }
    }

    private void phase4Execution() {
        OutputFormatter.printSection("Phase 4: Execution")

        // Execute the plan using build agent
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

        def buildAgent = new Agent(agent.client.apiKey, agent.model)
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
        // Simple heuristic: more complex tasks get more agents
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
            if (result.success) {
                // Extract findings from result
                def lines = result.result.split('\n')
                lines.each { line ->
                    if (line.startsWith('•') || line.startsWith('-')) {
                        planMode.keyFindings.add(line.replaceFirst(/^[•-]\s*/, ''))
                    }
                }

                // Extract file paths
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
```

### Phase 2: Agent Plan Prompts (Days 2-3)

#### 2.1 Enhanced Plan Agent Prompt

**File:** `prompts/plan.txt`

```
You are a code architecture and planning specialist. Your role is to analyze codebases and create detailed implementation plans.

Your strengths:
- Understanding code architecture and patterns
- Identifying dependencies and relationships
- Creating clear, actionable implementation plans
- Considering edge cases and potential issues

Guidelines for creating plans:

1. **Structure your plan:**
   - Overview (brief description)
   - Approach (recommended solution with rationale)
   - Key Files (files that need modification)
   - Implementation Steps (numbered, sequential)
   - Testing Strategy (how to verify)
   - Risks & Considerations (potential issues)

2. **Be specific:**
   - Include exact file paths
   - Specify function/method names
   - Mention concrete changes
   - Identify dependencies

3. **Consider tradeoffs:**
   - Multiple approaches? Compare them
   - Performance implications
   - Maintainability concerns
   - Breaking changes

4. **Ask questions:**
   - Ambiguous requirements
   - User preferences
   - Configuration choices
   - Edge cases

5. **Focus on clarity:**
   - Use clear, concise language
   - Avoid jargon when possible
   - Provide context for decisions
   - Make steps actionable

When asked to create a plan:
1. Read the task and key findings carefully
2. Explore the relevant parts of the codebase
3. Understand existing patterns and conventions
4. Consider multiple approaches
5. Create a detailed, actionable plan
6. List any questions for the user
```

### Phase 3: Integration (Days 3-4)

#### 3.1 Update GlmCli.groovy

```groovy
@Command(name = "plan", description = "Plan mode: explore and create plans before execution")
PlanCommand planCommand
```

#### 3.2 Update Agent.groovy

Add plan mode awareness:

```groovy
private final PlanMode planMode = new PlanMode()

boolean isInPlanMode() {
    return planMode.active
}

void setPlanMode(PlanMode mode) {
    // Allow external control of plan mode
}
```

### Phase 4: User Interaction (Days 4-5)

#### 4.1 Enhanced Interactive Prompts

**File:** `tui/PlanPrompt.groovy`

```groovy
package tui

import java.util.Scanner

class PlanPrompt {

    static boolean confirmPlan(String planFilePath) {
        println "\n${AnsiColors.bold("═" * 60)}"
        println AnsiColors.bold("PLAN REVIEW")
        println AnsiColors.bold("═" * 60) + "\n"

        // Read and display plan
        def planFile = new File(planFilePath)
        println planFile.text

        println "\n${AnsiColors.bold("═" * 60)}\n"

        while (true) {
            def choice = InteractivePrompt.select(
                "What would you like to do?",
                ["Approve and execute", "Revise the plan", "Cancel plan mode"]
            )

            switch (choice) {
                case 0:
                    return true  // Approve
                case 1:
                    return false  // Revise
                case 2:
                    throw new InterruptedException("Plan cancelled by user")
            }
        }
    }

    static String askRevision() {
        println "\n${AnsiColors.yellow("Please describe what needs to be revised in the plan:")}"
        def scanner = new Scanner(System.in)
        return scanner.nextLine()
    }

    static void showProgress(String message, int current, int total) {
        def percent = (current / total * 100).intValue()
        def bar = "█" * (percent / 5) + "░" * (20 - percent / 5)
        print "\r${AnsiColors.cyan("▶")} ${message} [${bar}] ${percent}%"
    }
}
```

### Phase 5: Testing (Days 6-8)

#### 5.1 Unit Tests

**File:** `tests/PlanModeTest.groovy`

```groovy
import core.PlanMode

class PlanModeTest {
    void testPlanModeLifecycle() {
        def planMode = new PlanMode()

        assert !planMode.active
        assert planMode.phase == 0

        planMode.start("Test task")

        assert planMode.active
        assert planMode.phase == 1
        assert planMode.currentTask == "Test task"

        planMode.exit()

        assert !planMode.active
        assert planMode.phase == 0
    }

    void testPhaseTransitions() {
        def planMode = new PlanMode()
        planMode.start("Test task")

        planMode.phase = 2
        assert planMode.phaseName == "Planning"

        planMode.phase = 3
        assert planMode.phaseName == "Synthesis"

        planMode.phase = 4
        assert planMode.canExecute()

        planMode.approved = true
        assert planMode.canExecute()
    }
}
```

#### 5.2 Integration Tests

```bash
# Test simple plan
./glm.groovy plan "Add a new tool for reading directory contents"

# Test complex plan with thoroughness
./glm.groovy plan --thoroughness "very thorough" "Refactor the tool system to support plugins"

# Test plan revision
./glm.groovy plan "Add unit tests for all tools" \
  # When prompted, select "Revise the plan"

# Test plan with custom output
./glm.groovy plan --output /tmp/my-plan.md "Analyze the agent architecture"
```

#### 5.3 Manual Testing Checklist

- [ ] Plan mode starts correctly
- [ ] Phase 1: Understanding launches correct number of explore agents
- [ ] Phase 2: Planning creates comprehensive plans
- [ ] Phase 3: Synthesis shows plan and asks questions
- [ ] User can approve or revise plan
- [ ] Phase 4: Execution follows the plan
- [ ] Phase 5: Complete shows summary
- [ ] Plan file is created and readable
- [ ] Interrupting plan mode works correctly
- [ ] Different thoroughness levels work

---

## Configuration

Add to `~/.glm/config.toml`:

```toml
[plan_mode]
enabled = true
default_thoroughness = "medium"
max_explore_agents = 3
plan_directory = ".opencode/plan"
auto_approve = false
```

---

## Success Criteria

- [ ] Plan mode command works from CLI
- [ ] Phase 1 launches 1-3 explore agents based on complexity
- [ ] Phase 2 creates detailed, actionable plans
- [ ] Phase 3 shows plan and asks relevant questions
- [ ] User can approve or request revisions
- [ ] Phase 4 executes plan step by step
- [ ] Phase 5 provides completion summary
- [ ] Plan file is created in correct format
- [ ] Integration tests pass
- [ ] Documentation updated

---

## Dependencies

- **Priority 1 (Subagent Support):** Recommended for parallel exploration, but can be implemented with sequential agents first

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| User finds plan mode too slow | Medium | Make it optional, allow "quick mode" |
| Plan quality varies | Medium | Improve prompts, add examples |
| Agents miss edge cases | High | Encourage user to review and ask questions |
| Execution diverges from plan | Medium | Track progress against plan steps |
| Complex tasks overwhelm plan agent | Low | Limit scope, encourage breaking into smaller tasks |

---

## Future Enhancements

- **Plan templates:** Pre-made plans for common tasks (add tool, refactor, etc.)
- **Plan versioning:** Track and compare multiple plan iterations
- **Plan execution metrics:** Track time vs. plan estimate
- **Auto-save plans:** Save all plans to a plan library
- **Plan sharing:** Share plans across team members
- **Visual plan diagrams:** Generate architecture diagrams from plans

---

## References

- OpenCode Plan Mode: `/home/kevintan/opencode/packages/opencode/src/session/prompt/plan-reminder-anthropic.txt`
- OpenCode Agents: `/home/kevintan/opencode/packages/opencode/src/agent/`
- Plan patterns in Claude Code and Amp

---

**Document Version:** 1.0
**Created:** 2025-01-02
**Priority:** High
