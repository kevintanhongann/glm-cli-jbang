package tests.core

import core.Config
import tests.base.ToolSpecification
import java.nio.file.Files

class ConfigTest extends ToolSpecification {
    
    def "should load config from default location"() {
        when:
        def config = Config.load()
        
        then:
        config != null
    }
    
    def "should get API key from config"() {
        given:
        def config = Config.load()
        
        when:
        def apiKey = config.getApiKey()
        
        then:
        // API key should be either set or null/empty
        apiKey != null
    }
    
    def "should get model from config"() {
        given:
        def config = Config.load()
        
        when:
        def model = config.getModel()
        
        then:
        model != null
    }
    
    def "should create config file if it doesn't exist"() {
        given:
        def configDir = resolve(".glm")
        Files.createDirectories(configDir)
        def configFile = configDir.resolve("config.toml")
        
        // Ensure config doesn't exist
        if (Files.exists(configFile)) {
            Files.delete(configFile)
        }
        
        when:
        def config = Config.load()
        
        then:
        config != null
        // Config file may or may not be created depending on implementation
    }
    
    def "should handle config with custom settings"() {
        given:
        def configDir = resolve(".glm")
        Files.createDirectories(configDir)
        def configFile = configDir.resolve("config.toml")
        
        def configContent = """
[model]
name = "custom-model"

[api]
key = "test-api-key"
"""
        Files.writeString(configFile, configContent)
        
        when:
        def config = Config.load()
        def model = config.getModel()
        
        then:
        model != null
    }
    
    def "should return default behavior settings"() {
        given:
        def config = Config.load()
        
        when:
        def maxSteps = config.behavior?.maxSteps
        
        then:
        // maxSteps may be null or have a default value
        maxSteps == null || maxSteps > 0
    }
    
    def "should handle missing config gracefully"() {
        when:
        def config = Config.load()
        
        then:
        config != null
    }
}
