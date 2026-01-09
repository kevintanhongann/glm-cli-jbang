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
            extensions: ['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'package.json', 'tsconfig.json', 'jsconfig.json') }
        ),
        'java': new LSPServerConfig(
            id: 'java',
            command: ['jdtls'],
            extensions: ['.java'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'pom.xml', 'build.gradle', 'settings.gradle', 'build.gradle.kts', 'settings.gradle.kts') }
        ),
        'groovy': new LSPServerConfig(
            id: 'groovy',
            command: ['groovy-language-server'],
            extensions: ['.groovy', '.gvy', '.gy'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'build.gradle', 'settings.gradle', 'gradle.properties', '.groovylintrc.json') }
        ),
        'python': new LSPServerConfig(
            id: 'python',
            command: ['npx', 'pyright-langserver', '--stdio'],
            extensions: ['.py', '.pyw'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'pyproject.toml', 'setup.py', 'requirements.txt', 'setup.cfg', 'pyrightconfig.json') }
        ),
        'go': new LSPServerConfig(
            id: 'go',
            command: ['gopls'],
            extensions: ['.go'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'go.mod', 'go.sum', 'go.work') }
        ),
        'rust': new LSPServerConfig(
            id: 'rust',
            command: ['rust-analyzer'],
            extensions: ['.rs'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'Cargo.toml', 'Cargo.lock', 'rust-toolchain.toml') }
        ),
        'json': new LSPServerConfig(
            id: 'json',
            command: ['npx', '-y', 'vscode-json-languageserver', '--stdio'],
            extensions: ['.json', '.jsonc', '.jsonl'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, 'package.json', 'tsconfig.json', '.eslintrc.json') }
        ),
        'yaml': new LSPServerConfig(
            id: 'yaml',
            command: ['npx', '-y', 'yaml-language-server', '--stdio'],
            extensions: ['.yaml', '.yml'],
            rootDetector: { dir -> RootDetector.findNearestFile(dir, '.yaml-lint', '.yamllint') }
        ),
        'html': new LSPServerConfig(
            id: 'html',
            command: ['npx', '-y', 'vscode-html-languageserver', '--stdio'],
            extensions: ['.html', '.htm'],
            rootDetector: { dir -> null }
        ),
        'css': new LSPServerConfig(
            id: 'css',
            command: ['npx', '-y', 'vscode-css-languageserver', '--stdio'],
            extensions: ['.css', '.scss', '.sass', '.less', '.styl'],
            rootDetector: { dir -> null }
        ),
        'markdown': new LSPServerConfig(
            id: 'markdown',
            command: ['npx', '-y', 'markdown-language-server', '--stdio'],
            extensions: ['.md', '.markdown'],
            rootDetector: { dir -> null }
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
