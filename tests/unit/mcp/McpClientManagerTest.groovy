package unit.mcp

import mcp.McpClientManager
import mcp.McpConfig
import mcp.McpServerConfig
import mcp.ServerState
import mcp.ServerStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.junit.jupiter.api.Assertions.*

class McpClientManagerTest {

    @BeforeEach
    void setUp() {
        McpClientManager.getInstance().shutdown()
    }

    @AfterEach
    void tearDown() {
        McpClientManager.getInstance().shutdown()
    }

    @Test
    void testSingletonInstance() {
        McpClientManager instance1 = McpClientManager.getInstance()
        McpClientManager instance2 = McpClientManager.getInstance()
        assertSame(instance1, instance2)
    }

    @Test
    void testInitializeWithNoConfig() {
        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()

        Map<String, ServerStatus> statuses = manager.getStatuses()
        assertNotNull(statuses)
        assertTrue(statuses.isEmpty())
    }

    @Test
    void testListServerNamesEmpty() {
        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()

        assertTrue(manager.listServerNames().isEmpty())
    }

    @Test
    void testGetStatusNotConnected() {
        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()

        assertNull(manager.getStatus("nonexistent"))
    }

    @Test
    void testHasConnectionFalse() {
        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()

        assertFalse(manager.hasConnection("nonexistent"))
    }

    @Test
    void testShutdownClearsState() {
        McpClientManager manager = McpClientManager.getInstance()
        manager.initialize()
        manager.shutdown()

        assertFalse(manager.getClients().isEmpty() == false)
    }

}
