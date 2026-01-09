#!/usr/bin/env jbang
//DEPS org.apache.groovy:groovy:4.0.27

import glm.core.AgentState
import glm.core.EventBus
import glm.core.EventType
import glm.core.ReactiveState
import glm.core.StateRegistry
import glm.core.TokenCounter
import glm.core.CompactionTrigger
import glm.core.SummaryGenerator
import glm.core.HistoryPruner
import glm.core.SessionCompactor
import glm.core.DoomLoopDetector
import glm.core.PermissionManager
import glm.core.PermissionPromptHandler
import glm.core.DoomLoopAgent
import glm.core.SessionManager
import models.Message

/**
 * Manual testing script for end-to-end workflows
 * This script simulates complete agent workflows
 */
class EndToEndWorkflowTest {
    
    static void main(String[] args) {
        println "=== End-to-End Workflow Manual Test ==="
        println "This script simulates complete agent workflows."
        println ""
        
        def testRunner = new EndToEndWorkflowTest()
        
        println "Available workflow tests:"
        println "1. Basic Agent Workflow"
        println "2. Session Compaction Workflow"
        println "3. Doom Loop Detection Workflow"
        println "4. Permission System Workflow"
        println "5. Complete Integration Workflow"
        println "6. Exit"
        println ""
        
        def scanner = new Scanner(System.in)
        while (true) {
            print("Select test (1-6): ")
            def choice = scanner.nextLine().trim()
            
            switch (choice) {
                case "1":
                    testRunner.testBasicAgentWorkflow()
                    break
                case "2":
                    testRunner.testSessionCompactionWorkflow()
                    break
                case "3":
                    testRunner.testDoomLoopDetectionWorkflow()
                    break
                case "4":
                    testRunner.testPermissionSystemWorkflow()
                    break
                case "5":
                    testRunner.testCompleteIntegrationWorkflow()
                    break
                case "6":
                    println "Exiting..."
                    return
                default:
                    println "Invalid choice. Please select 1-6."
            }
        }
    }
    
