package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpClientManager
import mcp.McpConfig
import mcp.ServerStatus
import mcp.ServerState

@Command(name = 'list', description = 'List all configured MCP servers')
class McpListCommand implements Runnable {

    @Option(names = ['--json'], description = 'Output as JSON')
    boolean jsonOutput = false

    @Option(names = ['-v', '--verbose'], description = 'Show detailed information')
    boolean verbose = false

    @Override
    void run() {
        McpClientManager.instance.initialize()
        Map<String, ServerStatus> statuses = McpClientManager.instance.getStatuses()

        if (jsonOutput) {
            printJson(statuses)
        } else {
            printTable(statuses)
        }
    }

    private void printTable(Map<String, ServerStatus> statuses) {
        if (statuses.isEmpty()) {
            println "No MCP servers configured."
            println "Use 'glm mcp add' to add an MCP server."
            return
        }

        println "MCP Servers:"
        println "".padRight(60, '-')

        statuses.each { name, status ->
            String icon = getStatusIcon(status.state)
            String stateStr = status.state.toString()
            String line = "${icon} ${name.padRight(25)} ${stateStr.padRight(12)}"

            if (verbose && status.errorMessage) {
                line += " - ${status.errorMessage}"
            }

            println line
        }

        println ""
        int connected = statuses.values().count { it.state == ServerState.CONNECTED }
        int failed = statuses.values().count { it.state == ServerState.FAILED }
        int total = statuses.size()

        println "Total: ${total} | Connected: ${connected} | Failed: ${failed}"
    }

    private void printJson(Map<String, ServerStatus> statuses) {
        def json = [:]
        json.servers = statuses.collect { name, status ->
            [
                name: name,
                state: status.state.toString(),
                connected: status.isConnected(),
                errorMessage: status.errorMessage,
                lastUpdate: status.lastUpdate?.format("yyyy-MM-dd'T'HH:mm:ss'Z'")
            ]
        }
        json.summary = [
            total: statuses.size(),
            connected: statuses.values().count { it.state == ServerState.CONNECTED },
            failed: statuses.values().count { it.state == ServerState.FAILED }
        ]

        println new groovy.json.JsonOutput().toJson(json)
    }

    private String getStatusIcon(ServerState state) {
        switch (state) {
            case ServerState.CONNECTED: return '●'
            case ServerState.CONNECTING: return '○'
            case ServerState.FAILED: return '⊗'
            case ServerState.DISABLED: return '⊘'
            default: return '○'
        }
    }

}
