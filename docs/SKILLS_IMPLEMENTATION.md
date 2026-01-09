# Claude Skills Implementation Plan for GLM-CLI-JBang

## Overview

Replicate the @opencode/ Claude Skills functionality in @glm-cli-jbang/. Skills are reusable behavior definitions that agents can discover and load on-demand during task execution.

## Status

- **Status:** Not Started
- **Priority:** Medium
- **Estimated Effort:** 4-5 days
- **Dependencies:** None (works with existing agent infrastructure)

---

## OpenCode Skills Architecture

### Core Concepts

1. **Skill Definition**: A `SKILL.md` file with YAML frontmatter containing:
   - `name` (required): Skill identifier
   - `description` (required): Human-readable description
   - `license` (optional): SPDX license identifier
   - `compatibility` (optional): Target platform
   - `metadata` (optional): Key-value pairs for filtering

2. **Skill Discovery**: Skills are discovered from multiple locations:
   - Project: `.glm/skills/<name>/SKILL.md`
   - Global: `~/.glm/skills/<name>/SKILL.md`
   - Claude-compatible: `.claude/skills/<name>/SKILL.md`
   - Claude global: `~/.claude/skills/<name>/SKILL.md`

3. **Skill Loading**: Agents use a `skill` tool to:
   - List available skills (in tool description)
   - Load skill content by name

4. **Permission System**: Pattern-based access control:
   - `allow`: Load immediately
   - `deny`: Hide from agent
   - `ask`: Prompt user for approval

5. **Tool Interface**:
   ```typescript
   skill({ name: "git-release" })  // Load skill content
   ```

### Directory Structure

```
.glm/
  skills/
    git-release/
      SKILL.md
    code-review/
      SKILL.md
```

### SKILL.md Format

```markdown
---
name: git-release
description: Create consistent releases and changelogs
license: MIT
compatibility: glm-cli
metadata:
  audience: maintainers
  workflow: github
---

## What I do

- Draft release notes from merged PRs
- Propose a version bump
- Provide a copy-pasteable `gh release create` command

## When to use me

Use this when you are preparing a tagged release.
```

---

## GLM-CLI-JBang Integration

### Existing Infrastructure

The following components are already in place and will be leveraged:

1. **AgentConfig.groovy**: Already supports tool filtering per agent type
2. **AgentRegistry.groovy**: Manages agent type selection
3. **Instructions.groovy**: Pattern for loading on-demand content with discovery
4. **Subagent.groovy**: Can load skills when spawned
5. **Tool.groovy interface**: Base for implementing the skill tool

### Skill File Locations

Following OpenCode conventions adapted for GLM-CLI-JBang:

| Location | Path | Priority |
|----------|------|----------|
| Project | `.glm/skills/<name>/SKILL.md` | 1 (highest) |
| Claude Project | `.claude/skills/<name>/SKILL.md` | 2 |
| Global | `~/.glm/skills/<name>/SKILL.md` | 3 |
| Claude Global | `~/.claude/skills/<name>/SKILL.md` | 4 (lowest) |

---

## Implementation Plan

### Phase 1: Skill Model and Loading (Days 1-2)

#### 1.1 Create Skill Model

**File:** `models/Skill.groovy`

```groovy
package models

import groovy.transform.Canonical

@Canonical
class Skill {
    String name
    String description
    String license
    String compatibility
    Map<String, String> metadata = [:]
    String content
    String sourcePath

    static Skill fromFile(File skillFile) {
        def content = skillFile.text
        def frontmatter = parseFrontmatter(content)
        def body = extractBody(content)

        return new Skill(
            name: frontmatter.name,
            description: frontmatter.description,
            license: frontmatter.license,
            compatibility: frontmatter.compatibility,
            metadata: frontmatter.metadata,
            content: body,
            sourcePath: skillFile.absolutePath
        )
    }

    static Map<String, String> parseFrontmatter(String content) {
        def result = [:]
        def matcher = content =~ /^---\s*\n([\s\S]*?)\n---/
        if (matcher) {
            def frontmatter = matcher[0][1]
            frontmatter.split('\n').each { line ->
                def keyValue = line.split(':', 2)
                if (keyValue.size() == 2) {
                    def key = keyValue[0].trim()
                    def value = keyValue[1].trim()
                    switch (key) {
                        case 'name':
                        case 'description':
                        case 'license':
                        case 'compatibility':
                            result[key] = value
                            break
                        case 'metadata':
                            break
                    }
                }
            }
        }
        return result
    }

    static String extractBody(String content) {
        def matcher = content =~ /^---\s*\n[\s\S]*?\n---\s*\n([\s\S]*)$/
        return matcher ? matcher[0][1].trim() : content
    }

    boolean isValid() {
        return name != null && !name.isEmpty() &&
               description != null && !description.isEmpty()
    }

    boolean matchesCompatibility() {
        if (!compatibility) return true
        return compatibility in ['glm-cli', 'opencode', '*', 'all']
    }
}
```

#### 1.2 Create Skill Registry

**File:** `core/SkillRegistry.groovy`

