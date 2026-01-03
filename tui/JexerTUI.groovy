package tui

import core.Auth
import core.GlmClient
import core.Config
import core.ModelCatalog
import core.AgentRegistry
import core.AgentType
import core.AgentConfig
import core.TokenTracker
import core.SessionStatsManager
import core.Instructions
import core.LspManager as SidebarLspManager
import core.LSPManager as LspClientManager
import core.LSPClient
import models.ChatRequest
import models.ChatResponse
import models.Message
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool
import tools.GrepTool
import tools.GlobTool
import tools.Tool
import tools.WebSearchTool
import tools.CodeSearchTool
import rag.RAGPipeline
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.nio.file.Paths

import jexer.TApplication
import jexer.TWindow
import jexer.TMessageBox
import jexer.event.TKeypressEvent
import jexer.bits.ColorTheme
import tui.widgets.JexerActivityLog
import tui.widgets.JexerCommandInput
import tui.widgets.JexerAutocompletePopup
import tui.widgets.JexerStatusBar
import tui.widgets.JexerSidebar
import static jexer.TKeypress.*

/**
 * Jexer-based TUI with dark OpenCode-style theme.
 * Structurally matches LanternaTUI with full feature parity.
 * Features: Activity log, sidebar, agent switching, model selection, LSP integration.
 */
class JexerTUI extends TApplication {

    private String currentModel
    private String providerId
    private String modelId
    private String sessionId
    private String currentCwd
    private Config config
    private GlmClient client
    private ObjectMapper mapper = new ObjectMapper()
    private List<Tool> tools = []
    private AgentRegistry agentRegistry

    // Modular UI components
    private JexerActivityLog activityLog
    private JexerCommandInput commandInput
    private JexerAutocompletePopup autocompletePopup
    private JexerStatusBar statusBar
    private JexerSidebar sidebarPanel

    // Window references
    private TWindow mainChatWindow
    private TWindow sidebarWindow

    // State
    private boolean sidebarEnabled = true
    private volatile boolean running = true

    /**
     * Create the Jexer application with dark theme.
     */
    JexerTUI() throws Exception {
        super(BackendType.XTERM)

        this.currentCwd = System.getProperty('user.dir')
        this.agentRegistry = new AgentRegistry(AgentType.BUILD)

        // Apply dark theme
        JexerTheme.applyDarkTheme(this)

        // Set up LSP tracking callback
        setupLspCallbacks()
    }

    /**
     * Handle keypress events for global shortcuts.
     */
    @Override
    protected boolean onKeypress(TKeypressEvent keypress) {
        // Ctrl+C: Exit
        if (keypress.getKey().equals(kbCtrlC)) {
            running = false
            exit()
            return true
        }

        // Tab: Cycle agent forward
        if (keypress.getKey().equals(kbTab)) {
            cycleAgent(1)
            return true
        }

        // Shift+Tab: Cycle agent backward
        if (keypress.getKey().equals(kbShiftTab)) {
            cycleAgent(-1)
            return true
        }

        // Ctrl+S: Export log
        if (keypress.getKey().equals(kbCtrlS)) {
            if (activityLog != null) {
                String exportPath = activityLog.exportLog()
                if (exportPath) {
                    activityLog.appendSystemMessage("Log exported to: ${exportPath}")
                }
            }
            return true
        }

        return super.onKeypress(keypress)
    }

    /**
     * Set up LSP tracking callbacks.
     */
    private void setupLspCallbacks() {
        try {
            if (!LspClientManager.instance.isEnabled()) {
                return
            }

            LspClientManager.instance.setOnClientCreated { String serverId, LSPClient client, String root ->
                // Register with sidebar LSP manager
                SidebarLspManager.instance.registerLsp(sessionId, serverId, client, root)

                // Update diagnostics status
                Thread.start {
                    try {
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
                    // Ignore
                    }
                }

                // Refresh sidebar
                refreshSidebar()
            }
        } catch (Exception e) {
        // LSP not available
        }
    }

