package core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility class for detecting project roots by finding marker files.
 */
class RootDetector {
    
    /**
     * Find the nearest directory containing any of the marker files.
     * @param startDir Starting directory
     * @param markers Marker file names to look for
     * @return Path to the root directory, or startDir if not found
     */
    static String findNearestFile(String startDir, String... markers) {
        Path current = Paths.get(startDir).toAbsolutePath()
        
        while (current != null) {
            for (String marker : markers) {
                if (Files.exists(current.resolve(marker))) {
                    return current.toString()
                }
            }
            current = current.parent
        }
        
        return startDir  // Fallback to start directory
    }
    
    /**
     * Find the git root directory.
     * @param startDir Starting directory
     * @return Path to the git root, or null if not in a git repo
     */
    static String findGitRoot(String startDir) {
        Path current = Paths.get(startDir).toAbsolutePath()
        
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current.toString()
            }
            current = current.parent
        }
        
        return null
    }
    
    /**
     * Find project root by common project markers.
     * @param startDir Starting directory
     * @return Path to project root
     */
    static String findProjectRoot(String startDir) {
        def markers = [
            // JavaScript/TypeScript
            "package.json", "tsconfig.json",
            // Java/Groovy
            "pom.xml", "build.gradle", "settings.gradle", "build.gradle.kts",
            // Python
            "pyproject.toml", "setup.py", "requirements.txt",
            // Go
            "go.mod",
            // Rust
            "Cargo.toml",
            // Generic
            ".git"
        ]
        
        return findNearestFile(startDir, markers as String[])
    }
}
