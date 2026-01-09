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
import core.SkillRegistry
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
import tools.SkillTool
import rag.RAGPipeline
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.Timer
import tui.lanterna.widgets.ActivityLogPanel
import tui.lanterna.widgets.CommandInputPanel
import tui.lanterna.widgets.SidebarPanel
import tui.lanterna.widgets.ModelSelectionDialog
import tui.lanterna.widgets.HeaderPanel
import tui.lanterna.widgets.FooterPanel
import tui.shared.CommandProvider

class LanternaTUI {

    private Screen screen
    private MultiWindowTextGUI textGUI
    private BasicWindow mainWindow
    private ActivityLogPanel activityLogPanel
    private CommandInputPanel commandInputPanel
    private HeaderPanel headerPanel
    private FooterPanel footerPanel
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

    // Cancellation support
    private java.util.concurrent.atomic.AtomicBoolean isGenerating = new java.util.concurrent.atomic.AtomicBoolean(false)
    private java.util.concurrent.atomic.AtomicBoolean stopRequested = new java.util.concurrent.atomic.AtomicBoolean(false)
    private List<Map> tools = []
    private AgentRegistry agentRegistry
    private SkillRegistry skillRegistry
    private volatile boolean running = true
    private Thread sidebarRefreshThread
    private tui.lanterna.widgets.Tooltip activeTooltip

    LanternaTUI() throws Exception {
        this.currentCwd = System.getProperty('user.dir')
        this.agentRegistry = new AgentRegistry(AgentType.BUILD)
        this.skillRegistry = new SkillRegistry()

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

            // Add global key interceptor for ESC
            mainWindow.addWindowListener(new WindowListenerAdapter() {
                @Override
                void onInput(Window basePane, com.googlecode.lanterna.input.KeyStroke keyStroke, java.util.concurrent.atomic.AtomicBoolean hasBeenHandled) {
                    if (keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape) {
                        if (isGenerating.get()) {
                            cancelGeneration()
                            hasBeenHandled.set(true)
                        }
                    }
                }
            })

            setupUI()

            setupResizeListener()
            initializeLspTracking()
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

        // Register skill tool
        tools << new SkillTool(skillRegistry)

        return true
    }

    private void setupMainWindow() {
        mainWindow = new BasicWindow("GLM CLI - ${currentModel}")
        mainWindow.setHints(Arrays.asList(
            Window.Hint.FULL_SCREEN,
            Window.Hint.NO_DECORATIONS,
            Window.Hint.FIT_TERMINAL_WINDOW
        ))
    }

    private void setupUI() {
        Panel mainContainer = new Panel()
        mainContainer.setLayoutManager(new BorderLayout())

        // Header at TOP
        headerPanel = new HeaderPanel("GLM CLI - ${currentModel}")
        mainContainer.addComponent(headerPanel, BorderLayout.Location.TOP)

        // Footer at BOTTOM
        footerPanel = new FooterPanel(currentCwd, agentRegistry.getCurrentAgentName(), 'GLM v1.0', currentModel)
        mainContainer.addComponent(footerPanel, BorderLayout.Location.BOTTOM)

        // Content panel in CENTER
        Panel contentPanel = new Panel()
        contentPanel.setLayoutManager(new BorderLayout())

        // Left panel (activity log + input) as CENTER of content
        Panel leftPanel = new Panel()
        leftPanel.setLayoutManager(new BorderLayout())

        activityLogPanel = new ActivityLogPanel(textGUI)
        activityLogPanel.appendWelcomeMessage(currentModel)
        leftPanel.addComponent(activityLogPanel.getTextBox(), BorderLayout.Location.CENTER)

        commandInputPanel = new CommandInputPanel(textGUI, this, currentCwd)
        leftPanel.addComponent(commandInputPanel.getTextBox(), BorderLayout.Location.BOTTOM)

        contentPanel.addComponent(leftPanel, BorderLayout.Location.CENTER)

        // Sidebar on RIGHT
        setupSidebar(contentPanel)

        mainContainer.addComponent(contentPanel, BorderLayout.Location.CENTER)
        mainWindow.setComponent(mainContainer)
        textGUI.addWindow(mainWindow)

        LanternaTheme.applyToPanel(headerPanel)
        LanternaTheme.applyToPanel(footerPanel)
        LanternaTheme.applyDarkTheme(textGUI)
        commandInputPanel.getTextBox().takeFocus()
    }

