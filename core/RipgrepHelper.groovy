package core

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

class RipgrepHelper {
    static final String RIPGREP_VERSION = "14.1.0"
    static final int MAX_RESULTS = 100
    
    static final Map<String, Map> PLATFORMS = [
        "linux-amd64": [
            platform: "x86_64-unknown-linux-musl",
            extension: "tar.gz"
        ],
        "linux-arm64": [
            platform: "aarch64-unknown-linux-gnu",
            extension: "tar.gz"
        ],
        "darwin-amd64": [
            platform: "x86_64-apple-darwin",
            extension: "tar.gz"
        ],
        "darwin-arm64": [
            platform: "aarch64-apple-darwin",
            extension: "tar.gz"
        ],
        "windows-amd64": [
            platform: "x86_64-pc-windows-msvc",
            extension: "zip"
        ]
    ]
    
    private static String cachedPath = null
    
    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows")
    }
    
    static String getPlatform() {
        String os = System.getProperty("os.name").toLowerCase()
        String arch = System.getProperty("os.arch")
        
        if (os.contains("linux")) {
            return arch.contains("aarch64") ? "linux-arm64" : "linux-amd64"
        } else if (os.contains("mac") || os.contains("darwin")) {
            return arch.contains("aarch64") ? "darwin-arm64" : "darwin-amd64"
        } else if (os.contains("windows")) {
            return "windows-amd64"
        }
        return null
    }
    
    static String getRipgrepPath() {
        if (cachedPath != null) return cachedPath
        
        cachedPath = findRipgrepInPath()
        if (cachedPath != null) {
            return cachedPath
        }
        
        cachedPath = findRipgrepInCache()
        if (cachedPath != null) {
            return cachedPath
        }
        
        return null
    }
    
    private static String findRipgrepInPath() {
        try {
            def which = isWindows() ? "where rg" : "which rg"
            def proc = which.execute()
            proc.waitFor()
            if (proc.exitValue() == 0) {
                String output = proc.text.trim()
                if (output) {
                    return output.split("\n")[0]
                }
            }
        } catch (Exception e) {
        }
        return null
    }
    
    private static String findRipgrepInCache() {
        try {
            Path binDir = getGlmBinDir()
            String rgBinary = isWindows() ? "rg.exe" : "rg"
            Path rgPath = binDir.resolve(rgBinary)
            
            if (Files.exists(rgPath) && Files.isExecutable(rgPath)) {
                return rgPath.toString()
            }
        } catch (Exception e) {
        }
        return null
    }
    
    static Path getGlmBinDir() {
        Path homeDir = Paths.get(System.getProperty("user.home"))
        Path glmDir = homeDir.resolve(".glm")
        Path binDir = glmDir.resolve("bin")
        
        try {
            if (!Files.exists(binDir)) {
                Files.createDirectories(binDir)
            }
        } catch (Exception e) {
        }
        
        return binDir
    }
    
    static ProcessResult executeRipgrep(List<String> args, String cwd = ".") {
        String rgPath = getRipgrepPath()
        if (rgPath == null) {
            return new ProcessResult(exitCode: 1, error: "Ripgrep not available")
        }
        
        try {
            def command = [rgPath] + args
            def proc = command.execute(null, new File(cwd))
            def stdout = new StringBuilder()
            def stderr = new StringBuilder()
            
            proc.consumeProcessOutput(stdout, stderr)
            proc.waitFor()
            
            return new ProcessResult(
                exitCode: proc.exitValue(),
                output: stdout.toString(),
                error: stderr.toString()
            )
        } catch (Exception e) {
            return new ProcessResult(exitCode: 1, error: "Error executing ripgrep: ${e.message}")
        }
    }
    
    static List<GrepMatch> search(String pattern, String path = ".", String include = null) {
        def args = [
            "--json",
            "--regexp", pattern,
            "--max-count", String.valueOf(MAX_RESULTS)
        ]
        
        if (include) {
            args += ["--glob", include]
        }
        
        args += [path]
        
        def result = executeRipgrep(args, path)
        if (result.exitCode != 0 && !result.output) {
            return []
        }
        
        return parseJsonOutput(result.output)
    }
    
    static List<String> files(String pattern, String path = ".") {
        def args = [
            "--files",
            "--glob", pattern,
            "--glob", "!.git/*"
        ]
        
        def result = executeRipgrep(args, path)
        if (result.exitCode != 0) {
            return []
        }
        
        return result.output
            .split("\n")
            .findAll { it.trim() }
            .take(MAX_RESULTS)
    }
    
    private static List<GrepMatch> parseJsonOutput(String output) {
        def matches = []
        def lines = output.split("\n")
        
        lines.each { line ->
            try {
                def json = new groovy.json.JsonSlurper().parseText(line)
                
                if (json.type == "match") {
                    def match = new GrepMatch(
                        filePath: json.data?.path?.text ?: "",
                        lineNumber: json.data?.line_number ?: 0,
                        lineText: json.data?.lines?.text ?: ""
                    )
                    
                    json.data?.submatches?.each { sub ->
                        match.submatches << new SubMatch(
                            text: sub?.match?.text ?: "",
                            start: sub?.start ?: 0,
                            end: sub?.end ?: 0
                        )
                    }
                    
                    matches << match
                }
            } catch (Exception e) {
            }
        }
        
        return matches
    }
    
    static boolean isAvailable() {
        return getRipgrepPath() != null
    }
}

class ProcessResult {
    int exitCode
    String output
    String error
}

class GrepMatch {
    String filePath
    int lineNumber
    String lineText
    List<SubMatch> submatches = []
}

class SubMatch {
    String text
    int start
    int end
}
