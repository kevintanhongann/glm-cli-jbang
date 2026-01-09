///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.groovy:groovy:4.0.27
//DEPS org.spockframework:spock-core:2.3-groovy-4.0
//DEPS org.junit.jupiter:junit-jupiter-api:5.10.0
//DEPS org.junit.jupiter:junit-jupiter-engine:5.10.0
//DEPS org.junit.platform:junit-platform-console:1.10.0

import org.junit.platform.console.ConsoleLauncher
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.engine.discovery.ClassNameFilter
import org.junit.platform.engine.discovery.DiscoverySelectors
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request

// Import all test classes
import tests.tools.ReadFileToolTest
import tests.tools.WriteFileToolTest
import tests.tools.GrepToolTest
import tests.tools.SkillToolTest
import tests.core.AgentTest
import tests.core.ConfigTest
import tests.core.InstructionsTest
import tests.core.SkillRegistryTest
import tests.models.SkillTest

println "=".repeat(70)
println "GLM-CLI Test Suite"
println "=".repeat(70)
println ""

// Run each test suite manually
def testClasses = [
    ReadFileToolTest,
    WriteFileToolTest,
    GrepToolTest,
    AgentTest,
    ConfigTest,
    InstructionsTest,
    SkillTest,
    SkillRegistryTest,
    SkillToolTest
]

def totalTests = 0
def passedTests = 0
def failedTests = 0

testClasses.each { testClass ->
    println "Running: ${testClass.simpleName}"
    println "-".repeat(70)
    
    def spockRunner = new org.spockframework.runtime.SpockRunner()
    
    try {
        // Create a simple test runner using JUnit Platform
        def request = request()
            .selectors(DiscoverySelectors.selectClass(testClass))
            .build()
        
        def launcher = org.junit.platform.launcher.core.LauncherFactory.create()
        
        def listener = new org.junit.platform.engine.TestDescriptor.TestResultListener() {
            int count = 0
            int success = 0
            int failed = 0
            
            void executionStarted(def descriptor) {
                if (descriptor.type.toString() == "TEST") {
                    count++
                    print "  ${descriptor.displayName}... "
                }
            }
            
            void executionFinished(def descriptor, def result) {
                if (descriptor.type.toString() == "TEST") {
                    if (result.status.toString() == "SUCCESSFUL") {
                        success++
                        println "✓ PASSED"
                    } else {
                        failed++
                        println "✗ FAILED"
                        result.throwables.ifPresent { t ->
                            t.printStackTrace(System.out)
                        }
                    }
                }
            }
            
            void executionSkipped(def descriptor, String reason) {
                if (descriptor.type.toString() == "TEST") {
                    print "  ${descriptor.displayName}... "
                    println "- SKIPPED: $reason"
                }
            }
        }
        
        launcher.registerTestExecutionListeners(listener)
        launcher.execute(request)
        
        totalTests += listener.count
        passedTests += listener.success
        failedTests += listener.failed
        
    } catch (Exception e) {
        println "  Error running test class: ${e.message}"
        e.printStackTrace()
        failedTests++
    }
    
    println ""
}

println "=".repeat(70)
println "Test Summary"
println "=".repeat(70)
println "Total tests: $totalTests"
println "Passed: $passedTests"
println "Failed: $failedTests"
println "Success rate: ${totalTests > 0 ? (passedTests * 100 / totalTests) : 0}%"
println "=".repeat(70)

if (failedTests > 0) {
    System.exit(1)
} else {
    println "\n✓ All tests passed!"
}
