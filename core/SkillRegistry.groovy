package core

import models.Skill
import java.nio.file.Files
import java.nio.file.Paths

class SkillRegistry {
    private static final List<String> SKILL_SEARCH_PATHS = [
        '.glm/skills',
        '.claude/skills',
        '~/.glm/skills',
        '~/.claude/skills'
    ]

    private Map<String, Skill> skills = [:]
    private boolean loaded = false

    void discover(String workDir = null) {
        if (loaded) return

        String cwd = workDir ?: System.getProperty("user.dir")
        String stopDir = findStopDirectory(cwd)

        searchPath('.glm/skills', cwd, stopDir)
        searchPath('.claude/skills', cwd, stopDir)
        searchPath(resolveHome('~/.glm/skills'), null, null)
        searchPath(resolveHome('~/.claude/skills'), null, null)

        loaded = true
    }

    private void searchPath(String basePath, String cwd, String stopDir) {
        def base = Paths.get(basePath)
        if (!Files.exists(base)) return

        Files.walk(base).withCloseable { stream ->
            stream.filter { it.fileName?.toString() == 'SKILL.md' }
                  .forEach { skillFile ->
                      try {
                          def skill = Skill.fromFile(skillFile.toFile())
                          if (skill.isValid() && !skills.containsKey(skill.name)) {
                              skills[skill.name] = skill
                          }
                      } catch (Exception e) {
                          System.err.println("Warning: Failed to load skill from ${skillFile}: ${e.message}")
                      }
                  }
        }
    }

    private String findStopDirectory(String startDir) {
        def gitRoot = RootDetector.findGitRoot(startDir)
        return gitRoot ?: System.getProperty("user.home")
    }

    private String resolveHome(String path) {
        if (path.startsWith('~')) {
            return path.replace('~', System.getProperty("user.home"))
        }
        return path
    }

    List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values())
    }

    List<Skill> getAvailableSkills() {
        return skills.values().findAll { it.matchesCompatibility() }
    }

    Skill getSkill(String name) {
        return skills[name]
    }

    boolean hasSkill(String name) {
        return skills.containsKey(name)
    }

    List<String> getSkillNames() {
        return skills.keySet().asList()
    }

    void clear() {
        skills.clear()
        loaded = false
    }
}
