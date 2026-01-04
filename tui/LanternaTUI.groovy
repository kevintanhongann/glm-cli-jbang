package tui

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.MouseCaptureMode
import core.Auth
import core.GlmClient
import core.Config
import core.AgentType
import core.AgentConfig
import core.AgentRegistry
import core.Instructions
import core.ModelCatalog
import core.SessionStatsManager
import core.TokenTracker
import core.LspManager as SidebarLspManager
import core.LSPManager as LspClientManager
import core.LSPClient
import models.ChatRequest
import models.ChatResponse
import models.Message
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool
import tools.WebSearchTool
import tools.CodeSearchTool
import tools.GrepTool
import tools.GlobTool
import tools.Tool
import rag.RAGPipeline
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.UUID
import tui.lanterna.widgets.ActivityLogPanel
import tui.lanterna.widgets.CommandInputPanel
import tui.lanterna.widgets.SidebarPanel
import tui.lanterna.widgets.ModelSelectionDialog
import tui.shared.CommandProvider

class LanternaTUI {

    private Screen screen
    private MultiWindowTextGUI textGUI
    private BasicWindow mainWindow
    private ActivityLogPanel activityLogPanel
    private CommandInputPanel commandInputPanel
    private Panel statusBar
    private Label scrollPositionLabel
    private Label agentSwitcherLabel
    private SidebarPanel sidebarPanel
    private boolean sidebarEnabled = true

    private String currentModel
    private String currentCwd
    private String providerId
    private String modelId
    private String sessionId
    private String apiKey
    private Config config
    private GlmClient client
    private ObjectMapper mapper = new ObjectMapper()
    private List<Map> tools = []
    private AgentRegistry agentRegistry
    private volatile boolean running = true
    private Thread sidebarRefreshThread
    private tui.lanterna.widgets.Tooltip activeTooltip

    LanternaTUI() throws Exception {
        this.currentCwd = System.getProperty('user.dir')
        this.agentRegistry = new AgentRegistry(AgentType.BUILD)

        // Set up LSP tracking callback
        LspClientManager.instance.setOnClientCreated { String serverId, LSPClient client, String root ->
            // Register with sidebar LSP manager
            SidebarLspManager.instance.registerLsp(sessionId, serverId, client, root)

            // Update diagnostics status based on initial diagnostics
            Thread.start {
                try {
                    // Wait a bit for diagnostics to arrive
                    Thread.sleep(500)

                    int diagCount = client.getTotalDiagnosticCount()
                    if (diagCount > 0) {
                        SidebarLspManager.instance.updateLspStatus(
                            sessionId,
                            serverId,
                            'connected',
                            "${diagCount} diagnostics"
                        )
                    }
                } catch (Exception e) {
                }
            }

            // Refresh sidebar to show new LSP server
            refreshSidebar()
        }
    }

