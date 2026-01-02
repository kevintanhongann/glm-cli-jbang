package core

import java.nio.file.Files
import java.nio.file.Paths

class Instructions {
    static final List<String> LOCAL_RULE_FILES = [
        "AGENTS.md",
        "CLAUDE.md",
        "CONTEXT.md"
    ]

    static final List<String> GLOBAL_RULE_FILES = [
        "${System.getProperty('user.home')}/.glm/AGENTS.md",
        "${System.getProperty('user.home')}/.claude/CLAUDE.md"
    ]

    static List<String> detect(String workDir = null, String stopDir = null) {
        String cwd = workDir ?: System.getProperty("user.dir")
        String stop = stopDir ?: findStopDirectory(cwd)

        Set<String> paths = new LinkedHashSet<>()
        Config config = Config.load()

        for (String ruleFile : LOCAL_RULE_FILES) {
            def matches = Filesystem.findUp(ruleFile, cwd, stop)
            if (!matches.isEmpty()) {
                paths.addAll(matches)
                break
            }
        }

        for (String globalRuleFile : GLOBAL_RULE_FILES) {
            if (Files.exists(Paths.get(globalRuleFile))) {
                paths.add(globalRuleFile)
                break
            }
        }

        if (config.instructions) {
            for (String instruction : config.instructions) {
                def resolved = resolveInstructionPath(instruction, cwd, stop)
                paths.addAll(resolved)
            }
        }

        return new ArrayList<>(paths)
    }

    static List<String> loadAll(String workDir = null, String stopDir = null) {
        List<String> paths = detect(workDir, stopDir)
        List<String> contents = []

        paths.each { path ->
            try {
                def content = Files.readString(Paths.get(path))
                contents << "Instructions from: ${path}\n${content}"
            } catch (Exception e) {
                System.err.println("Warning: Failed to read ${path}: ${e.message}")
            }
        }

        return contents
    }

    private static String findStopDirectory(String startDir) {
        def gitRoot = RootDetector.findGitRoot(startDir)
        if (gitRoot) {
            return gitRoot
        }

        def projectRoot = RootDetector.findProjectRoot(startDir)
        if (projectRoot && projectRoot != startDir) {
            return projectRoot
        }

        return System.getProperty("user.home")
    }

    private static List<String> resolveInstructionPath(String instruction, String cwd, String stop) {
        String path = instruction.startsWith('~/') ?
            instruction.replaceFirst('^~/', System.getProperty('user.home') + '/') :
            instruction

        if (Paths.get(path).isAbsolute()) {
            if (Files.exists(Paths.get(path))) {
                return [path]
            }
            return []
        } else {
            return Filesystem.globUp(path, cwd, stop)
        }
    }
}
