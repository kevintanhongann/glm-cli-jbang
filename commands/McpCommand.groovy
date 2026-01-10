package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import mcp.McpClientManager
import mcp.McpConfig
import mcp.McpServerConfig

@Command(name = 'mcp', description = 'Manage MCP (Model Context Protocol) servers',
        subcommands = [McpListCommand.class, McpAddCommand.class, McpRemoveCommand.class,
                       McpConnectCommand.class, McpDisconnectCommand.class, McpDebugCommand.class,
                       McpAuthCommand.class])
class McpCommand implements Runnable {

    @Option(names = ['--help'], help = true)
    boolean help

    @Override
    void run() {
        McpClientManager.instance.initialize()
        CommandLine.usage(this, System.out)
    }

}
