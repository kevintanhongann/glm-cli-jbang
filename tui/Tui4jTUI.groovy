package tui

import com.williamcallahan.tui4j.compat.bubbletea.*
import com.williamcallahan.tui4j.compat.bubbletea.message.*
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.*
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.join.HorizontalJoinDecorator
import com.williamcallahan.tui4j.compat.bubbletea.bubbles.spinner.SpinnerType
import com.williamcallahan.tui4j.compat.bubbletea.bubbles.spinner.Spinner
import com.williamcallahan.tui4j.compat.bubbletea.bubbles.textinput.TextInput
import core.GlmClient
import core.Config
import core.AgentRegistry
import core.AgentType
import core.SessionManager
import core.ModelCatalog
import tools.Tool
import tui.tui4j.Tui4jTheme
import tui.tui4j.messages.*
import tui.tui4j.commands.*
import java.util.UUID

class Tui4jTUI implements Model {

    // Application state
    private List<Map> conversationHistory = []
    private boolean loading = false
    private String statusMessage = 'Ready'
    private boolean sidebarVisible = true
    private String currentModel
    private String currentCwd
    private String sessionId
    private String providerId

    // Dependencies
    private GlmClient client
    private Config config
    private AgentRegistry agentRegistry
    private List<Tool> tools = []
    private java.util.concurrent.BlockingQueue<Message> messageQueue

    // Nested components (bubbles)
    private TextInput textInput
    private Spinner spinner
    private tui.tui4j.components.ConversationView conversationView
    private tui.tui4j.components.SidebarView sidebarView

    void start(String model = 'opencode/big-pickle', String workingDir = null) {
        this.config = Config.load()
        this.currentModel = model ?: config.behavior.defaultModel
        this.currentCwd = workingDir ?: System.getProperty('user.dir')

        def parts = currentModel.split('/', 2)
        if (parts.length == 2) {
            this.providerId = parts[0]
        } else {
            this.providerId = 'opencode'
        }

        // Create session
        this.sessionId = SessionManager.instance.createSession(
            currentCwd,
            'BUILD',
            currentModel
        )

        this.client = new GlmClient(providerId)
        this.agentRegistry = new AgentRegistry(AgentType.BUILD)
        this.messageQueue = new java.util.concurrent.LinkedBlockingQueue<Message>()

        // Initialize nested bubbles
        this.textInput = new TextInput()
        textInput.setPlaceholder('Enter your message...')
        textInput.setCharLimit(10000)
        textInput.focus()

        this.spinner = new Spinner(SpinnerType.DOT)
        this.conversationView = new tui.tui4j.components.ConversationView()
        this.sidebarView = new tui.tui4j.components.SidebarView(sessionId)

        // Run program
        def program = new Program(this)
            .withAltScreen()
            .withMouseAllMotion()
            .withReportFocus()

        program.run()
    }

    @Override
    Command init() {
        return Command.batch(
            textInput.init(),
            spinner.init(),
            conversationView.init(),
            sidebarView.init(),
            new InitializeToolsCommand(sessionId, providerId),
            () -> new StatusMessage("Ready. Model: ${currentModel}")
        )
    }

