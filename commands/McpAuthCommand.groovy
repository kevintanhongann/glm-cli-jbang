package commands

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import mcp.McpClientManager
import mcp.McpConfig
import mcp.McpServerConfig
import mcp.OAuthCallbackServer
import mcp.OAuthTokenStorage
import mcp.OAuthTokens

@Command(name = 'auth', description = 'Start OAuth authentication flow for an MCP server')
class McpAuthCommand implements Runnable {

    @Parameters(index = "0", description = "MCP server name")
    String serverName

    @Option(names = ['--port'], description = "Callback port")
    int port = 19876

    @Option(names = ['--open-browser'], description = "Open browser for authentication", arity = "0")
    boolean openBrowser = true

    @Override
    void run() {
        if (serverName == null) {
            System.err.println "Error: Server name is required"
            CommandLine.usage(this, System.out)
            return
        }

        McpClientManager.instance.initialize()
        McpConfig config = McpConfig.load()

        McpServerConfig serverConfig = config.getServer(serverName)
        if (serverConfig == null) {
            System.err.println "Error: MCP server '${serverName}' not found"
            return
        }

        if (!serverConfig.isRemote()) {
            System.err.println "Error: OAuth is only supported for remote MCP servers"
            return
        }

        if (serverConfig.oauth == null) {
            System.err.println "Error: No OAuth configuration found for server '${serverName}'"
            return
        }

        def oauth = serverConfig.oauth

        println "Starting OAuth authentication for '${serverName}'..."
        println ""

        String state = generateState()
        String authUrl = buildAuthUrl(oauth, state)

        println "Authorization URL: ${authUrl}"
        println ""

        if (openBrowser) {
            openBrowser(authUrl)
        } else {
            println "Please open the URL above in your browser."
        }

        OAuthCallbackServer callbackServer = new OAuthCallbackServer(port)
        println "Waiting for OAuth callback..."

        String authCode = callbackServer.waitForAuthCode(60)

        if (callbackServer.getError()) {
            System.err.println "OAuth error: ${callbackServer.getError()}"
            return
        }

        if (!authCode) {
            System.err.println "Error: No authorization code received"
            return
        }

        println "Authorization code received. Exchanging for tokens..."

        try {
            OAuthTokens tokens = exchangeTokens(oauth, authCode)
            OAuthTokenStorage.instance.saveTokens(serverName, tokens)
            println "✓ Authentication successful!"
            println ""
            println "You can now connect to the MCP server: glm mcp connect ${serverName}"
        } catch (Exception e) {
            System.err.println "Error exchanging authorization code: ${e.message}"
        }
    }

    private String generateState() {
        return UUID.randomUUID().toString()
    }

    private String buildAuthUrl(mcp.OAuthConfig oauth, String state) {
        StringBuilder url = new StringBuilder()
        url.append(oauth.authorizationEndpoint)
        url.append("?response_type=code")
        url.append("&client_id=${URLEncoder.encode(oauth.clientId ?: '', 'UTF-8')}")
        url.append("&redirect_uri=${URLEncoder.encode(oauth.redirectUri, 'UTF-8')}")
        url.append("&scope=${URLEncoder.encode(oauth.scope ?: 'read', 'UTF-8')}")
        url.append("&state=${state}")

        return url.toString()
    }

    private void openBrowser(String url) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler ${url}")
            } else {
                Runtime.getRuntime().exec(["xdg-open", url].toArray(new String[0]))
            }
        } catch (Exception e) {
            println "Could not open browser. Please open the URL manually."
        }
    }

    private OAuthTokens exchangeTokens(mcp.OAuthConfig oauth, String authCode) {
        URL url = new URL(oauth.tokenEndpoint)
        HttpURLConnection conn = (HttpURLConnection) url.openConnection()
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setDoOutput(true)

        String body = "grant_type=authorization_code"
        body += "&code=${authCode}"
        body += "&redirect_uri=${URLEncoder.encode(oauth.redirectUri, 'UTF-8')}"
        if (oauth.clientId) {
            body += "&client_id=${URLEncoder.encode(oauth.clientId, 'UTF-8')}"
        }
        if (oauth.clientSecret) {
            body += "&client_secret=${URLEncoder.encode(oauth.clientSecret, 'UTF-8')}"
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes())
        }

        try (InputStream is = conn.getInputStream()) {
            String response = new String(is.readAllBytes())
            def parsed = new groovy.json.JsonSlurper().parseText(response)

            OAuthTokens tokens = new OAuthTokens()
            tokens.accessToken = parsed.access_token
            tokens.refreshToken = parsed.refresh_token
            tokens.tokenType = parsed.token_type ?: 'Bearer'

            if (parsed.expires_in) {
                long expiresAt = System.currentTimeMillis() + (parsed.expires_in as int) * 1000
                tokens.expiresAt = new Date(expiresAt)
            }

            return tokens
        }
    }

}
