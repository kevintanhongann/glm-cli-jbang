#!/usr/bin/env groovy
// Test script to verify responsive sidebar behavior

class SidebarWidthTest {
    static int calculateSidebarWidth(int terminalWidth) {
        if (terminalWidth >= 100) {
            return 42
        } else if (terminalWidth >= 80) {
            return 32
        } else {
            return 0
        }
    }

    static void main(String[] args) {
        println "Testing responsive sidebar width calculation:\n"

        // Test cases
        def testCases = [
            [120, 42, "Large monitor - full sidebar"],
            [100, 42, "Minimum for full sidebar"],
            [99, 32, "Just below full threshold - reduced sidebar"],
            [90, 32, "Standard terminal - reduced sidebar"],
            [80, 32, "Minimum for reduced sidebar"],
            [79, 0, "Just below threshold - no sidebar"],
            [60, 0, "Small terminal - no sidebar"],
            [40, 0, "Very small terminal - no sidebar"]
        ]

        boolean allPassed = true

        testCases.each { testCase ->
            int terminalWidth = testCase[0]
            int expectedWidth = testCase[1]
            String description = testCase[2]

            int actualWidth = calculateSidebarWidth(terminalWidth)
            boolean passed = actualWidth == expectedWidth

            def status = passed ? "✓ PASS" : "✗ FAIL"
            println "${status}: Terminal ${terminalWidth} cols -> Sidebar ${actualWidth} cols (${description})"

            if (!passed) {
                println "         Expected: ${expectedWidth}, Got: ${actualWidth}"
                allPassed = false
            }
        }

        println "\n" + "=" * 60
        if (allPassed) {
            println "All tests PASSED ✓"
            System.exit(0)
        } else {
            println "Some tests FAILED ✗"
            System.exit(1)
        }
    }
}
