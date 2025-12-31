package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.Auth
import core.GlmClient
import core.Config
import models.ChatRequest
import models.Message
import models.ChatResponse

@Command(name = "chat", description = "Start a chat session with GLM-4", mixinStandardHelpOptions = true)
class ChatCommand implements Runnable {

    @Option(names = ["-m", "--model"], description = "Model to use (default: glm-4.7)")
    String model = "glm-4.7"

    @Parameters(index = "0", arity = "0..1", description = "Initial message")
    String initialMessage

    private List<Message> history = []
    private GlmClient client

    @Override
    void run() {
        Config config = Config.load()
        
        // Priority: 1) env var, 2) auth.json (from `glm auth login`), 3) config.toml
        String apiKey = System.getenv("ZAI_API_KEY")
        if (!apiKey) {
            def authCredential = Auth.get("zai")
            apiKey = authCredential?.key
        }
        if (!apiKey) {
            apiKey = config.api.key
        }
        
        String modelToUse = model ?: config.behavior.defaultModel

        client = apiKey ? new GlmClient(apiKey) : new GlmClient()
        println "Starting chat with model: ${modelToUse} (Type 'exit' or 'quit' to stop)"

        // Update local model var for consistency
        this.model = modelToUse

        
        if (initialMessage) {
            processInput(initialMessage)
        }

        Scanner scanner = new Scanner(System.in)
        while (true) {
            print "\n> "
            if (!scanner.hasNextLine()) break
            String input = scanner.nextLine().trim()
            
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break
            }
            if (input.isEmpty()) continue

            processInput(input)
        }
    }

    private void processInput(String input) {
        history.add(new Message("user", input))
        
        ChatRequest request = new ChatRequest()
        request.model = model
        request.messages = history
        request.stream = true
        
        StringBuffer fullResponse = new StringBuffer()

        try {
            client.streamMessage(request) { ChatResponse chunk ->
                if (chunk.choices && !chunk.choices.isEmpty()) {
                    def delta = chunk.choices[0].delta
                    if (delta && delta.content) {
                        print delta.content
                        fullResponse.append(delta.content)
                        System.out.flush()
                    }
                }
            }
            println() // Newline after stream
            history.add(new Message("assistant", fullResponse.toString()))
            
        } catch (Exception e) {
            System.err.println("\nError: ${e.message}")
        }
    }
}
