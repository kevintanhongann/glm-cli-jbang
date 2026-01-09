package glm.core

class AgentState {
    static final String CURRENT_STEP = "agent.currentStep"
    static final String MAX_STEPS = "agent.maxSteps"
    static final String IS_RUNNING = "agent.isRunning"
    static final String CURRENT_TOOL = "agent.currentTool"
    static final String PROGRESS = "agent.progress"
    static final String TOKENS_USED = "agent.tokensUsed"

    static ReactiveState<Integer> currentStep
    static ReactiveState<Integer> maxSteps
    static ReactiveState<Boolean> isRunning
    static ReactiveState<String> currentTool
    static ReactiveState<Double> progress
    static ReactiveState<Map<String, Integer>> tokensUsed

    static void initialize() {
        currentStep = StateRegistry.instance.register(CURRENT_STEP, 0)
        maxSteps = StateRegistry.instance.register(MAX_STEPS, 25)
        isRunning = StateRegistry.instance.register(IS_RUNNING, false)
        currentTool = StateRegistry.instance.register(CURRENT_TOOL, "")
        progress = StateRegistry.instance.register(PROGRESS, 0.0)
        tokensUsed = StateRegistry.instance.register(TOKENS_USED, [input: 0, output: 0])
    }

    static void reset() {
        currentStep.set(0)
        isRunning.set(false)
        currentTool.set("")
        progress.set(0.0)
    }
}
