package tui.tui4j.messages

import com.williamcallahan.tui4j.compat.bubbletea.Message
import tools.Tool

record ChatResponseMessage(
    String content,
    List<Map> toolCalls,
    Map metadata

) implements Message {}

record ToolResultMessage(
    String toolCallId,
    String result,
    List<Map> allResults = []

) implements Message {}

record StreamChunkMessage(
    String chunk,
    boolean isComplete

) implements Message {}

record StatusMessage(String text) implements Message { }

record ErrorMessage(String error, Throwable cause) implements Message { }

record ToolsInitializedMessage(
    List<Tool> tools

) implements Message {}

record TickMessage() implements Message {}
