package core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Filesystem {

    static List<String> findUp(String target, String startDir, String stopDir = null) {
        List<String> results = []
        Path current = Paths.get(startDir).toAbsolutePath().normalize()
        Path stop = stopDir ? Paths.get(stopDir).toAbsolutePath().normalize() : null

        while (true) {
            Path searchPath = current.resolve(target)
            if (Files.exists(searchPath)) {
                results << searchPath.toString()
            }

            if (stop && current.equals(stop)) {
                break
            }

            Path parent = current.parent
            if (parent == null || parent.equals(current)) {
                break
            }
            current = parent
        }

        return results
    }

    static List<String> globUp(String pattern, String startDir, String stopDir = null) {
        List<String> results = []
        Path current = Paths.get(startDir).toAbsolutePath().normalize()
        Path stop = stopDir ? Paths.get(stopDir).toAbsolutePath().normalize() : null
        def rgHelper = new RipgrepHelper()

        while (true) {
            try {
                def regexPattern = globToRegex(pattern)
                def matches = rgHelper.searchRecursive(
                    current.toString(),
                    regexPattern,
                    [:]
                )
                matches.each { match ->
                    if (!results.contains(match)) {
                        results << match
                    }
                }
            } catch (Exception e) {
            }

            if (stop && current.equals(stop)) {
                break
            }

            Path parent = current.parent
            if (parent == null || parent.equals(current)) {
                break
            }
            current = parent
        }

        return results
    }

    private static String globToRegex(String glob) {
        String regex = glob.replaceAll('\\*\\*', '.*')
        regex = regex.replaceAll('\\*', '[^/]*')
        regex = regex.replaceAll('\\?', '[^/]')
        return regex
    }

    static boolean contains(String parent, String child) {
        Path parentPath = Paths.get(parent).toAbsolutePath().normalize()
        Path childPath = Paths.get(child).toAbsolutePath().normalize()
        return childPath.startsWith(parentPath)
    }

    static String normalizePath(String path) {
        return Paths.get(path).toAbsolutePath().normalize().toString()
    }
}
