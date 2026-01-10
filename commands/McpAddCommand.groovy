package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpConfig
import mcp.McpServerConfig
import mcp.OAuthConfig
import java.nio.file.Files
import java.nio.file.Paths

@Command(name = 'add', description = 'Add a new MCP server')
class McpAddCommand implements Runnable {

    @Parameters(index = "0", description = "Server name", arity = "0..1")
    String serverName

    @Option(names = ['--type'], description = "Server type: local or remote")
    String type

    @Option(names = ['--command'], description = "Command for local server (e.g., 'npx -y @modelcontextprotocol/server-everything')")
    String command

    @Option(names = ['--url'], description = "URL for remote server")
    String url

    @Option(names = ['--env'], description = "Environment variables (key=value, comma-separated)")
    String envVars

    @Option(names = ['--header'], description = "HTTP headers (key=value, comma-separated)")
    String headers

    @Option(names = ['--no-auto-connect'], description = "Don't connect automatically")
    boolean noAutoConnect = false

    @Option(names = ['--transport'], description = "Transport type: stdio (local), http or sse (remote)")
    String transport = 'stdio'

    private final Scanner scanner = new Scanner(System.in)

    @Override
    void run() {
        McpConfig config = McpConfig.load()

        if (serverName == null) {
            serverName = prompt("Server name")
        }

        if (config.getServer(serverName) != null) {
            System.err.println("Server '${serverName}' already exists. Use 'glm mcp remove ${serverName}' to remove it first.")
            return
        }

        McpServerConfig serverConfig = new McpServerConfig()

        if (type == null) {
            type = promptChoice("Transport type", ["1" : "Local (stdio)", "2" : "Remote (HTTP/SSE)"], "1")
        }

        if ("1".equals(type) || "local".equalsIgnoreCase(type)) {
            serverConfig.type = 'local'
            serverConfig.transport = 'stdio'

            if (command == null) {
                command = prompt("Command", "npx -y @modelcontextprotocol/server-everything")
            }
            serverConfig.command = command.split(' ').toList()

            if (envVars == null) {
                println "Environment variables (optional, press Enter to skip):"
                String envInput = scanner.nextLine()?.trim()
                if (envInput && !envInput.isEmpty()) {
                    envInput.split(',').each { pair ->
                        def parts = pair.split('=', 2)
                        if (parts.length == 2) {
                            serverConfig.environment.put(parts[0].trim(), parts[1].trim())
                        }
                    }
                }
            } else {
                envVars.split(',').each { pair ->
                    def parts = pair.split('=', 2)
                    if (parts.length == 2) {
                        serverConfig.environment.put(parts[0].trim(), parts[1].trim())
                    }
                }
            }
        } else {
            serverConfig.type = 'remote'

            if (url == null) {
                url = prompt("Server URL", "https://mcp.example.com")
            }
            serverConfig.url = url

            if (transport == null || transport.isEmpty()) {
                transport = promptChoice("Transport", ["1" : "HTTP", "2" : "SSE"], "1")
            }

            if ("2".equals(transport) || "sse".equalsIgnoreCase(transport)) {
                serverConfig.transport = 'sse'
            } else {
                serverConfig.transport = 'http'
            }

            if (headers == null) {
                println "HTTP headers (optional, e.g., 'API_KEY={env:API_KEY}', press Enter to skip):"
                String headerInput = scanner.nextLine()?.trim()
                if (headerInput && !headerInput.isEmpty()) {
                    headerInput.split(',').each { pair ->
                        def parts = pair.split('=', 2)
                        if (parts.length == 2) {
                            serverConfig.headers.put(parts[0].trim(), parts[1].trim())
                        }
                    }
                }
            } else {
                headers.split(',').each { pair ->
                    def parts = pair.split('=', 2)
                    if (parts.length == 2) {
                        serverConfig.headers.put(parts[0].trim(), parts[1].trim())
                    }
                }
            }
        }

        serverConfig.enabled = !noAutoConnect

        config.addServer(serverName, serverConfig)

        println ""
        println "✓ MCP server '${serverName}' added successfully"
        println ""
        println "To connect, run: glm mcp connect ${serverName}"
    }

    private String prompt(String message, String defaultValue = null) {
        String promptText = message
        if (defaultValue) {
            promptText += " [${defaultValue}]"
        }
        promptText += ": "

        print promptText
        String result = scanner.nextLine()?.trim()
        return result ?: defaultValue
    }

    private String promptChoice(String message, Map<String, String> choices, String defaultValue) {
        println "${message}:"
        choices.each { key, value ->
            println "  [${key}] ${value}"
        }
        print "Choose (${defaultValue}): "
        String result = scanner.nextLine()?.trim()
        return result ?: defaultValue
    }

}
