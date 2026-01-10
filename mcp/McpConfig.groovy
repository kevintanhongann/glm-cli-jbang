package mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@JsonIgnoreProperties(ignoreUnknown = true)
class McpConfig {

    @JsonProperty('mcpServers')
    Map<String, McpServerConfig> mcpServers = [:]

    static McpConfig load() {
        McpConfig config = new McpConfig()
        Path configPath = findConfigFile()

        if (configPath != null && Files.exists(configPath)) {
            try {
                ObjectMapper mapper = new ObjectMapper()
                config = mapper.readValue(configPath.toFile(), McpConfig.class)
            } catch (Exception e) {
                System.err.println("Warning: Failed to load MCP config: ${e.message}")
            }
        }

        return config
    }

    private static Path findConfigFile() {
        Path currentDir = Paths.get(System.getProperty('user.dir')).toAbsolutePath()

        while (currentDir != null) {
            Path mcpConfig = currentDir.resolve('.glm').resolve('mcp.json')
            if (Files.exists(mcpConfig)) {
                return mcpConfig
            }

            Path gitDir = currentDir.resolve('.git')
            if (Files.exists(gitDir)) {
                break
            }

            currentDir = currentDir.parent
        }

        Path userConfig = Paths.get(System.getProperty('user.home'), '.glm', 'mcp.json')
        if (Files.exists(userConfig)) {
            return userConfig
        }

        return null
    }

    void save() {
        Path configPath = Paths.get(System.getProperty('user.home'), '.glm', 'mcp.json')
        try {
            Files.createDirectories(configPath.parent)
            ObjectMapper mapper = new ObjectMapper()
            mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), this)
        } catch (Exception e) {
            System.err.println("Warning: Failed to save MCP config: ${e.message}")
        }
    }

    McpServerConfig getServer(String name) {
        return mcpServers?.get(name)
    }

    void addServer(String name, McpServerConfig config) {
        if (mcpServers == null) {
            mcpServers = [:]
        }
        mcpServers[name] = config
        save()
    }

    void removeServer(String name) {
        if (mcpServers != null) {
            mcpServers.remove(name)
            save()
        }
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
class McpServerConfig {

    @JsonProperty('type')
    String type = 'local'

    @JsonProperty('command')
    List<String> command

    @JsonProperty('url')
    String url

    @JsonProperty('environment')
    Map<String, String> environment = [:]

    @JsonProperty('headers')
    Map<String, String> headers = [:]

    @JsonProperty('enabled')
    boolean enabled = true

    @JsonProperty('timeout')
    int timeout = 30000

    @JsonProperty('oauth')
    OAuthConfig oauth

    @JsonProperty('transport')
    String transport = 'stdio'

    boolean isLocal() {
        return type == 'local'
    }

    boolean isRemote() {
        return type == 'remote'
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
class OAuthConfig {

    @JsonProperty('authorization_endpoint')
    String authorizationEndpoint

    @JsonProperty('token_endpoint')
    String tokenEndpoint

    @JsonProperty('client_id')
    String clientId

    @JsonProperty('client_secret')
    String clientSecret

    @JsonProperty('scope')
    String scope = 'read'

    @JsonProperty('redirect_uri')
    String redirectUri = 'http://localhost:19876/callback'

}
