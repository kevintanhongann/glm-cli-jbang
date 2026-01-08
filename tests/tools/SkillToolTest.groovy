package tools

import core.SkillRegistry

class SkillToolTest {
    static void main(String[] args) {
        println "Running SkillTool tests..."

        testGetName()
        testGetDescription()
        testGetParameters()
        testListAvailableSkills()
        testLoadSkill()

        println "All SkillTool tests passed!"
    }

    static void testGetName() {
        def registry = new SkillRegistry()
        def tool = new SkillTool(registry)

        assert tool.getName() == 'skill' : "Expected tool name 'skill'"

        println "  testGetName: PASSED"
    }

    static void testGetDescription() {
        def registry = new SkillRegistry()
        def tool = new SkillTool(registry)

        def description = tool.getDescription()
        assert description != null : "Expected description to not be null"
        assert description.contains('skill') : "Expected description to contain 'skill'"
        assert description.contains('list_available') : "Expected description to contain 'list_available'"

        println "  testGetDescription: PASSED"
    }

    static void testGetParameters() {
        def registry = new SkillRegistry()
        def tool = new SkillTool(registry)

        def params = tool.getParameters()
        assert params != null : "Expected parameters to not be null"
        assert params.type == 'object' : "Expected type 'object'"
        assert params.properties != null : "Expected properties to not be null"
        assert params.properties.name != null : "Expected 'name' property"
        assert params.properties.list_available != null : "Expected 'list_available' property"

        println "  testGetParameters: PASSED"
    }

    static void testListAvailableSkills() {
        def registry = new SkillRegistry()
        def tool = new SkillTool(registry)

        def result = tool.execute([list_available: true])

        assert result != null : "Expected result to not be null"
        assert result.contains('Available Skills') : "Expected result to contain 'Available Skills'"
        assert result.contains('code-review') : "Expected result to contain 'code-review'"
        assert result.contains('git-release') : "Expected result to contain 'git-release'"

        println "  testListAvailableSkills: PASSED"
    }

    static void testLoadSkill() {
        def registry = new SkillRegistry()
        def tool = new SkillTool(registry)

        def result = tool.execute([name: 'code-review'])

        assert result != null : "Expected result to not be null"
        assert result.contains('Loaded Skill: code-review') : "Expected result to contain 'Loaded Skill: code-review'"
        assert result.contains('## What I do') : "Expected result to contain skill body"

        def loadedNames = tool.getLoadedSkillNames()
        assert loadedNames.contains('code-review') : "Expected 'code-review' in loaded skill names"

        def loadedContent = tool.getLoadedSkillContent('code-review')
        assert loadedContent != null : "Expected loaded content for 'code-review'"
        assert loadedContent.contains('## What I do') : "Expected loaded content to contain skill body"

        println "  testLoadSkill: PASSED"
    }
}