    /**
     * Start TUI with given model and working directory.
     */
    void start(String model = 'opencode/big-pickle', String cwd = null) {
        this.currentModel = model
        this.sessionId = UUID.randomUUID().toString()

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

        // Initialize client
        if (!initClient()) {
            return
        }

        // Set up UI
        setupUI()

        // Start periodic sidebar refresh
        startSidebarRefresh()

        // Initialize LSP tracking
        initializeLspTracking()

        // Run the application
        run()
    }

    /**
     * Initialize API client and tools.
     */
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

        // Register tools with session ID for tracking
        def writeFileTool = new WriteFileTool()
        writeFileTool.setSessionId(sessionId)
        tools << new ReadFileTool()
        tools << writeFileTool
        tools << new ListFilesTool()
        tools << new GrepTool()
        tools << new GlobTool()

        if (config?.webSearch?.enabled) {
            tools << new WebSearchTool(authCredential.key)
        }

        if (config?.rag?.enabled) {
            try {
                def ragPipeline = new RAGPipeline(config.rag.cacheDir)
                tools << new CodeSearchTool(ragPipeline)
            } catch (Exception e) {
            // RAG not available
            }
        }

        return true
    }

    /**
     * Set up the UI components in modular fashion.
     */
    private void setupUI() {
        int screenWidth = getScreen().getWidth()
        int screenHeight = getScreen().getHeight()

        // Auto-hide sidebar on small terminals (< 100 columns)
        if (screenWidth < 100) {
            sidebarEnabled = false
        }

        // Calculate dimensions
        int sidebarWidth = sidebarEnabled ? 42 : 0
        int mainContentWidth = screenWidth - sidebarWidth - 2
        int activityLogHeight = screenHeight - 5  // Space for status bar + input

        // Create main chat window
        mainChatWindow = addWindow(
            "GLM CLI - ${currentModel}",
            0, 0, mainContentWidth, screenHeight - 2,
            TWindow.CENTERED
        )

        // Create activity log component
        activityLog = new JexerActivityLog(mainChatWindow, mainContentWidth - 2, activityLogHeight - 2)
        activityLog.setX(1)
        activityLog.setY(1)
        activityLog.setOnScrollPositionChanged { int currentLine, int totalLines ->
            if (statusBar != null) {
                statusBar.setScrollPosition(currentLine, totalLines)
            }
        }
        activityLog.appendWelcomeMessage(currentModel)

        // Create command input component
        commandInput = new JexerCommandInput(mainChatWindow, mainContentWidth - 10, currentCwd)
        commandInput.setX(6)
        commandInput.setY(activityLogHeight)
        commandInput.setOnSubmit { String input, List<String> mentions ->
            processUserInput(input, mentions)
        }

        // Create autocomplete popup
        autocompletePopup = new JexerAutocompletePopup(this)
        commandInput.setAutocompletePopup(autocompletePopup)

        // Create status bar
        statusBar = new JexerStatusBar(this, mainContentWidth)
        statusBar.setX(1)
        statusBar.setY(screenHeight - 2)
        statusBar.setModel(currentModel)
        statusBar.setDirectory(currentCwd)
        statusBar.setAgent(agentRegistry.getCurrentAgentName())
        statusBar.setSidebarEnabled(sidebarEnabled)

        // Widgets are already added to mainChatWindow via constructor

        // Create sidebar window (if enabled)
        if (sidebarEnabled) {
            sidebarPanel = new JexerSidebar(this, sessionId, screenHeight - 2)
            sidebarPanel.setX(screenWidth - sidebarWidth - 1)
            sidebarPanel.setY(0)
            sidebarWindow = sidebarPanel
        }
    }

    /**
     * Start periodic sidebar refresh thread.
     */
    private void startSidebarRefresh() {
        Thread.start {
            while (running) {
                try {
                    Thread.sleep(2000)

                    if (sidebarPanel && sidebarPanel.getExpanded()) {
                        // Update LSP diagnostic counts
                        try {
                            SidebarLspManager.instance.updateDiagnosticCounts(sessionId)
                        } catch (Exception e) {
                        // Ignore
                        }

                        // Refresh sidebar
                        refreshSidebar()
                    }
                } catch (InterruptedException e) {
                    break
                } catch (Exception e) {
                    System.err.println("Sidebar refresh error: ${e.message}")
                }
            }
        }
    }

    /**
     * Initialize LSP tracking by touching a file in the working directory.
     */
    private void initializeLspTracking() {
        try {
            if (!LspClientManager.instance.isEnabled()) {
                return
            }

            Thread.start {
                try {
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
                                break
                            } catch (Exception e) {
                            // Try next file
                            }
                        }
                    }
                } catch (Exception e) {
                // Don't fail TUI if LSP init fails
                }
            }
        } catch (Exception e) {
        // LSP not available
        }
    }

    /**
     * Process user input with optional file mentions.
     */
    void processUserInput(String input, List<String> mentions = []) {
        // Handle slash commands
        if (input.startsWith('/')) {
            handleSlashCommand(input)
            return
        }

        activityLog.appendUserMessage(input)

        Thread.start {
            processInput(input, mentions)
        }
    }

    /**
     * Handle slash commands.
     */
    private void handleSlashCommand(String input) {
        def parsed = CommandProvider.parse(input)
        if (!parsed) {
            activityLog.appendSystemMessage("Unknown command: ${input}")
            return
        }

        String command = parsed.name.toLowerCase()
        String args = parsed.arguments ?: ''

        switch (command) {
            case 'models':
                showModels()
                break

            case 'model':
                if (args) {
                    switchModel(args)
                } else {
                    activityLog.appendSystemMessage("Current model: ${currentModel}")
                }
                break

            case 'sidebar':
                toggleSidebar()
                break

            case 'help':
                showHelp()
                break

            case 'clear':
                activityLog.clear()
                activityLog.appendWelcomeMessage(currentModel)
                activityLog.appendSystemMessage('Chat history cleared')
                break

            case 'cwd':
                if (args) {
                    def newDir = new File(args)
                    if (newDir.isDirectory()) {
                        currentCwd = newDir.absolutePath
                        if (statusBar) {
                            statusBar.setDirectory(currentCwd)
                        }
                        activityLog.appendSystemMessage("Changed to: ${currentCwd}")
                    } else {
                        activityLog.appendError("Not a directory: ${args}")
                    }
                } else {
                    activityLog.appendSystemMessage("Working directory: ${currentCwd}")
                }
                break

            case 'ls':
                String path = args.isEmpty() ? currentCwd : args
                def dir = new File(path)
                if (dir.isDirectory()) {
                    activityLog.appendSystemMessage("Contents of ${path}:")
                    dir.listFiles()?.sort()?.take(20)?.each { f ->
                        String icon = f.isDirectory() ? '📁' : '📄'
                        activityLog.appendSystemMessage("  ${icon} ${f.name}")
                    }
                } else {
                    activityLog.appendError("Not a directory: ${path}")
                }
                break

            case 'read':
                if (args) {
                    readAndShowFile(args)
                } else {
                    activityLog.appendSystemMessage('Usage: /read <filename>')
                }
                break

            case 'tools':
                activityLog.appendSystemMessage('Available tools:')
                tools.each { tool ->
                    activityLog.appendSystemMessage("  ${tool.name} - ${tool.description?.take(50) ?: ''}")
                }
                break

            case 'context':
                activityLog.appendSystemMessage("Model: ${currentModel}")
                activityLog.appendSystemMessage("Working directory: ${currentCwd}")
                activityLog.appendSystemMessage("Agent: ${agentRegistry.getCurrentAgentName()}")
                activityLog.appendSystemMessage("Tools: ${tools.size()} registered")
                break

            case 'exit':
                running = false
                exit()
                break

            default:
                activityLog.appendSystemMessage("Command '${command}' is not yet implemented")
        }
    }

    /**
     * Show available models.
     */
    private void showModels() {
        activityLog.appendSystemMessage('Available models:')
        def allModels = ModelCatalog.getAllModels()
        allModels.values().sort { a, b -> a.provider <=> b.provider }.each { model ->
            def isFree = model.cost?.input == 0 && model.cost?.output == 0
            def freeTag = isFree ? ' (Free)' : ''
            activityLog.appendSystemMessage("  ${model.provider}/${model.id}${freeTag} - ${model.name}")
    }
}

    /**
     * Switch to a new model.
     */
    private void switchModel(String newModel) {
        try {
            def parts = newModel.split('/', 2)
            if (parts.length != 2) {
                activityLog.appendError('Invalid model format. Expected: provider/model-id')
                return
            }

            String newProviderId = parts[0]
            String newModelId = parts[1]

            // Validate provider
            def providerInfo = ModelCatalog.getProvider(newProviderId)
            if (!providerInfo) {
                activityLog.appendError("Unknown provider: ${newProviderId}")
                return
            }

            // Validate model
            def modelInfo = ModelCatalog.getModel(newModel)
            if (!modelInfo) {
                activityLog.appendError("Unknown model: ${newModel}")
                return
            }

            // Check authentication
            def authCredential = Auth.get(newProviderId)
            if (!authCredential) {
                activityLog.appendError("Not authenticated for provider '${newProviderId}'")
                activityLog.appendSystemMessage("Run 'glm auth login ${newProviderId}' to authenticate")
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

            activityLog.appendSystemMessage("Switched to model: ${newModel}")
        } catch (Exception e) {
            activityLog.appendError("Error switching model: ${e.message}")
        }
    }

    /**
     * Update window title and status bar after model change.
     */
    private void updateWindowAndStatusBar() {
        mainChatWindow.setTitle("GLM CLI - ${currentModel}")

        if (statusBar) {
            statusBar.setModel(currentModel)
        }
    }

    /**
     * Show help message.
     */
    private void showHelp() {
        activityLog.appendSystemMessage('=== Available Commands ===')
        activityLog.appendSystemMessage('/models   - List available models')
        activityLog.appendSystemMessage('/model    - Show current model or switch to specific model')
        activityLog.appendSystemMessage('/help     - Show this help message')
        activityLog.appendSystemMessage('/clear    - Clear chat history')
        activityLog.appendSystemMessage('/cwd      - Show/change working directory')
        activityLog.appendSystemMessage('/ls       - List files in directory')
        activityLog.appendSystemMessage('/read     - Read a file')
        activityLog.appendSystemMessage('/tools    - List available tools')
        activityLog.appendSystemMessage('/context  - Show current context')
        if (sidebarEnabled) {
            activityLog.appendSystemMessage('/sidebar  - Toggle sidebar')
        }
        activityLog.appendSystemMessage('/exit     - Exit TUI')
        activityLog.appendSystemMessage('')
        activityLog.appendSystemMessage('Keyboard shortcuts:')
        activityLog.appendSystemMessage('Tab        - Switch agent forward')
        activityLog.appendSystemMessage('Shift+Tab  - Switch agent backward')
        activityLog.appendSystemMessage('Ctrl+S     - Export activity log')
        activityLog.appendSystemMessage('Ctrl+C     - Exit')
    }

    /**
     * Toggle sidebar visibility.
     */
    void toggleSidebar() {
        if (!sidebarPanel) return
        sidebarPanel.toggle()
        activityLog.appendSystemMessage(sidebarPanel.getExpanded() ? 'Sidebar shown' : 'Sidebar hidden')
    }

    /**
     * Refresh sidebar content.
     */
    void refreshSidebar() {
        if (sidebarPanel) {
            sidebarPanel.refresh()
        }
    }

    /**
     * Cycle agent (Tab/Shift+Tab).
     */
    void cycleAgent(int direction = 1) {
        agentRegistry.cycleAgent(direction)
        if (statusBar) {
            statusBar.setAgent(agentRegistry.getCurrentAgentName())
        }
        activityLog.appendSystemMessage("Switched to ${agentRegistry.getCurrentAgentName()} agent")
    }

    /**
     * Read and display a file with optional line range.
     */
    private void readAndShowFile(String pathSpec) {
        def parsed = FileProvider.extractLineRange(pathSpec)
        String path = parsed.baseQuery
        Integer startLine = parsed.startLine
        Integer endLine = parsed.endLine

        File file = new File(path)
        if (!file.isAbsolute()) {
            file = new File(currentCwd, path)
        }

        if (!file.exists()) {
            activityLog.appendError("File not found: ${path}")
            return
        }

        try {
            List<String> lines = file.readLines()
            int start = startLine ? Math.max(1, startLine) : 1
            int end = endLine ? Math.min(lines.size(), endLine) : Math.min(lines.size(), start + 20)

            activityLog.appendSystemMessage("── ${file.name} (lines ${start}-${end} of ${lines.size()}) ──")
            for (int i = start - 1; i < end && i < lines.size(); i++) {
                activityLog.appendSystemMessage("${(i + 1).toString().padLeft(4)}: ${lines[i]}")
            }
            activityLog.appendSystemMessage('────────────────────────────────────────────────')
        } catch (Exception e) {
            activityLog.appendError("Error reading file: ${e.message}")
        }
    }

    /**
     * Process input with AI and tool execution.
     */
    private void processInput(String userInput, List<String> mentions = []) {
        List<Message> messages = []

        // Load AGENTS.md custom instructions
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
        tools.each { tool ->
            if (agentConfig.isToolAllowed(tool.name)) {
                allowedTools << tool
            }
        }

        while (iteration < maxIterations) {
            iteration++

            activityLog.appendStatus('Thinking...')

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

                activityLog.removeStatus()

                if (message.content) {
                    activityLog.appendAIResponse(message.content)
                    activityLog.appendSeparator()
                }

                if (choice.finishReason == 'tool_calls' || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                    messages << message

                    message.toolCalls.each { toolCall ->
                        String functionName = toolCall.function.name
                        String arguments = toolCall.function.arguments
                        String callId = toolCall.id

                        Map<String, Object> args = mapper.readValue(arguments, Map.class)
                        String toolDisplay = formatToolCall(functionName, args)

                        activityLog.appendToolExecution(toolDisplay)

                        def tool = tools.find { it.name == functionName }
                        String result = ''

                        if (tool) {
                            try {
                                // Safety check for write_file
                                if (functionName == 'write_file') {
                                    int confirm = messageBox(
                                        'Confirm Write',
                                        "Write to ${args.path}?",
                                        TMessageBox.Type.YESNO
                                    ).getResult()

                                    if (confirm != TMessageBox.Result.YES) {
                                        result = 'User cancelled write operation'
                                        activityLog.appendToolError('Cancelled')
                                    } else {
                                        Object output = tool.execute(args)
                                        result = output.toString()
                                        activityLog.appendToolResult('Success')
                                    }
                                } else {
                                    Object output = tool.execute(args)
                                    result = output.toString()
                                    activityLog.appendToolResult('Success')
                                }
                            } catch (Exception e) {
                                result = "Error: ${e.message}"
                                activityLog.appendToolError(e.message)
                            }
                        } else {
                            result = 'Error: Tool not found'
                            activityLog.appendToolError('Tool not found')
                        }

                        Message toolMsg = new Message()
                        toolMsg.role = 'tool'
                        toolMsg.content = result
                        toolMsg.toolCallId = callId
                        messages << toolMsg
                    }
                    activityLog.appendSeparator()
                } else {
                    break
                }
            } catch (Exception e) {
                activityLog.removeStatus()
                activityLog.appendError(e.message)
                break
            }
        }

        if (iteration >= maxIterations) {
            activityLog.appendWarning('Reached maximum iterations')
        }
    }

    /**
     * Format tool call for display.
     */
    private String formatToolCall(String name, Map args) {
        switch (name) {
            case 'read_file':
                return "Read ${args.path}"
            case 'write_file':
                return "Write ${args.path}"
            case 'list_files':
                return "List ${args.path ?: '.'}"
            case 'grep':
                return "Grep \"${truncate(args.pattern?.toString(), 30)}\""
            case 'glob':
                return "Glob \"${truncate(args.pattern?.toString(), 30)}\""
            case 'web_search':
                return "Search \"${truncate(args.query?.toString(), 30)}\""
            case 'code_search':
                return "CodeSearch \"${truncate(args.query?.toString(), 30)}\""
            default:
                return "${name}(${truncate(args.toString(), 40)})"
        }
    }

    /**
     * Truncate string.
     */
    private static String truncate(String s, int maxLen) {
        if (!s || s.length() <= maxLen) return s ?: ''
        return s.substring(0, maxLen - 3) + '...'
    }

}
