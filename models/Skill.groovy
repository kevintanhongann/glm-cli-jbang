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
                            result[key] = parseMetadata(value)
                            break
                    }
                }
            }
        }
        return result
    }

    private static Map<String, String> parseMetadata(String metadataStr) {
        def result = [:]
        if (!metadataStr) return result

        metadataStr.split(',').each { pair ->
            def keyValue = pair.split(':')
            if (keyValue.size() == 2) {
                result[keyValue[0].trim()] = keyValue[1].trim()
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
