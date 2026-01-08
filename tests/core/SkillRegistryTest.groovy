package core

import models.Skill

class SkillRegistryTest {
    static void main(String[] args) {
        println "Running SkillRegistry tests..."

        testDiscover()
        testGetSkill()
        testHasSkill()
        testGetSkillNames()
        testGetAvailableSkills()
        testClear()

        println "All SkillRegistry tests passed!"
    }

    static void testDiscover() {
        def registry = new SkillRegistry()

        registry.discover()

        def skills = registry.getAllSkills()
        assert !skills.isEmpty() : "Expected to discover at least one skill"

        def skillNames = registry.getSkillNames()
        assert skillNames.contains('code-review') : "Expected to find 'code-review' skill"
        assert skillNames.contains('git-release') : "Expected to find 'git-release' skill"

        println "  testDiscover: PASSED"
    }

    static void testGetSkill() {
        def registry = new SkillRegistry()
        registry.discover()

        def skill = registry.getSkill('code-review')
        assert skill != null : "Expected to find 'code-review' skill"
        assert skill.name == 'code-review' : "Expected skill name 'code-review'"
        assert skill.description != null : "Expected skill to have description"

        def nonExistent = registry.getSkill('non-existent')
        assert nonExistent == null : "Expected non-existent skill to return null"

        println "  testGetSkill: PASSED"
    }

    static void testHasSkill() {
        def registry = new SkillRegistry()
        registry.discover()

        assert registry.hasSkill('code-review') : "Expected hasSkill to return true for 'code-review'"
        assert registry.hasSkill('git-release') : "Expected hasSkill to return true for 'git-release'"
        assert !registry.hasSkill('non-existent') : "Expected hasSkill to return false for 'non-existent'"

        println "  testHasSkill: PASSED"
    }

    static void testGetSkillNames() {
        def registry = new SkillRegistry()
        registry.discover()

        def names = registry.getSkillNames()
        assert names.size() >= 2 : "Expected at least 2 skill names"
        assert names.contains('code-review') : "Expected 'code-review' in skill names"
        assert names.contains('git-release') : "Expected 'git-release' in skill names"

        println "  testGetSkillNames: PASSED"
    }

    static void testGetAvailableSkills() {
        def registry = new SkillRegistry()
        registry.discover()

        def available = registry.getAvailableSkills()
        assert !available.isEmpty() : "Expected at least one available skill"

        available.each { skill ->
            assert skill.matchesCompatibility() : "Expected skill '${skill.name}' to match compatibility"
        }

        println "  testGetAvailableSkills: PASSED"
    }

    static void testClear() {
        def registry = new SkillRegistry()
        registry.discover()

        def initialCount = registry.getAllSkills().size()
        assert initialCount > 0 : "Expected at least one skill before clear"

        registry.clear()

        def afterClear = registry.getAllSkills()
        assert afterClear.isEmpty() : "Expected no skills after clear"

        println "  testClear: PASSED"
    }
}