```groovy
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
```

#### 1.3 Update Config for Skill Permissions

**File:** `core/Config.groovy`

Add skill permission configuration support:

```groovy
List<String> getSkillPermissionPatterns() {
    return behavior?.skill_permissions ?: ['*']
}

boolean isSkillAllowed(String skillName) {
    def patterns = getSkillPermissionPatterns()
    for (pattern in patterns) {
        if (matchPattern(pattern, skillName)) {
            return pattern != 'deny'
        }
    }
    return true
}

private boolean matchPattern(String pattern, String skillName) {
    if (pattern == '*') return true
    if (pattern.endsWith('*')) {
        return skillName.startsWith(pattern[0..-2])
    }
    return skillName == pattern
}
```

### Phase 2: Skill Tool Implementation (Days 2-3)

#### 2.1 Create Skill Tool

**File:** `tools/SkillTool.groovy`

```groovy
package tools

import core.SkillRegistry
import models.Skill
import com.fasterxml.jackson.databind.ObjectMapper

class SkillTool implements Tool {
    private final SkillRegistry skillRegistry
    private final ObjectMapper mapper = new ObjectMapper()
    private final Map<String, String> loadedSkills = [:]

    SkillTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry
    }

    @Override
    String getName() {
        return "skill"
    }

    @Override
    String getDescription() {
        return "Load a reusable skill definition. " +
               "Skills provide specialized instructions for common tasks. " +
               "Use list_available=true to see what skills are available."
    }

    @Override
    Map<String, Object> getParameters() {
        return [
            type: "object",
            properties: [
                name: [
                    type: "string",
                    description: "Name of the skill to load (e.g., 'git-release', 'code-review')"
                ],
                list_available: [
                    type: "boolean",
                    description: "If true, lists all available skills instead of loading one"
                ]
            ],
            required: ["list_available"]
        ]
    }

    @Override
    Object execute(Map<String, Object> args) {
        if (args.containsKey('list_available') && args['list_available'] == true) {
            return listAvailableSkills()
        }

        String skillName = args.get("name")
        if (!skillName) {
            return "Error: 'name' parameter is required"
        }

        skillRegistry.discover()

        if (!isSkillAllowed(skillName)) {
            return "Error: Skill '${skillName}' is not allowed by permissions"
        }

        Skill skill = skillRegistry.getSkill(skillName)
        if (!skill) {
            return "Error: Skill '${skillName}' not found. Use list_available=true to see available skills."
        }

        loadedSkills[skillName] = skill.content

        return """
**Loaded Skill: ${skillName}**

${skill.content}

---
*Skill loaded from: ${skill.sourcePath}*
"""
    }

    private String listAvailableSkills() {
        skillRegistry.discover()
        def availableSkills = skillRegistry.getAvailableSkills()

        if (availableSkills.isEmpty()) {
            return "No skills found. Create .glm/skills/<name>/SKILL.md files to define skills."
        }

        def skillList = availableSkills.collect { skill ->
            def metaInfo = skill.metadata ? " (${skill.metadata.collect { "${it.key}:${it.value}" }.join(', ')})" : ""
            return "• **${skill.name}**: ${skill.description}${metaInfo}"
        }.join('\n')

        return """
**Available Skills:**

${skillList}

To load a skill, call: skill({ name: "skill-name" })
"""
    }

    String getLoadedSkillContent(String skillName) {
        return loadedSkills[skillName]
    }

    List<String> getLoadedSkillNames() {
        return new ArrayList<>(loadedSkills.keySet())
    }

    private boolean isSkillAllowed(String skillName) {
        return true
    }
}
```

#### 2.2 Update Agent to Support Skills

**File:** `core/Agent.groovy`

Add skill tool registration and skill loading:

```groovy
private final SkillRegistry skillRegistry = new SkillRegistry()
private final SkillTool skillTool

skillTool = new SkillTool(skillRegistry)
registerTool(skillTool)

void loadSkill(String skillName) {
    skillRegistry.discover()
    def content = skillTool.getLoadedSkillContent(skillName)
    if (content) {
        this.systemPrompt += "\n\n--- Loaded Skill: ${skillName} ---\n${content}"
    }
}
```

#### 2.3 Update AgentConfig for Skill Permissions

**File:** `core/AgentConfig.groovy`

```groovy
List<String> allowedSkills = []
List<String> deniedSkills = []
Map<String, String> skillPermissions = [:]

boolean isSkillAllowed(String skillName) {
    if (skillName in deniedSkills) return false
    if (!allowedSkills.isEmpty() && !(skillName in allowedSkills)) return false
    return true
}

String getSkillPermission(String skillName) {
    if (skillPermissions.containsKey(skillName)) {
        return skillPermissions[skillName]
    }
    if (skillName in deniedSkills) return "deny"
    if (!allowedSkills.isEmpty() && skillName in allowedSkills) return "allow"
    return "allow"
}
```

### Phase 3: CLI Integration (Days 3-4)

#### 3.1 Add Skill Command

**File:** `commands/SkillCommand.groovy`

