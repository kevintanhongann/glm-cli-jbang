package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpClientManager
import mcp.McpConfig

@Command(name = 'remove', description = 'Remove an MCP server')
class McpRemoveCommand implements Runnable {

    @Parameters(index = "0", description = "Server name to remove")
    String serverName

    @Option(names = ['--force', '-f'], description = "Remove without confirmation")
    boolean force = false

    @Override
    void run() {
        if (serverName == null) {
            System.err.println "Error: Server name is required"
            CommandLine.usage(this, System.out)
            return
        }

        McpConfig config = McpConfig.load()

        if (config.getServer(serverName) == null) {
            System.err.println "Error: MCP server '${serverName}' not found"
            return
        }

        boolean confirmed = force
        if (!force) {
            print "Are you sure you want to remove MCP server '${serverName}'? (y/N): "
            def input = System.in.newReader().readLine()?.trim()?.toLowerCase()
            confirmed = 'y'.equals(input) || 'yes'.equals(input)
        }

        if (confirmed) {
            if (McpClientManager.instance.hasConnection(serverName)) {
                McpClientManager.instance.disconnect(serverName)
            }
            config.removeServer(serverName)
            println "✓ MCP server '${serverName}' removed successfully"
        } else {
            println "Removal cancelled"
        }
    }

}
