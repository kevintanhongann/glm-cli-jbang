package commands

import core.SkillRegistry
import models.Skill
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option

@Command(name = "skill", description = "Manage and discover skills", mixinStandardHelpOptions = true)
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
        def skills = skillRegistry.getAvailableSkills()
        println "Available Skills:"
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
