package models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class ChatResponse {
    String id
    Long created
    String model
    List<Choice> choices
    Usage usage

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        Integer index
        @JsonProperty("finish_reason")
        String finishReason
        Message message // For non-streaming
        Message delta   // For streaming
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Usage {
        @JsonProperty("prompt_tokens")
        Integer promptTokens
        @JsonProperty("completion_tokens")
        Integer completionTokens
        @JsonProperty("total_tokens")
        Integer totalTokens
    }
}
