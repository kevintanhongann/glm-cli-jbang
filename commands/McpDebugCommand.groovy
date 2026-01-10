package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpClientManager
import mcp.McpConfig
import mcp.McpServerConfig
import mcp.McpToolDiscovery
import mcp.ServerState

@Command(name = 'debug', description = 'Show detailed debugging information for an MCP server')
class McpDebugCommand implements Runnable {

    @Parameters(index = "0", description = "Server name")
    String serverName

    @Option(names = ['--json'], description = "Output as JSON")
    boolean jsonOutput = false

    @Option(names = ['--tools'], description = "Show available tools")
    boolean showTools = false

    @Override
    void run() {
        if (serverName == null) {
            System.err.println "Error: Server name is required"
            CommandLine.usage(this, System.out)
            return
        }

        McpClientManager.instance.initialize()
        McpConfig config = McpConfig.load()

        def serverConfig = config.getServer(serverName)
        def status = McpClientManager.instance.getStatus(serverName)

        if (serverConfig == null && status == null) {
            System.err.println "Error: MCP server '${serverName}' not found"
            return
        }

        if (jsonOutput) {
            printJson(serverConfig, status)
        } else {
            printDetails(serverConfig, status)
        }

        if (showTools && status?.isConnected()) {
            println ""
            println "Available Tools:"
            println "".padRight(60, '-')

            Map<String, mcp.McpToolAdapter> tools = mcp.McpToolDiscovery.getCachedTools()
            tools.each { name, tool ->
                println "  • ${name}"
                if (tool.getDescription()) {
                    println "    ${tool.getDescription()}"
                }
            }

            println ""
            println "Total: ${tools.size()} tools"
        }
    }

    private void printDetails(McpServerConfig serverConfig, mcp.ServerStatus status) {
        println "MCP Server Debug: ${serverName}"
        println "=".padRight(60, '=')
        println ""

        println "Configuration:"
        println "-".padRight(40, '-')
        if (serverConfig) {
            println "  Type: ${serverConfig.type}"
            println "  Enabled: ${serverConfig.enabled}"
            println "  Transport: ${serverConfig.transport}"

            if (serverConfig.isLocal()) {
                println "  Command: ${serverConfig.command?.join(' ')}"
            } else if (serverConfig.isRemote()) {
                println "  URL: ${serverConfig.url}"
            }
        } else {
            println "  (No configuration found)"
        }
        println ""

        println "Connection Status:"
        println "-".padRight(40, '-')
        if (status) {
            println "  State: ${status.state}"
            println "  Connected: ${status.isConnected()}"
            if (status.errorMessage) {
                println "  Error: ${status.errorMessage}"
            }
            if (status.lastUpdate) {
                println "  Last Update: ${status.lastUpdate}"
            }
        } else {
            println "  (No status available)"
        }
        println ""

        if (status?.isConnected()) {
            println "Statistics:"
            println "-".padRight(40, '-')
            println "  Tools Discovered: ${mcp.McpToolDiscovery.getToolCount()}"
        }
    }

    private void printJson(McpServerConfig serverConfig, mcp.ServerStatus status) {
        def json = [:]
        json.serverName = serverName

        if (serverConfig) {
            json.config = [
                type: serverConfig.type,
                enabled: serverConfig.enabled,
                transport: serverConfig.transport,
                command: serverConfig.command,
                url: serverConfig.url,
                environment: serverConfig.environment,
                headers: serverConfig.headers
            ]
        }

        if (status) {
            json.status = [
                state: status.state.toString(),
                connected: status.isConnected(),
                failed: status.isFailed(),
                disabled: status.isDisabled(),
                errorMessage: status.errorMessage,
                lastUpdate: status.lastUpdate?.format("yyyy-MM-dd'T'HH:mm:ss'Z'")
            ]
        }

        json.tools = [
            count: mcp.McpToolDiscovery.getToolCount(),
            names: new ArrayList<>(mcp.McpToolDiscovery.getToolNames())
        ]

        println new groovy.json.JsonOutput().toJson(json)
    }

}
