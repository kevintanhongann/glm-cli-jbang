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
