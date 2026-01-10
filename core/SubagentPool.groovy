package core

import tools.Tool
import groovy.transform.Canonical
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class SubagentPool {
    private final GlmClient client
    private final List<Tool> allTools
    private final ExecutorService executor
    private final AtomicInteger agentId = new AtomicInteger(0)

    SubagentPool(GlmClient client, List<Tool> allTools) {
        this.client = client
        this.allTools = allTools
        this.executor = Executors.newCachedThreadPool()
    }

    Subagent createAgent(AgentConfig config) {
        return new Subagent(config, client, allTools)
    }

    List<SubagentResult> spawnAgents(List<AgentTask> tasks) {
        List<Future<SubagentResult>> futures = []

        tasks.each { task ->
            def future = executor.submit({
                def agent = createAgent(task.config)
                def startTime = System.currentTimeMillis()

                try {
                    SubagentOutput output = agent.execute(task.prompt)
                    def duration = output.duration

                    return new SubagentResult(
                        agentId: agentId.getAndIncrement(),
                        configName: task.config.name,
                        result: output.content,
                        history: agent.history,
                        duration: duration,
                        success: output.success
                    )
                } catch (Exception e) {
                    def duration = System.currentTimeMillis() - startTime
                    return new SubagentResult(
                        agentId: agentId.getAndIncrement(),
                        configName: task.config.name,
                        result: null,
                        history: agent.history,
                        duration: duration,
                        success: false,
                        error: e.message
                    )
                }
            } as Callable<SubagentResult>)

            futures.add(future)
        }

        def results = futures.collect { it.get() }
        return results
    }

    void shutdown() {
        executor.shutdown()
        executor.awaitTermination(60, TimeUnit.SECONDS)
    }

    static class AgentTask {
        AgentConfig config
        String prompt

        AgentTask(AgentConfig config, String prompt) {
            this.config = config
            this.prompt = prompt
        }
    }

    @Canonical
    static class SubagentResult {
        int agentId
        String configName
        String result
        List<models.Message> history
        long duration
        boolean success
        String error
    }
}
