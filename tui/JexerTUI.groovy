package tui

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

import jexer.TApplication
import jexer.TWindow
import jexer.TText
import jexer.TField
import jexer.TAction
import jexer.TMessageBox
import jexer.TImage
import jexer.event.TKeypressEvent
import jexer.bits.CellAttributes
import jexer.bits.Color
import jexer.bits.ColorTheme
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Paths
import static jexer.TKeypress.*

/**
 * Jexer-based TUI with dark OpenCode-style theme.
 * Provides a Turbo Vision-style UI for the GLM CLI.
 */
class JexerTUI extends TApplication {

    private String currentModel
    private String currentCwd
    private String apiKey
    private Config config
    private GlmClient client
    private ObjectMapper mapper = new ObjectMapper()
    private List<Map> tools = []

    // UI components
    private TWindow chatWindow
    private TText chatLog
    private AutocompleteField inputField
    private AutocompletePopup autocompletePopup
    private TImage logoImage
    private StringBuilder logContent = new StringBuilder()

    /**
     * Create the Jexer application with dark theme.
     */
    JexerTUI() throws Exception {
        super(BackendType.XTERM)

        // Apply dark theme
        applyDarkTheme()

    // Note: Ctrl+C handling is done in onKeypress() override
    }

    /**
     * Handle keypress events for Ctrl+C exit.
     */
    @Override
    protected boolean onKeypress(TKeypressEvent keypress) {
        if (keypress.getKey().equals(kbCtrlC)) {
            exit()
            return true
        }
        return super.onKeypress(keypress)
    }

    /**
     * Apply dark OpenCode-style theme with safe null checks.
     */
    private void applyDarkTheme() {
        ColorTheme theme = getTheme()

        // Helper to safely set colors
        def setColor = { String key, Color fg, Color bg, boolean bold = false ->
            try {
                CellAttributes attr = theme.getColor(key)
                if (attr != null) {
                    attr.setForeColor(fg)
                    attr.setBackColor(bg)
                    if (bold) attr.setBold(true)
                }
            } catch (Exception e) {
            // Ignore missing theme keys
            }
        }

        // Desktop/background
        setColor('tdesktop.background', Color.WHITE, Color.BLACK)

        // Window colors
        setColor('twindow.border', Color.CYAN, Color.BLACK)
        setColor('twindow.background', Color.WHITE, Color.BLACK)
        setColor('twindow.border.inactive', Color.WHITE, Color.BLACK)
        setColor('twindow.border.modal', Color.CYAN, Color.BLACK)
        setColor('twindow.border.modal.inactive', Color.WHITE, Color.BLACK)

        // Text and labels
        setColor('ttext', Color.WHITE, Color.BLACK)
        setColor('tlabel', Color.CYAN, Color.BLACK, true)

        // Fields
        setColor('tfield.active', Color.WHITE, Color.BLACK)
        setColor('tfield.inactive', Color.WHITE, Color.BLACK)

        // Buttons
        setColor('tbutton.inactive', Color.WHITE, Color.BLACK)
        setColor('tbutton.active', Color.BLACK, Color.CYAN)
        setColor('tbutton.disabled', Color.WHITE, Color.BLACK)

        // Menus
        setColor('tmenu', Color.WHITE, Color.BLACK)
        setColor('tmenu.highlighted', Color.BLACK, Color.CYAN)
    }

    /**
     * Start the TUI with the given model and working directory.
     */
    void start(String model = 'glm-4.7', String cwd = null) {
        this.currentModel = model
        this.currentCwd = cwd ?: System.getProperty('user.dir')

        // Initialize client
        if (!initClient()) {
            return
        }

        // Set up UI
        setupUI()

        // Run the application
        run()
    }