    private void setupSidebar(Panel contentPanel) {
        try {
            TerminalSize size = screen.getTerminalSize()
            int terminalWidth = size.getColumns()

            if (sidebarEnabled && terminalWidth >= 80) {
                int sidebarWidth = Math.min(30, terminalWidth / 4)
                sidebarPanel = new SidebarPanel(textGUI, sessionId, sidebarWidth)
                sidebarPanel.setPreferredSize(new TerminalSize(sidebarWidth, 0))

                contentPanel.addComponent(sidebarPanel, BorderLayout.Location.RIGHT)
            }
        } catch (Exception e) {
        }
    }

    private void handleResize(TerminalSize newSize) {
        try {
            int terminalWidth = newSize.getColumns()
            int terminalHeight = newSize.getRows()

            if (sidebarEnabled && terminalWidth >= 80) {
                if (sidebarPanel == null) {
                    Panel contentPanel = (Panel) mainWindow.getComponent().getChildren().get(1) // Header is 0, Content is 1
                    setupSidebar(contentPanel)
                } else {
                    int sidebarWidth = Math.min(30, terminalWidth / 4)
                    sidebarPanel.setPreferredSize(new TerminalSize(sidebarWidth, 0))
                }
            } else if (!sidebarEnabled && sidebarPanel != null) {
                sidebarPanel = null
            }

            if (activityLogPanel != null) {
                activityLogPanel.handleResize(terminalWidth, terminalHeight)
            }

            if (textGUI != null && textGUI.getGUIThread() != null) {
                textGUI.getGUIThread().invokeLater {
                    if (mainWindow != null) {
                        mainWindow.invalidate()
                    }
                    textGUI.updateScreen()
                }
            }
        } catch (Exception e) {
        }
    }

    private void setupResizeListener() {
        screen.doResizeIfNecessary()
        // Poll for resize or use callback if available
        Thread.start {
            while (running) {
                try {
                    TerminalSize newSize = screen.doResizeIfNecessary()
                    if (newSize != null) {
                        handleResize(newSize)
                    }
                    Thread.sleep(100)
                } catch (Exception e) {
                    break
                }
            }
        }
    }

    private void updateHeader(int inputTokens, int outputTokens, int percentage, BigDecimal cost, int lspCount) {
        if (headerPanel != null) {
            headerPanel.update(inputTokens, outputTokens, percentage, cost, lspCount)
        }
    }

    private void updateFooter() {
        if (footerPanel != null) {
            footerPanel.update(
                currentCwd,
                SidebarLspManager.instance.getConnectedLspCount(sessionId),
                SidebarLspManager.instance.getErrorLspCount(sessionId),
                0, 0,
                agentRegistry.getCurrentAgentName(),
                agentRegistry.getCurrentAgent() == AgentType.BUILD,
                currentModel
            )
        }
    }

    private void updateScrollPosition(int currentLine, int totalLines) {
    // Handled by FooterPanel now
    }

    private void updateStreamingIndicator(boolean isStreaming, String indicator) {
    // Handled by FooterPanel now
    }

    void cycleAgent(int direction = 1) {
        agentRegistry.cycleAgent(direction)
        activityLogPanel.appendStatus("Switched to ${agentRegistry.getCurrentAgentName()} agent")
        updateFooter()
    }

    void cancelGeneration() {
        if (isGenerating.get() && !stopRequested.get()) {
            stopRequested.set(true)
            activityLogPanel.appendStatus("Stopping generation...")
        }
    }

    void toggleSidebar() {
        if (!sidebarPanel) return
        sidebarPanel.toggle()
        activityLogPanel.appendStatus(sidebarPanel.getExpanded() ? 'Sidebar shown' : 'Sidebar hidden')
        updateFooter()
    }

