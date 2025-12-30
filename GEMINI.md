# GLM-CLI (JBang Edition)

## Project Overview
GLM-CLI is a command-line interface tool built with **Groovy** and **JBang** that acts as an AI coding companion powered by **Z.ai's GLM-4 models**. It allows developers to interact with the LLM directly from the terminal for chat, code questions, and autonomous file editing tasks.

The project is designed as a single-script application (`glm.groovy`) that bootstraps a full CLI environment using standard Java/Groovy libraries.

### Key Features
*   **Interactive Chat**: Conversational interface with real-time streaming (SSE).
*   **Autonomous Agent**: A "ReAct" (Reasoning + Acting) loop that can read/write files to complete complex tasks.
*   **Tooling System**: Extensible tool interface with built-in file system capabilities (`read_file`, `write_file`, `list_files`).
*   **Safety**: Explicit user confirmation required for file modifications.
*   **Native Performance**: leverages JVM optimization via JBang.

## Architecture & Technology
*   **Runtime**: [JBang](https://jbang.dev/) (manages JDK and dependencies).
*   **Language**: Groovy 4.x.
*   **CLI Framework**: `picocli` (Command parsing, help generation).
*   **HTTP Client**: `java.net.http.HttpClient` (Java 11+ standard client).
*   **JSON/TOML**: `jackson-databind` and `jackson-dataformat-toml`.
*   **Auth**: `java-jwt` for ZhipuAI API token generation.

### Directory Structure
*   `glm.groovy`: Main entry point and dependency declaration (`//DEPS`, `//SOURCES`).
*   `commands/`: Picocli command implementations (`ChatCommand`, `AgentCommand`).
*   `core/`: Core business logic (`GlmClient` for API, `Agent` for the loop, `Config`).
*   `tools/`: Tool definitions (`Tool` interface and implementations).
*   `models/`: POJOs for API request/response mapping (`ChatRequest`, `ChatResponse`, `Message`).

## Building and Running

### Prerequisites
*   **JBang**: Must be installed (`curl -Ls https://sh.jbang.dev | bash -s - app setup`).
*   **API Key**: A valid GLM-4 API key from [bigmodel.cn](https://open.bigmodel.cn/).

### Execution
You can run the script directly. JBang will handle compilation and dependencies.

```bash
# Set API Key (or configure in ~/.glm/config.toml)
export ZAI_API_KEY=your_api_key_here

# Run Help
./glm.groovy --help

# Start Chat
./glm.groovy chat "Hello world"

# Run Agent
./glm.groovy agent "Read README.md and summarize it"
```

### Installation
To install `glm` as a system command:
```bash
jbang app install --name glm glm.groovy
```

## Development Conventions

*   **Dependency Management**: All dependencies are declared in the header of `glm.groovy` using `//DEPS`.
*   **Source Inclusion**: New source files must be explicitly registered in `glm.groovy` using `//SOURCES` to be visible during JBang execution.
*   **Configuration**: The app supports a configuration file at `~/.glm/config.toml` for API keys and behavior settings.
*   **Safety**: The `Agent` class implements safety checks (interactive confirmation) before executing destructive tools like `write_file`.
