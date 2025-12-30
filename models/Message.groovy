package models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class Message {
    String role
    String content
    
    @JsonProperty("tool_calls")
    List<ToolCall> toolCalls
    
    @JsonProperty("tool_call_id")
    String toolCallId
    
    // Default constructor
    Message() {}

    Message(String role, String content) {
        this.role = role
        this.content = content
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ToolCall {
        String id
        String type
        Function function
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Function {
        String name
        String arguments
    }
}

