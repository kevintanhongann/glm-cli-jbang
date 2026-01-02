package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.Auth
import core.GlmClient
import core.Config
import core.SessionManager
import core.MessageStore
import models.ChatRequest
import models.Message
import models.ChatResponse

@Command(name = "chat", description = "Start a chat session with GLM-4", mixinStandardHelpOptions = true)
class ChatCommand implements Runnable {

    @Option(names = ["-m", "--model"], description = "Model to use (default: glm-4.7)")
    String model = "glm-4.7"

    @Option(names = ["-s", "--session"], description = "Resume existing session by ID")
    String sessionId

    @Option(names = ["-n", "--new"], description = "Start a new session even if one exists")
    boolean newSession = false

    @Parameters(index = "0", arity = "0..1", description = "Initial message")
    String initialMessage

    private List<Message> history = []
    private GlmClient client
    private SessionManager sessionManager
    private MessageStore messageStore
    private String currentSessionId

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

        // Initialize session management
        sessionManager = SessionManager.instance
        messageStore = new MessageStore()

        // Create or resume session
        if (sessionId && !newSession) {
            // Resume existing session
            def session = sessionManager.getSession(sessionId)
            if (!session) {
                println "Error: Session '${sessionId}' not found."
                return
            }
            if (session.isArchived) {
                println "Error: Session is archived. Unarchive it first with 'glm session unarchive ${sessionId}'"
                return
            }
            currentSessionId = sessionId

            // Load existing messages
            history.addAll(messageStore.getMessages(currentSessionId))
            println "Resuming session: ${session.title ?: currentSessionId} (${history.size()} messages)"
        } else {
            // Create new session
            currentSessionId = sessionManager.createSession(
                System.getProperty("user.dir"),
                "CHAT",
                modelToUse
            )
            println "Starting new session: ${currentSessionId}"
        }

        println "Model: ${modelToUse} (Type 'exit' or 'quit' to stop)"

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
        if (history.isEmpty()) {
            def customInstructions = core.Instructions.loadAll()
            customInstructions.each { instruction ->
                history.add(new Message("system", instruction))
            }
        }

        history.add(new Message("user", input))

        // Save user message to database
        messageStore.saveMessage(currentSessionId, new Message("user", input))
        sessionManager.touchSession(currentSessionId)

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

            def assistantMessage = new Message("assistant", fullResponse.toString())
            history.add(assistantMessage)

            // Save assistant message to database
            messageStore.saveMessage(currentSessionId, assistantMessage)
            sessionManager.touchSession(currentSessionId)

        } catch (Exception e) {
            System.err.println("\nError: ${e.message}")
        }
    }
}
