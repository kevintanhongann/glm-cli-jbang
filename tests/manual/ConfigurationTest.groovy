#!/usr/bin/env jbang
//DEPS org.apache.groovy:groovy:4.0.27

import glm.core.Config
import glm.core.CompactionTrigger
import glm.core.DoomLoopDetector
import glm.core.TokenCounter
import glm.core.AgentState
import glm.core.EventBus
import glm.core.EventType
import glm.core.ReactiveState
import glm.core.StateRegistry
import glm.core.PermissionManager
import glm.core.PermissionPromptHandler
import glm.core.DoomLoopAgent
import glm.core.SessionCompactor
import glm.core.SummaryGenerator
import glm.core.HistoryPruner
import glm.core.SessionManager
import models.Message

/**
 * Manual testing script for configuration
 * This script tests configuration loading and application
 */
class ConfigurationTest {
    
    static void main(String[] args) {
        println "=== Configuration Manual Test ==="
        println "This script tests configuration loading and application."
        println ""
        
        def testRunner = new ConfigurationTest()
        
        println "Available configuration tests:"
        println "1. Load Default Configuration"
        println "2. Test Configuration Values"
        println "3. Test Configuration Overrides"
        println "4. Test Component Configuration"
        println "5. Test Configuration Validation"
        println "6. Exit"
        println ""
        
        def scanner = new Scanner(System.in)
        while (true) {
            print("Select test (1-6): ")
            def choice = scanner.nextLine().trim()
            
            switch (choice) {
                case "1":
                    testRunner.testLoadDefaultConfiguration()
                    break
                case "2":
                    testRunner.testConfigurationValues()
                    break
                case "3":
                    testRunner.testConfigurationOverrides()
                    break
                case "4":
                    testRunner.testComponentConfiguration()
                    break
                case "5":
                    testRunner.testConfigurationValidation()
                    break
                case "6":
                    println "Exiting..."
                    return
                default:
                    println "Invalid choice. Please select 1-6."
            }
        }
    }
    
    void testLoadDefaultConfiguration() {
        println "\n=== Testing Default Configuration Loading ==="
        println "Loading default configuration..."
        
        try {
            def config = Config.instance
            
            println "Configuration loaded successfully!"
            println "Configuration file: ${config.configFile}"
            println "Max iterations: ${config.maxIterations}"
            println "Token usage threshold: ${config.tokenUsageThreshold}"
            println "Message count threshold: ${config.messageCountThreshold}"
            println "Similarity threshold: ${config.similarityThreshold}"
            println "Min messages for detection: ${config.minMessagesForDetection}"
            println "Consecutive similar threshold: ${config.consecutiveSimilarThreshold}"
            
            println "Default configuration test completed!"
            
        } catch (Exception e) {
            println "Default configuration test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testConfigurationValues() {
        println "\n=== Testing Configuration Values ==="
        println "Testing configuration value access..."
        
        try {
            def config = Config.instance
            
            // Test basic configuration values
            println "Basic configuration values:"
            println "  Max iterations: ${config.maxIterations}"
            println "  Token usage threshold: ${config.tokenUsageThreshold}"
            println "  Message count threshold: ${config.messageCountThreshold}"
            
            // Test that values are within expected ranges
            assert config.maxIterations > 0, "Max iterations should be positive"
            assert config.tokenUsageThreshold > 0 && config.tokenUsageThreshold <= 1, "Token usage threshold should be between 0 and 1"
            assert config.messageCountThreshold > 0, "Message count threshold should be positive"
            
            println "Configuration values are valid!"
            println "Configuration values test completed!"
            
        } catch (Exception e) {
            println "Configuration values test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testConfigurationOverrides() {
        println "\n=== Testing Configuration Overrides ==="
        println "Testing configuration override mechanisms..."
        
        try {
            def config = Config.instance
            
            // Test that we can create components with custom configurations
            println "Creating components with custom configurations..."
            
            def customCompactionTrigger = new CompactionTrigger(0.9, 5)
            println "Custom compaction trigger - Token threshold: ${customCompactionTrigger.tokenUsageThreshold}, Message threshold: ${customCompactionTrigger.messageCountThreshold}"
            
            def customDetector = new DoomLoopDetector(0.95, 3, 2)
            println "Custom doom loop detector - Similarity threshold: ${customDetector.similarityThreshold}, Min messages: ${customDetector.minMessagesForDetection}, Consecutive threshold: ${customDetector.consecutiveSimilarThreshold}"
            
            println "Configuration overrides test completed!"
            
        } catch (Exception e) {
            println "Configuration overrides test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testComponentConfiguration() {
        println "\n=== Testing Component Configuration ==="
        println "Testing component configuration with loaded settings..."
        
        try {
            def config = Config.instance
            
            // Test that components can be created with configuration values
            println "Creating components with configuration values..."
            
            def compactionTrigger = new CompactionTrigger(
                config.tokenUsageThreshold, 
                config.messageCountThreshold
            )
            println "Compaction trigger created with config values"
            
            def detector = new DoomLoopDetector(
                config.similarityThreshold,
                config.minMessagesForDetection,
                config.consecutiveSimilarThreshold
            )
            println "Doom loop detector created with config values"
            
            def agentState = new AgentState()
            agentState.maxIterations.set(config.maxIterations)
            println "Agent state configured with max iterations: ${agentState.maxIterations.get()}"
            
            println "Component configuration test completed!"
            
        } catch (Exception e) {
            println "Component configuration test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testConfigurationValidation() {
        println "\n=== Testing Configuration Validation ==="
        println "Testing configuration validation..."
        
        try {
            def config = Config.instance
            
            // Test that configuration values are reasonable
            println "Validating configuration values..."
            
            // Validate max iterations
            assert config.maxIterations > 0 && config.maxIterations <= 1000, "Max iterations should be between 1 and 1000"
            
            // Validate thresholds
            assert config.tokenUsageThreshold > 0 && config.tokenUsageThreshold <= 1, "Token usage threshold should be between 0 and 1"
            assert config.similarityThreshold > 0 && config.similarityThreshold <= 1, "Similarity threshold should be between 0 and 1"
            
            // Validate counts
            assert config.messageCountThreshold > 0 && config.messageCountThreshold <= 1000, "Message count threshold should be reasonable"
            assert config.minMessagesForDetection > 0 && config.minMessagesForDetection <= 100, "Min messages for detection should be reasonable"
            assert config.consecutiveSimilarThreshold > 0 && config.consecutiveSimilarThreshold <= 10, "Consecutive similar threshold should be reasonable"
            
            println "All configuration values are valid!"
            println "Configuration validation test completed!"
            
        } catch (Exception e) {
            println "Configuration validation test failed: ${e.message}"
            e.printStackTrace()
        }
    }
}