    @Override
    UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPressMessage) {
            KeyPressMessage key = (KeyPressMessage) msg
            switch (key.key()) {
                case 'ctrl+c':
                case 'esc':
                    return UpdateResult.from(this, QuitMessage::new)
                case 'ctrl+s':
                    this.sidebarVisible = !sidebarVisible
                    return UpdateResult.from(this)
                case 'tab':
                    agentRegistry.cycleAgent(1)
                    statusMessage = "Switched to ${agentRegistry.getCurrentAgentName()} agent"
                    return UpdateResult.from(this)
                case 'enter':
                    if (!loading && textInput.value().trim()) {
                        return sendMessage()
                    }
                    break
            }
        }

        if (msg instanceof ChatResponseMessage) {
            return handleChatResponse((ChatResponseMessage) msg)
        }

        if (msg instanceof ToolResultMessage) {
            return handleToolResult((ToolResultMessage) msg)
        }

        if (msg instanceof StreamChunkMessage) {
            return handleStreamChunk((StreamChunkMessage) msg)
        }

        if (msg instanceof ToolsInitializedMessage) {
            this.tools = ((ToolsInitializedMessage) msg).tools()
            return UpdateResult.from(this)
        }

        if (msg instanceof ErrorMessage) {
            return handleError((ErrorMessage) msg)
        }

        if (msg instanceof StatusMessage) {
            this.statusMessage = ((StatusMessage) msg).text()
            return UpdateResult.from(this)
        }

        if (msg instanceof TickMessage) {
            if (loading) {
                def spinnerResult = spinner.update(msg)
                this.spinner = spinnerResult.model()
                return UpdateResult.from(this, spinnerResult.command())
            }
        }

        // Forward to nested components
        def inputResult = textInput.update(msg)
        this.textInput = inputResult.model()

        def conversationResult = conversationView.update(msg)
        this.conversationView = conversationResult.model()

        def sidebarResult = sidebarView.update(msg)
        this.sidebarView = sidebarResult.model()

        return UpdateResult.from(this, Command.batch(
            inputResult.command(),
            conversationResult.command(),
            sidebarResult.command()
        ))
    }

    private UpdateResult<Tui4jTUI> sendMessage() {
        String userMessage = textInput.value().trim()
        conversationHistory << [role: 'user', content: userMessage]
        conversationView.setMessages(conversationHistory)
        textInput.setValue('')
        this.loading = true
        statusMessage = 'Sending...'

        Command sendCmd = new SendChatCommand(
            client,
            conversationHistory,
            [model: currentModel, behavior: config.behavior],
            sessionId,
            currentCwd,
            agentRegistry,
            tools
        )

        return UpdateResult.from(this, Command.batch(
            sendCmd,
            Command.tick(java.time.Duration.ofMillis(100), { t -> new TickMessage() })
        ))
    }

    private UpdateResult<Tui4jTUI> handleChatResponse(ChatResponseMessage msg) {
        this.loading = false

        def metadata = msg.metadata() as Map
        def toolCalls = msg.toolCalls()

        if (toolCalls && !toolCalls.isEmpty()) {
            conversationHistory << [role: 'assistant', content: msg.content()]
            conversationView.setMessages(conversationHistory)

            Command toolCmd = new ExecuteToolCommand(toolCalls, tools, sessionId)
            return UpdateResult.from(this, toolCmd)
        } else {
            conversationHistory << [role: 'assistant', content: msg.content()]
            conversationView.setMessages(conversationHistory)

            if (metadata?.usage) {
                def usage = metadata.usage as Map
                def promptTokens = usage.promptTokens ?: 0
                def completionTokens = usage.completionTokens ?: 0
                statusMessage = "Tokens: ${promptTokens} + ${completionTokens} = ${promptTokens + completionTokens}"
            } else {
                statusMessage = 'Response complete'
            }

            return UpdateResult.from(this)
        }
    }

    private UpdateResult<Tui4jTUI> handleToolResult(ToolResultMessage msg) {
        def allResults = msg.allResults()

        for (result in allResults) {
            if (result.error) {
                statusMessage = "Tool error: ${result.content}"
            } else {
                conversationHistory << [role: 'tool', content: result.content]
            }
        }

        conversationView.setMessages(conversationHistory)

        def refreshCmd = new RefreshSidebarCommand(sessionId)
        return UpdateResult.from(this, refreshCmd)
    }

    private UpdateResult<Tui4jTUI> handleStreamChunk(StreamChunkMessage msg) {
        if (!msg.isComplete()) {
            def chunk = msg.chunk()
            if (conversationHistory.isEmpty() || conversationHistory[-1].role != 'assistant') {
                conversationHistory << [role: 'assistant', content: '']
            }
            conversationHistory[-1].content += chunk
            conversationView.setMessages(conversationHistory)
        } else {
            this.loading = false
            statusMessage = 'Response complete'
        }
        return UpdateResult.from(this)
    }

    private UpdateResult<Tui4jTUI> handleError(ErrorMessage msg) {
        this.loading = false
        statusMessage = "Error: ${msg.error()}"
        conversationHistory << [role: 'system', content: "❌ ${msg.error()}"]
        conversationView.setMessages(conversationHistory)
        return UpdateResult.from(this)
    }

    @Override
    String view() {
        def theme = Tui4jTheme.instance
        def mainContent = renderMainContent(theme)

        if (sidebarVisible) {
            String sidebar = sidebarView.view()
            return HorizontalJoinDecorator.joinHorizontal(Position.Top, sidebar, mainContent)
        }
        return mainContent
    }

    private String renderMainContent(Tui4jTheme theme) {
        def sb = new StringBuilder()
        sb.append(theme.headerStyle.render(" GLM-CLI (${currentModel}) ")).append("\n\n")
        sb.append(conversationView.view())

        if (loading) {
            sb.append(spinner.view()).append(' Thinking...')
        } else {
            sb.append(theme.promptStyle.render('❯ ')).append(textInput.view())
        }
        sb.append("\n\n").append(theme.statusStyle.render(statusMessage))
        return sb.toString()
    }

}
