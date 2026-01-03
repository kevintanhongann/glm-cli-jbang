package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import core.Auth
import core.GlmClient
import core.Config
import core.SessionManager
import core.MessageStore
import core.ModelCatalog
import models.ChatRequest
import models.Message
import models.ChatResponse

@Command(name = "chat", description = "Start a chat session with GLM-4", mixinStandardHelpOptions = true)
class ChatCommand implements Runnable {

    @Option(names = ["-m", "--model"], description = "Model to use (default: opencode/big-pickle)")
    String model = "opencode/big-pickle"

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
    private String providerId
    private String modelId

    @Override
    void run() {
        Config config = Config.load()

        String modelToUse = model ?: config.behavior.defaultModel
        
        // Check if there are recent models and use the most recent one
        if (!model && config.behavior.recentModels && !config.behavior.recentModels.isEmpty()) {
            modelToUse = config.behavior.recentModels[0]
        }

        def parts = modelToUse.split("/", 2)
        if (parts.length == 2) {
            providerId = parts[0]
            modelId = parts[1]
        } else {
            println "Warning: Model format should be 'provider/model-id'. Using default provider 'opencode'."
            providerId = "opencode"
            modelId = parts[0]
        }

        def providerInfo = ModelCatalog.getProvider(providerId)
        if (!providerInfo) {
            println "Error: Unknown provider '${providerId}'"
            println "\nAvailable providers:"
            ModelCatalog.getProviders().each { id, info ->
                println "  ${id} - ${info.name}"
            }
            return
        }

        def authCredential = Auth.get(providerId)
        if (!authCredential) {
            println "Error: No credential found for provider '${providerId}'"
            println "  Use 'glm auth login ${providerId}' to authenticate"
            println "  Get your API key at: ${providerInfo.url}"
            return
        }

        client = new GlmClient(providerId)

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

        def modelInfo = ModelCatalog.getModel(modelToUse)
        def modelName = modelInfo?.name ?: modelId
        
        println "Model: ${providerId}/${modelId} (${modelName}) (Type 'exit' or 'quit' to stop)"

        // Update local model var for consistency
        this.model = modelId


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
        request.model = modelId
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
