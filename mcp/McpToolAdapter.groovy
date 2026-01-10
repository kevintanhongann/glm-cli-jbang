package mcp

import tools.Tool
import io.modelcontextprotocol.client.McpSyncClient
import com.fasterxml.jackson.databind.ObjectMapper

class McpToolAdapter implements Tool {

    private final McpSyncClient client
    private final String serverName
    private final Map<String, Object> toolDefinition
    private final ObjectMapper mapper = new ObjectMapper()

    McpToolAdapter(McpSyncClient client, String serverName, Map<String, Object> toolDefinition) {
        this.client = client
        this.serverName = serverName
        this.toolDefinition = toolDefinition
    }

    @Override
    String getName() {
        String sanitizedServerName = sanitizeName(serverName)
        String toolName = toolDefinition.name ?: 'unknown'
        String sanitizedToolName = sanitizeName(toolName)
        return "${sanitizedServerName}_${sanitizedToolName}"
    }

    @Override
    String getDescription() {
        return toolDefinition.description ?: 'No description available'
    }

    @Override
    Map<String, Object> getParameters() {
        return toolDefinition.inputSchema ?: [:]
    }

    @Override
    Object execute(Map<String, Object> args) {
        try {
            String result = client.callTool(
                toolDefinition.name,
                args
            ).block()

            return formatResult(result)
        } catch (Exception e) {
            return "Error executing MCP tool: ${e.message}"
        }
    }

    private String sanitizeName(String name) {
        if (name == null) return 'unknown'
        return name.replaceAll(/[^a-zA-Z0-9_-]/, '_')
    }

    private String formatResult(String result) {
        if (result == null || result.isEmpty()) {
            return "No output from tool"
        }

        try {
            def parsed = mapper.readValue(result, Object)
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed)
        } catch (Exception e) {
            return result
        }
    }

    String getServerName() {
        return serverName
    }

    String getToolName() {
        return toolDefinition.name ?: 'unknown'
    }

}
