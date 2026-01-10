package mcp

import io.modelcontextprotocol.client.McpSyncClient

class McpResourceManager {

    private final McpClientManager clientManager
    private final Map<String, List<McpResource>> cachedResources = new HashMap<>()

    McpResourceManager() {
        this.clientManager = McpClientManager.getInstance()
    }

    Map<String, List<McpResource>> discoverResources() {
        cachedResources.clear()

        clientManager.getClients().each { serverName, client ->
            try {
                if (clientManager.getStatus(serverName)?.isConnected()) {
                    List<McpResource> resources = listResources(client)
                    if (resources) {
                        cachedResources.put(serverName, resources)
                    }
                }
            } catch (Exception e) {
                System.err.println "Error discovering resources from ${serverName}: ${e.message}"
            }
        }

        return cachedResources
    }

    private List<McpResource> listResources(McpSyncClient client) {
        try {
            return client.listResources().block() ?: []
        } catch (Exception e) {
            System.err.println "Error listing resources: ${e.message}"
            return []
        }
    }

    String readResource(String serverName, String uri) {
        McpSyncClient client = clientManager.getClient(serverName)
        if (client == null) {
            return "Error: Not connected to server '${serverName}'"
        }

        try {
            return client.readResource(uri).block()
        } catch (Exception e) {
            return "Error reading resource: ${e.message}"
        }
    }

    void subscribeToResource(String serverName, String uri, Closure callback) {
        McpSyncClient client = clientManager.getClient(serverName)
        if (client == null) {
            System.err.println "Error: Not connected to server '${serverName}'"
            return
        }

        try {
            client.subscribeResource(uri).subscribe(
                { result -> callback.onNext(result) },
                { error -> callback.onError(error) },
                { callback.onComplete() }
            )
        } catch (Exception e) {
            System.err.println "Error subscribing to resource: ${e.message}"
        }
    }

    List<McpResource> getResourcesForServer(String serverName) {
        return cachedResources.get(serverName) ?: []
    }

    int getTotalResourceCount() {
        return cachedResources.values().flatten().size()
    }

}

class McpResource {
    String uri
    String name
    String description
    String mimeType
}
