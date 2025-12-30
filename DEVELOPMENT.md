# Development Guide

This document provides comprehensive guidance for developing and extending GLM-CLI.

## Table of Contents

- [Development Setup](#development-setup)
- [Local Development](#local-development)
- [Debugging](#debugging)
- [Testing](#testing)
- [Adding Features](#adding-features)
- [Extending Tools](#extending-tools)
- [Performance Profiling](#performance-profiling)

## Development Setup

### Prerequisites

- **Java 11+** - Required for JBang and compilation
- **JBang** - Script execution and dependency management
- **Git** - Version control
- **IDE** (optional) - IntelliJ IDEA, VSCode, or similar
- **GLM-4 API Key** - For testing with real API

### Clone Repository

```bash
git clone https://github.com/yourusername/glm-cli-jbang.git
cd glm-cli-jbang
```

### Install JBang

```bash
# Using curl
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Using brew
brew install jbang

# Verify installation
jbang --version
```

### Verify Setup

```bash
# Run with help
./glm.groovy --version

# Expected output: glm-cli 0.1
```

## Local Development

### Running the CLI

#### Direct Execution

```bash
# Run directly
./glm.groovy chat "Hello!"

# Run with specific model
./glm.groovy chat --model glm-4 "Complex task"

# Run agent
./glm.groovy agent "Read README.md"
```

#### Global Installation

```bash
# Install globally
jbang app install --name glm glm.groovy

# Now run from anywhere
glm chat "Test"
```

#### Force Reinstall

```bash
# During development, force reinstall for changes
jbang app install --force --name glm glm.groovy
```

### IDE Support

#### IntelliJ IDEA

Open project with full dependency resolution:

```bash
jbang edit glm.groovy
```

**Configuration:**
1. Open IntelliJ IDEA
2. File > Open
3. Select project directory
4. Configure SDK to use JBang's JDK
5. Enable Groovy support

**Tips:**
- Use Groovy plugin for syntax highlighting
- Install Picocli plugin for CLI annotations
- Use "Run with JBang" run configuration

#### VSCode

```bash
# Install Groovy extension
code --install-extension groovygroovy.vscode-groovy

# Open project
code .
```

**Create `.vscode/settings.json`:**

```json
{
  "groovy.compileClasspath": ["lib/*"],
  "files.associations": {
    "*.groovy": "groovy"
  },
  "editor.formatOnSave": true
}
```

### Development Workflow

1. **Edit source files** in appropriate directories

```
glm-cli-jbang/
├── commands/     # Modify CLI commands
├── core/         # Modify core logic
├── tools/        # Add/modify tools
└── models/       # Add/modify data models
```

2. **Update dependencies** in `glm.groovy`

```groovy
//DEPS new.library:version
//DEPS another.library:version
```

3. **Register new sources** in `glm.groovy`

```groovy
//SOURCES commands/NewCommand.groovy
//SOURCES core/NewComponent.groovy
```

4. **Test locally**

```bash
./glm.groovy chat "Test new feature"
```

5. **Iterate** until satisfied

## Debugging

### Enabling Debug Logging

#### Environment Variable

```bash
export GLM_LOG_LEVEL=DEBUG
./glm.groovy chat "Test"
```

#### Config File

```toml
[logging]
level = "DEBUG"
file = "/path/to/debug.log"
```

#### Verbosity Flag (if implemented)

```bash
./glm.groovy -v agent "Debug this task"
```

### Debugging Agent Loop

#### Check Conversation History

The agent maintains a conversation history. To inspect:

1. Add logging statement in `Agent.groovy`:

```groovy
void run(String prompt) {
    history.add(new Message("user", prompt))
    println "History size: ${history.size()}"
    println "Last message: ${history.last()}"
    // ... rest of code
}
```

#### Trace Tool Calls

Log tool execution details:

```groovy
def toolCalls = message.toolCalls
toolCalls.each { toolCall ->
    println "Tool called: ${toolCall.function.name}"
    println "Arguments: ${toolCall.function.arguments}"
    // ... execute tool
    println "Result: ${result}"
}
```

#### Inspect API Requests

Log request before sending:

```groovy
String sendMessage(ChatRequest request) {
    String jsonBody = mapper.writeValueAsString(request)
    println "Sending request: ${jsonBody}" // Debug log
    // ... send request
}
```

### Common Debugging Scenarios

#### Issue: Tool not found

**Symptoms**: `Error: Tool not found`

**Debug Steps**:
1. Check tool is registered:
   ```groovy
   agent.registerTool(new MyTool())
   ```

2. Check tool name matches:
   ```groovy
   String getName() { "my_tool" } // Must match what LLM calls
   ```

3. Check tool is in correct package:
   ```groovy
   package tools
   ```

#### Issue: Agent stuck in loop

**Symptoms**: Repeated tool calls without progress

**Debug Steps**:
1. Add step counter:
   ```groovy
   int steps = 0
   while (steps < maxSteps) {
       // ... agent logic
       steps++
       println "Step ${steps} of ${maxSteps}"
   }
   ```

2. Check tool result format:
   ```groovy
   // Ensure result is a string
   return "Success: ${result}"
   // Not: return result (if result is not string)
   ```

3. Add max steps limit:
   ```groovy
   agent.setMaxSteps(10)
   ```

#### Issue: Diff shows no changes

**Symptoms**: Write operation shows empty diff

**Debug Steps**:
1. Check file normalization:
   ```groovy
   Path path = Paths.get(pathStr).normalize()
   println "Normalized path: ${path}"
   ```

2. Check file exists:
   ```groovy
   if (Files.exists(path)) {
       println "File exists, will show diff"
   } else {
       println "New file, will show full content"
   }
   ```

3. Check content comparison:
   ```groovy
   List<String> original = Files.readAllLines(path)
   List<String> revised = newContent.lines().toList()
   println "Original lines: ${original.size()}"
   println "Revised lines: ${revised.size()}"
   ```

### Debugging HTTP Requests

#### Enable HTTP Logging

Add logging in `GlmClient.groovy`:

```groovy
String sendMessage(ChatRequest request) {
    String jsonBody = mapper.writeValueAsString(request)
    println "=== HTTP Request ==="
    println "URL: ${BASE_URL}"
    println "Headers: Authorization: Bearer ${token.take(8)}..."
    println "Body: ${jsonBody.take(100)}..."
    println "==================="
    // ... send request
}
```

#### Inspect Response

```groovy
HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
println "=== HTTP Response ==="
println "Status: ${response.statusCode()}"
println "Body: ${response.body().take(200)}..."
println "===================="
```

#### Test API with curl

```bash
# Generate JWT token (debug mode in agent)
curl -X POST https://open.bigmodel.cn/api/paas/v4/chat/completions \
  -H "Authorization: Bearer <your-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "glm-4-flash",
    "messages": [{"role": "user", "content": "test"}]
  }'
```

## Testing

### Manual Testing

#### Test Chat Command

```bash
# Simple chat
./glm.groovy chat "Hello!"

# Specific model
./glm.groovy chat --model glm-4 "Complex question"

# Interactive mode
./glm.groovy chat
# Then type messages interactively
```

#### Test Agent Command

```bash
# Simple task
./glm.groovy agent "Create a test.txt file with Hello World"

# Complex task
./glm.groovy agent "Refactor User class and add tests"

# Read-only task
./glm.groovy agent "Summarize the project structure"
```

### Test Scenarios

#### File Operations

```bash
# Create file
./glm.groovy agent "Create a new file called test.groovy with a Hello World class"

# Read file
./glm.groovy agent "Read the README.md file and tell me what it's about"

# Modify file
./glm.groovy agent "Update the README.md to add a new section called 'Testing'"

# Delete file (via agent modifying content)
./glm.groovy agent "Remove the test.groovy file"
```

#### Error Handling

```bash
# Test with invalid path
./glm.groovy agent "Read /nonexistent/file.txt"

# Test with permission denied
./glm.groovy agent "Write to /root/test.txt"

# Test with no API key
unset ZAI_API_KEY
./glm.groovy chat "Test"
```

### Adding Automated Tests

#### Test Structure

Create `tests/` directory:

```
glm-cli-jbang/
├── tests/
│   ├── tools/
│   │   └── ReadFileToolSpec.groovy
│   ├── core/
│   │   └── GlmClientSpec.groovy
│   └── AgentSpec.groovy
```

#### Example Test (Spock Framework)

```groovy
// tests/tools/ReadFileToolSpec.groovy
package tools

import spock.lang.Specification

class ReadFileToolSpec extends Specification {
    def "read file returns content when file exists"() {
        given: "a read file tool and test file"
        def tool = new ReadFileTool()
        new File("test.txt").write("Hello, World!")

        when: "reading the file"
        def result = tool.execute([path: "test.txt"])

        then: "content is returned"
        result.contains("Hello, World!")
        !result.startsWith("Error:")

        cleanup:
        new File("test.txt").delete()
    }

    def "read file returns error when file does not exist"() {
        given: "a read file tool"
        def tool = new ReadFileTool()

        when: "reading nonexistent file"
        def result = tool.execute([path: "nonexistent.txt"])

        then: "error is returned"
        result.startsWith("Error:")
        result.contains("not found")
    }
}
```

#### Running Tests

When test framework is integrated:

```bash
# Run all tests
./glm.groovy test

# Run specific test file
./glm.groovy test tests/tools/ReadFileToolSpec.groovy

# Run with coverage
./glm.groovy test --coverage
```

## Adding Features

### Adding a New Command

#### Step 1: Create Command Class

```groovy
// commands/ReviewCommand.groovy
package commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import core.GlmClient
import core.Config

@Command(name = "review", description = "Review code with AI")
class ReviewCommand implements Runnable {

    @Option(names = ["-f", "--file"], description = "File to review")
    String filePath

    @Override
    void run() {
        Config config = Config.load()
        String apiKey = System.getenv("ZAI_API_KEY") ?: config.api.key

        if (!apiKey) {
            System.err.println("Error: API Key required")
            return
        }

        if (!filePath) {
            System.err.println("Error: --file parameter required")
            return
        }

        GlmClient client = new GlmClient(apiKey)
        // ... implement review logic
    }
}
```

#### Step 2: Register Command

Add to `commands/GlmCli.groovy`:

```groovy
@Command(
    name = "glm",
    description = "GLM-4 based AI coding agent",
    subcommands = [
        ChatCommand.class,
        AgentCommand.class,
        ReviewCommand.class  // Add new command
    ]
)
class GlmCli implements Runnable {
    // ...
}
```

#### Step 3: Add to Sources

Add to `glm.groovy`:

```groovy
//SOURCES commands/ReviewCommand.groovy
```

#### Step 4: Test

```bash
./glm.groovy review --file src/User.groovy
```

### Adding a New Model

#### Step 1: Create Model Class

```groovy
// models/ReviewRequest.groovy
package models

class ReviewRequest {
    String filePath
    String guidelines

    ReviewRequest() {}

    ReviewRequest(String filePath, String guidelines) {
        this.filePath = filePath
        this.guidelines = guidelines
    }
}
```

#### Step 2: Add Jackson Annotations

```groovy
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class ReviewRequest {
    @JsonProperty("file_path")
    String filePath

    @JsonProperty("guidelines")
    String guidelines
}
```

## Extending Tools

See [TOOLS.md](./TOOLS.md#creating-custom-tools) for detailed guide.

Quick example:

```groovy
// tools/GrepTool.groovy
package tools

class GrepTool implements Tool {
    @Override
    String getName() { "grep" }

    @Override
    String getDescription() {
        "Search for patterns in file contents"
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                pattern: [type: "string", description: "Regex pattern"],
                path: [type: "string", description: "File path"]
            ],
            required: ["pattern", "path"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String pattern = args.get("pattern")
            String path = args.get("path")
            def file = new File(path)

            if (!file.exists()) {
                return "Error: File not found: ${path}"
            }

            def matcher = file.text =~ pattern
            def matches = matcher.findAll()
            return "Found ${matches.size()} matches:\n${matches.join('\n')}"
        } catch (Exception e) {
            return "Error: ${e.message}"
        }
    }
}
```

## Performance Profiling

### Measuring Startup Time

```bash
# Time the CLI startup
time ./glm.groovy --help
```

Add profiling code:

```groovy
class Main {
    static void main(String... args) {
        long start = System.currentTimeMillis()

        // CLI initialization
        int exitCode = new CommandLine(new GlmCli()).execute(args)

        long end = System.currentTimeMillis()
        println "Startup time: ${end - start}ms"

        System.exit(exitCode)
    }
}
```

### Measuring API Latency

```groovy
class GlmClient {
    String sendMessage(ChatRequest request) {
        long start = System.nanoTime()

        // ... send request

        long end = System.nanoTime()
        double latency = (end - start) / 1_000_000.0
        println "API latency: ${String.format('%.2f', latency)}ms"

        return response.body()
    }
}
```

### Memory Profiling

```bash
# Run with JVM memory logging
java -Xlog:gc* -jar glm-cli.jar --help

# Or use environment variable
export JAVA_OPTS="-Xlog:gc*"
./glm.groovy --help
```

## Best Practices

### Code Organization

- Keep classes focused and single-purpose
- Use packages to group related code
- Avoid circular dependencies
- Prefer composition over inheritance

### Error Handling

- Return descriptive error strings from tools
- Use try-catch for external operations
- Log errors for debugging
- Validate input before processing

### Testing

- Test both success and failure cases
- Test edge cases (empty input, null values)
- Mock external dependencies (API, file system)
- Use descriptive test names

### Performance

- Cache frequently accessed data
- Minimize API calls
- Use streaming for large responses
- Optimize hot paths in code

## References

- [STYLE_GUIDE.md](./STYLE_GUIDE.md) - Coding conventions
- [AGENTS.md](./AGENTS.md) - Agent system guide
- [TOOLS.md](./TOOLS.md) - Tool development
- [CONTRIBUTING.md](./CONTRIBUTING.md) - Contribution process
- [Architecture](./architecture.md) - Technical architecture
