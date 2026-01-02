package tui

import com.googlecode.lanterna.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import core.Auth
import core.GlmClient
import core.Config
import models.ChatRequest
import models.ChatResponse
import models.Message
import tools.ReadFileTool
import tools.WriteFileTool
import tools.ListFilesTool
import tools.WebSearchTool
import tools.CodeSearchTool
import rag.RAGPipeline
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths

class LanternaTUI {

    private Screen screen
    private MultiWindowTextGUI textGUI
    private BasicWindow mainWindow
    private ActivityLogPanel activityLogPanel
    private CommandInputPanel commandInputPanel
    private Panel statusBar
    
    private String currentModel
    private String currentCwd
    private String apiKey
    private Config config
    private GlmClient client
    private ObjectMapper mapper = new ObjectMapper()
    private List<Map> tools = []
    
    LanternaTUI() throws Exception {
        this.currentCwd = System.getProperty('user.dir')
    }
    
    void start(String model = 'glm-4.7', String cwd = null) {
        this.currentModel = model
        this.currentCwd = cwd ?: System.getProperty('user.dir')
        
        if (!initClient()) {
            System.err.println('Failed to initialize client')
            return
        }

        try {
            screen = new DefaultTerminalFactory().createScreen()
            screen.startScreen()

            textGUI = new MultiWindowTextGUI(screen)
            textGUI.setEOFWhenNoWindows(true)

            setupMainWindow()
            setupUI()

            textGUI.waitForWindowToClose(mainWindow)
        } catch (Exception e) {
            System.err.println("TUI Error: ${e.message}")
            e.printStackTrace()
        } finally {
            if (screen != null) {
                screen.stopScreen()
            }
        }
    }

    private boolean initClient() {
        config = Config.load()

        apiKey = System.getenv('ZAI_API_KEY')
        if (!apiKey) {
            def authCredential = Auth.get('zai')
            apiKey = authCredential?.key
        }
        if (!apiKey) {
            apiKey = config?.api?.key
        }

        if (!apiKey) {
            System.err.println("Error: No API key found. Run 'glm auth login' to authenticate.")
            return false
        }

        client = new GlmClient(apiKey)

        tools << new ReadFileTool()
        tools << new WriteFileTool()
        tools << new ListFilesTool()

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
        Panel mainPanel = new Panel()
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL))

        activityLogPanel = new ActivityLogPanel(textGUI)
        activityLogPanel.appendWelcomeMessage(currentModel)

        def activityLogComponent = activityLogPanel.getTextBox().withBorder(Borders.singleLine('Activity Log'))
        activityLogComponent.setLayoutData(
            LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)
        )
        mainPanel.addComponent(activityLogComponent)

        commandInputPanel = new CommandInputPanel(textGUI, this)
        def commandInputComponent = commandInputPanel.getTextBox().withBorder(Borders.singleLine('Command'))
        commandInputComponent.setLayoutData(
            LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)
        )
        mainPanel.addComponent(commandInputComponent)

        statusBar = createStatusBar()
        mainPanel.addComponent(statusBar.withBorder(Borders.singleLine()))

        mainWindow.setComponent(mainPanel)
        textGUI.addWindow(mainWindow)

        LanternaTheme.applyDarkTheme(textGUI)
        commandInputPanel.getTextBox().takeFocus()
    }

    private Panel createStatusBar() {
        Panel panel = new Panel()
        panel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL))

        panel.addComponent(new Label("Model: ${currentModel}"))
        panel.addComponent(new Label("  |  "))
        panel.addComponent(new Label("Dir: ${Paths.get(currentCwd).fileName}"))
        panel.addComponent(new Label("  |  "))
        panel.addComponent(new Label("Ctrl+C: Exit"))
        
        return panel
    }

    void processUserInput(String input) {
        activityLogPanel.appendUserMessage(input)

        Thread.start {
            processInput(input)
        }
    }

    private void processInput(String userInput) {
        List<Message> messages = []

        messages << new Message('user', userInput)

        int maxIterations = 10
        int iteration = 0

        while (iteration < maxIterations) {
            iteration++

            activityLogPanel.appendStatus('Thinking...')

            try {
                ChatRequest request = new ChatRequest()
                request.model = currentModel
                request.messages = messages
                request.stream = false
                request.tools = tools.collect { tool ->
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
            default:
                return "${name}(${truncate(args.toString(), 40)})"
        }
    }

    private static String truncate(String s, int maxLen) {
        if (!s || s.length() <= maxLen) return s ?: ''
        return s.substring(0, maxLen - 3) + '...'
    }

}
