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
import tui.tui4j.Tui4jTheme
import tui.tui4j.messages.*
import tui.tui4j.commands.*

class Tui4jTUI implements Model {

    // Application state
    private List<Map> conversationHistory = []
    private boolean loading = false
    private String statusMessage = ''
    private boolean sidebarVisible = true

    // Dependencies
    private GlmClient client
    private Config config
    private String apiKey

    // Nested components (bubbles)
    private TextInput textInput
    private Spinner spinner
    private tui.tui4j.components.ConversationView conversationView
    private tui.tui4j.components.SidebarView sidebarView

    void start(String model, String workingDir) {
        this.config = Config.load()
        if (model) {
            config.behavior.defaultModel = model
        }

        String providerId = 'opencode'
        def parts = config.behavior.defaultModel.split('/', 2)
        if (parts.length == 2) {
            providerId = parts[0]
        }

        this.client = new GlmClient(providerId)

        // Initialize nested bubbles
        this.textInput = new TextInput()
        textInput.setPlaceholder('Enter your message...')
        textInput.setCharLimit(10000)
        textInput.focus()

        this.spinner = new Spinner(SpinnerType.DOT)
        this.conversationView = new tui.tui4j.components.ConversationView()
        this.sidebarView = new tui.tui4j.components.SidebarView()

        // Run the program
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
            () -> new StatusMessage("Ready. Model: ${ config.behavior.defaultModel }")
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
        if (msg instanceof ErrorMessage) {
            return handleError((ErrorMessage) msg)
        }
        if (msg instanceof StatusMessage) {
            this.statusMessage = ((StatusMessage) msg).text()
            return UpdateResult.from(this)
        }

        // Forward to nested components
        def inputResult = textInput.update(msg)
        this.textInput = inputResult.model()

        def conversationResult = conversationView.update(msg)
        this.conversationView = conversationResult.model()

        def sidebarResult = sidebarView.update(msg)
        this.sidebarView = sidebarResult.model()

        def spinnerResult = spinner.update(msg)
        this.spinner = spinnerResult.model()

        return UpdateResult.from(this, Command.batch(
            inputResult.command(),
            conversationResult.command(),
            sidebarResult.command(),
            spinnerResult.command()
        ))
    }

    private UpdateResult<Tui4jTUI> sendMessage() {
        String userMessage = textInput.value().trim()
        conversationHistory << [role: 'user', content: userMessage]
        conversationView.setMessages(conversationHistory)
        textInput.setValue('')
        this.loading = true

        Command sendCmd = new SendChatCommand(client, conversationHistory, config)
        return UpdateResult.from(this, sendCmd)
    }

    private UpdateResult<Tui4jTUI> handleChatResponse(ChatResponseMessage msg) {
        this.loading = false
        conversationHistory << [role: 'assistant', content: msg.content()]
        conversationView.setMessages(conversationHistory)
        return UpdateResult.from(this)
    }

    private UpdateResult<Tui4jTUI> handleError(ErrorMessage msg) {
        this.loading = false
        this.statusMessage = "Error: ${msg.error()}"
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
        sb.append(theme.headerStyle.render(' GLM-CLI (TUI4J) ')).append("\n\n")
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
