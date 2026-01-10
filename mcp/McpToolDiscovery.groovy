package mcp

import tools.Tool
import io.modelcontextprotocol.client.McpSyncClient
import java.util.concurrent.ConcurrentHashMap

class McpToolDiscovery {

    private static final Map<String, Tool> cachedTools = new ConcurrentHashMap<>()

    static Map<String, Tool> discoverTools() {
        cachedTools.clear()

        McpClientManager manager = McpClientManager.getInstance()
        Map<String, McpSyncClient> clients = manager.getClients()

        clients.each { serverName, client ->
            try {
                if (manager.getStatus(serverName)?.isConnected()) {
                    List<Map<String, Object>> tools = listTools(client)
                    tools.each { toolDef ->
                        McpToolAdapter adapter = new McpToolAdapter(client, serverName, toolDef)
                        cachedTools.put(adapter.getName(), adapter)
                    }
                }
            } catch (Exception e) {
                System.err.println("Error discovering tools from ${serverName}: ${e.message}")
            }
        }

        return Collections.unmodifiableMap(cachedTools)
    }

    static Map<String, Tool> getCachedTools() {
        return Collections.unmodifiableMap(cachedTools)
    }

    static List<Map<String, Object>> listTools(McpSyncClient client) {
        try {
            return client.listTools().block()
        } catch (Exception e) {
            System.err.println("Error listing tools: ${e.message}")
            return []
        }
    }

    static Tool getTool(String toolName) {
        return cachedTools.get(toolName)
    }

    static void refreshTools() {
        discoverTools()
    }

    static int getToolCount() {
        return cachedTools.size()
    }

    static Set<String> getToolNames() {
        return Collections.unmodifiableSet(cachedTools.keySet())
    }

}
