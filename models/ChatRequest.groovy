package models

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
class ChatRequest {
    String model
    List<Message> messages
    Boolean stream
    Double temperature
    
    @JsonProperty("max_tokens")
    Integer maxTokens
    
    @JsonProperty("tools")
    List<Object> tools // List of tool definitions
    
    @JsonProperty("tool_choice")
    Object toolChoice // "auto", "none", or specific tool
}

