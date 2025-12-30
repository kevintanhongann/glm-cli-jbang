// Example: Z.AI Coding Plan Integration
// 
// This example demonstrates how to use Z.AI Coding Plan (zai-coding-plan)
// with GLM-CLI. It showcases:
// 1. Enhanced context (204,800 tokens vs 131,072)
// 2. Thinking mode (chain of thought)
// 3. Tool calling with web search
// 4. Zero token cost for input/output

package examples

import core.Config
import core.ZaiCodingPlanClient
import dev.langchain4j.model.chat.*
import dev.langchain4j.model.tool.*

@groovy.transform.CompileStatic
class ZaiCodingPlanExample {

    static void main(String[] args) {
        println """
        === Z.AI Coding Plan Integration Example ===
        
        Z.AI Coding Plan provides:
        - Extended context: 204,800 tokens (56% larger than standard GLM-4)
        - Thinking mode: Chain of thought reasoning
        - Tool calling: Up to 128 functions with web search
        - Zero token cost: Input/output tokens are FREE with subscription
        - Web search: Built-in results with titles, links, and icons
        
        ========================================
        
        Example 1: Basic Chat
        Example 2: Chat with Thinking Mode
        Example 3: Tool Calling (Web Search)
        ========================================
        
        """
        
        Config config = Config.load()
        String apiKey = System.getenv("ZHIPU_API_KEY") ?: System.getenv("ZAI_API_KEY")
        
        if (!apiKey) {
            System.err.println("Error: ZHIPU_API_KEY or ZAI_API_KEY environment variable required")
            System.err.println("Get your key from: https://z.ai/manage-apikey/apikey-list")
            System.exit(1)
        }
        
        ZaiCodingPlanClient client = new ZaiCodingPlanClient(config)
        
        println "\n=== Example 1: Basic Chat ===\n"
        def messages = [
            new ChatMessage(ChatMessageRole.USER.value(), "What are the key features of Java 17?")
        ]
        def response = client.generate(messages)
        println "Response: ${response}\n"
        
        println "\n=== Example 2: Chat with Thinking Mode ===\n"
        println "Enabling thinking mode (chain of thought)..."
        messages = [
            new ChatMessage(ChatMessageRole.USER.value(), "Should I upgrade to Java 21 for a new Spring Boot project?")
        ]
        response = client.generate(messages)
        println "Response: ${response}\n"
        println "Note: The response includes 'reasoning_content' field with the AI's thinking process\n"
        
        println "\n=== Example 3: Tool Calling ===\n"
        println "Using tool: web_search to find information about latest Java version"
        messages = [
            new ChatMessage(ChatMessageRole.USER.value(), "What's new in Java 21?")
        ]
        
        def toolSpec = new ToolSpecification.builder()
            .name("web_search")
            .description("Search web for information")
            .parameters([
                new ToolParameter.builder()
                    .name("query")
                    .type("string")
                    .description("Search query")
                    .build()
            ])
            .build()
        
        response = client.generate(messages, null, [toolSpec])
        println "Response: ${response}\n"
        println "Note: Web search results are included in the response\n"
        
        println "\n=== Configuration Options ===\n"
        println "To use Z.AI Coding Plan, set provider='zai-coding-plan'"
        println "Or add to config.toml:"
        println """
            [features]
            provider = "zai-coding-plan"
            
            [zai_coding_plan]
            model = "glm-4.7"
            enable_thinking = true
            enable_tools = true
            stream = true
            temperature = 0.7
        """
        
        println "\n=== Pricing ===\n"
        println "Plan     | Price       | Usage Limits"
        println "---------|------------|------------------|"
        println "Lite (\$3/mo) | ~120 prompts/5hrs | 3× GLM quota"
        println "Pro (\$15/mo) | ~600 prompts/5hrs | 3× GLM quota"
        println "Max (\$?)     | ~2400 prompts/5hrs | 20× GLM quota"
        println "\nZero token cost for input/output with any plan!"
        
        println "\n=== Documentation ===\n"
        println "https://docs.z.ai/devpack/overview"
        println "https://docs.z.ai/api-reference/llm/chat-completion"
    }
}
