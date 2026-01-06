package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import core.GlmClient
import models.ChatRequest
import models.Message as ChatMessage

class SendChatCommand implements Command {

    private final GlmClient client
    private final List<Map> history
    private final def config

    SendChatCommand(GlmClient client, List<Map> history, config) {
        this.client = client
        this.history = history
        this.config = config
    }

    @Override
    Message execute() {
        try {
            def messages = history.collect {
                new ChatMessage(role: it.role, content: it.content)
            }

            def request = new ChatRequest(
                model: config.model,
                messages: messages,
                stream: false
            )

            def response = client.chat(request)

            return new ChatResponseMessage(
                response.choices[0].message.content,
                response.choices[0].message.tool_calls,
                response.usage
            )
        } catch (Exception e) {
            return new ErrorMessage("API Error: ${e.message}", e)
        }
    }

}