```groovy
package commands

import core.SkillRegistry
import models.Skill
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option

@Command(name = "skill", description = "Manage and discover skills")
class SkillCommand implements Runnable {

    @Option(names = ["--list", "-l"], description = "List all available skills")
    boolean listSkills = false

    @Option(names = ["--path"], description = "Show skill file path")
    boolean showPath = false

    @Parameters(index = "0", description = "Skill name to show details for", arity = "0..1")
    String skillName = null

    private SkillRegistry skillRegistry = new SkillRegistry()

    @Override
    void run() {
        skillRegistry.discover()

        if (skillName) {
            showSkillDetails(skillName)
        } else {
            listAllSkills()
        }
    }

    private void listAllSkills() {
        def skills = skillRegistry.getAvailableSkills        println "Available()
 Skills:"
        println ""
        skills.each { skill ->
            println "• ${skill.name}: ${skill.description}"
            if (showPath) {
                println "  ${skill.sourcePath}"
            }
            println ""
        }
    }

    private void showSkillDetails(String name) {
        Skill skill = skillRegistry.getSkill(name)
        if (!skill) {
            println "Skill '${name}' not found."
            return
        }
        println "Skill: ${skill.name}"
        println "Description: ${skill.description}"
        println "Source: ${skill.sourcePath}"
        println ""
        println "Content:"
        println skill.content
    }
}
```

#### 3.2 Update GlmCli

**File:** `commands/GlmCli.groovy`

```groovy
@Command(name = "skill", description = "Manage and discover skills")
SkillCommand skillCommand
```

### Phase 4: Documentation and Examples (Day 4-5)

#### 4.1 Create Example Skills

**File:** `.glm/skills/code-review/SKILL.md`

```markdown
---
name: code-review
description: Perform thorough code reviews focusing on code quality, security, and best practices
license: MIT
compatibility: glm-cli
metadata:
  audience: developers
---

## What I do

- Review code for potential bugs and edge cases
- Check for security vulnerabilities
- Verify adherence to coding standards
- Suggest performance improvements

## Guidelines

1. Review systematically: structure, logic, edge cases, security, performance
2. Provide specific, actionable feedback with code examples
3. Be constructive and focus on improvement
```

**File:** `.glm/skills/git-release/SKILL.md`

```markdown
---
name: git-release
description: Create consistent releases and changelogs
license: MIT
compatibility: glm-cli
---

## What I do

- Analyze git history since last release
- Categorize changes (feat, fix, docs, refactor)
- Draft release notes
- Suggest version bump

## When to use me

Use when preparing a tagged release.
```

#### 4.2 Create Documentation

**File:** `docs/SKILLS.md`

```markdown
# Agent Skills

Agent skills let GLM-CLI discover reusable instructions from your repo or home directory.

## Create Skills

Create one folder per skill name and put a `SKILL.md` inside:

- `.glm/skills/<name>/SKILL.md`
- `~/.glm/skills/<name>/SKILL.md`
- `.claude/skills/<name>/SKILL.md`

## SKILL.md Format

```yaml
---
name: my-skill
description: Brief description
license: MIT
compatibility: glm-cli
metadata:
  key: value
---

## What I do
Detailed description...

## When to use me
When to apply...
```

## Use Skills

```
skill({ list_available: true })  // List skills
skill({ name: "git-release" })   // Load skill
```
```

---

## Configuration

```toml
[skills]
enabled = true

[skill_permissions]
"git-release" = "allow"
"code-review" = "allow"
"experimental-*" = "ask"
"*" = "allow"
```

---

## Success Criteria

- [ ] Skill model and registry discover skills from all paths
- [ ] Skill tool available to agents at runtime
- [ ] Permission system controls skill access
- [ ] `glm skill` CLI command works
- [ ] Example skills created and functional
- [ ] Documentation complete
- [ ] Tests pass

---

## Key Files to Create/Modify

| File | Action |
|------|--------|
| `models/Skill.groovy` | Create |
| `core/SkillRegistry.groovy` | Create |
| `tools/SkillTool.groovy` | Create |
| `commands/SkillCommand.groovy` | Create |
| `core/Agent.groovy` | Modify |
| `core/AgentConfig.groovy` | Modify |
| `core/Config.groovy` | Modify |
| `docs/SKILLS.md` | Create |
| `.glm/skills/code-review/SKILL.md` | Create |
| `.glm/skills/git-release/SKILL.md` | Create |

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Skill discovery slows startup | Low | Cache discovered skills, lazy load |
| YAML parsing edge cases | Low | Use simple regex-based parser |
| Permission bypass | Medium | Validate permissions before loading |
| Duplicate skill names | Low | First match wins, warn on duplicates |

---

## References

- OpenCode Skills: `/home/kevintan/opencode/packages/web/src/content/docs/skills.mdx`
- OpenCode Example: `/home/kevintan/opencode/.opencode/skill/test-skill/SKILL.md`
- Existing Instructions: `/home/kevintan/glm-cli-jbang/core/Instructions.groovy`

---

**Document Version:** 1.0
**Created:** 2025-01-08
**Priority:** Medium
