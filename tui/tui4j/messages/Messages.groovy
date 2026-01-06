package tui.tui4j.messages

import com.williamcallahan.tui4j.compat.bubbletea.Message

record ChatResponseMessage(
    String content,
    List<Map> toolCalls,
    Map usage

) implements Message {}

record ToolResultMessage(
    String toolCallId,
    String result

) implements Message {}

record StreamChunkMessage(
    String chunk,
    boolean isComplete

) implements Message {}

record StatusMessage(String text) implements Message { }

record ErrorMessage(String error, Throwable cause) implements Message { }