    void start(String model = 'opencode/big-pickle', String cwd = null) {
        this.currentModel = model
        // Create a proper session in the database to satisfy FK constraints for token_stats
        this.sessionId = core.SessionManager.instance.createSession(
            currentCwd ?: System.getProperty('user.dir'),
            'BUILD',
            model
        )

        def parts = model.split('/', 2)
        if (parts.length == 2) {
            this.providerId = parts[0]
            this.modelId = parts[1]
        } else {
            System.err.println("Warning: Model format should be 'provider/model-id'. Using default provider 'opencode'.")
            this.providerId = 'opencode'
            this.modelId = parts[0]
        }

        this.currentCwd = cwd ?: System.getProperty('user.dir')

        if (!initClient()) {
            System.err.println('Failed to initialize client')
            return
        }

        try {
            screen = new DefaultTerminalFactory()
                .setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG)
                .createScreen()
            screen.startScreen()

            textGUI = new MultiWindowTextGUI(screen)
            textGUI.setEOFWhenNoWindows(true)

            setupMainWindow()
            setupUI()

            // Initialize LSP tracking by touching a file
            initializeLspTracking()

            // Start periodic sidebar refresh for LSP diagnostics
            startSidebarRefreshThread()

            textGUI.waitForWindowToClose(mainWindow)
        } catch (Exception e) {
            System.err.println("TUI Error: ${e.message}")
            e.printStackTrace()
        } finally {
            running = false
            core.SessionManager.instance?.shutdown()
            if (screen != null) {
                screen.stopScreen()
            }
        }
    }

    private void startSidebarRefreshThread() {
        sidebarRefreshThread = new Thread({
            while (running) {
                try {
                    Thread.sleep(2000) // Refresh every 2 seconds

                    if (sidebarPanel && sidebarPanel.getExpanded()) {
                        // Update LSP diagnostic counts and refresh sidebar
                        SidebarLspManager.instance.updateDiagnosticCounts(sessionId)

                        // Refresh sidebar on UI thread
                        if (textGUI && !textGUI.getGUIThread().isShutdown()) {
                            try {
                                textGUI.getGUIThread().invokeLater {
                                    refreshSidebar()
                                }
                            } catch (Exception e) {
                            // Ignore if UI is shutting down
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    // Thread was interrupted
                    break
                } catch (Exception e) {
                    // Log but don't crash
                    System.err.println("Sidebar refresh error: ${e.message}")
                }
            }
        })
        sidebarRefreshThread.setDaemon(true)
        sidebarRefreshThread.start()
    }

    /**
     * Initialize LSP tracking by touching a file in the working directory.
     * This triggers LSP manager to spawn servers for the project.
     */
    private void initializeLspTracking() {
        if (!LspClientManager.instance.isEnabled()) {
            return
        }

        Thread.start {
            try {
                // Touch a common file to trigger LSP server spawn
                // Try glm.groovy first, then README.md
                def filesToTouch = [
                    'glm.groovy',
                    'README.md',
                    'package.json',
                    'pom.xml',
                    'build.gradle'
                ]

                for (file in filesToTouch) {
                    def filePath = new File(currentCwd, file).absolutePath
                    if (new File(filePath).exists()) {
                        try {
                            LspClientManager.instance.touchFile(filePath, false)
                            break // Only need to touch one file
                        } catch (Exception e) {
                        // Try next file
                        }
                    }
                }
            } catch (Exception e) {
                // Don't fail TUI if LSP init fails
                System.err.println("LSP initialization error: ${e.message}")
            }
        }
    }

    private boolean initClient() {
        config = Config.load()

        def providerInfo = ModelCatalog.getProvider(providerId)
        if (!providerInfo) {
            System.err.println("Error: Unknown provider '${providerId}'")
            System.err.println("\nAvailable providers:")
            ModelCatalog.getProviders().each { id, info ->
                System.err.println("  ${id} - ${info.name}")
            }
            return false
        }

        def authCredential = Auth.get(providerId)
        if (!authCredential) {
            System.err.println("Error: No credential found for provider '${providerId}'")
            System.err.println("  Use 'glm auth login ${providerId}' to authenticate")
            System.err.println("  Get your API key at: ${providerInfo.url}")
            return false
        }

        try {
            client = new GlmClient(providerId)
        } catch (Exception e) {
            System.err.println("Error initializing client: ${e.message}")
            return false
        }

        // Create tools and set session ID for tracking
        def writeFileTool = new WriteFileTool()
        writeFileTool.setSessionId(sessionId)
        tools << new ReadFileTool()
        tools << writeFileTool
        tools << new ListFilesTool()
        tools << new GrepTool()
        tools << new GlobTool()

        if (config?.webSearch?.enabled) {
            tools << new WebSearchTool(apiKey)
        }

        if (config?.rag?.enabled) {
            try {
                def ragPipeline = new RAGPipeline(config.rag.cacheDir)
                tools << new CodeSearchTool(ragPipeline)
            } catch (Exception e) {
            }
        }

        return true
    }

    private void setupMainWindow() {
        mainWindow = new BasicWindow("GLM CLI - ${currentModel}")
        mainWindow.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))
    }

    private void setupUI() {
        Panel mainContainer = new Panel()
        mainContainer.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        // Content panel (left/center) - contains activity log, input, status bar
        Panel contentPanel = new Panel()
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        activityLogPanel = new ActivityLogPanel(textGUI)
        activityLogPanel.appendWelcomeMessage(currentModel)

        // Wire up scroll position updates to status bar
        activityLogPanel.setOnScrollPositionChanged { int currentLine, int totalLines ->
            updateScrollPosition(currentLine, totalLines)
        }

        def activityLogComponent = activityLogPanel.getTextBox().withBorder(Borders.singleLine('Activity Log'))
        activityLogComponent.setLayoutData(
            LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow)
        )
        contentPanel.addComponent(activityLogComponent)

        commandInputPanel = new CommandInputPanel(textGUI, this, currentCwd)
        def commandInputComponent = commandInputPanel.getTextBox().withBorder(Borders.singleLine('Command'))
        commandInputComponent.setLayoutData(
            LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.None)
        )
        contentPanel.addComponent(commandInputComponent)

        statusBar = createStatusBar()
        def statusBarComponent = statusBar.withBorder(Borders.singleLine())
        statusBarComponent.setLayoutData(
            LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.None)
        )
        contentPanel.addComponent(statusBarComponent)

        // Add content panel to container
        contentPanel.setLayoutData(
            LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow)
        )
        mainContainer.addComponent(contentPanel)

        // Sidebar (right) - optional
        // Auto-hide on small terminals (< 100 columns)
        try {
            int terminalWidth = screen.getTerminalSize().getColumns()
            if (sidebarEnabled && terminalWidth >= 100) {
                sidebarPanel = new SidebarPanel(textGUI, sessionId)
                sidebarPanel.setLayoutData(
                    LinearLayout.createLayoutData(LinearLayout.Alignment.Center, LinearLayout.GrowPolicy.None)
                )
                mainContainer.addComponent(sidebarPanel)
            }
        } catch (Exception e) {
        // Ignore terminal size check errors
        }

        mainWindow.setComponent(mainContainer)
        textGUI.addWindow(mainWindow)

        LanternaTheme.applyDarkTheme(textGUI)
        commandInputPanel.getTextBox().takeFocus()
    }

    private Panel createStatusBar() {
        Panel panel = new Panel()
        panel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        panel.addComponent(new Label("Model: ${currentModel}"))
        panel.addComponent(new Label('  |  '))
        panel.addComponent(new Label("Dir: ${Paths.get(currentCwd).fileName}"))
        panel.addComponent(new Label('  |  '))

        // Dynamic scroll position label
        scrollPositionLabel = new Label('')
        panel.addComponent(scrollPositionLabel)

        panel.addComponent(new Label('  |  '))
        panel.addComponent(new Label('Ctrl+S: Save Log'))
        panel.addComponent(new Label('  |  '))
        panel.addComponent(new Label('Ctrl+C: Exit'))
        panel.addComponent(new Label('  |  '))

        // Agent switcher indicator
        agentSwitcherLabel = new Label(agentRegistry.getCurrentAgentName())
        agentSwitcherLabel.setForegroundColor(LanternaTheme.getAgentBuildColor())
        panel.addComponent(agentSwitcherLabel)

        // Tab hint
        panel.addComponent(new Label(' (Tab/Shift+Tab to switch)'))

        // Sidebar hint
        if (sidebarPanel) {
            panel.addComponent(new Label('  |  '))
            panel.addComponent(new Label('/sidebar: Toggle'))
        }

        return panel
    }

    private void updateScrollPosition(int currentLine, int totalLines) {
        if (scrollPositionLabel != null) {
            if (currentLine < totalLines - 5) {
                scrollPositionLabel.setText("Line ${currentLine}/${totalLines}")
            } else {
                scrollPositionLabel.setText('')
            }
        }
    }

    void cycleAgent(int direction = 1) {
        agentRegistry.cycleAgent(direction)
        updateAgentSwitcherIndicator()
        activityLogPanel.appendStatus("Switched to ${agentRegistry.getCurrentAgentName()} agent")
    }

    void toggleSidebar() {
        if (!sidebarPanel) return
        sidebarPanel.toggle()
        activityLogPanel.appendStatus(sidebarPanel.getExpanded() ? 'Sidebar shown' : 'Sidebar hidden')
    }

    void refreshSidebar() {
        if (sidebarPanel) {
            sidebarPanel.refresh()
        }
    }

    private void updateAgentSwitcherIndicator() {
        if (agentSwitcherLabel != null) {
            AgentType currentType = agentRegistry.getCurrentAgent()
            String agentText = currentType.toString()
            agentSwitcherLabel.setText(agentText)

            if (currentType == AgentType.BUILD) {
                agentSwitcherLabel.setForegroundColor(LanternaTheme.getAgentBuildColor())
            } else {
                agentSwitcherLabel.setForegroundColor(LanternaTheme.getAgentPlanColor())
            }
        }
    }

    void processUserInput(String input, List<String> mentions = []) {
        // Handle slash commands
        if (input.startsWith('/')) {
            handleSlashCommand(input)
            return
        }

        activityLogPanel.appendUserMessage(input)

        Thread.start {
            processInput(input, mentions)
        }
    }

    private void handleSlashCommand(String input) {
        def parsed = CommandProvider.parse(input)
        if (!parsed) {
            appendSystemMessage("Unknown command: ${input}")
            return
        }

        String command = parsed.name
        String args = parsed.arguments

        switch (command) {
            case 'models':
                // Defer dialog creation to avoid blocking in input filter context
                Thread.start {
                    showModelSelectionDialog()
                }
                break

            case 'model':
                if (args) {
                    switchModel(args)
                } else {
                    appendSystemMessage("Current model: ${currentModel}")
                }
                break

            case 'sidebar':
                toggleSidebar()
                break

            case 'help':
                showHelp()
                break

            case 'clear':
                activityLogPanel.clear()
                activityLogPanel.appendWelcomeMessage(currentModel)
                appendSystemMessage('Chat history cleared')
                break

            case 'exit':
                core.SessionManager.instance?.shutdown()
                mainWindow.close()
                break

            default:
                appendSystemMessage("Command '${command}' is not yet implemented")
        }
    }

    private void appendSystemMessage(String message) {
        activityLogPanel.getTextBox().getRenderer().addLine("ℹ️  ${message}")
    }

    void showModelSelectionDialog() {
        def dialog = new ModelSelectionDialog(textGUI)
        String selectedModel = dialog.show()

        if (selectedModel) {
            switchModel(selectedModel)
        }
    }

    private void switchModel(String newModel) {
        try {
            def parts = newModel.split('/', 2)
            if (parts.length != 2) {
                activityLogPanel.appendSystemMessage('Invalid model format. Expected: provider/model-id')
                return
            }

            String newProviderId = parts[0]
            String newModelId = parts[1]

            // Validate model exists
            def providerInfo = ModelCatalog.getProvider(newProviderId)
            if (!providerInfo) {
                activityLogPanel.appendSystemMessage("Unknown provider: ${newProviderId}")
                return
            }

            def modelInfo = ModelCatalog.getModel(newModel)
            if (!modelInfo) {
                activityLogPanel.appendSystemMessage("Unknown model: ${newModel}")
                return
            }

            // Check if provider is authenticated
            def authCredential = Auth.get(newProviderId)
            if (!authCredential) {
                activityLogPanel.appendSystemMessage("Not authenticated for provider '${newProviderId}'")
                activityLogPanel.appendSystemMessage("Run 'glm auth login ${newProviderId}' to authenticate")
                return
            }

            // Update model state
            this.currentModel = newModel
            this.providerId = newProviderId
            this.modelId = newModelId

            // Reinitialize client
            this.client = new GlmClient(newProviderId)

            // Update UI
            updateWindowAndStatusBar()

            activityLogPanel.appendSystemMessage("Switched to model: ${newModel}")
        } catch (Exception e) {
            activityLogPanel.appendSystemMessage("Error switching model: ${e.message}")
        }
    }

    private void updateWindowAndStatusBar() {
        // Update window title
        mainWindow.setTitle("GLM CLI - ${currentModel}")

        // Rebuild status bar by removing and re-adding all components
        statusBar.removeAllComponents()

        // Re-add all status bar components with updated model
        statusBar.addComponent(new Label("Model: ${currentModel}"))
        statusBar.addComponent(new Label('  |  '))
        statusBar.addComponent(new Label("Dir: ${Paths.get(currentCwd).fileName}"))
        statusBar.addComponent(new Label('  |  '))

        // Re-create scroll position label
        scrollPositionLabel = new Label('')
        statusBar.addComponent(scrollPositionLabel)

        statusBar.addComponent(new Label('  |  '))
        statusBar.addComponent(new Label('Ctrl+S: Save Log'))
        statusBar.addComponent(new Label('  |  '))
        statusBar.addComponent(new Label('Ctrl+C: Exit'))
        statusBar.addComponent(new Label('  |  '))

        // Re-create agent switcher indicator
        agentSwitcherLabel = new Label(agentRegistry.getCurrentAgentName())
        agentSwitcherLabel.setForegroundColor(LanternaTheme.getAgentBuildColor())
        statusBar.addComponent(agentSwitcherLabel)

        statusBar.addComponent(new Label(' (Tab/Shift+Tab to switch)'))
    }

    private void showHelp() {
        appendSystemMessage('=== Available Commands ===')
        appendSystemMessage('/models   - Open model selection dialog')
        appendSystemMessage('/model    - Show current model or switch to specific model')
        appendSystemMessage('/help     - Show this help message')
        appendSystemMessage('/clear    - Clear chat history')
        appendSystemMessage('/exit     - Exit TUI')
        appendSystemMessage('')
        appendSystemMessage('Keyboard shortcuts:')
        appendSystemMessage('Ctrl+M   - Open model selection dialog')
        appendSystemMessage('Tab      - Switch agent')
        appendSystemMessage('Ctrl+C   - Exit')
    }

    private void processInput(String userInput, List<String> mentions = []) {
        List<Message> messages = []

        // Load AGENTS.md instructions
        def customInstructions = Instructions.loadAll(currentCwd)
        customInstructions.each { instruction ->
            messages << new Message('system', instruction)
        }

        // Get agent config for current type
        AgentConfig agentConfig = agentRegistry.getCurrentAgentConfig()

        // Load agent-specific system prompt
        def promptFile = new File("prompts/${agentConfig.type.name().toLowerCase()}.txt")
        if (promptFile.exists()) {
            messages << new Message('system', promptFile.text)
        }

        messages << new Message('user', userInput)

        int maxIterations = 10
        int iteration = 0

        // Filter tools based on agent type
        List<Tool> allowedTools = []
        tools.each { toolMap ->
            def toolInstance = toolMap as Tool
            if (agentConfig.isToolAllowed(toolInstance.name)) {
                allowedTools << toolInstance
            }
        }

        while (iteration < maxIterations) {
            iteration++

            activityLogPanel.appendStatus('Thinking...')

            try {
                ChatRequest request = new ChatRequest()
                request.model = modelId
                request.messages = messages
                request.stream = false
                request.tools = allowedTools.collect { tool ->
                    [
                        type: 'function',
                        function: [
                            name: tool.name,
                            description: tool.description,
                            parameters: tool.parameters
                        ]
                    ]
                }

                String responseJson = client.sendMessage(request)
                ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)

                def choice = response.choices[0]
                def message = choice.message

                // Track token usage
                if (response.usage) {
                    int inputTokens = response.usage.promptTokens ?: 0
                    int outputTokens = response.usage.completionTokens ?: 0
                    BigDecimal cost = response.usage.cost ?: 0.0000

                    // Update in-memory and database
                    TokenTracker.instance.recordTokens(sessionId, inputTokens, outputTokens, cost)
                    SessionStatsManager.instance.updateTokenCount(sessionId, inputTokens, outputTokens, cost)

                    // Refresh sidebar to show updated token count
                    refreshSidebar()
                }

                activityLogPanel.removeStatus()

                if (message.content) {
                    activityLogPanel.appendAIResponse(message.content)
                    activityLogPanel.appendSeparator()
                }

                if (choice.finishReason == 'tool_calls' || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                    messages << message

                    message.toolCalls.each { toolCall ->
                        String functionName = toolCall.function.name
                        String arguments = toolCall.function.arguments
                        String callId = toolCall.id

                        Map<String, Object> args = mapper.readValue(arguments, Map.class)
                        String toolDisplay = formatToolCall(functionName, args)

                        activityLogPanel.appendToolExecution(toolDisplay)

                        def tool = tools.find { it.name == functionName }
                        String result = ''

                        if (tool) {
                            try {
                                Object output = tool.execute(args)
                                result = output.toString()
                                activityLogPanel.appendToolResult('Success')
                            } catch (Exception e) {
                                result = "Error: ${e.message}"
                                activityLogPanel.appendToolError(e.message)
                            }
                        } else {
                            result = 'Error: Tool not found'
                            activityLogPanel.appendToolError('Tool not found')
                        }

                        Message toolMsg = new Message()
                        toolMsg.role = 'tool'
                        toolMsg.content = result
                        toolMsg.toolCallId = callId
                        messages << toolMsg
                    }
                    activityLogPanel.appendSeparator()
                } else {
                    break
                }
            } catch (Exception e) {
                activityLogPanel.removeStatus()
                activityLogPanel.appendError(e.message)
                break
            }
        }

        if (iteration >= maxIterations) {
            activityLogPanel.appendWarning('Reached maximum iterations')
        }
    }

    private String formatToolCall(String name, Map args) {
        switch (name) {
            case 'read_file':
                return "Read ${args.path}"
            case 'write_file':
                return "Write ${args.path}"
            case 'list_files':
                return "List ${args.path ?: '.'}"
            case 'web_search':
                return "Search \"${truncate(args.query?.toString(), 30)}\""
            case 'code_search':
                return "CodeSearch \"${truncate(args.query?.toString(), 30)}\""
            case 'grep':
                return "Grep \"${truncate(args.pattern?.toString(), 30)}\""
            case 'glob':
                return "Glob \"${truncate(args.pattern?.toString(), 30)}\""
            default:
                return "${name}(${truncate(args.toString(), 40)})"
        }
    }

    private static String truncate(String s, int maxLen) {
        if (!s || s.length() <= maxLen) return s ?: ''
        return s.substring(0, maxLen - 3) + '...'
    }

}
