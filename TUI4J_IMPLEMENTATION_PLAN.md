# TUI4J Implementation Plan for GLM-CLI

This document outlines the plan to integrate TUI4J as an alternative TUI framework for GLM-CLI, alongside the existing Lanterna and Jexer implementations.

## Overview

**TUI4J** (https://github.com/WilliamAGH/tui4j) is a Bubble Tea-inspired Terminal User Interface framework for Java that implements The Elm Architecture pattern. It provides a functional, declarative approach to building terminal applications with built-in async support—ideal for AI/LLM CLI applications.

## Why TUI4J?

### Advantages for GLM-CLI

| Feature | Benefit for GLM-CLI |
|---------|---------------------|
| **Elm Architecture** | Clean separation of state, updates, and view—perfect for complex chat UIs |
| **Native Async Commands** | Built-in support for async API calls without manual threading |
| **Lipgloss Styling** | Advanced, expressive terminal styling for rich output formatting |
| **Virtual Threads (JDK 21+)** | Efficient concurrent command execution |
| **Component Composition** | Reusable "bubbles" for list, input, spinner widgets |
| **Mouse Support** | Modern terminal interaction with click tracking |
| **Functional Immutability** | Easier testing and reasoning about state changes |

### Comparison with Existing Implementations

```
┌─────────────────┬──────────────────┬──────────────────┬──────────────────┐
│ Feature         │ TUI4J            │ Lanterna         │ Jexer            │
├─────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ Architecture    │ Elm (Functional) │ Imperative       │ Swing-like       │
│ Async Model     │ Native Commands  │ Manual threads   │ Event-driven     │
│ State Mgmt      │ Immutable Model  │ Mutable state    │ Mutable state    │
│ Styling         │ Lipgloss         │ Basic themes     │ Built-in themes  │
│ Code Complexity │ Lower            │ Moderate         │ Higher           │
│ Testing         │ Easy (pure fn)   │ Moderate         │ Harder           │
│ Learning Curve  │ Moderate         │ Easy             │ Moderate         │
└─────────────────┴──────────────────┴──────────────────┴──────────────────┘
```

## Architecture Design

### Elm Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         TUI4J GLM-CLI Architecture                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐              │
│   │    init()   │────▶│   update()  │────▶│   view()    │              │
│   │             │     │             │     │             │              │
│   │  Initial    │     │  Handle     │     │  Render     │              │
│   │  State +    │     │  Messages   │     │  Terminal   │              │
│   │  Commands   │     │  Update     │     │  Output     │              │
│   │             │     │  State      │     │             │              │
│   └─────────────┘     └──────┬──────┘     └─────────────┘              │
│                              │                                          │
│                              ▼                                          │
│                       ┌─────────────┐                                   │
│                       │  Commands   │                                   │
│                       │             │                                   │
│                       │  - API Call │                                   │
│                       │  - Tick     │                                   │
│                       │  - Batch    │                                   │
│                       └──────┬──────┘                                   │
│                              │                                          │
│                              ▼                                          │
│                       ┌─────────────┐                                   │
│                       │  Messages   │──────────────────▶ (back to       │
│                       │             │                     update())     │
│                       │  - Response │                                   │
│                       │  - Error    │                                   │
│                       │  - KeyPress │                                   │
│                       └─────────────┘                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Message Flow for Chat

```
User types message
       │
       ▼
┌─────────────────┐
│ KeyPressMessage │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────────────┐
│ update()        │────▶│ SendChatCommand         │
│ - Buffer input  │     │ - Async API call        │
│ - On Enter:     │     │ - Returns ChatResponse  │
└─────────────────┘     └────────────┬────────────┘
                                     │
                                     ▼
                        ┌─────────────────────────┐
                        │ ChatResponseMessage     │
                        └────────────┬────────────┘
                                     │
                                     ▼
                        ┌─────────────────────────┐
                        │ update()                │
                        │ - Append to history     │
                        │ - Handle tool calls     │
                        └────────────┬────────────┘
                                     │
                                     ▼
                        ┌─────────────────────────┐
                        │ view()                  │
                        │ - Render conversation   │
                        │ - Show spinner/status   │
                        └─────────────────────────┘
```

## File Structure

```
tui/
├── tui4j/
│   ├── Tui4jTUI.groovy              # Main application Model
│   ├── Tui4jTheme.groovy            # Lipgloss-based theme
│   ├── messages/
│   │   ├── ChatResponseMessage.groovy
│   │   ├── ToolResultMessage.groovy
│   │   ├── StreamChunkMessage.groovy
│   │   ├── StatusMessage.groovy
│   │   └── ErrorMessage.groovy
│   ├── commands/
│   │   ├── SendChatCommand.groovy
│   │   ├── ExecuteToolCommand.groovy
│   │   └── RefreshSidebarCommand.groovy
│   └── components/
│       ├── ConversationView.groovy   # Nested Model for chat history
│       ├── InputField.groovy         # TextInput wrapper
│       ├── StatusBar.groovy          # Status display
│       ├── SidebarView.groovy        # File tree / diagnostics
│       └── SpinnerView.groovy        # Loading indicator
├── jexer/                            # (existing)
├── lanterna/                         # (existing)
├── shared/                           # (existing)
├── JexerTUI.groovy                   # (existing)
├── LanternaTUI.groovy                # (existing)
└── Tui4jTUI.groovy                   # NEW - Entry point wrapper
```

## Dependency

Add to `glm.groovy`:

```groovy
//DEPS com.williamcallahan:tui4j:0.2.5
```

## Implementation Phases

### Phase 1: Core Setup (Week 1)

#### 1.1 Add Dependency and Entry Point

```groovy
// In glm.groovy
//DEPS com.williamcallahan:tui4j:0.2.5

// Add TUI selector
def selectTui(String type) {
    switch (type) {
        case 'tui4j': return new Tui4jTUI()
        case 'lanterna': return new LanternaTUI()
        case 'jexer': return new JexerTUI()
        default: return new LanternaTUI()
    }
}
```

#### 1.2 Create Base Model Structure

```groovy
// tui/tui4j/Tui4jTUI.groovy
package tui.tui4j

import com.williamcallahan.tui4j.compat.bubbletea.*
import com.williamcallahan.tui4j.compat.bubbletea.message.*
import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.*
import core.GlmClient
import core.Config

class Tui4jTUI implements Model {
    // Application state
    private List<Map> conversationHistory = []
    private String inputBuffer = ""
    private boolean loading = false
    private String statusMessage = ""
    private int scrollPosition = 0
    private boolean sidebarVisible = true
    
    // Dependencies
    private final GlmClient client
    private final Config config
    private final String apiKey
    
    // Nested components (bubbles)
    private com.williamcallahan.tui4j.compat.bubbletea.bubbles.textinput.TextInput textInput
    private com.williamcallahan.tui4j.compat.bubbletea.bubbles.spinner.Spinner spinner
    
    Tui4jTUI() {
        this.config = Config.load()
        this.apiKey = core.Auth.getApiKey()
        this.client = new GlmClient(apiKey, config)
        
        // Initialize nested bubbles
        this.textInput = new com.williamcallahan.tui4j.compat.bubbletea.bubbles.textinput.TextInput()
        textInput.setPlaceholder("Enter your message...")
        textInput.setCharLimit(10000)
        
        this.spinner = new com.williamcallahan.tui4j.compat.bubbletea.bubbles.spinner.Spinner()
    }
    
    @Override
    Command init() {
        // Initialize with welcome message
        return Command.batch(
            textInput.init(),
            spinner.init(),
            () -> new StatusMessage("Ready. Model: ${config.model}")
        )
    }
    
    @Override
    UpdateResult<? extends Model> update(Message msg) {
        // Delegate to specific handlers
        if (msg instanceof KeyPressMessage) {
            return handleKeyPress((KeyPressMessage) msg)
        }
        if (msg instanceof ChatResponseMessage) {
            return handleChatResponse((ChatResponseMessage) msg)
        }
        if (msg instanceof ErrorMessage) {
            return handleError((ErrorMessage) msg)
        }
        if (msg instanceof StatusMessage) {
            this.statusMessage = ((StatusMessage) msg).text
            return UpdateResult.from(this)
        }
        
        // Forward to nested components
        def inputResult = textInput.update(msg)
        this.textInput = inputResult.model()
        
        if (loading) {
            def spinnerResult = spinner.update(msg)
            this.spinner = spinnerResult.model()
            return UpdateResult.from(this, 
                Command.batch(inputResult.command(), spinnerResult.command()))
        }
        
        return UpdateResult.from(this, inputResult.command())
    }
    
    private UpdateResult<Tui4jTUI> handleKeyPress(KeyPressMessage key) {
        switch (key.key()) {
            case "enter":
                if (!loading && textInput.value().trim()) {
                    return sendMessage()
                }
                break
            case "ctrl+c":
            case "q":
                return UpdateResult.from(this, QuitMessage::new)
            case "ctrl+s":
                this.sidebarVisible = !sidebarVisible
                break
            case "up":
            case "k":
                scrollUp()
                break
            case "down":
            case "j":
                scrollDown()
                break
        }
        return UpdateResult.from(this)
    }
    
    private UpdateResult<Tui4jTUI> sendMessage() {
        String userMessage = textInput.value().trim()
        
        // Add to history
        conversationHistory << [role: 'user', content: userMessage]
        
        // Clear input and set loading
        textInput.setValue("")
        this.loading = true
        
        // Create async command for API call
        Command sendCmd = new SendChatCommand(client, conversationHistory, config)
        
        return UpdateResult.from(this, Command.batch(
            sendCmd,
            Command.tick(java.time.Duration.ofMillis(100), { t -> new SpinnerTickMessage() })
        ))
    }
    
    private UpdateResult<Tui4jTUI> handleChatResponse(ChatResponseMessage msg) {
        this.loading = false
        conversationHistory << [role: 'assistant', content: msg.content]
        
        // Handle tool calls if present
        if (msg.toolCalls) {
            return UpdateResult.from(this, 
                new ExecuteToolCommand(msg.toolCalls, this))
        }
        
        return UpdateResult.from(this)
    }
    
    @Override
    String view() {
        def theme = Tui4jTheme.instance
        def sb = new StringBuilder()
        
        // Layout: Sidebar (optional) | Main Content
        String mainContent = renderMainContent(theme)
        
        if (sidebarVisible) {
            String sidebar = renderSidebar(theme)
            sb.append(HorizontalJoinDecorator.joinHorizontal(
                Position.Top, sidebar, mainContent))
        } else {
            sb.append(mainContent)
        }
        
        return sb.toString()
    }
    
    private String renderMainContent(Tui4jTheme theme) {
        def sb = new StringBuilder()
        
        // Header
        sb.append(theme.headerStyle.render(" GLM-CLI "))
        sb.append("\n")
        
        // Conversation history
        sb.append(renderConversation(theme))
        sb.append("\n")
        
        // Input area
        if (loading) {
            sb.append(spinner.view()).append(" Thinking...")
        } else {
            sb.append(theme.promptStyle.render("❯ "))
            sb.append(textInput.view())
        }
        sb.append("\n")
        
        // Status bar
        sb.append(theme.statusStyle.render(statusMessage))
        
        return sb.toString()
    }
    
    private String renderConversation(Tui4jTheme theme) {
        def sb = new StringBuilder()
        
        for (msg in conversationHistory) {
            if (msg.role == 'user') {
                sb.append(theme.userStyle.render("You: "))
                sb.append(msg.content)
            } else {
                sb.append(theme.assistantStyle.render("Assistant: "))
                sb.append(msg.content)
            }
            sb.append("\n\n")
        }
        
        return sb.toString()
    }
    
    private String renderSidebar(Tui4jTheme theme) {
        // Simplified sidebar for Phase 1
        return theme.sidebarStyle.render("""
│ Files
│ ───────
│ src/
│   main.groovy
│ tests/
│
│ Diagnostics
│ ───────
│ ✓ No issues
""")
    }
    
    // Scroll helpers
    private void scrollUp() { scrollPosition = Math.max(0, scrollPosition - 1) }
    private void scrollDown() { scrollPosition++ }
    
    // Entry point
    static void run() {
        def app = new Tui4jTUI()
        def program = new Program(app)
            .withAltScreen()
            .withMouseAllMotion()
            .withReportFocus()
        
        program.run()
    }
}
```

#### 1.3 Create Theme

```groovy
// tui/tui4j/Tui4jTheme.groovy
package tui.tui4j

import com.williamcallahan.tui4j.compat.bubbletea.lipgloss.*

@Singleton
class Tui4jTheme {
    
    // Colors
    static final String BG_DARK = "#1a1a2e"
    static final String FG_WHITE = "#ffffff"
    static final String ACCENT_BLUE = "#4fc3f7"
    static final String ACCENT_GREEN = "#81c784"
    static final String ACCENT_RED = "#e57373"
    static final String ACCENT_YELLOW = "#fff176"
    static final String BORDER_COLOR = "#3a3a5e"
    
    // Styles
    Style headerStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_BLUE))
        .bold(true)
        .padding(0, 1)
    
    Style userStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_GREEN))
        .bold(true)
    
    Style assistantStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_BLUE))
        .bold(true)
    
    Style promptStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_GREEN))
    
    Style statusStyle = Style.newStyle()
        .foreground(Color.color("#888888"))
        .padding(0, 1)
    
    Style errorStyle = Style.newStyle()
        .foreground(Color.color(ACCENT_RED))
        .bold(true)
    
    Style sidebarStyle = Style.newStyle()
        .width(30)
        .padding(1)
        .border(Border.rounded())
        .borderForeground(Color.color(BORDER_COLOR))
    
    Style codeBlockStyle = Style.newStyle()
        .background(Color.color("#2d2d4a"))
        .padding(1)
        .margin(0, 2)
}
```

### Phase 2: Messages and Commands (Week 1-2)

#### 2.1 Custom Messages

```groovy
// tui/tui4j/messages/ChatResponseMessage.groovy
package tui.tui4j.messages

import com.williamcallahan.tui4j.compat.bubbletea.Message

record ChatResponseMessage(
    String content,
    List<Map> toolCalls,
    Map usage
) implements Message {}

// tui/tui4j/messages/ToolResultMessage.groovy
record ToolResultMessage(
    String toolCallId,
    String result
) implements Message {}

// tui/tui4j/messages/StreamChunkMessage.groovy
record StreamChunkMessage(
    String chunk,
    boolean isComplete
) implements Message {}

// tui/tui4j/messages/StatusMessage.groovy
record StatusMessage(String text) implements Message {}

// tui/tui4j/messages/ErrorMessage.groovy
record ErrorMessage(String error, Throwable cause) implements Message {}
```

#### 2.2 Commands

```groovy
// tui/tui4j/commands/SendChatCommand.groovy
package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import core.GlmClient
import models.ChatRequest
import models.Message as ChatMessage

class SendChatCommand implements Command {
    private final GlmClient client
    private final List<Map> history
    private final def config
    
    SendChatCommand(GlmClient client, List<Map> history, config) {
        this.client = client
        this.history = history
        this.config = config
    }
    
    @Override
    Message execute() {
        try {
            def messages = history.collect { 
                new ChatMessage(role: it.role, content: it.content) 
            }
            
            def request = new ChatRequest(
                model: config.model,
                messages: messages,
                stream: false
            )
            
            def response = client.chat(request)
            
            return new ChatResponseMessage(
                response.choices[0].message.content,
                response.choices[0].message.tool_calls,
                response.usage
            )
        } catch (Exception e) {
            return new ErrorMessage("API Error: ${e.message}", e)
        }
    }
}
```

### Phase 3: Component Composition (Week 2)

#### 3.1 Conversation View Component

```groovy
// tui/tui4j/components/ConversationView.groovy
package tui.tui4j.components

import com.williamcallahan.tui4j.compat.bubbletea.*
import com.williamcallahan.tui4j.compat.bubbletea.message.*
import tui.tui4j.Tui4jTheme
import tui.shared.MarkdownRenderer

class ConversationView implements Model {
    private List<Map> messages = []
    private int scrollOffset = 0
    private int visibleHeight = 20
    private final Tui4jTheme theme = Tui4jTheme.instance
    
    void addMessage(Map msg) {
        messages << msg
        scrollToBottom()
    }
    
    void scrollUp() {
        scrollOffset = Math.max(0, scrollOffset - 1)
    }
    
    void scrollDown() {
        scrollOffset = Math.min(messages.size() - 1, scrollOffset + 1)
    }
    
    void scrollToBottom() {
        scrollOffset = Math.max(0, messages.size() - visibleHeight)
    }
    
    @Override
    Command init() { null }
    
    @Override
    UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof KeyPressMessage) {
            switch (((KeyPressMessage) msg).key()) {
                case "pageup": scrollUp(); break
                case "pagedown": scrollDown(); break
            }
        }
        return UpdateResult.from(this)
    }
    
    @Override
    String view() {
        def sb = new StringBuilder()
        
        def visibleMessages = messages.drop(scrollOffset).take(visibleHeight)
        
        for (m in visibleMessages) {
            if (m.role == 'user') {
                sb.append(theme.userStyle.render("You "))
                sb.append(m.content)
            } else if (m.role == 'assistant') {
                sb.append(theme.assistantStyle.render("Assistant "))
                // Render markdown
                sb.append(MarkdownRenderer.render(m.content))
            } else if (m.role == 'tool') {
                sb.append(theme.statusStyle.render("Tool Result: "))
                sb.append(m.content.take(200))
            }
            sb.append("\n\n")
        }
        
        // Scroll indicator
        if (messages.size() > visibleHeight) {
            int pos = (scrollOffset * 100 / (messages.size() - visibleHeight)) as int
            sb.append(theme.statusStyle.render("─── ${pos}% ───"))
        }
        
        return sb.toString()
    }
}
```

#### 3.2 Sidebar Component

```groovy
// tui/tui4j/components/SidebarView.groovy
package tui.tui4j.components

import com.williamcallahan.tui4j.compat.bubbletea.*
import com.williamcallahan.tui4j.compat.bubbletea.bubbles.list.*
import tui.tui4j.Tui4jTheme

class SidebarView implements Model {
    private List fileList
    private List diagnosticsList
    private String activeTab = "files"  // "files" | "diagnostics" | "lsp"
    private final Tui4jTheme theme = Tui4jTheme.instance
    
    SidebarView(int width, int height) {
        // Initialize file list
        Item[] fileItems = []
        this.fileList = new List(fileItems, new DefaultDelegate(), width, height / 2)
        
        // Initialize diagnostics list
        Item[] diagItems = []
        this.diagnosticsList = new List(diagItems, new DefaultDelegate(), width, height / 2)
    }
    
    void updateFiles(List<String> files) {
        // Convert files to list items
        Item[] items = files.collect { 
            new DefaultItem(it, getFileIcon(it)) 
        }.toArray(new Item[0])
        this.fileList = new List(items, new DefaultDelegate(), 
            fileList.width(), fileList.height())
    }
    
    void updateDiagnostics(List<Map> diagnostics) {
        Item[] items = diagnostics.collect {
            new DefaultItem(
                "${it.severity}: ${it.message}",
                "${it.file}:${it.line}"
            )
        }.toArray(new Item[0])
        this.diagnosticsList = new List(items, new DefaultDelegate(),
            diagnosticsList.width(), diagnosticsList.height())
    }
    
    private String getFileIcon(String filename) {
        if (filename.endsWith('.groovy')) return '📜'
        if (filename.endsWith('.java')) return '☕'
        if (filename.endsWith('.md')) return '📝'
        if (filename.endsWith('/')) return '📁'
        return '📄'
    }
    
    @Override
    Command init() {
        return Command.batch(fileList.init(), diagnosticsList.init())
    }
    
    @Override
    UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage) {
            def key = ((com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage) msg)
            switch (key.key()) {
                case "tab":
                    activeTab = activeTab == "files" ? "diagnostics" : "files"
                    return UpdateResult.from(this)
            }
        }
        
        // Forward to active list
        if (activeTab == "files") {
            def result = fileList.update(msg)
            this.fileList = (List) result.model()
            return UpdateResult.from(this, result.command())
        } else {
            def result = diagnosticsList.update(msg)
            this.diagnosticsList = (List) result.model()
            return UpdateResult.from(this, result.command())
        }
    }
    
    @Override
    String view() {
        def sb = new StringBuilder()
        
        // Tabs
        String filesTab = activeTab == "files" 
            ? theme.headerStyle.render(" Files ") 
            : theme.statusStyle.render(" Files ")
        String diagTab = activeTab == "diagnostics"
            ? theme.headerStyle.render(" Diagnostics ")
            : theme.statusStyle.render(" Diagnostics ")
        
        sb.append(filesTab).append(diagTab).append("\n")
        sb.append("─".multiply(30)).append("\n")
        
        // Active list
        if (activeTab == "files") {
            sb.append(fileList.view())
        } else {
            sb.append(diagnosticsList.view())
        }
        
        return theme.sidebarStyle.render(sb.toString())
    }
}
```

### Phase 4: Tool Integration (Week 2-3)

#### 4.1 Tool Execution Command

```groovy
// tui/tui4j/commands/ExecuteToolCommand.groovy
package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import tools.*

class ExecuteToolCommand implements Command {
    private final List<Map> toolCalls
    private final def tuiRef
    
    ExecuteToolCommand(List<Map> toolCalls, tuiRef) {
        this.toolCalls = toolCalls
        this.tuiRef = tuiRef
    }
    
    @Override
    Message execute() {
        def results = []
        
        for (call in toolCalls) {
            try {
                def tool = getTool(call.function.name)
                def args = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(call.function.arguments, Map)
                
                def result = tool.execute(args)
                results << [
                    tool_call_id: call.id,
                    role: 'tool',
                    content: result.toString()
                ]
            } catch (Exception e) {
                results << [
                    tool_call_id: call.id,
                    role: 'tool',
                    content: "Error: ${e.message}"
                ]
            }
        }
        
        return new ToolResultMessage(
            toolCalls[0].id,
            results.collect { it.content }.join("\n")
        )
    }
    
    private Tool getTool(String name) {
        switch (name) {
            case 'read_file': return new ReadFileTool()
            case 'write_file': return new WriteFileTool()
            case 'list_files': return new ListFilesTool()
            case 'grep': return new GrepTool()
            case 'glob': return new GlobTool()
            case 'web_search': return new WebSearchTool()
            default: throw new IllegalArgumentException("Unknown tool: $name")
        }
    }
}
```

### Phase 5: Streaming Support (Week 3)

#### 5.1 Streaming Chat Command

```groovy
// tui/tui4j/commands/StreamChatCommand.groovy
package tui.tui4j.commands

import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import tui.tui4j.messages.*
import core.GlmClient
import models.ChatRequest

class StreamChatCommand implements Command {
    private final GlmClient client
    private final List<Map> history
    private final def config
    private final java.util.concurrent.BlockingQueue<Message> messageQueue
    
    StreamChatCommand(GlmClient client, List<Map> history, config, queue) {
        this.client = client
        this.history = history
        this.config = config
        this.messageQueue = queue
    }
    
    @Override
    Message execute() {
        def fullContent = new StringBuilder()
        
        try {
            def messages = history.collect {
                new models.Message(role: it.role, content: it.content)
            }
            
            def request = new ChatRequest(
                model: config.model,
                messages: messages,
                stream: true
            )
            
            client.streamChat(request) { chunk ->
                if (chunk.choices && chunk.choices[0].delta?.content) {
                    String content = chunk.choices[0].delta.content
                    fullContent.append(content)
                    
                    // Push chunk to queue for immediate rendering
                    messageQueue.offer(new StreamChunkMessage(content, false))
                }
            }
            
            return new StreamChunkMessage(fullContent.toString(), true)
        } catch (Exception e) {
            return new ErrorMessage("Stream Error: ${e.message}", e)
        }
    }
}
```

### Phase 6: Polish and Integration (Week 3-4)

#### 6.1 Integration with glm.groovy

```groovy
// Add to glm.groovy main command handler

@Command(name = 'tui', description = 'Launch Terminal UI')
class TuiCommand implements Runnable {
    @Option(names = ['-t', '--type'], description = 'TUI type: lanterna, jexer, tui4j')
    String type = 'lanterna'
    
    void run() {
        switch (type) {
            case 'tui4j':
                tui.tui4j.Tui4jTUI.run()
                break
            case 'jexer':
                new tui.JexerTUI().run()
                break
            default:
                new tui.LanternaTUI().run()
        }
    }
}
```

#### 6.2 Configuration

```toml
# ~/.glm/config.toml
[tui]
type = "tui4j"   # Options: lanterna, jexer, tui4j
sidebar = true
theme = "dark"

[tui.keybindings]
send = "enter"
quit = "ctrl+c"
toggle_sidebar = "ctrl+s"
scroll_up = "k"
scroll_down = "j"
```

## Testing Strategy

### Unit Tests

```groovy
// tests/Tui4jTUITest.groovy
class Tui4jTUITest {
    
    void testInitReturnsCommand() {
        def tui = new Tui4jTUI()
        def cmd = tui.init()
        assert cmd != null
    }
    
    void testKeyPressQuitReturnsQuitMessage() {
        def tui = new Tui4jTUI()
        def msg = new KeyPressMessage("q", KeyType.Character, false, [])
        def result = tui.update(msg)
        
        // Execute the command and check for QuitMessage
        def message = result.command()?.execute()
        assert message instanceof QuitMessage
    }
    
    void testChatResponseUpdatesHistory() {
        def tui = new Tui4jTUI()
        def msg = new ChatResponseMessage("Hello!", null, [:])
        def result = tui.update(msg)
        
        // Verify conversation was updated
        assert tui.conversationHistory.size() == 1
        assert tui.conversationHistory[0].content == "Hello!"
    }
}
```

### Integration Tests

```groovy
// tests/Tui4jIntegrationTest.groovy
class Tui4jIntegrationTest {
    
    void testFullChatFlow() {
        def tui = new Tui4jTUI()
        
        // Simulate user input
        tui.update(new KeyPressMessage("h", KeyType.Character, false, ['h']))
        tui.update(new KeyPressMessage("i", KeyType.Character, false, ['i']))
        tui.update(new KeyPressMessage("enter", KeyType.Enter, false, []))
        
        // Verify loading state
        assert tui.loading == true
        
        // Simulate response
        tui.update(new ChatResponseMessage("Hello!", null, [:]))
        
        // Verify state reset
        assert tui.loading == false
        assert tui.conversationHistory.size() == 2
    }
}
```

## Migration Path

### From Lanterna/Jexer

1. **Phase 1**: Add TUI4J as optional alternative (`--tui tui4j`)
2. **Phase 2**: Feature parity with existing implementations
3. **Phase 3**: Set TUI4J as default (configurable)
4. **Phase 4**: Deprecate older implementations (optional)

### Coexistence Strategy

All three TUI implementations can coexist:

```bash
# Use different TUIs
glm tui                    # Default (Lanterna)
glm tui --type=lanterna    # Explicit Lanterna
glm tui --type=jexer       # Jexer
glm tui --type=tui4j       # TUI4J
```

## Timeline Summary

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1 | Week 1 | Core Model, Theme, Basic UI |
| Phase 2 | Week 1-2 | Messages, Commands, API Integration |
| Phase 3 | Week 2 | Component Composition (Sidebar, Conversation) |
| Phase 4 | Week 2-3 | Tool Execution Integration |
| Phase 5 | Week 3 | Streaming Support |
| Phase 6 | Week 3-4 | Polish, Testing, Documentation |

**Total Estimated Time**: 4 weeks

## References

- **TUI4J Repository**: https://github.com/WilliamAGH/tui4j
- **TUI4J Tutorial**: https://github.com/WilliamAGH/tui4j/blob/main/docs/tutorial.md
- **Bubble Tea (Go)**: https://github.com/charmbracelet/bubbletea
- **Lipgloss (Go)**: https://github.com/charmbracelet/lipgloss
- **Elm Architecture**: https://guide.elm-lang.org/architecture/
- **GLM-CLI Lanterna Plan**: LANTERNA_TUI_IMPLEMENTATION_PLAN.md