    void testBasicAgentWorkflow() {
        println "\n=== Testing Basic Agent Workflow ==="
        println "Simulating a basic agent workflow..."
        
        try {
            def agentState = new AgentState()
            def eventBus = EventBus.instance
            
            // Subscribe to events
            def eventsReceived = []
            eventBus.subscribe(EventType.AGENT_STEP_STARTED, { event ->
                eventsReceived << event
                println "Step started: ${event.data.step}"
            })
            
            eventBus.subscribe(EventType.AGENT_STEP_COMPLETED, { event ->
                eventsReceived << event
                println "Step completed: ${event.data.step} - Success: ${event.data.success}"
            })
            
            // Simulate agent workflow
            println "Starting agent workflow..."
            agentState.setRunning(true)
            
            // Step 1: Analyze requirements
            println "Step 1: Analyzing requirements..."
            agentState.setCurrentStep("analyze_requirements")
            eventBus.publish(EventType.AGENT_STEP_STARTED, [step: "analyze_requirements"])
            Thread.sleep(500)  // Simulate work
            eventBus.publish(EventType.AGENT_STEP_COMPLETED, [step: "analyze_requirements", success: true])
            
            // Step 2: Generate plan
            println "Step 2: Generating plan..."
            agentState.setCurrentStep("generate_plan")
            eventBus.publish(EventType.AGENT_STEP_STARTED, [step: "generate_plan"])
            Thread.sleep(500)  // Simulate work
            eventBus.publish(EventType.AGENT_STEP_COMPLETED, [step: "generate_plan", success: true])
            
            // Step 3: Execute plan
            println "Step 3: Executing plan..."
            agentState.setCurrentStep("execute_plan")
            eventBus.publish(EventType.AGENT_STEP_STARTED, [step: "execute_plan"])
            Thread.sleep(500)  // Simulate work
            eventBus.publish(EventType.AGENT_STEP_COMPLETED, [step: "execute_plan", success: true])
            
            // Complete workflow
            agentState.setRunning(false)
            agentState.setCurrentStep(null)
            
            println "Events received: ${eventsReceived.size()}"
            println "Basic agent workflow test completed!"
            
        } catch (Exception e) {
            println "Basic agent workflow test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testSessionCompactionWorkflow() {
        println "\n=== Testing Session Compaction Workflow ==="
        println "Simulating session compaction workflow..."
        
        try {
            def compactor = new SessionCompactor()
            def trigger = new CompactionTrigger(0.5, 5)  // Low thresholds for testing
            def messages = []
            
            // Generate test messages
            println "Generating test conversation history..."
            20.times { i ->
                messages << new Message("user", "This is message $i with some content.")
                messages << new Message("assistant", "This is response $i with some content.")
            }
            
            println "Original message count: ${messages.size()}"
            println "Original token count: ${TokenCounter.estimateHistoryTokens(messages)}"
            
            // Check if compaction is needed
            def shouldCompact = trigger.shouldCompact(messages, "You are a helpful assistant.", 1000)
            println "Should compact: $shouldCompact"
            
            if (shouldCompact) {
                println "Performing session compaction..."
                def result = compactor.compactSession(messages, "You are a helpful assistant.", 1000)
                
                println "Compacted message count: ${result.compactedHistory.size()}"
                println "Compacted token count: ${TokenCounter.estimateHistoryTokens(result.compactedHistory)}"
                println "Summary generated: ${result.summary.length() > 0}"
                println "Summary: ${result.summary.take(100)}..."
            }
            
            println "Session compaction workflow test completed!"
            
        } catch (Exception e) {
            println "Session compaction workflow test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testDoomLoopDetectionWorkflow() {
        println "\n=== Testing Doom Loop Detection Workflow ==="
        println "Simulating doom loop detection workflow..."
        
        try {
            def detector = new DoomLoopDetector()
            def agent = new DoomLoopAgent()
            def messages = []
            
            // Generate test messages with potential loop
            println "Generating test conversation with potential loop..."
            8.times {
                messages << new Message("user", "Same question")
                messages << new Message("assistant", "Same answer")
            }
            
            println "Message count: ${messages.size()}"
            
            // Detect doom loop
            def isLoop = detector.isDoomLoop(messages)
            println "Doom loop detected: $isLoop"
            
            if (isLoop) {
                println "Handling doom loop..."
                def handled = agent.handleDoomLoop(messages)
                println "Doom loop handled: $handled"
            }
            
            println "Doom loop detection workflow test completed!"
            
        } catch (Exception e) {
            println "Doom loop detection workflow test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testPermissionSystemWorkflow() {
        println "\n=== Testing Permission System Workflow ==="
        println "Simulating permission system workflow..."
        
        try {
            def manager = new PermissionManager()
            def handler = new PermissionPromptHandler()
            manager.promptHandler = handler
            
            // Test permission requests
            println "Testing permission requests..."
            
            def permissions = [
                "Write file to disk",
                "Read sensitive configuration",
                "Execute system command",
                "Access external API"
            ]
            
            permissions.each { permission ->
                println "Requesting permission: $permission"
                def granted = manager.requestPermission(permission)
                println "Permission granted: $granted"
            }
            
            println "Permission system workflow test completed!"
            
        } catch (Exception e) {
            println "Permission system workflow test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testCompleteIntegrationWorkflow() {
        println "\n=== Testing Complete Integration Workflow ==="
        println "Simulating complete agent integration workflow..."
        
        try {
            def agentState = new AgentState()
            def eventBus = EventBus.instance
            def compactor = new SessionCompactor()
            def trigger = new CompactionTrigger(0.8, 10)
            def detector = new DoomLoopDetector()
            def agent = new DoomLoopAgent()
            
            // Subscribe to events
            def eventsReceived = []
            eventBus.subscribe(EventType.STATE_CHANGED, { event ->
                eventsReceived << event
            })
            
            // Simulate complete workflow
            println "Starting complete integration workflow..."
            agentState.setRunning(true)
            
            def messages = []
            def stepCount = 0
            
            // Simulate multiple steps with growing conversation
            15.times { i ->
                stepCount++
                agentState.incrementLoopCount()
                
                // Add conversation messages
                messages << new Message("user", "User query $i")
                messages << new Message("assistant", "Assistant response $i")
                
                // Check for compaction
                def shouldCompact = trigger.shouldCompact(messages, "You are a helpful assistant.", 1000)
                if (shouldCompact) {
                    println "Step $i: Compaction triggered"
                    def result = compactor.compactSession(messages, "You are a helpful assistant.", 1000)
                    messages = result.compactedHistory
                    println "  Compacted to ${messages.size()} messages"
                }
                
                // Check for doom loop
                def isLoop = detector.isDoomLoop(messages)
                if (isLoop) {
                    println "Step $i: Doom loop detected"
                    def handled = agent.handleDoomLoop(messages)
                    println "  Doom loop handled: $handled"
                }
                
                // Simulate step completion
                agentState.setCurrentStep("step_$i")
                Thread.sleep(100)  // Simulate work
            }
            
            agentState.setRunning(false)
            
            println "Complete workflow completed:"
            println "  Total steps: $stepCount"
            println "  Final message count: ${messages.size()}"
            println "  Events received: ${eventsReceived.size()}"
            println "  Loop count: ${agentState.loopCount.get()}"
            
            println "Complete integration workflow test completed!"
            
        } catch (Exception e) {
            println "Complete integration workflow test failed: ${e.message}"
            e.printStackTrace()
        }
    }
}