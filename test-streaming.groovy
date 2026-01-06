#!/usr/bin/env groovy

// Simple test script to verify TUI streaming functionality
@Grapes([
    @Grab('org.codehaus.groovy:groovy:4.0.15'),
    @Grab('com.googlecode.lanterna:lanterna:3.1.1'),
])

import tui.lanterna.widgets.ActivityLogPanel
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory

// Create a minimal GUI setup for testing
def terminal = new DefaultTerminalFactory().createTerminal()
def screen = new TerminalScreen(terminal)
screen.startScreen()

def textGUI = new MultiWindowTextGUI(screen)

// Create ActivityLogPanel
def activityLogPanel = new ActivityLogPanel(textGUI)

// Test streaming functionality
println "Testing streaming functionality..."

activityLogPanel.appendUserMessage("Hello, can you explain streaming?")

// Start streaming response
activityLogPanel.startStreamingResponse()

// Simulate streaming chunks
def chunks = [
    "Hello! ",
    "Streaming is a technique ",
    "where content is delivered ",
    "incrementally rather than ",
    "all at once. ",
    "This provides better user experience ",
    "as users can see the response ",
    "building up in real-time."
]

chunks.eachWithIndex { chunk, index ->
    Thread.sleep(500) // Simulate network delay
    activityLogPanel.appendStreamChunk(chunk)
    println "Added chunk ${index + 1}: '${chunk}'"
}

// Finish streaming
activityLogPanel.finishStreamingResponse()

Thread.sleep(2000) // Show animation

screen.stopScreen()
println "Streaming test completed successfully!"