    /**
     * Initialize API client and tools.
     */
    private boolean initClient() {
        config = Config.load()

        // Get API key
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

        // Register tools
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
            // RAG not available
            }
        }

        return true
    }

    /**
     * Set up the UI components.
     */
    private void setupUI() {
        // Create main chat window
        int width = getScreen().getWidth()
        int height = getScreen().getHeight()

        chatWindow = addWindow(
            "GLM CLI - ${currentModel}",
            0, 0, width, height - 2,
            TWindow.RESIZABLE | TWindow.CENTERED
        )

        // Try to load and display logo image
        int logoHeight = 0
        try {
            File logoFile = new File('assets/logo.png')
            if (!logoFile.exists()) {
                // Try relative to script location
                logoFile = new File(System.getProperty('user.dir'), 'assets/logo.png')
            }
            if (logoFile.exists()) {
                BufferedImage image = ImageIO.read(logoFile)
                if (image != null) {
                    // Calculate display size (scale to fit in window width, max 8 rows height)
                    int imgWidth = Math.min(chatWindow.getWidth() - 4, 40)
                    int imgHeight = Math.min(8, (int)(imgWidth * image.getHeight() / image.getWidth() / 2))
                    
                    // Create TImage widget
                    logoImage = new TImage(chatWindow, 1, 1, imgWidth, imgHeight, image, 0, 0, null)
                    logoImage.setScaleType(TImage.Scale.SCALE)
                    logoHeight = imgHeight + 1
                }
            }
        } catch (Exception e) {
            // Image loading failed, will use ASCII art below
        }

        // Add header text below image (or ASCII art if no image)
        if (logoHeight == 0) {
            appendAsciiArt()
            logoHeight = 13  // ASCII art height (12 lines + 1 blank)
        } else {
            // Add minimal text header when image is shown
            appendLog('')
            appendLog("Model: ${currentModel}")
            appendLog('Type your message and press Enter. Press Ctrl+Q to quit.')
            appendLog('')
        }

        // Create text area for chat log (positioned below logo/header)
        chatLog = chatWindow.addText(
            logContent.toString(),
            1, logoHeight,
            chatWindow.getWidth() - 4,
            chatWindow.getHeight() - logoHeight - 4
        )

        // Create input field at bottom with autocomplete support
        chatWindow.addLabel('You> ', 1, chatWindow.getHeight() - 4)
        
        int inputY = chatWindow.getHeight() - 4
        int inputWidth = chatWindow.getWidth() - 8
        
        // Create autocomplete popup (positioned above input)
        autocompletePopup = new AutocompletePopup(
            chatWindow,
            6,
            inputY - 10  // Above the input field
        )
        autocompletePopup.setPopupVisible(false)
        
        // Create custom autocomplete field
        inputField = new AutocompleteField(
            chatWindow,
            6, inputY,
            inputWidth,
            false,
            ''
        )
        inputField.setPopup(autocompletePopup)

        // Handle Enter key on input field
        inputField.setEnterAction(new TAction() {

            @Override
            void DO() {
                String input = inputField.getText().trim()
                if (!input.isEmpty()) {
                    // Extract file mentions before clearing
                    List<String> mentions = inputField.extractMentions()
                    inputField.setText('')
                    // Run in background thread so UI updates immediately
                    Thread.start {
                        handleInput(input, mentions)
                    }
                }
            }

        })

        // Focus on input field
        inputField.activate()
    }

    /**
     * Append ASCII art logo as fallback.
     */
    private void appendAsciiArt() {
        appendLog('+--------------------------------------------------+')
        appendLog('|   ____  _     __  __    ____  _     ___          |')
        appendLog('|  / ___|| |   |  \\/  |  / ___|| |   |_ _|         |')
        appendLog('| | |  _ | |   | |\\/| | | |    | |    | |          |')
        appendLog('| | |_| || |___| |  | | | |___ | |___ | |          |')
        appendLog('|  \\____||_____|_|  |_|  \\____||_____|___|         |')
        appendLog('|                                                  |')
        appendLog("|  Model: ${currentModel.padRight(40)}|")
        appendLog('|  Type your message and press Enter               |')
        appendLog('|  Press Ctrl+Q to quit                            |')
        appendLog('+--------------------------------------------------+')
        appendLog('')
    }

    /**
     * Append text to the chat log.
     */
    private void appendLog(String text) {
        logContent.append(text).append("\n")
        if (chatLog != null) {
            chatLog.setText(logContent.toString())
            // Scroll to bottom
            chatLog.toBottom()
        }
    }

    /**
     * Handle user input with optional file mentions.
     */
    private void handleInput(String input, List<String> mentions = []) {
        // Handle commands
        if (input.equalsIgnoreCase('exit') || input.equalsIgnoreCase('quit')) {
            exit()
            return
        }

        if (input.startsWith('/')) {
            handleCommand(input)
            return
        }

        // Add user message to log
        appendLog("You> ${input}")
        
        // Show mentioned files
        if (mentions && !mentions.isEmpty()) {
            appendLog("  📎 Attached: ${mentions.join(', ')}")
        }
        appendLog('')

        // Process with AI, including file context
        processInput(input, mentions)
    }

    /**
     * Handle slash commands with improved parsing.
     */
    private void handleCommand(String cmd) {
        def parsed = CommandProvider.parse(cmd)
        if (parsed == null) return
        
        String name = parsed.name.toLowerCase()
        String args = parsed.arguments ?: ''
        
        switch (name) {
            case 'help':
            case 'commands':
                appendLog('Commands:')
                CommandProvider.getCommands().each { item ->
                    def cmdDef = CommandProvider.get(item.value)
                    String desc = cmdDef?.description ?: ''
                    appendLog("  /${item.value.padRight(10)} - ${desc}")
                }
                appendLog('')
                appendLog('File mentions:')
                appendLog('  @filename   - Attach file to context')
                appendLog('  @path#L10   - Attach specific line')
                appendLog('  @path#L1-50 - Attach line range')
                appendLog('')
                break
                
            case 'clear':
                logContent = new StringBuilder()
                appendAsciiArt()
                appendLog('Chat cleared.')
                appendLog('')
                break
                
            case 'model':
                if (args.isEmpty()) {
                    appendLog("Current model: ${currentModel}")
                } else {
                    currentModel = args
                    appendLog("Model changed to: ${currentModel}")
                }
                appendLog('')
                break
                
            case 'models':
                appendLog('Available models:')
                appendLog('  glm-4-plus      - High-performance model')
                appendLog('  glm-4           - Standard model')
                appendLog('  glm-4-flash     - Fast, cost-effective')
                appendLog('  glm-4-long      - Extended context')
                appendLog('  glm-4.7         - Latest version')
                appendLog('')
                break
                
            case 'cwd':
                if (args.isEmpty()) {
                    appendLog("Working directory: ${currentCwd}")
                } else {
                    def newDir = new File(args)
                    if (newDir.isDirectory()) {
                        currentCwd = newDir.absolutePath
                        appendLog("Changed to: ${currentCwd}")
                    } else {
                        appendLog("Not a directory: ${args}")
                    }
                }
                appendLog('')
                break
                
            case 'ls':
                String path = args.isEmpty() ? currentCwd : args
                def dir = new File(path)
                if (dir.isDirectory()) {
                    appendLog("Contents of ${path}:")
                    dir.listFiles()?.sort()?.take(20)?.each { f ->
                        String icon = f.isDirectory() ? '📁' : '📄'
                        appendLog("  ${icon} ${f.name}")
                    }
                } else {
                    appendLog("Not a directory: ${path}")
                }
                appendLog('')
                break
                
            case 'read':
                if (args.isEmpty()) {
                    appendLog('Usage: /read <filename>')
                } else {
                    readAndShowFile(args)
                }
                appendLog('')
                break
                
            case 'tools':
                appendLog('Available tools:')
                tools.each { tool ->
                    appendLog("  ${tool.name} - ${tool.description?.take(50) ?: ''}")
                }
                appendLog('')
                break
                
            case 'context':
                appendLog("Model: ${currentModel}")
                appendLog("Working directory: ${currentCwd}")
                appendLog("Tools: ${tools.size()} registered")
                appendLog('')
                break
                
            case 'debug':
                appendLog('Debug mode toggled')
                appendLog('')
                break
                
            case 'exit':
                exit()
                break
                
            default:
                appendLog("Unknown command: /${name}")
                appendLog("Type /help for available commands")
                appendLog('')
        }
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
            appendLog("File not found: ${path}")
            return
        }
        
        try {
            List<String> lines = file.readLines()
            int start = startLine ? Math.max(1, startLine) : 1
            int end = endLine ? Math.min(lines.size(), endLine) : Math.min(lines.size(), start + 20)
            
            appendLog("── ${file.name} (lines ${start}-${end} of ${lines.size()}) ──")
            for (int i = start - 1; i < end && i < lines.size(); i++) {
                appendLog("${(i + 1).toString().padLeft(4)}: ${lines[i]}")
            }
            appendLog("────────────────────────────────────────")
        } catch (Exception e) {
            appendLog("Error reading file: ${e.message}")
        }
    }

    /**
     * Process user input with AI, including file context from mentions.
     */
    private void processInput(String userInput, List<String> mentions = []) {
        List<Message> messages = []
        
        // Build context with mentioned files
        StringBuilder contextBuilder = new StringBuilder()
        
        if (mentions && !mentions.isEmpty()) {
            contextBuilder.append("# Referenced Files\n\n")
            mentions.each { mention ->
                def parsed = FileProvider.extractLineRange(mention)
                String path = parsed.baseQuery
                Integer startLine = parsed.startLine
                Integer endLine = parsed.endLine
                
                File file = new File(path)
                if (!file.isAbsolute()) {
                    file = new File(currentCwd, path)
                }
                
                if (file.exists() && file.isFile()) {
                    try {
                        List<String> lines = file.readLines()
                        int start = startLine ? Math.max(1, startLine) : 1
                        int end = endLine ? Math.min(lines.size(), endLine) : lines.size()
                        
                        contextBuilder.append("## ${file.name}")
                        if (startLine) {
                            contextBuilder.append(" (lines ${start}-${end})")
                        }
                        contextBuilder.append("\n```${getFileExtension(file.name)}\n")
                        
                        for (int i = start - 1; i < end && i < lines.size(); i++) {
                            contextBuilder.append(lines[i]).append('\n')
                        }
                        contextBuilder.append("```\n\n")
                    } catch (Exception e) {
                        contextBuilder.append("## ${file.name}\nError reading file: ${e.message}\n\n")
                    }
                } else {
                    contextBuilder.append("## ${mention}\nFile not found\n\n")
                }
            }
            contextBuilder.append("---\n\n")
        }
        
        String fullInput = contextBuilder.length() > 0 
            ? contextBuilder.toString() + "# User Request\n" + userInput
            : userInput
            
        messages << new Message('user', fullInput)

        int maxIterations = 10
        int iteration = 0

        while (iteration < maxIterations) {
            iteration++

            appendLog('GLM> Thinking...')

            try {
                // Prepare request
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

                // Send request
                String responseJson = client.sendMessage(request)
                ChatResponse response = mapper.readValue(responseJson, ChatResponse.class)

                def choice = response.choices[0]
                def message = choice.message

                // Clear "Thinking..." and show response
                if (message.content) {
                    // Remove the "Thinking..." line
                    String log = logContent.toString()
                    int thinkingIdx = log.lastIndexOf('GLM> Thinking...')
                    if (thinkingIdx >= 0) {
                        logContent = new StringBuilder(log.substring(0, thinkingIdx))
                    }

                    appendLog("GLM> ${message.content}")
                    appendLog('')
                }

                // Check for tool calls
                if (choice.finishReason == 'tool_calls' || (message.toolCalls != null && !message.toolCalls.isEmpty())) {
                    messages << message

                    message.toolCalls.each { toolCall ->
                        String functionName = toolCall.function.name
                        String arguments = toolCall.function.arguments
                        String callId = toolCall.id

                        // Show tool execution
                        Map<String, Object> args = mapper.readValue(arguments, Map.class)
                        String toolDisplay = formatToolCall(functionName, args)
                        appendLog("  → ${toolDisplay}")

                        // Execute tool
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
                                        appendLog('    ✗ Cancelled')
                                    } else {
                                        Object output = tool.execute(args)
                                        result = output.toString()
                                        appendLog('    ✓ Written')
                                    }
                                } else {
                                    Object output = tool.execute(args)
                                    result = output.toString()
                                    appendLog('    ✓ Done')
                                }
                            } catch (Exception e) {
                                result = "Error: ${e.message}"
                                appendLog("    ✗ ${e.message}")
                            }
                        } else {
                            result = 'Error: Tool not found'
                            appendLog('    ✗ Unknown tool')
                        }

                        // Add tool result
                        Message toolMsg = new Message()
                        toolMsg.role = 'tool'
                        toolMsg.content = result
                        toolMsg.toolCallId = callId
                        messages << toolMsg
                    }
                    appendLog('')
                } else {
                    // No tool calls, done
                    break
                }
            } catch (Exception e) {
                appendLog("Error: ${e.message}")
                appendLog('')
                break
            }
        }

        if (iteration >= maxIterations) {
            appendLog('⚠️ Reached maximum iterations')
            appendLog('')
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

    /**
     * Get file extension for syntax highlighting.
     */
    private static String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.')
        if (dot < 0) return ''
        
        String ext = filename.substring(dot + 1).toLowerCase()
        switch (ext) {
            case 'groovy': return 'groovy'
            case 'java': return 'java'
            case 'kt': return 'kotlin'
            case 'js': return 'javascript'
            case 'ts': return 'typescript'
            case 'jsx': case 'tsx': return 'tsx'
            case 'py': return 'python'
            case 'rb': return 'ruby'
            case 'go': return 'go'
            case 'rs': return 'rust'
            case 'c': case 'h': return 'c'
            case 'cpp': case 'hpp': return 'cpp'
            case 'json': return 'json'
            case 'yaml': case 'yml': return 'yaml'
            case 'xml': return 'xml'
            case 'md': return 'markdown'
            case 'sh': case 'bash': return 'bash'
            case 'html': return 'html'
            case 'css': return 'css'
            case 'sql': return 'sql'
            default: return ext
        }
    }

}
