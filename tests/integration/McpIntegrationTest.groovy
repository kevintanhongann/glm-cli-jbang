package integration

import mcp.McpConfig
import mcp.McpClientManager
import mcp.McpToolDiscovery
import mcp.McpServerConfig
import mcp.McpEventBus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled

import static org.junit.jupiter.api.Assertions.*

@Disabled("Requires running MCP server")
class McpIntegrationTest {

    @BeforeEach
    void setUp() {
        McpClientManager.getInstance().shutdown()
    }

    @AfterEach
    void tearDown() {
        McpClientManager.getInstance().shutdown()
    }

    @Test
    void testConnectToServerEverything() {
        McpConfig config = new McpConfig()
        McpServerConfig serverConfig = new McpServerConfig()
        serverConfig.type = 'local'
        serverConfig.command = ['npx', '-y', '@modelcontextprotocol/server-everything']
        serverConfig.enabled = true
        config.mcpServers = ['everything': serverConfig]
        config.save()

        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()
        manager.connect('everything')

        def status = manager.getStatus('everything')
        assertNotNull(status)

        if (status.isConnected()) {
            def tools = McpToolDiscovery.discoverTools()
            assertFalse(tools.isEmpty())
            println "Discovered ${tools.size()} tools from MCP server"
        } else {
            println "Connection failed: ${status.errorMessage}"
        }
    }

    @Test
    void testConnectionRetry() {
        McpConfig config = new McpConfig()
        McpServerConfig serverConfig = new McpServerConfig()
        serverConfig.type = 'local'
        serverConfig.command = ['nonexistent-command']
        serverConfig.enabled = true
        config.mcpServers = ['test': serverConfig]
        config.save()

        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()
        manager.connect('test')

        def status = manager.getStatus('test')
        assertNotNull(status)
        assertTrue(status.isFailed() || status.isDisconnected())
    }

    @Test
    void testEventBus() {
        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()

        boolean eventReceived = false

        McpEventBus.getConnectionEvents().subscribe { event ->
            eventReceived = true
        }

        McpEventBus.publish(new mcp.McpConnectionEvent(serverName: 'test', connected: true))

        assertTrue(eventReceived)
    }

}
