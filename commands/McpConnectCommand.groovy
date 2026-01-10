package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpClientManager
import mcp.McpConfig
import mcp.McpConnectionRetry
import mcp.ServerState

@Command(name = 'connect', description = 'Connect to an MCP server')
class McpConnectCommand implements Runnable {

    @Parameters(index = "0", description = "Server name to connect")
    String serverName

    @Option(names = ['--retry'], description = "Number of retry attempts")
    int maxRetries = 3

    @Override
    void run() {
        if (serverName == null) {
            System.err.println "Error: Server name is required"
            CommandLine.usage(this, System.out)
            return
        }

        McpClientManager.instance.initialize()

        def status = McpClientManager.instance.getStatus(serverName)
        if (status != null && status.isConnected()) {
            println "Already connected to '${serverName}'"
            return
        }

        if (status != null && status.isDisabled()) {
            McpConfig config = McpConfig.load()
            def serverConfig = config.getServer(serverName)
            if (serverConfig) {
                serverConfig.enabled = true
                config.save()
            }
        }

        McpConnectionRetry retry = new McpConnectionRetry()
        retry.maxRetries = maxRetries

        print "Connecting to '${serverName}'... "

        try {
            retry.executeWithRetry({
                McpClientManager.instance.connect(serverName)
            })

            status = McpClientManager.instance.getStatus(serverName)
            if (status?.isConnected()) {
                println "✓ Connected"
                int toolCount = mcp.McpToolDiscovery.getToolCount()
                println "Discovered ${toolCount} tools from ${serverName}"
            } else {
                println "✗ Failed to connect"
                if (status?.errorMessage) {
                    println "Error: ${status.errorMessage}"
                }
            }
        } catch (Exception e) {
            println "✗ Failed"
            println "Error: ${e.message}"
        }
    }

}
