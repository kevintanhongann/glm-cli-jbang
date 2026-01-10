package core

import models.ChatRequest
import models.Message
import core.GlmClient

class SummaryGenerator {
    private final GlmClient client
    private final String model

    SummaryGenerator(GlmClient client, String model = "zai/glm-4-flash") {
        this.client = client
        this.model = model
    }

    String generateSummary(List<Message> messages, int maxMessages = 10) {
        if (messages.size() <= maxMessages) {
            return "No compaction needed - conversation is within limits"
        }

        List<Message> recentMessages = messages.takeLast(maxMessages)
        StringBuilder context = new StringBuilder()
        context.append("Summarize the following conversation history into a concise paragraph (2-3 sentences):\n\n")

        recentMessages.each { msg ->
            context.append("[${msg.role.toUpperCase()}]\n")
            context.append(msg.content?.take(500) ?: "")
            context.append("\n\n")
        }

        String summaryPrompt = context.toString()

        try {
            ChatRequest request = new ChatRequest()
            request.model = model
            request.messages = [new Message("user", summaryPrompt)]
            request.maxTokens = 300

            String response = client.sendMessage(request)
            return extractSummary(response)
        } catch (Exception e) {
            return "Error generating summary: ${e.message}"
        }
    }

    private String extractSummary(String response) {
        def parsed = new groovy.json.JsonSlurper().parseText(response)
        return parsed.choices[0]?.message?.content ?: "Summary unavailable"
    }
}
