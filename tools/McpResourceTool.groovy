package tools

import mcp.McpClientManager
import mcp.McpResourceManager
import com.fasterxml.jackson.databind.ObjectMapper

class McpResourceTool implements Tool {

    private final McpResourceManager resourceManager
    private final ObjectMapper mapper = new ObjectMapper()

    McpResourceTool() {
        this.resourceManager = new McpResourceManager()
    }

    @Override
    String getName() {
        return 'mcp_resource'
    }

    @Override
    String getDescription() {
        return 'Access resources from MCP servers. Provide server name and resource URI.'
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: 'object',
            properties: [
                server: [
                    type: 'string',
                    description: 'MCP server name'
                ],
                uri: [
                    type: 'string',
                    description: 'Resource URI to access'
                ],
                action: [
                    type: 'string',
                    description: 'Action: read (default), list',
                    enum: ['read', 'list']
                ]
            ],
            required: ['server']
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        String server = args.server
        String uri = args.uri
        String action = args.action ?: 'read'

        if (!server) {
            return 'Error: server parameter is required'
        }

        McpClientManager.instance.initialize()

        def status = McpClientManager.instance.getStatus(server)
        if (status == null || !status.isConnected()) {
            return "Error: Not connected to MCP server '${server}'"
        }

        if ('list'.equals(action)) {
            return listResources(server)
        }

        if (!uri) {
            return "Error: uri parameter is required for 'read' action"
        }

        return resourceManager.readResource(server, uri)
    }

    private String listResources(String server) {
        def resources = resourceManager.getResourcesForServer(server)

        if (resources.empty) {
            return "No resources available on server '${server}'"
        }

        def output = ["Resources on ${server}:", '']
        resources.each { resource ->
            output << "  ${resource.uri}"
            if (resource.name) {
                output << "    Name: ${resource.name}"
            }
            if (resource.description) {
                output << "    Description: ${resource.description}"
            }
            if (resource.mimeType) {
                output << "    Type: ${resource.mimeType}"
            }
            output << ''
        }

        return output.join('\n')
    }

}
