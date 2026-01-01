package core

import groovy.transform.Canonical

/**
 * Configuration for an LSP server.
 */
@Canonical
class LSPServerConfig {
    String id
    List<String> command
    List<String> extensions
    Map<String, String> env = [:]
    Map initializationOptions = [:]
    Closure<String> rootDetector
}

/**
 * Registry of built-in language server configurations.
 * Handles server definitions and process spawning.
 */
class LSPServerRegistry {
    
    private static final Map<String, LSPServerConfig> BUILTIN_SERVERS = [
        'typescript': new LSPServerConfig(
            id: 'typescript',
            command: ['npx', 'typescript-language-server', '--stdio'],
            extensions: ['.ts', '.tsx', '.js', '.jsx'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'package.json', 'tsconfig.json') }
        ),
        'java': new LSPServerConfig(
            id: 'java',
            command: ['jdtls'],
            extensions: ['.java'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'pom.xml', 'build.gradle', 'settings.gradle') }
        ),
        'groovy': new LSPServerConfig(
            id: 'groovy',
            command: ['groovy-language-server'],
            extensions: ['.groovy'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'build.gradle', 'settings.gradle') }
        ),
        'python': new LSPServerConfig(
            id: 'python',
            command: ['npx', 'pyright-langserver', '--stdio'],
            extensions: ['.py'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'pyproject.toml', 'setup.py', 'requirements.txt') }
        ),
        'go': new LSPServerConfig(
            id: 'go',
            command: ['gopls'],
            extensions: ['.go'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'go.mod') }
        ),
        'rust': new LSPServerConfig(
            id: 'rust',
            command: ['rust-analyzer'],
            extensions: ['.rs'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'Cargo.toml') }
        )
    ]
    
    private Map<String, LSPServerConfig> customServers = [:]
    
    /**
     * Register a custom server configuration.
     * @param config Server configuration
     */
    void registerServer(LSPServerConfig config) {
        customServers.put(config.id, config)
    }
    
    /**
     * Get server configuration for a file.
     * @param filePath File path
     * @return Server config or null
     */
    LSPServerConfig getServerForFile(String filePath) {
        def ext = getExtension(filePath)
        if (!ext) return null
        
        // Check custom servers first
        for (config in customServers.values()) {
            if (config.extensions.contains(ext)) {
                return config
            }
        }
        
        // Check builtin servers
        for (config in BUILTIN_SERVERS.values()) {
            if (config.extensions.contains(ext)) {
                return config
            }
        }
        
        return null
    }
    
    /**
     * Spawn a language server process.
     * @param config Server configuration
     * @param root Project root directory
     * @return Process
     */
    Process spawn(LSPServerConfig config, String root) {
        def pb = new ProcessBuilder(config.command)
        pb.directory(new File(root))
        pb.redirectErrorStream(false)
        
        // Add environment variables
        def env = pb.environment()
        config.env.each { k, v -> env.put(k, v) }
        
        // Check if command exists
        def cmd = config.command[0]
        if (!isCommandAvailable(cmd)) {
            throw new RuntimeException("LSP server command not found: ${cmd}")
        }
        
        return pb.start()
    }
    
    /**
     * Check if a command is available.
     */
    private boolean isCommandAvailable(String cmd) {
        if (cmd == 'npx') return true  // npx auto-installs
        
        try {
            def pb = new ProcessBuilder('which', cmd)
            def process = pb.start()
            return process.waitFor() == 0
        } catch (Exception e) {
            return false
        }
    }
    
    /**
     * Get file extension including the dot.
     */
    private String getExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.')
        if (lastDot < 0 || lastDot == filePath.length() - 1) return null
        return filePath.substring(lastDot).toLowerCase()
    }
    
    /**
     * Get list of supported extensions.
     */
    List<String> getSupportedExtensions() {
        def exts = [] as Set
        BUILTIN_SERVERS.values().each { exts.addAll(it.extensions) }
        customServers.values().each { exts.addAll(it.extensions) }
        return exts.toList()
    }
}
