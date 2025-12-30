///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.langchain4j:langchain4j-community-zhipu-ai:1.10.0-beta18
//DEPS dev.langchain4j:langchain4j-agentic:1.10.0-beta18
//DEPS dev.langchain4j:langchain4j-embeddings:*:1.10.0-beta18
//DEPS dev.langchain4j:langchain4j-document-loaders:*:1.10.0-beta18

//SOURCES core/ZaiCodingPlanClient.groovy
//SOURCES commands/GlmCli.groovy
//SOURCES commands/ChatCommand.groovy
//SOURCES commands/AgentCommand.groovy

package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.Config
import core.ZaiCodingPlanClient

@Command(name = "glm", mixinStandardHelpOptions = true, version = "glm-cli 0.1",
        subcommands = [ChatCommand.class, AgentCommand.class])
class GlmCli implements Runnable {

    @Option(names = ["-m", "--model"], description = "Model to use (default: glm-4-flash)", defaultValue = "glm-4-flash")
    @Option(names = ["-p", "--provider"], description = "Provider: glm (custom), langchain4j (Zhipu AI), or zai-coding-plan (Z.AI GLM-4.7)", defaultValue = "glm")

    @Override
    void run() {
        Config config = Config.load()
        String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key
        String model = this.model
        String provider = this.provider
        
        if (provider == "langchain4j") {
            ZhipuAI chat
        } else if (provider == "zai-coding-plan") {
            try {
                ZaiCodingPlanClient client = new ZaiCodingPlanClient(config)
                def messages = [
                    new dev.langchain4j.model.chat.ChatMessage(
                        dev.langchain4j.model.chat.ChatMessageRole.SYSTEM.value(),
                        "You are a helpful coding assistant optimized for software development tasks."
                    ),
                    new dev.langchain4j.model.chat.ChatMessage(
                        dev.langchain4j.model.chat.ChatMessageRole.USER.value(),
                        prompt
                    )
                ]
                
                println "Response: ${client.generate(messages)}"
            } catch (Exception e) {
                System.err.println("Error: ${e.message}")
            }
        } else {
            println "Using GLM provider: ${provider}, model: ${model}"
        }
    }
}
