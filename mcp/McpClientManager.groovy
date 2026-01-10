package mcp

import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.StdioClientTransport
import io.modelcontextprotocol.client.transport.ServerParameters
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import com.fasterxml.jackson.databind.ObjectMapper

import java.util.concurrent.ConcurrentHashMap

class McpClientManager {

    private static McpClientManager instance
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>()
    private final Map<String, ServerStatus> statuses = new ConcurrentHashMap<>()
    private final Map<String, McpServerConfig> configs = new ConcurrentHashMap<>()
    private final ObjectMapper mapper = new ObjectMapper()
    private boolean initialized = false

    private McpClientManager() {}

    static synchronized McpClientManager getInstance() {
        if (instance == null) {
            instance = new McpClientManager()
        }
        return instance
    }

    void initialize() {
        if (initialized) return

        McpConfig config = McpConfig.load()
        config.mcpServers?.each { name, serverConfig ->
            configs[name] = serverConfig
            if (serverConfig.enabled) {
                statuses[name] = new ServerStatus(name: name, state: ServerState.DISCONNECTED)
            } else {
                statuses[name] = new ServerStatus(name: name, state: ServerState.DISABLED)
            }
        }

        initialized = true
    }

    void connect(String serverName) {
        McpServerConfig config = configs.get(serverName)
        if (config == null) {
            System.err.println("MCP server not found: ${serverName}")
            return
        }

        if (!config.enabled) {
            System.err.println("MCP server is disabled: ${serverName}")
            return
        }

        updateStatus(serverName, ServerState.CONNECTING)

        try {
            McpClient client = createClient(serverName, config)
            clients.put(serverName, client)
            updateStatus(serverName, ServerState.CONNECTED)
            McpEventBus.publish(new McpConnectionEvent(serverName: serverName, connected: true))
        } catch (Exception e) {
            System.err.println("Failed to connect to MCP server ${serverName}: ${e.message}")
            updateStatus(serverName, ServerState.FAILED, e.message)
        }
    }

    private McpSyncClient createClient(String serverName, McpServerConfig config) {
        McpSyncClient client

        if (config.isLocal()) {
            if (config.command == null || config.command.isEmpty()) {
                throw new IllegalArgumentException("Command is required for local MCP server")
            }

            Map<String, String> env = new HashMap<>()
            env.putAll(System.getenv())
            config.environment?.each { key, value ->
                env.put(key, resolveEnvValue(value))
            }

            StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(config.command[0])
                    .args(config.command.size() > 1 ? config.command[1..-1] as List<String> : [])
                    .env(env)
                    .build()
            )

            client = McpClient.sync(transport).build()
        } else if (config.isRemote()) {
            if (config.url == null || config.url.isEmpty()) {
                throw new IllegalArgumentException("URL is required for remote MCP server")
            }

            Map<String, String> headers = new HashMap<>()
            config.headers?.each { key, value ->
                headers.put(key, resolveEnvValue(value))
            }

            if ('sse'.equalsIgnoreCase(config.transport)) {
                HttpClientSseClientTransport transport = new HttpClientSseClientTransport(
                    new URL(config.url),
                    headers
                )
                client = McpClient.sync(transport).build()
            } else {
                HttpClientStreamableHttpTransport transport = new HttpClientStreamableHttpTransport(
                    new URL(config.url),
                    headers
                )
                client = McpClient.sync(transport).build()
            }
        } else {
            throw new IllegalArgumentException("Unknown MCP server type: ${config.type}")
        }

        return client
    }

    private String resolveEnvValue(String value) {
        if (value == null) return null

        if (value.startsWith('{env:') && value.endsWith('}')) {
            String envVar = value[5..-2]
            return System.getenv(envVar) ?: ''
        }

        return value
    }

    void disconnect(String serverName) {
        McpSyncClient client = clients.remove(serverName)
        if (client != null) {
            try {
                client.close()
            } catch (Exception e) {
                System.err.println("Error closing MCP client ${serverName}: ${e.message}")
            }
        }
        updateStatus(serverName, ServerState.DISCONNECTED)
        McpEventBus.publish(new McpConnectionEvent(serverName: serverName, connected: false))
    }

    void shutdown() {
        clients.keySet().each { serverName ->
            disconnect(serverName)
        }
        clients.clear()
        statuses.clear()
        configs.clear()
        initialized = false
    }

    McpSyncClient getClient(String serverName) {
        return clients.get(serverName)
    }

    Map<String, McpSyncClient> getClients() {
        return Collections.unmodifiableMap(clients)
    }

    Map<String, ServerStatus> getStatuses() {
        return Collections.unmodifiableMap(statuses)
    }

    ServerStatus getStatus(String serverName) {
        return statuses.get(serverName)
    }

    List<String> listServerNames() {
        return new ArrayList<>(configs.keySet())
    }

    private void updateStatus(String serverName, ServerState state, String errorMessage = null) {
        statuses.put(serverName, new ServerStatus(
            name: serverName,
            state: state,
            errorMessage: errorMessage,
            lastUpdate: new Date()
        ))
        McpEventBus.publish(new McpStatusChangeEvent(serverName: serverName, status: statuses.get(serverName)))
    }

    boolean hasConnection(String serverName) {
        return clients.containsKey(serverName)
    }

}

enum ServerState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
    DISABLED
}

class ServerStatus {
    String name
    ServerState state
    String errorMessage
    Date lastUpdate

    boolean isConnected() {
        return state == ServerState.CONNECTED
    }

    boolean isFailed() {
        return state == ServerState.FAILED
    }

    boolean isDisabled() {
        return state == ServerState.DISABLED
    }
}

class McpConnectionEvent {
    String serverName
    boolean connected
    String errorMessage
}

class McpStatusChangeEvent {
    String serverName
    ServerStatus status
}
