# Web Search Tool Implementation Plan

This document provides a comprehensive guide for implementing a web search tool in GLM-CLI, enabling the agent to search the web for current information using Z.AI's Web Search API.

## Overview

The web search tool extends GLM-CLI's capabilities by allowing the agent to:
- Search the web for up-to-date information
- Find recent documentation and news
- Research current trends and package versions
- Access information not available in the model's training data

**Reference**: [Z.AI Web Search API](https://docs.z.ai/guides/tools/web-search)

## Architecture Analysis

### Current Tool System

GLM-CLI implements a ReAct (Reasoning + Acting) agent pattern with tools:

```
Agent → Tool Call → Tool Execution → Result → Agent Loop
```

**Tool Interface Requirements:**
- `getName()`: Unique tool identifier
- `getDescription()`: Tool description for LLM
- `getParameters()`: JSON Schema for parameters
- `execute()`: Tool implementation with error handling

**Registration Pattern:**
```groovy
Agent agent = new Agent(apiKey, model)
agent.registerTool(new WebSearchTool(apiKey))
```

### Web Search API Details

Z.AI provides a structured web search API optimized for LLMs:

**API Capabilities:**
- Intent-enhanced retrieval for LLM-optimized results
- Structured output with title, content, link, media, publish_date, icon
- Customizable search scope (count, domain filter, time range)
- Time-aware output with publication dates

**Supported Parameters:**
- `search_query` (required): Search query string
- `search_engine` (optional): "search-prime" (default)
- `count` (optional): Number of results (1-50, default: 10)
- `search_domain_filter` (optional): Limit to specific domain
- `search_recency_filter` (optional): Time filter options

## Implementation Components

### 1. Web Search Response Model

**File:** `models/WebSearchResponse.groovy`

Purpose: Define structured response models for type-safe parsing.

```groovy
package models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class WebSearchResponse {
    String id
    String request_id
    Long created
    List<SearchResult> search_result

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchResult {
        String title
        String content
        String link
        String media
        String publish_date
        String icon
        String refer
    }
}
```

**Design Decisions:**
- `@JsonIgnoreProperties(ignoreUnknown)`: Forward-compatible with API changes
- Nested static class: Clean organization for search results
- String types for all fields: Simple, flexible for JSON parsing

### 2. Web Search Client

**File:** `core/WebSearchClient.groovy`

Purpose: Handle HTTP communication with Z.AI Web Search API.

```groovy
package core

import models.WebSearchResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class WebSearchClient {
    private static final String BASE_URL = "https://api.z.ai/api/tools/web_search"
    private final String apiKey
    private final HttpClient client
    private final ObjectMapper mapper

    WebSearchClient(String apiKey) {
        this.apiKey = apiKey
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        this.mapper = new ObjectMapper()
    }

    WebSearchResponse search(String searchQuery, Map<String, Object> options = [:]) {
        try {
            Map<String, Object> requestBody = [
                search_query: searchQuery,
                search_engine: options.getOrDefault("search_engine", "search-prime"),
                count: options.getOrDefault("count", 10)
            ]

            if (options.containsKey("search_domain_filter")) {
                requestBody.put("search_domain_filter", options.get("search_domain_filter"))
            }

            if (options.containsKey("search_recency_filter")) {
                requestBody.put("search_recency_filter", options.get("search_recency_filter"))
            }

            String jsonBody = mapper.writeValueAsString(requestBody)

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Authorization", "Bearer ${apiKey}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw new RuntimeException("Web Search API failed with code ${response.statusCode()}: ${response.body()}")
            }

            return mapper.readValue(response.body(), WebSearchResponse.class)
        } catch (Exception e) {
            throw new RuntimeException("Web search failed: ${e.message}", e)
        }
    }
}
```

**Design Decisions:**
- Separate client class: Clean separation of API logic from tool
- Java 11 HttpClient: No additional dependencies needed
- Configurable timeout: 10 second default
- Error wrapping: Convert exceptions to runtime exceptions with context

### 3. Web Search Tool

**File:** `tools/WebSearchTool.groovy`

Purpose: Implement Tool interface for web search functionality.

```groovy
package tools

import core.WebSearchClient

class WebSearchTool implements Tool {
    private final WebSearchClient client

    WebSearchTool(String apiKey) {
        this.client = new WebSearchClient(apiKey)
    }

    @Override
    String getName() { "web_search" }

    @Override
    String getDescription() {
        "Search the web for current information, news, documentation, or facts. " +
        "Useful for finding recent information that may not be in the training data."
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                search_query: [
                    type: "string",
                    description: "The search query string"
                ],
                count: [
                    type: "integer",
                    description: "Number of results to return (1-50, default: 10)"
                ],
                search_domain_filter: [
                    type: "string",
                    description: "Filter results to specific domain (e.g., www.github.com, spring.io)"
                ],
                search_recency_filter: [
                    type: "string",
                    description: "Time-based filter: noLimit (default), 1d, 1w, 1m, 1y"
                ]
            ],
            required: ["search_query"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String searchQuery = args.get("search_query")

            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return "Error: search_query is required and cannot be empty"
            }

            Map<String, Object> options = [:]

            if (args.containsKey("count")) {
                def count = args.get("count")
                if (count instanceof Number) {
                    int countValue = ((Number) count).intValue()
                    if (countValue < 1 || countValue > 50) {
                        return "Error: count must be between 1 and 50"
                    }
                    options.put("count", countValue)
                } else {
                    return "Error: count must be a number"
                }
            }

            if (args.containsKey("search_domain_filter")) {
                def domainFilter = args.get("search_domain_filter")
                if (domainFilter instanceof String) {
                    options.put("search_domain_filter", domainFilter)
                }
            }

            if (args.containsKey("search_recency_filter")) {
                def recencyFilter = args.get("search_recency_filter")
                if (recencyFilter instanceof String) {
                    if (!["noLimit", "1d", "1w", "1m", "1y"].contains(recencyFilter)) {
                        return "Error: search_recency_filter must be one of: noLimit, 1d, 1w, 1m, 1y"
                    }
                    options.put("search_recency_filter", recencyFilter)
                }
            }

            WebSearchResponse response = client.search(searchQuery, options)

            if (response.search_result == null || response.search_result.isEmpty()) {
                return "No search results found for query: '${searchQuery}'"
            }

            return formatResults(response)
        } catch (Exception e) {
            return "Error performing web search: ${e.message}"
        }
    }

    private String formatResults(WebSearchResponse response) {
        StringBuilder sb = new StringBuilder()
        sb.append("Found ${response.search_result.size()} results:\n\n")

        response.search_result.eachWithIndex { result, i ->
            sb.append("${i + 1}. ${result.title}\n")
            sb.append("   ${result.content.take(200)}...\n")
            sb.append("   URL: ${result.link}\n")

            if (result.publish_date && !result.publish_date.isEmpty()) {
                sb.append("   Published: ${result.publish_date}\n")
            }

            if (result.media && !result.media.isEmpty()) {
                sb.append("   Source: ${result.media}\n")
            }

            sb.append("\n")
        }

        return sb.toString()
    }
}
```

**Design Decisions:**
- Injected WebSearchClient: Dependency injection for testability
- Input validation: Validate parameters before API call
- Type checking: Ensure count is a number, recency filter is valid
- Truncated content: Limit to 200 chars for readability
- Optional fields: Only show publish_date/media if present
- Error messages: Clear, user-friendly error strings

### 4. Agent Integration

**File:** `commands/AgentCommand.groovy`

Add web search tool registration to existing tool registration.

**Find existing registration block:**
```groovy
Agent agent = new Agent(apiKey, model)
agent.registerTool(new ReadFileTool())
agent.registerTool(new WriteFileTool())
agent.registerTool(new ListFilesTool())
```

**Add web search tool:**
```groovy
Agent agent = new Agent(apiKey, model)
agent.registerTool(new ReadFileTool())
agent.registerTool(new WriteFileTool())
agent.registerTool(new ListFilesTool())
agent.registerTool(new WebSearchTool(apiKey))  // ADD THIS LINE
```

### 5. Source Registration

**File:** `glm.groovy`

Add new source files to JBang source list.

**Find existing //SOURCES lines:**
```groovy
//SOURCES models/Message.groovy
//SOURCES models/ChatRequest.groovy
//SOURCES models/ChatResponse.groovy
```

**Add new sources:**
```groovy
//SOURCES models/WebSearchResponse.groovy
//SOURCES core/WebSearchClient.groovy
//SOURCES tools/WebSearchTool.groovy
```

## API Integration Details

### Endpoint and Authentication

**Base URL:**
```
https://api.z.ai/api/tools/web_search
```

**Authentication:**
- Method: Bearer token in Authorization header
- Token: Same JWT used for chat completions
- Format: `Authorization: Bearer <jwt-token>`

**Note:** The web search API may use different authentication. If it doesn't use JWT, update `WebSearchClient.groovy` accordingly.

### Request Format

**HTTP POST:**
```json
{
    "search_query": "Java 21 features",
    "search_engine": "search-prime",
    "count": 10,
    "search_domain_filter": "www.oracle.com",
    "search_recency_filter": "noLimit"
}
```

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `search_query` | string | Yes | - | Search query text |
| `search_engine` | string | No | "search-prime" | Search engine identifier |
| `count` | integer | No | 10 | Number of results (1-50) |
| `search_domain_filter` | string | No | - | Domain restriction |
| `search_recency_filter` | string | No | "noLimit" | Time filter |

### Response Format

**Success Response:**
```json
{
    "id": "20250101120000abc123def456",
    "request_id": "20250101120000abc123def456",
    "created": 1735689600,
    "search_result": [
        {
            "title": "Java 21: What's New",
            "content": "Java 21 introduces virtual threads, pattern matching...",
            "link": "https://example.com/java21",
            "media": "Example Media",
            "publish_date": "2025-09-15",
            "icon": "https://example.com/icon.png",
            "refer": "ref_1"
        }
    ]
}
```

**Error Response:**
```json
{
    "error": {
        "code": "invalid_request",
        "message": "Invalid search_query parameter"
    }
}
```

### Parameter Handling

**Count Validation:**
- Range: 1-50
- Type: Integer
- Default: 10
- Validation: Check in `WebSearchTool.execute()`

**Domain Filter:**
- Format: Full domain (e.g., "www.github.com")
- Optional: Omit if no filtering needed
- Validation: Must be valid string

**Recency Filter:**
- Options: "noLimit", "1d", "1w", "1m", "1y"
- Meaning: All time, 1 day, 1 week, 1 month, 1 year
- Validation: Must be one of valid options

### Error Management

**Error Categories:**

1. **Input Validation Errors**
   - Empty search query
   - Invalid count (not a number or out of range)
   - Invalid recency filter
   - **Action:** Return clear error message immediately

2. **Network Errors**
   - Connection timeout
   - DNS resolution failure
   - **Action:** Retry with exponential backoff (3 attempts)

3. **API Errors**
   - 401 Unauthorized
   - 429 Rate limit
   - 500 Server error
   - **Action:** Return error with HTTP code and message

4. **Parsing Errors**
   - Invalid JSON response
   - Missing required fields
   - **Action:** Return generic error with details

**Error Message Format:**
```
Error: <category> - <details>

Examples:
- Error: Input validation - search_query cannot be empty
- Error: Network - Failed to connect to API after 3 attempts
- Error: API - Rate limit exceeded (HTTP 429)
- Error: Parsing - Invalid response format from API
```

## Configuration Updates

### Config File Options

**File:** `~/.glm/config.toml`

**Add new section:**
```toml
[web_search]
enabled = true
default_count = 10
default_recency_filter = "noLimit"
```

**Configuration Options:**

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable web search tool |
| `default_count` | integer | `10` | Default number of results (1-50) |
| `default_recency_filter` | string | `"noLimit"` | Default time filter |

### Environment Variables

**Add to existing environment variables:**

| Variable | Config Key | Description |
|----------|-------------|-------------|
| `GLM_WEB_SEARCH_ENABLED` | `web_search.enabled` | Enable web search (true/false) |
| `GLM_WEB_SEARCH_COUNT` | `web_search.default_count` | Default result count (1-50) |
| `GLM_WEB_SEARCH_RECENCY` | `web_search.default_recency_filter` | Default time filter |

**Setting Environment Variables:**

```bash
# Enable web search
export GLM_WEB_SEARCH_ENABLED=true

# Set default count
export GLM_WEB_SEARCH_COUNT=5

# Set default recency
export GLM_WEB_SEARCH_RECENCY="1w"
```

### Configuration Priority

Configuration loading order (highest to lowest priority):

1. Environment variables
2. `~/.glm/config.toml`
3. Default values in code

**Example:**

```toml
# config.toml
[web_search]
default_count = 10
```

```bash
# Environment (overrides config)
export GLM_WEB_SEARCH_COUNT=5
# Result: Uses 5
```

### Config Loading Integration

**Update:** `core/Config.groovy`

Add web_search configuration section parsing:

```groovy
class Config {
    // ... existing config fields

    Boolean webSearchEnabled
    Integer webSearchDefaultCount
    String webSearchDefaultRecencyFilter

    static Config load() {
        // ... existing loading logic

        def webSearchConfig = configTable.get("web_search")
        if (webSearchConfig != null) {
            result.webSearchEnabled = webSearchConfig.get("enabled", true)
            result.webSearchDefaultCount = webSearchConfig.get("default_count", 10)
            result.webSearchDefaultRecencyFilter = webSearchConfig.get("default_recency_filter", "noLimit")
        }

        // ... return config
    }
}
```

## Testing Strategy

### Manual Testing Scenarios

#### Test 1: Basic Search

```bash
# Simple search
./glm.groovy agent "Search for recent news about Java 21 features"

# Expected behavior:
# - Agent calls web_search tool
# - Displays search results with title, content, URL
# - Summarizes findings
```

#### Test 2: Domain-Filtered Search

```bash
# Search specific domain
./glm.groovy agent "Find documentation on github.com about Groovy 4"

# Expected behavior:
# - Agent uses search_domain_filter parameter
# - Results only from github.com
```

#### Test 3: Recent Content Search

```bash
# Search with time filter
./glm.groovy agent "Search for AI news from the last week"

# Expected behavior:
# - Agent uses search_recency_filter="1w"
# - Results only from last 7 days
```

#### Test 4: Multiple Searches in One Task

```bash
# Complex multi-search task
./glm.groovy agent "Research the latest GLM-4 model features and write a summary in a file called summary.md"

# Expected behavior:
# - Agent performs web search
# - Reads results
# - May perform additional searches
# - Writes summary to file
```

#### Test 5: Empty Results

```bash
# Search for very specific/obscure query
./glm.groovy agent "Search for 'xyz123abc'"

# Expected behavior:
# - API returns empty results
# - Agent displays "No search results found"
# - Continues task gracefully
```

#### Test 6: Invalid Input

```bash
# Test error handling
./glm.groovy agent "Search for empty query"

# Expected behavior:
# - Agent validates input
# - Returns clear error message
# - Doesn't make API call
```

### Test Cases Matrix

| Test Case | Input | Expected Result | Priority |
|-----------|-------|----------------|----------|
| Valid basic search | `"Java 21"` | 10 results with titles, URLs | P0 |
| Domain filter | `"Spring"`, `spring.io` | Results only from spring.io | P0 |
| Recency filter | `"AI"`, `"1w"` | Results from last 7 days | P0 |
| Count limit | `"test"`, `5` | Exactly 5 results | P1 |
| No results | `"xyz123abc"` | "No search results found" | P1 |
| Empty query | `""` | Error: search_query required | P0 |
| Invalid count | `"test"`, `100` | Error: count 1-50 | P1 |
| Invalid recency | `"test"`, `"2d"` | Error: invalid recency filter | P1 |
| Network error | N/A | Retry then return error | P2 |
| API error (401) | N/A | Error with HTTP code | P2 |

### Expected Behavior

**Success Flow:**
```
User: "Search for Java 21 features"
  ↓
Agent: [Thinking] Need current info about Java 21
  ↓
Agent: [Tool Call] web_search(search_query: "Java 21 features", count: 10)
  ↓
WebSearchTool: Validates parameters
  ↓
WebSearchClient: Sends HTTP request to API
  ↓
API: Returns search results
  ↓
WebSearchTool: Formats results
  ↓
Agent: Receives formatted results
  ↓
Agent: Summarizes findings
  ↓
User: Receives summary
```

**Error Flow:**
```
User: "Search for empty query"
  ↓
Agent: [Tool Call] web_search(search_query: "")
  ↓
WebSearchTool: Validates input
  ↓
WebSearchTool: Returns "Error: search_query cannot be empty"
  ↓
Agent: Receives error message
  ↓
Agent: Reports error to user
  ↓
User: Sees clear error
```

### Debug Mode Testing

Enable detailed logging:

```bash
export GLM_LOG_LEVEL=DEBUG
./glm.groovy agent "Search for test query"
```

**Expected debug output:**
```
DEBUG: WebSearchTool: Executing with query: "test query"
DEBUG: WebSearchClient: Sending request to https://api.z.ai/api/tools/web_search
DEBUG: WebSearchClient: Request body: {"search_query":"test query"...}
DEBUG: WebSearchClient: Response status: 200
DEBUG: WebSearchClient: Parsing response...
DEBUG: WebSearchClient: Found 10 results
```

## Implementation Phases

### Phase 1: Core Implementation (Priority 1)

**Estimated Time:** 4-5 hours

**Tasks:**

1. ✅ Create response models (`models/WebSearchResponse.groovy`)
   - Define `WebSearchResponse` class
   - Define `SearchResult` nested class
   - Add Jackson annotations

2. ✅ Implement web search client (`core/WebSearchClient.groovy`)
   - Set up HTTP client
   - Implement search() method
   - Handle API communication
   - Add error handling

3. ✅ Create web search tool (`tools/WebSearchTool.groovy`)
   - Implement Tool interface
   - Define parameters schema
   - Implement execute() method
   - Add input validation
   - Format results

4. ✅ Register with agent (`commands/AgentCommand.groovy`)
   - Add tool registration
   - Test integration

5. ✅ Add to sources (`glm.groovy`)
   - Register source files
   - Verify JBang compilation

6. ✅ Basic testing
   - Manual test scenarios
   - Verify tool works end-to-end

**Success Criteria:**
- [ ] Agent can successfully search the web
- [ ] Search results are properly formatted
- [ ] Error handling works correctly
- [ ] Tool doesn't break existing functionality

### Phase 2: Integration & Configuration (Priority 2)

**Estimated Time:** 3-4 hours

**Tasks:**

1. ✅ Add configuration support (`core/Config.groovy`)
   - Parse `[web_search]` section
   - Handle environment variables
   - Set default values

2. ✅ Update documentation
   - Create `WEB_SEARCH_IMPLEMENTATION.md`
   - Update `TOOLS.md` with web_search section
   - Update `README.md` with examples
   - Update `CONFIGURATION.md` with options
   - Update `DEVELOPMENT.md` with testing
   - Update `AGENTS.md` with usage patterns
   - Update `roadmap.md`

3. ✅ Improve error handling
   - Add retry logic with exponential backoff
   - Better error messages
   - Graceful degradation

4. ✅ Enhance result formatting
   - Make content truncation configurable
   - Add better formatting for different result types
   - Support Markdown or plain text output

5. ✅ Add comprehensive tests
   - Manual test matrix completion
   - Edge case testing
   - Error scenario testing

**Success Criteria:**
- [ ] Configuration loading works correctly
- [ ] All documentation is complete
- [ ] Error handling is robust
- [ ] Results are well-formatted
- [ ] All test scenarios pass

### Phase 3: Advanced Features (Priority 3)

**Estimated Time:** 4-6 hours

**Tasks:**

1. ⏳ Add caching support
   - Implement search result cache
   - Add cache TTL
   - Invalidate cache on manual refresh
   - Display cache hits in output

2. ⏳ Implement search history
   - Track recent searches
   - Allow search from history
   - Export/import search history

3. ⏳ Add result ranking
   - AI-powered relevance scoring
   - Reorder results by relevance
   - Show confidence scores

4. ⏳ Support multiple search engines
   - Allow engine selection
   - Compare results across engines
   - Aggregate results

5. ⏳ Web page fetching integration
   - Combine with webfetch tool
   - Get full page content
   - Extract specific information

6. ⏳ Add search analytics
   - Track search usage
   - Popular queries
   - Success rate metrics

**Success Criteria:**
- [ ] Cache reduces API calls
- [ ] Search history is useful
- [ ] Results are ranked intelligently
- [ ] Multiple engines work correctly
- [ ] Page fetching integration works
- [ ] Analytics are meaningful

## Design Decisions

### Client Separation

**Decision:** Create separate `WebSearchClient` class

**Rationale:**
- Separation of concerns: API logic separate from tool
- Testability: Can mock client for unit tests
- Reusability: Client can be used elsewhere if needed
- Maintainability: Easier to update API changes

**Alternative:** Direct API calls in tool
- **Rejected:** Tightly coupled code, harder to test

### Error Handling Approach

**Decision:** Return error strings instead of throwing exceptions

**Rationale:**
- Consistent with existing tools (`ReadFileTool`, `WriteFileTool`)
- Errors are part of normal agent flow
- Agent can handle error strings gracefully
- Simpler exception handling for caller

**Alternative:** Throw exceptions
- **Rejected:** Would require try-catch in agent loop

### Result Formatting Strategy

**Decision:** Truncate content to 200 characters

**Rationale:**
- Readability: Long summaries overwhelm agent
- Token efficiency: Reduce context usage
- Show snippet: Enough context for relevance
- Provide URL: User can get full content if needed

**Alternative:** Show full content
- **Rejected:** Too verbose, wastes tokens

### Permission Level

**Decision:** Always allow (no user confirmation)

**Rationale:**
- Read-only operation (no destructive actions)
- No security concerns
- Better UX: Faster agent execution
- Consistent with other read tools (`read_file`)

**Alternative:** Ask for confirmation
- **Rejected:** Unnecessary friction for safe operation

### Parameter Defaults

**Decision:**
- `count`: 10 (balanced between quantity and context)
- `recency_filter`: "noLimit" (maximum information)
- `search_engine`: "search-prime" (Z.AI recommended)

**Rationale:**
- Sensible defaults for most use cases
- User can override via parameters
- Matches API documentation defaults

## Challenges & Solutions

### Challenge 1: API Endpoint Verification

**Issue:** Exact web search API endpoint may differ from documentation

**Symptoms:**
- 404 Not Found errors
- Connection refused

**Solution:**
1. Test with curl before implementation:
   ```bash
   curl -X POST https://api.z.ai/api/tools/web_search \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"search_query":"test"}'
   ```
2. Update `BASE_URL` in `WebSearchClient.groovy` if different
3. Document correct endpoint in implementation guide

### Challenge 2: Rate Limiting

**Issue:** Web search API may have rate limits

**Symptoms:**
- 429 Too Many Requests errors
- Request failures under load

**Solution:**
1. Implement retry logic with exponential backoff:
   ```groovy
   int maxRetries = 3
   long delay = 1000 // 1 second

   for (int i = 0; i < maxRetries; i++) {
       try {
           return sendRequest()
       } catch (HttpException e) {
           if (e.statusCode == 429 && i < maxRetries - 1) {
               Thread.sleep(delay)
               delay *= 2 // Exponential backoff
           } else {
               throw e
           }
       }
   }
   ```
2. Add delay between multiple searches in agent loop
3. Check API documentation for rate limit details
4. Consider adding cache to reduce API calls

### Challenge 3: Large Responses

**Issue:** Search results can be verbose and exceed context limits

**Symptoms:**
- Agent receives too much information
- Token usage increases significantly
- Context window exceeded

**Solution:**
1. Implement configurable truncation:
   ```groovy
   int contentLimit = 200 // Configurable
   String truncated = result.content.take(contentLimit) + "..."
   ```
2. Limit result count (max 10 by default)
3. Implement intelligent result selection
4. Add configuration for `max_content_length`

### Challenge 4: Authentication Differences

**Issue:** Web search API may use different auth than chat completions

**Symptoms:**
- 401 Unauthorized errors
- Auth header rejected

**Solution:**
1. Test with different auth methods:
   - Bearer token (same as chat)
   - API key directly
   - Custom header
2. Update `WebSearchClient.groovy` accordingly:
   ```groovy
   // Option 1: Use API key directly
   .header("Authorization", "Bearer ${apiKey}")

   // Option 2: Generate JWT (same as chat)
   .header("Authorization", "Bearer ${generateJwt(apiKey)}")

   // Option 3: Custom header
   .header("X-API-Key", apiKey)
   ```
3. Document correct auth method

### Challenge 5: Timeout Issues

**Issue:** Search API can be slow or unresponsive

**Symptoms:**
- Requests hang
- Agent appears frozen
- No feedback to user

**Solution:**
1. Set reasonable timeout (10 seconds)
2. Provide timeout configuration:
   ```toml
   [web_search]
   timeout_seconds = 10
   ```
3. Show progress to user:
   ```
   Agent: Searching web...
   Agent: [web_search] Searching for "Java 21"...
   ```
4. Implement timeout handling:
   ```groovy
   try {
       return sendRequest(timeout)
   } catch (TimeoutException e) {
       return "Error: Web search timed out after ${timeout} seconds"
   }
   ```

### Challenge 6: Malformed Responses

**Issue:** API may return unexpected or malformed JSON

**Symptoms:**
- JSON parsing errors
- Missing required fields
- Null pointer exceptions

**Solution:**
1. Use `@JsonIgnoreProperties(ignoreUnknown = true)`
2. Add field validation:
   ```groovy
   if (response.search_result == null) {
       return "Error: API returned malformed response"
   }
   ```
3. Add error recovery:
   ```groovy
   try {
       return mapper.readValue(responseBody, WebSearchResponse.class)
   } catch (JsonProcessingException e) {
       log.error("Failed to parse response: ${responseBody}")
       return "Error: Failed to parse API response"
   }
   ```
4. Log raw response for debugging

### Challenge 7: Network Connectivity

**Issue:** User may not have internet connection

**Symptoms:**
- Connection errors
- DNS resolution failures

**Solution:**
1. Catch network errors explicitly:
   ```groovy
   catch (UnknownHostException e) {
       return "Error: Could not resolve API host. Check internet connection."
   }
   catch (ConnectException e) {
       return "Error: Could not connect to API. Check internet connection."
   }
   ```
2. Provide helpful error messages
3. Suggest offline alternatives (if applicable)

## Success Criteria

The web search tool implementation is successful when:

### Functional Requirements

- [ ] **Basic Search**: Agent can search web and get results
- [ ] **Domain Filtering**: `search_domain_filter` parameter works correctly
- [ ] **Recency Filtering**: `search_recency_filter` parameter works correctly
- [ ] **Count Limiting**: `count` parameter respects 1-50 range
- [ ] **Error Handling**: All error scenarios handled gracefully
- [ ] **Result Formatting**: Results are readable and well-structured

### Integration Requirements

- [ ] **Tool Registration**: Tool registered with agent successfully
- [ ] **Configuration**: Config file options load correctly
- [ ] **Environment Variables**: Environment variable overrides work
- [ ] **Agent Loop**: Tool works in ReAct loop without issues
- [ ] **No Regressions**: Existing tools and functionality not broken

### Quality Requirements

- [ ] **Code Quality**: Follows existing code style and conventions
- [ ] **Documentation**: All documentation complete and accurate
- [ ] **Testing**: All test scenarios pass
- [ ] **Error Messages**: Clear, actionable error messages
- [ ] **Performance**: Acceptable response time (< 10 seconds typical)

### Usability Requirements

- [ ] **Discovery**: Users can easily discover the tool (docs, examples)
- [ **Ease of Use**: Intuitive parameter names and descriptions
- [ ] **Reliability**: Works consistently across different queries
- [ ] **Helpful**: Results are relevant and useful for tasks

## Future Enhancements

### Immediate Enhancements (Next Sprint)

1. **Search Result Caching**
   - Cache results by query hash
   - 1-hour TTL by default
   - Manual cache invalidation
   - Display cache status

2. **Advanced Filters**
   - Language filter (English, Chinese, etc.)
   - File type filter (PDF, HTML, etc.)
   - Region filter (country-specific results)
   - Safe search toggle

3. **Result Ranking**
   - AI-powered relevance scoring
   - Sort by date, relevance, or source
   - Highlight key terms in results
   - Show confidence scores

### Medium-Term Enhancements

4. **Search History**
   - Track last 100 searches
   - Re-run previous searches
   - Export search history to JSON
   - Search within history

5. **Multi-Engine Support**
   - Support multiple search engines
   - Compare results across engines
   - Aggregate and deduplicate results
   - Engine-specific features

6. **Web Page Fetching**
   - Integrate with `webfetch` tool
   - Extract main content from pages
   - Remove ads and navigation
   - Provide summarized content

### Long-Term Enhancements

7. **Search Analytics**
   - Track popular queries
   - Monitor API usage
   - Measure success rate
   - Identify patterns

8. **Personalization**
   - Remember user preferences
   - Learn from successful searches
   - Suggest related queries
   - Bookmark favorite results

9. **Advanced AI Integration**
   - Use search results in chain-of-thought reasoning
   - Multi-turn search refinement
   - Question answering over results
   - Fact-checking verification

10. **Performance Optimization**
    - Parallel search requests
    - Result streaming
    - Predictive caching
    - Reduce API calls

## References

### API Documentation

- [Z.AI Web Search API](https://docs.z.ai/guides/tools/web-search) - Official API documentation
- [Z.AI API Reference](https://docs.z.ai/api-reference/tools/web-search) - Detailed API specs

### Project Documentation

- [AGENTS.md](./AGENTS.md) - Agent system and tool calling guidelines
- [TOOLS.md](./TOOLS.md) - Complete tool reference
- [CONFIGURATION.md](./CONFIGURATION.md) - Configuration options
- [DEVELOPMENT.md](./DEVELOPMENT.md) - Development and testing guide

### Related Tools

- [ReadFileTool](../tools/ReadFileTool.groovy) - Read file contents
- [WriteFileTool](../tools/WriteFileTool.groovy) - Write/create files
- [ListFilesTool](../tools/ListFilesTool.groovy) - List directory contents

### External References

- [Z.AI Platform](https://z.ai) - Main platform
- [API Keys](https://z.ai/manage-apikey/apikey-list) - Get API keys
- [Pricing](https://docs.z.ai/guides/overview/pricing) - Web search pricing

## Appendix

### A. Example Code Snippets

#### Minimal Web Search Tool

```groovy
class MinimalWebSearchTool implements Tool {
    private final WebSearchClient client

    MinimalWebSearchTool(String apiKey) {
        this.client = new WebSearchClient(apiKey)
    }

    String getName() { "web_search" }
    String getDescription() { "Search the web" }

    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                search_query: [type: "string"]
            ],
            required: ["search_query"]
        ]
    }

    Object execute(Map<String, Object> args) {
        def response = client.search(args.get("search_query"))
        return "Found ${response.search_result.size()} results"
    }
}
```

#### Configured Web Search Tool

```groovy
// Using default configuration
agent.registerTool(new WebSearchTool(apiKey))

// Using custom configuration
WebSearchTool tool = new WebSearchTool(apiKey)
tool.setDefaultCount(5)
tool.setDefaultRecency("1w")
agent.registerTool(tool)
```

### B. Test Script

```bash
#!/bin/bash
# Test web search tool

echo "Test 1: Basic search"
./glm.groovy agent "Search for Java 21 features"

echo -e "\nTest 2: Domain filter"
./glm.groovy agent "Find docs on spring.io about Spring Boot"

echo -e "\nTest 3: Recency filter"
./glm.groovy agent "Search for AI news from last week"

echo -e "\nTest 4: No results"
./glm.groovy agent "Search for xyz123abc"

echo -e "\nTest 5: Invalid input"
./glm.groovy agent "Search for empty query"
```

### C. Troubleshooting Checklist

- [ ] API key is valid and has web search permissions
- [ ] Internet connection is working
- [ ] API endpoint is correct
- [ ] Authentication method is correct
- [ ] Tool is registered with agent
- [ ] Sources are added to `glm.groovy`
- [ ] Configuration file is properly formatted
- [ ] Environment variables are set correctly
- [ ] No rate limiting issues
- [ ] Search query is not empty

### D. Glossary

- **ReAct**: Reasoning + Acting - Agent pattern used by GLM-CLI
- **JWT**: JSON Web Token - Authentication method used by Z.AI
- **SSE**: Server-Sent Events - Streaming response format
- **JSON Schema**: Format for defining tool parameters
- **Bearer Token**: HTTP authorization method

---

**Document Version:** 1.0
**Last Updated:** 2025-01-01
**Author:** GLM-CLI Development Team
