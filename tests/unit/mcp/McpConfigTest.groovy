package unit.mcp

import mcp.McpConfig
import mcp.McpServerConfig
import mcp.OAuthConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.junit.jupiter.api.Assertions.*

class McpConfigTest {

    private Path tempDir
    private Path mcpConfigFile

    @BeforeEach
    void setUp() {
        tempDir = Files.createTempDirectory('glm-test')
        mcpConfigFile = tempDir.resolve('mcp.json')
    }

    @AfterEach
    void tearDown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    @Test
    void testLoadEmptyConfig() {
        McpConfig config = McpConfig.load()
        assertNotNull(config)
        assertNotNull(config.mcpServers)
        assertTrue(config.mcpServers.isEmpty())
    }

    @Test
    void testLoadConfigFromFile() {
        String json = '''{
  "mcpServers": {
    "test-server": {
      "type": "local",
      "command": ["npx", "test-server"],
      "enabled": true
    }
  }
}'''

        Files.writeString(mcpConfigFile, json)

        McpConfig config = McpConfig.load()
        assertNotNull(config.mcpServers)
        assertEquals(1, config.mcpServers.size())

        McpServerConfig server = config.getServer("test-server")
        assertNotNull(server)
        assertEquals("local", server.type)
        assertTrue(server.enabled)
    }

    @Test
    void testGetServer() {
        String json = '''{
  "mcpServers": {
    "server1": {
      "type": "local",
      "command": ["test"],
      "enabled": true
    },
    "server2": {
      "type": "remote",
      "url": "https://example.com",
      "enabled": false
    }
  }
}'''

        Files.writeString(mcpConfigFile, json)

        McpConfig config = McpConfig.load()

        assertNotNull(config.getServer("server1"))
        assertNotNull(config.getServer("server2"))
        assertNull(config.getServer("nonexistent"))
    }

    @Test
    void testServerConfigIsLocal() {
        McpServerConfig config = new McpServerConfig()
        config.type = 'local'
        assertTrue(config.isLocal())
        assertFalse(config.isRemote())
    }

    @Test
    void testServerConfigIsRemote() {
        McpServerConfig config = new McpServerConfig()
        config.type = 'remote'
        config.url = 'https://example.com'
        assertTrue(config.isRemote())
        assertFalse(config.isLocal())
    }

    @Test
    void testOAuthConfig() {
        OAuthConfig oauth = new OAuthConfig()
        oauth.authorizationEndpoint = 'https://auth.example.com/authorize'
        oauth.tokenEndpoint = 'https://auth.example.com/token'
        oauth.clientId = 'test-client'
        oauth.redirectUri = 'http://localhost:19876/callback'

        assertNotNull(oauth.authorizationEndpoint)
        assertNotNull(oauth.tokenEndpoint)
        assertEquals('test-client', oauth.clientId)
    }

}
