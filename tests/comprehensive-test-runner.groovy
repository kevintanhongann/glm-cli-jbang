#!/usr/bin/env jbang
//DEPS org.apache.groovy:groovy:4.0.27
//DEPS org.spockframework:spock-core:2.3-groovy-4.0
//DEPS org.junit.jupiter:junit-jupiter-api:5.10.0
//DEPS org.junit.jupiter:junit-jupiter-engine:5.10.0
//DEPS org.junit.platform:junit-platform-launcher:1.10.0

import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.discovery.ClassNameFilter

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Comprehensive test runner for GLM-CLI
 * This script runs all tests including unit, integration, and performance tests
 */
class ComprehensiveTestRunner {
    
    static void main(String[] args) {
        println "=== GLM-CLI Comprehensive Test Runner ==="
        println "Running all tests for GLM-CLI implementation"
        println ""
        
        def runner = new ComprehensiveTestRunner()
        
        // Parse command line arguments
        def options = parseArguments(args)
        
        if (options.help) {
            printUsage()
            return
        }
        
        // Run tests based on options
        if (options.unit) {
            runner.runUnitTests()
        }
        
        if (options.integration) {
            runner.runIntegrationTests()
        }
        
        if (options.performance) {
            runner.runPerformanceTests()
        }
        
        if (options.manual) {
            runner.runManualTests()
        }
        
        if (options.all) {
            runner.runAllTests()
        }
        
        if (!options.unit && !options.integration && !options.performance && !options.manual && !options.all) {
            // Default: run unit and integration tests
            runner.runUnitTests()
            runner.runIntegrationTests()
        }
        
        println "\n=== Test Run Complete ==="
    }
    
    static Map<String, Boolean> parseArguments(String[] args) {
        def options = [
            help: false,
            unit: false,
            integration: false,
            performance: false,
            manual: false,
            all: false
        ]
        
        args.each { arg ->
            switch (arg.toLowerCase()) {
                case '--help':
                case '-h':
                    options.help = true
                    break
                case '--unit':
                case '-u':
                    options.unit = true
                    break
                case '--integration':
                case '-i':
                    options.integration = true
                    break
                case '--performance':
                case '-p':
                    options.performance = true
                    break
                case '--manual':
                case '-m':
                    options.manual = true
                    break
                case '--all':
                case '-a':
                    options.all = true
                    break
            }
        }
        
        return options
    }
    
    static void printUsage() {
        println "Usage: jbang comprehensive-test-runner.groovy [options]"
        println ""
        println "Options:"
        println "  --help, -h          Show this help message"
        println "  --unit, -u          Run unit tests"
        println "  --integration, -i   Run integration tests"
        println "  --performance, -p   Run performance tests"
        println "  --manual, -m        Run manual tests"
        println "  --all, -a           Run all tests"
        println ""
        println "If no options are specified, runs unit and integration tests by default."
        println ""
        println "Examples:"
        println "  jbang comprehensive-test-runner.groovy --unit"
        println "  jbang comprehensive-test-runner.groovy --all"
        println "  jbang comprehensive-test-runner.groovy -u -i"
    }
    
    void runUnitTests() {
        println "\n=== Running Unit Tests ==="
        runTests("tests.core", "Unit")
    }
    
    void runIntegrationTests() {
        println "\n=== Running Integration Tests ==="
        runTests("tests.integration", "Integration")
    }
    
    void runPerformanceTests() {
        println "\n=== Running Performance Tests ==="
        runTests("tests.performance", "Performance")
    }
    
    void runManualTests() {
        println "\n=== Running Manual Tests ==="
        println "Manual tests require interactive input. Starting manual test scripts..."
        
        def manualTests = [
            "tests.manual.TuiInteractionTest",
            "tests.manual.EndToEndWorkflowTest", 
            "tests.manual.ConfigurationTest"
        ]
        
        manualTests.each { testClass ->
            try {
                println "\n--- Running $testClass ---"
                def process = ["jbang", "${testClass}.groovy"].execute()
                process.in.eachLine { line ->
                    println "  $line"
                }
                process.waitFor()
                println "Manual test $testClass completed with exit code: ${process.exitValue()}"
            } catch (Exception e) {
                println "Manual test $testClass failed: ${e.message}"
            }
        }
    }
    
    void runAllTests() {
        println "\n=== Running All Tests ==="
        runUnitTests()
        runIntegrationTests()
        runPerformanceTests()
        runManualTests()
    }
    
    void runTests(String packageName, String testType) {
        try {
            // Build request for the specific package
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage(packageName))
                .filters(ClassNameFilter.includeClassNamePatterns(".*Test.*"))
                .build()
            
            // Execute tests
            Launcher launcher = LauncherFactory.create()
            def listener = new TestListener(testType)
            launcher.registerTestExecutionListeners(listener)
            launcher.execute(request)
            
            // Print summary
            println "\n${testType} Test Summary:"
            println "Total tests run: ${listener.testCount}"
            println "Successful: ${listener.successCount}"
            println "Failed: ${listener.failureCount}"
            println "Skipped: ${listener.skippedCount}"
            
            if (listener.failureCount > 0) {
                println "❌ ${testType} tests had failures"
            } else {
                println "✅ All ${testType.toLowerCase()} tests passed"
            }
            
        } catch (Exception e) {
            println "❌ Failed to run ${testType.toLowerCase()} tests: ${e.message}"
            e.printStackTrace()
        }
    }
}

// Simple test listener for comprehensive test runner
class TestListener implements org.junit.platform.engine.TestDescriptor.TestResultListener {
    private String testType
    int testCount = 0
    int successCount = 0
    int failureCount = 0
    int skippedCount = 0
    
    TestListener(String testType) {
        this.testType = testType
    }
    
    void executionStarted(org.junit.platform.engine.TestDescriptor descriptor) {
        if (descriptor.type == org.junit.platform.engine.TestDescriptor.Type.TEST) {
            testCount++
            println "  Running ${testType}: ${descriptor.displayName}"
        }
    }
    
    void executionFinished(org.junit.platform.engine.TestDescriptor descriptor, 
                           org.junit.platform.engine.TestExecutionResult result) {
        if (descriptor.type == org.junit.platform.engine.TestDescriptor.Type.TEST) {
            if (result.status == org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL) {
                successCount++
                println "    ✅ PASSED"
            } else if (result.status == org.junit.platform.engine.TestExecutionResult.Status.FAILED) {
                failureCount++
                println "    ❌ FAILED"
                result.throwables.ifPresent { t ->
                    t.each { throwable ->
                        println "      Error: ${throwable.message}"
                    }
                }
            } else if (result.status == org.junit.platform.engine.TestExecutionResult.Status.ABORTED) {
                skippedCount++
                println "    ⏭️  SKIPPED"
            }
        }
    }
    
    void executionSkipped(org.junit.platform.engine.TestDescriptor descriptor, String reason) {
        if (descriptor.type == org.junit.platform.engine.TestDescriptor.Type.TEST) {
            skippedCount++
            println "    ⏭️  SKIPPED: $reason"
        }
    }
}