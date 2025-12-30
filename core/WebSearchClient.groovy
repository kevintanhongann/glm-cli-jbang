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
                // If 401/403, might be invalid key, but we throw generic error.
                // Log body for debugging
                throw new RuntimeException("Web Search API failed with code ${response.statusCode()}: ${response.body()}")
            }

            return mapper.readValue(response.body(), WebSearchResponse.class)
        } catch (Exception e) {
            throw new RuntimeException("Web search failed: ${e.message}", e)
        }
    }
}