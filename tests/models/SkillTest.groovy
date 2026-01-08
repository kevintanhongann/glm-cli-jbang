package models

import java.nio.file.Files
import java.nio.file.Path

class SkillTest {
    static void main(String[] args) {
        println "Running Skill model tests..."

        testParseFrontmatter()
        testExtractBody()
        testIsValid()
        testMatchesCompatibility()

        println "All Skill tests passed!"
    }

    static void testParseFrontmatter() {
        def content = '''---
name: test-skill
description: A test skill
license: MIT
compatibility: glm-cli
metadata: audience:developers, workflow:github
---

## What I do
This is the body content.
'''

        def frontmatter = Skill.parseFrontmatter(content)

        assert frontmatter.name == 'test-skill' : "Expected name 'test-skill', got '${frontmatter.name}'"
        assert frontmatter.description == 'A test skill' : "Expected description 'A test skill', got '${frontmatter.description}'"
        assert frontmatter.license == 'MIT' : "Expected license 'MIT', got '${frontmatter.license}'"
        assert frontmatter.compatibility == 'glm-cli' : "Expected compatibility 'glm-cli', got '${frontmatter.compatibility}'"
        assert frontmatter.metadata != null : "Expected metadata to be a Map"
        assert frontmatter.metadata['audience'] == 'developers' : "Expected metadata audience 'developers'"
        assert frontmatter.metadata['workflow'] == 'github' : "Expected metadata workflow 'github'"

        println "  testParseFrontmatter: PASSED"
    }

    static void testExtractBody() {
        def content = '''---
name: test-skill
description: A test skill
---

## What I do
This is the body content.
And it has multiple lines.
'''

        def body = Skill.extractBody(content)

        assert body.contains('## What I do') : "Expected body to contain '## What I do'"
        assert body.contains('This is the body content.') : "Expected body to contain 'This is the body content.'"
        assert body.contains('And it has multiple lines.') : "Expected body to contain 'And it has multiple lines.'"

        println "  testExtractBody: PASSED"
    }

    static void testIsValid() {
        def validSkill = new Skill(
            name: 'test-skill',
            description: 'A test skill',
            content: 'Some content'
        )

        assert validSkill.isValid() : "Expected valid skill to be valid"

        def invalidSkill1 = new Skill(
            name: '',
            description: 'A test skill',
            content: 'Some content'
        )

        assert !invalidSkill1.isValid() : "Expected skill with empty name to be invalid"

        def invalidSkill2 = new Skill(
            name: 'test-skill',
            description: '',
            content: 'Some content'
        )

        assert !invalidSkill2.isValid() : "Expected skill with empty description to be invalid"

        println "  testIsValid: PASSED"
    }

    static void testMatchesCompatibility() {
        def skillWithAny = new Skill(
            name: 'test',
            description: 'Test',
            compatibility: '*'
        )

        assert skillWithAny.matchesCompatibility() : "Expected '*' to match"

        def skillWithAll = new Skill(
            name: 'test',
            description: 'Test',
            compatibility: 'all'
        )

        assert skillWithAll.matchesCompatibility() : "Expected 'all' to match"

        def skillWithGlm = new Skill(
            name: 'test',
            description: 'Test',
            compatibility: 'glm-cli'
        )

        assert skillWithGlm.matchesCompatibility() : "Expected 'glm-cli' to match"

        def skillWithOpencode = new Skill(
            name: 'test',
            description: 'Test',
            compatibility: 'opencode'
        )

        assert skillWithOpencode.matchesCompatibility() : "Expected 'opencode' to match"

        def skillWithIncompatible = new Skill(
            name: 'test',
            description: 'Test',
            compatibility: 'other'
        )

        assert !skillWithIncompatible.matchesCompatibility() : "Expected 'other' to not match"

        def skillWithNull = new Skill(
            name: 'test',
            description: 'Test',
            compatibility: null
        )

        assert skillWithNull.matchesCompatibility() : "Expected null to match"

        println "  testMatchesCompatibility: PASSED"
    }
}
