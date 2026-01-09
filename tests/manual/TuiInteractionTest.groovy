#!/usr/bin/env jbang
//DEPS org.apache.groovy:groovy:4.0.27
//DEPS com.googlecode.lanterna:lanterna:3.1.4

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.*
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import glm.tui.LanternaTUI
import glm.tui.TuiPermissionPromptHandler
import glm.core.AgentState
import glm.core.EventBus
import glm.core.EventType
import glm.core.ReactiveState
import glm.core.StateRegistry
import glm.core.StateChange
import glm.core.TokenCounter
import glm.core.CompactionTrigger
import glm.core.SummaryGenerator
import glm.core.HistoryPruner
import glm.core.SessionCompactor
import glm.core.DoomLoopDetector
import glm.core.PermissionManager
import glm.core.PermissionPromptHandler
import glm.core.DoomLoopAgent
import models.Message

/**
 * Manual testing script for TUI interactions
 * This script provides a test interface to manually test TUI components
 */
class TuiInteractionTest {
    
    static void main(String[] args) {
        println "=== TUI Interaction Manual Test ==="
        println "This script provides manual testing for TUI components."
        println ""
        
        def testRunner = new TuiInteractionTest()
        
        println "Available tests:"
        println "1. TUI Component Test"
        println "2. Permission Prompt Test"
        println "3. Event System Test"
        println "4. Agent State Test"
        println "5. Exit"
        println ""
        
        def scanner = new Scanner(System.in)
        while (true) {
            print("Select test (1-5): ")
            def choice = scanner.nextLine().trim()
            
            switch (choice) {
                case "1":
                    testRunner.testTuiComponents()
                    break
                case "2":
                    testRunner.testPermissionPrompt()
                    break
                case "3":
                    testRunner.testEventSystem()
                    break
                case "4":
                    testRunner.testAgentState()
                    break
                case "5":
                    println "Exiting..."
                    return
                default:
                    println "Invalid choice. Please select 1-5."
            }
        }
    }
    
    void testTuiComponents() {
        println "\n=== Testing TUI Components ==="
        println "Starting TUI test interface..."
        
        try {
            // Create a simple TUI test
            Terminal terminal = new DefaultTerminalFactory().createTerminal()
            Screen screen = new TerminalScreen(terminal)
            screen.startScreen()
            
            WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen)
            
            // Create main window
            BasicWindow mainWindow = new BasicWindow("TUI Test Interface")
            Panel contentPane = new Panel(new LinearLayout(Direction.VERTICAL))
            
            // Add test buttons
            Button testButton1 = new Button("Test Button 1", {
                MessageBox.showMessageBox(textGUI, "Test", "Button 1 clicked!")
            })
            
            Button testButton2 = new Button("Test Button 2", {
                MessageBox.showMessageBox(textGUI, "Test", "Button 2 clicked!")
            })
            
            Button exitButton = new Button("Exit", {
                mainWindow.close()
            })
            
            contentPane.addComponent(testButton1)
            contentPane.addComponent(testButton2)
            contentPane.addComponent(exitButton)
            
            mainWindow.setComponent(contentPane)
            textGUI.addWindowAndWait(mainWindow)
            
            screen.stopScreen()
            println "TUI test completed successfully!"
            
        } catch (Exception e) {
            println "TUI test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testPermissionPrompt() {
        println "\n=== Testing Permission Prompt ==="
        println "Testing permission prompt handler..."
        
        try {
            def handler = new TuiPermissionPromptHandler()
            
            // Test permission prompt
            println "Testing permission prompt for 'Write file'..."
            def result = handler.promptPermission("Do you want to write a file?")
            println "Permission result: $result"
            
            // Test with different prompts
            println "Testing permission prompt for 'Read file'..."
            result = handler.promptPermission("Do you want to read a file?")
            println "Permission result: $result"
            
            println "Permission prompt test completed!"
            
        } catch (Exception e) {
            println "Permission prompt test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testEventSystem() {
        println "\n=== Testing Event System ==="
        println "Testing event system integration..."
        
        try {
            def eventBus = EventBus.instance
            def agentState = new AgentState()
            
            // Subscribe to events
            def eventsReceived = []
            eventBus.subscribe(EventType.STATE_CHANGED, { event ->
                eventsReceived << event
                println "Event received: ${event.type} - ${event.data}"
            })
            
            // Test state changes
            println "Setting current step to 'test_step'..."
            agentState.setCurrentStep("test_step")
            
            println "Setting running state to true..."
            agentState.setRunning(true)
            
            println "Setting paused state to true..."
            agentState.setPaused(true)
            
            // Wait a moment for events
            Thread.sleep(1000)
            
            println "Events received: ${eventsReceived.size()}"
            println "Event system test completed!"
            
        } catch (Exception e) {
            println "Event system test failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    void testAgentState() {
        println "\n=== Testing Agent State ==="
        println "Testing agent state management..."
        
        try {
            def agentState = new AgentState()
            
            // Test state changes
            println "Initial state:"
            println "  Current step: ${agentState.currentStep.get()}"
            println "  Is running: ${agentState.isRunning.get()}"
            println "  Is paused: ${agentState.isPaused.get()}"
            println "  Loop count: ${agentState.loopCount.get()}"
            
            // Change states
            println "\nChanging states..."
            agentState.setCurrentStep("analyze_requirements")
            agentState.setRunning(true)
            agentState.setPaused(false)
            agentState.incrementLoopCount()
            
            println "Updated state:"
            println "  Current step: ${agentState.currentStep.get()}"
            println "  Is running: ${agentState.isRunning.get()}"
            println "  Is paused: ${agentState.isPaused.get()}"
            println "  Loop count: ${agentState.loopCount.get()}"
            
            // Test compaction state
            println "\nTesting compaction state..."
            agentState.setCompacting(true, 0.5, "Compacting history...")
            println "  Is compacting: ${agentState.isCompacting.get()}"
            println "  Compaction progress: ${agentState.compactionProgress.get()}"
            println "  Compaction status: ${agentState.compactionStatus.get()}"
            
            agentState.setCompacting(false, 0.0, null)
            println "  Is compacting: ${agentState.isCompacting.get()}"
            
            println "Agent state test completed!"
            
        } catch (Exception e) {
            println "Agent state test failed: ${e.message}"
            e.printStackTrace()
        }
    }
}