package models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class WebSearchResponse {
    String id
    @JsonProperty("request_id")
    String requestId
    Long created
    @JsonProperty("search_result")
    List<SearchResult> searchResult

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchResult {
        String title
        String content
        String link
        String media
        @JsonProperty("publish_date")
        String publishDate
        String icon
        String refer
    }
}