package unit.mcp

import mcp.McpToolAdapter
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class McpToolAdapterTest {

    @Test
    void testGetNameSanitization() {
        def toolDefinition = [
            name: 'test-tool'
        ]

        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        assertEquals('test_server_test_tool', adapter.getName())
    }

    @Test
    void testGetNameWithSpecialCharacters() {
        def toolDefinition = [
            name: 'test-tool-123'
        ]

        McpToolAdapter adapter = new McpToolAdapter(null, 'my-server!@#', toolDefinition)

        String name = adapter.getName()
        assertFalse(name.contains('!'))
        assertFalse(name.contains('@'))
        assertFalse(name.contains('#'))
        assertTrue(name.contains('my_server_123'))
    }

    @Test
    void testGetDescription() {
        def toolDefinition = [
            name: 'test-tool',
            description: 'A test tool for unit testing'
        ]

        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        assertEquals('A test tool for unit testing', adapter.getDescription())
    }

    @Test
    void testGetDescriptionDefault() {
        def toolDefinition = [
            name: 'test-tool'
        ]

        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        assertEquals('No description available', adapter.getDescription())
    }

    @Test
    void testGetParameters() {
        def toolDefinition = [
            name: 'test-tool',
            inputSchema: [
                type: 'object',
                properties: [
                    arg1: [type: 'string'],
                    arg2: [type: 'integer']
                ]
            ]
        ]

        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        Map<String, Object> params = adapter.getParameters()
        assertNotNull(params)
        assertEquals('object', params.type)
    }

    @Test
    void testGetParametersDefault() {
        def toolDefinition = [
            name: 'test-tool'
        ]

        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        Map<String, Object> params = adapter.getParameters()
        assertNotNull(params)
        assertTrue(params.isEmpty())
    }

    @Test
    void testGetServerName() {
        def toolDefinition = [name: 'test-tool']
        McpToolAdapter adapter = new McpToolAdapter(null, 'my-server', toolDefinition)

        assertEquals('my-server', adapter.getServerName())
    }

    @Test
    void testGetToolName() {
        def toolDefinition = [name: 'my-tool']
        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        assertEquals('my-tool', adapter.getToolName())
    }

    @Test
    void testExecuteReturnsErrorOnNullClient() {
        def toolDefinition = [name: 'test-tool']
        McpToolAdapter adapter = new McpToolAdapter(null, 'test-server', toolDefinition)

        Object result = adapter.execute([:])
        assertTrue(result.toString().contains('Error executing MCP tool'))
    }

}
