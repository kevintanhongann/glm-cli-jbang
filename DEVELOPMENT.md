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

## Web Search Tool Development

### Prerequisites

- Valid GLM-4 API key with web search permissions
- Internet connectivity for API access
- Review [WEB_SEARCH_IMPLEMENTATION.md](./WEB_SEARCH_IMPLEMENTATION.md) for full details

### Testing Web Search

#### Manual Testing

```bash
# Basic search
./glm.groovy agent "Search for recent news about Java 21"

# Domain-filtered search
./glm.groovy agent "Find documentation on github.com about Groovy 4"

# Recent content search
./glm.groovy agent "Search for AI news from last week"

# Multiple searches in one task
./glm.groovy agent "Research latest GLM-4 model features and write a summary"
```

#### Debug Web Search Tool

Enable debug logging:

```bash
export GLM_LOG_LEVEL=DEBUG
./glm.groovy agent "Search for test query"
```

Add logging in `WebSearchClient.groovy`:

```groovy
String search(String searchQuery, Map<String, Object> options = [:]) {
    println "DEBUG: Search Query: ${searchQuery}"
    println "DEBUG: Options: ${options}"

    String jsonBody = mapper.writeValueAsString(requestBody)
    println "DEBUG: Request Body: ${jsonBody}"

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL))
        .header("Authorization", "Bearer ${apiKey.take(8)}...")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build()

    HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    println "DEBUG: Response Status: ${response.statusCode()}"
    println "DEBUG: Response Length: ${response.body().length()}"

    if (response.statusCode() != 200) {
        println "DEBUG: Error Response: ${response.body()}"
        throw new RuntimeException("Web Search API failed with code ${response.statusCode()}")
    }

    return mapper.readValue(response.body(), WebSearchResponse.class)
}
```

### Common Issues

#### API Endpoint Not Found

**Symptoms**: `Error: Failed to connect to web search API`

**Solution**: Verify correct endpoint in `WebSearchClient.groovy`

```bash
# Test endpoint with curl
curl -X POST https://api.z.ai/api/tools/web_search \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"search_query":"test"}'
```

#### Rate Limiting

**Symptoms**: `Error: Too many requests` or HTTP 429

**Solution**: Implement retry logic with exponential backoff

```groovy
int maxRetries = 3
long delay = 1000 // 1 second

for (int i = 0; i < maxRetries; i++) {
    try {
        return sendRequest()
    } catch (HttpException e) {
        if (e.statusCode == 429 && i < maxRetries - 1) {
            println "Rate limit hit, retrying in ${delay}ms..."
            Thread.sleep(delay)
            delay *= 2 // Exponential backoff
        } else {
            throw e
        }
    }
}
```

Add delay between multiple searches:

```groovy
// In agent loop, after tool call
if (toolName == "web_search") {
    Thread.sleep(1000) // 1 second delay
}
```

#### Empty Results

**Symptoms**: No search results returned

**Solution**: Verify search query and try alternatives

```bash
# Test different queries
./glm.groovy agent "Search for 'xyz123abc'"  # Very specific
./glm.groovy agent "Search for 'Java 21'"  # Broader
./glm.groovy agent "Search for 'Java programming'"  # More generic
```

#### Authentication Errors

**Symptoms**: `Error: Unauthorized (401)`

**Solution**: Check API key and authentication method

```bash
# Verify API key works for chat
./glm.groovy chat "test"

# Check if web search uses different auth
# May need to update WebSearchClient to generate JWT instead of using API key directly
```

#### Timeout Issues

**Symptoms**: Web search appears to hang or times out

**Solution**: Increase timeout or add configuration

```groovy
// In WebSearchClient.groovy constructor
private final HttpClient client

WebSearchClient(String apiKey, int timeoutSeconds = 10) {
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
        .build()
}
```

Add configuration option:

```toml
[web_search]
timeout_seconds = 10
```

### Test Cases

Run through complete test matrix:

```bash
#!/bin/bash
echo "Test 1: Basic search"
./glm.groovy agent "Search for Java 21 features"

echo -e "\nTest 2: Domain filter"
./glm.groovy agent "Find docs on spring.io about Spring Boot"

echo -e "\nTest 3: Recency filter"
./glm.groovy agent "Search for AI news from last week"

echo -e "\nTest 4: Count limit"
./glm.groovy agent "Search for test query, limit to 3 results"

echo -e "\nTest 5: No results"
./glm.groovy agent "Search for xyz123abc"

echo -e "\nTest 6: Invalid input"
./glm.groovy agent "Search for empty query"

echo -e "\nTest 7: Complex task"
./glm.groovy agent "Research Java 21 features and write summary to summary.md"
```

### Performance Testing

Measure response times:

```groovy
long startTime = System.currentTimeMillis()
def response = client.search("test query")
long endTime = System.currentTimeMillis()
long duration = endTime - startTime
println "Search took ${duration}ms"
```

Benchmark with different parameters:

```bash
# Test different counts
time ./glm.groovy agent "Search with count=5"
time ./glm.groovy agent "Search with count=10"
time ./glm.groovy agent "Search with count=50"
```

### Integration Testing

Verify tool works in agent loop:

```bash
# Test that agent can use web_search in complex tasks
./glm.groovy agent "Search for recent documentation about Groovy, then write a guide called groovy_guide.md"

# Expected behavior:
# 1. Agent calls web_search
# 2. Receives search results
# 3. May call read_file to check if groovy_guide.md exists
# 4. Calls write_file with formatted content
# 5. Confirms completion
```

Test with other tools:

```bash
# Combine web_search with read_file
./glm.groovy agent "Read the README.md and search for any mentioned tools online"

# Combine web_search with write_file
./glm.groovy agent "Search for best practices and add them to the project guidelines"

# Combine web_search with list_files
./glm.groovy agent "List all .groovy files and search for documentation on Groovy closures"
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