    void refreshSidebar() {
        if (sidebarPanel) {
            sidebarPanel.refresh()
        }
        updateFooter()
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

            case 'skill':
                handleSkillCommand(args)
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

            activityLogPanel.appendSystemMessage("Switched to model: ${newModel}")
        } catch (Exception e) {
            activityLogPanel.appendSystemMessage("Error switching model: ${e.message}")
        }
    }

    private void showHelp() {
        appendSystemMessage('=== Available Commands ===')
        appendSystemMessage('/models   - Open model selection dialog')
        appendSystemMessage('/model    - Show current model or switch to specific model')
        appendSystemMessage('/skill    - List or show skill details')
        appendSystemMessage('/sidebar  - Toggle sidebar')
        appendSystemMessage('/help     - Show this help message')
        appendSystemMessage('/clear    - Clear chat history')
        appendSystemMessage('/exit     - Exit TUI')
        appendSystemMessage('')
        appendSystemMessage('Keyboard shortcuts:')
        appendSystemMessage('Ctrl+M   - Open model selection dialog')
        appendSystemMessage('Tab      - Switch agent')
        appendSystemMessage('Ctrl+C   - Exit')
    }

    private void handleSkillCommand(String args) {
        skillRegistry.discover()

        if (!args || args == 'list' || args == '--list' || args == '-l') {
            def skills = skillRegistry.getAvailableSkills()
            if (skills.isEmpty()) {
                appendSystemMessage('No skills found. Create .glm/skills/<name>/SKILL.md files.')
            } else {
                appendSystemMessage('Available Skills:')
                skills.each { skill ->
                    appendSystemMessage("  • ${skill.name}: ${skill.description}")
                }
                appendSystemMessage('Use /skill <name> to view skill details')
            }
        } else {
            String skillName = args.trim()
            def skill = skillRegistry.getSkill(skillName)
            if (!skill) {
                appendSystemMessage("Skill '${skillName}' not found")
            } else {
                appendSystemMessage("=== ${skill.name} ===")
                appendSystemMessage(skill.content)
                appendSystemMessage("--- Source: ${skill.sourcePath} ---")
            }
        }
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

        // Get max steps from config (default 25, or unlimited if null)
        int maxIterations = config.behavior?.maxSteps ?: 25
        int iteration = 0

        // Filter tools based on agent type
        List<Tool> allowedTools = []
        tools.each { toolMap ->
            def toolInstance = toolMap as Tool
            if (agentConfig.isToolAllowed(toolInstance.name)) {
                allowedTools << toolInstance
            }
        }

        boolean continueAfterTools = false  // Track if we're continuing after tool execution

        isGenerating.set(true)
        stopRequested.set(false)

        try {
            while (iteration < maxIterations) {
                if (stopRequested.get()) {
                    activityLogPanel.appendWarning("Generation stopped by user")
                    break
                }

                iteration++

            // Check if this is the last step
            boolean isLastStep = (iteration >= maxIterations - 1)

            // At 80% of max iterations, warn user
            if (iteration >= (maxIterations * 0.8) && iteration < maxIterations) {
                int remaining = maxIterations - iteration
                activityLogPanel.appendInfo("Step ${iteration}/${maxIterations} - ${remaining} steps remaining")
            }

            activityLogPanel.appendStatus('Thinking...')

            try {
                ChatRequest request = new ChatRequest()
                request.model = modelId
                request.messages = messages
                request.stream = false

                if (isLastStep) {
                    // Last step: disable tools and inject max-steps prompt
                    request.tools = []

                    def maxStepsPrompt = loadMaxStepsPrompt()
                    messages << new Message('assistant', maxStepsPrompt)

                    activityLogPanel.appendWarning('Maximum steps reached - requesting summary')
                } else {
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
                }

                activityLogPanel.removeStatus()

                // Only start new streaming response if not continuing after tool calls
                if (!continueAfterTools) {
                    activityLogPanel.startStreamingResponse()
                } else {
                    // Continuing after tool execution - append directly without new prefix
                    continueAfterTools = false
                }

                // Use streaming for real-time display
                StringBuilder fullResponseBuilder = new StringBuilder()

                client.streamMessageForTUI(request,
                    { String chunk ->
                        activityLogPanel.appendStreamChunk(chunk)
                        fullResponseBuilder.append(chunk)
                    },
                    { String fullResponse ->
                        activityLogPanel.finishStreamingResponse()
                        activityLogPanel.appendSeparator()

                        // Now check if this response contains tool calls
                        // We need to make a non-streaming call to get proper tool call structure
                        if (!isLastStep && request.tools && !stopRequested.get()) {
                            // Make a second non-streaming call to get tool calls
                            ChatRequest nonStreamRequest = new ChatRequest()
                            nonStreamRequest.model = modelId
                            nonStreamRequest.messages = messages
                            nonStreamRequest.stream = false
                            nonStreamRequest.tools = request.tools

                            try {
                                String responseJson = client.sendMessage(nonStreamRequest)
                                ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)

                                def choice = response.choices[0]
                                def message = choice.message

                                // Track token usage
                                if (response.usage) {
                                    int inputTokens = response.usage.promptTokens ?: 0
                                    int outputTokens = response.usage.completionTokens ?: 0
                                    BigDecimal cost = response.usage.cost ?: 0.0000

                                    TokenTracker.instance.recordTokens(sessionId, inputTokens, outputTokens, cost)
                                    SessionStatsManager.instance.updateTokenCount(sessionId, inputTokens, outputTokens, cost)

                                    int totalTokens = inputTokens + outputTokens
                                    int percentage = Math.min((totalTokens / 128000) * 100 as int, 100)
                                    int lspCount = SidebarLspManager.instance.getConnectedLspCount(sessionId)

                                    updateHeader(inputTokens, outputTokens, percentage, cost, lspCount)
                                    updateFooter()
                                    refreshSidebar()
                                }

                                // Use the streaming content for display, but tool calls from structured response
                                def displayMessage = new Message()
                                displayMessage.role = 'assistant'
                                displayMessage.content = fullResponse

                                if (choice.finishReason == 'tool_calls' || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                                    // Add streaming content to messages
                                    messages << displayMessage

                                    // Process tool calls from structured response
                                    message.toolCalls.each { toolCall ->
                                        if (stopRequested.get()) return // Stop tool processing if requested during loop

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

                                    // Continue loop to process tool results
                                    continueAfterTools = true
                                // Continue while loop
                                } else {
                                    // No tool calls, we are done
                                    messages << displayMessage
                                    // Exit the while loop by setting iteration to maxIterations
                                    iteration = maxIterations
                                }
                            } catch (Exception e) {
                                activityLogPanel.appendError("Error processing tool calls: ${e.message}")
                            }
                         } else {
                              // No tools or last step, we are done
                              def displayMessage = new Message()
                              displayMessage.role = 'assistant'
                              displayMessage.content = fullResponse
                              messages << displayMessage
                              // Exit the while loop by setting iteration to maxIterations
                              iteration = maxIterations
                         }
                    },
                    { stopRequested.get() }
                ) // End of streamMessageForTUI

                // If stopped, break out of the while loop
                if (stopRequested.get()) {
                    break
                }

                // If we are not continuing after tools (and not stopped), we are done with this turn
                if (!continueAfterTools) {
                    break
                }

            } catch (Exception e) {
                activityLogPanel.appendError("Error in chat request: ${e.message}")
            } finally {
                activityLogPanel.removeStatus()
            }

            }

        } catch (Exception e) {
            activityLogPanel.appendError("Error in conversation loop: ${e.message}")
            e.printStackTrace()
        } finally {
            isGenerating.set(false)
            activityLogPanel.removeStatus()
        }
    }

    // Helper method
    private String loadMaxStepsPrompt() {
        def promptFile = new File('prompts/max-steps.txt')
        if (promptFile.exists()) {
            return promptFile.text
        }
        // Fallback prompt
        return '''CRITICAL - MAXIMUM STEPS REACHED
The maximum number of steps allowed has been reached. Tools are disabled.
Please provide a summary of work completed and any remaining tasks.'''
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
            case 'skill':
                if (args.list_available) {
                    return 'List available skills'
                }
                return "Load skill: ${args.name}"
            default:
                return "${name}(${truncate(args.toString(), 40)})"
        }
    }

    private static String truncate(String s, int maxLen) {
        if (!s || s.length() <= maxLen) return s ?: ''
        return s.substring(0, maxLen - 3) + '...'
    }

}
