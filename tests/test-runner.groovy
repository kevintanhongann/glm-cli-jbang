///usr/bin/env jbang "$0" "$@" ; exit $?
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

import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns

// Build request to include all test packages
LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
    .selectors(
        DiscoverySelectors.selectPackage("tests.core"),
        DiscoverySelectors.selectPackage("tests.integration"),
        DiscoverySelectors.selectPackage("tests.performance")
    )
    .filters(includeClassNamePatterns(".*Test.*"))
    .build()

// Execute tests
Launcher launcher = LauncherFactory.create()
def listener = new TestListener()
launcher.registerTestExecutionListeners(listener)
launcher.execute(request)

// Print summary
println "\n" + "=".repeat(60)
println "Test Summary"
println "=".repeat(60)
println "Total tests run: ${listener.testCount}"
println "Successful: ${listener.successCount}"
println "Failed: ${listener.failureCount}"
println "Skipped: ${listener.skippedCount}"
println "=".repeat(60)

if (listener.failureCount > 0) {
    System.exit(1)
}

// Simple test listener
class TestListener implements org.junit.platform.engine.TestDescriptor.TestResultListener {
    int testCount = 0
    int successCount = 0
    int failureCount = 0
    int skippedCount = 0

    void executionStarted(org.junit.platform.engine.TestDescriptor descriptor) {
        if (descriptor.type == org.junit.platform.engine.TestDescriptor.Type.TEST) {
            testCount++
            println "Running: ${descriptor.displayName}"
        }
    }

    void executionFinished(org.junit.platform.engine.TestDescriptor descriptor, 
                           org.junit.platform.engine.TestExecutionResult result) {
        if (descriptor.type == org.junit.platform.engine.TestDescriptor.Type.TEST) {
            if (result.status == org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL) {
                successCount++
                println "  ✓ PASSED"
            } else if (result.status == org.junit.platform.engine.TestExecutionResult.Status.FAILED) {
                failureCount++
                println "  ✗ FAILED"
                result.throwables.ifPresent { t ->
                    t.printStackTrace(System.out)
                }
            }
        }
    }

    void executionSkipped(org.junit.platform.engine.TestDescriptor descriptor, String reason) {
        if (descriptor.type == org.junit.platform.engine.TestDescriptor.Type.TEST) {
            skippedCount++
            println "  - SKIPPED: $reason"
        }
    }
}
