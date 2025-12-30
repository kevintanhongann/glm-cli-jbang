package tools

import core.WebSearchClient
import models.WebSearchResponse

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

            Map<String, Object> options = [: ]

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

            if (response.searchResult == null || response.searchResult.isEmpty()) {
                return "No search results found for query: '${searchQuery}'"
            }

            return formatResults(response)
        } catch (Exception e) {
            return "Error performing web search: ${e.message}"
        }
    }

    private String formatResults(WebSearchResponse response) {
        StringBuilder sb = new StringBuilder()
        sb.append("Found ").append(response.searchResult.size()).append(" results:\n\n")

        response.searchResult.eachWithIndex { result, i ->
            sb.append("${i + 1}. ").append(result.title).append("\n")
            if (result.content) {
                sb.append("   ").append(result.content.take(200)).append("...")
            }
            sb.append("   URL: ").append(result.link).append("\n")

            if (result.publishDate && !result.publishDate.isEmpty()) {
                sb.append("   Published: ").append(result.publishDate).append("\n")
            }

            if (result.media && !result.media.isEmpty()) {
                sb.append("   Source: ").append(result.media).append("\n")
            }

            sb.append("\n")
        }

        return sb.toString()
    }
}