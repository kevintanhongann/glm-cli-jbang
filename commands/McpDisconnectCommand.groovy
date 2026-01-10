package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpClientManager

@Command(name = 'disconnect', description = 'Disconnect from an MCP server')
class McpDisconnectCommand implements Runnable {

    @Parameters(index = "0", description = "Server name to disconnect")
    String serverName

    @Override
    void run() {
        if (serverName == null) {
            System.err.println "Error: Server name is required"
            CommandLine.usage(this, System.out)
            return
        }

        McpClientManager.instance.initialize()

        def status = McpClientManager.instance.getStatus(serverName)
        if (status == null || !status.isConnected()) {
            println "Not connected to '${serverName}'"
            return
        }

        McpClientManager.instance.disconnect(serverName)
        println "Disconnected from '${serverName}'"
    }

}
