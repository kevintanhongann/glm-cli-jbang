package core

import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Configuration for LSP behavior.
 */
class LSPConfig {
    /** Whether LSP is enabled */
    boolean enabled = true
    
    /** Timeout for waiting for diagnostics (ms) */
    int diagnosticTimeout = 3000
    
    /** Custom server configurations */
    Map<String, Map> servers = [:]
    
    /** Disabled server IDs */
    Set<String> disabledServers = []
    
    /**
     * Load configuration from ~/.glm/config.json or ~/.glm/config.toml
     */
    static LSPConfig load() {
        def config = new LSPConfig()
        
        // Try JSON config first
        def jsonPath = Paths.get(System.getProperty("user.home"), ".glm", "config.json")
        if (Files.exists(jsonPath)) {
            try {
                def json = new JsonSlurper().parse(jsonPath.toFile())
                def lspConfig = json?.lsp
                if (lspConfig) {
                    if (lspConfig.enabled != null) config.enabled = lspConfig.enabled
                    if (lspConfig.diagnosticTimeout) config.diagnosticTimeout = lspConfig.diagnosticTimeout
                    if (lspConfig.servers) {
                        lspConfig.servers.each { id, serverConf ->
                            if (serverConf.disabled) {
                                config.disabledServers.add(id)
                            } else {
                                config.servers.put(id, serverConf as Map)
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse LSP config: ${e.message}")
            }
        }
        
        // Check environment variable override
        def envEnabled = System.getenv("GLM_LSP_ENABLED")
        if (envEnabled != null) {
            config.enabled = envEnabled.toLowerCase() in ['true', '1', 'yes']
        }
        
        return config
    }
    
    /**
     * Check if a specific server is disabled.
     */
    boolean isServerDisabled(String serverId) {
        return disabledServers.contains(serverId)
    }
}
