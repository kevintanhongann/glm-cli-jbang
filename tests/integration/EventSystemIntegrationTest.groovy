package tests.integration

import glm.core.AgentState
import glm.core.Event
import glm.core.EventBus
import glm.core.EventType
import glm.core.ReactiveState
import glm.core.StateRegistry
import glm.core.StateChange
import spock.lang.Specification
import spock.lang.Subject

class EventSystemIntegrationTest extends Specification {
    
    @Subject
    EventBus eventBus = EventBus.instance
    
    def setup() {
        StateRegistry.instance.clear()
        eventBus.listeners.clear()
    }
    
    def "should integrate event system with agent state"() {
        given:
        def agentState = new AgentState()
        def stateChanges = []
        def events = []
        
        // Subscribe to state changes
        agentState.currentStep.subscribe { StateChange<String> change ->
            stateChanges << change
        }
        
        // Subscribe to events
        eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
            events << event
        })
        
        when:
        agentState.setCurrentStep("test_step")
        
        then:
        stateChanges.size() == 1
        events.size() == 1
        stateChanges[0].newValue == "test_step"
        events[0].data.name == "currentStep"
        events[0].data.change.newValue == "test_step"
    }
    
    def "should integrate event system with reactive state"() {
        given:
        def reactiveState = new ReactiveState<String>("test", "initial")
        def stateChanges = []
        def events = []
        
        // Subscribe to state changes
        reactiveState.subscribe { StateChange<String> change ->
            stateChanges << change
        }
        
        // Subscribe to events
        eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
            events << event
        })
        
        when:
        reactiveState.set("changed")
        
        then:
        stateChanges.size() == 1
        events.size() == 1
        stateChanges[0].newValue == "changed"
        events[0].data.name == "test"
        events[0].data.change.newValue == "changed"
    }
    
    def "should handle multiple state changes in sequence"() {
        given:
        def agentState = new AgentState()
        def stateChanges = []
        def events = []
        
        // Subscribe to state changes
        agentState.currentStep.subscribe { StateChange<String> change ->
            stateChanges << change
        }
        
        // Subscribe to events
        eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
            events << event
        })
        
        when:
        agentState.setCurrentStep("step1")
        agentState.setCurrentStep("step2")
        agentState.setCurrentStep("step3")
        
        then:
        stateChanges.size() == 3
        events.size() == 3
        stateChanges.every { it.propertyName == "currentStep" }
        events.every { it.data.name == "currentStep" }
        stateChanges*.newValue == ["step1", "step2", "step3"]
    }
    
    def "should handle concurrent state updates"() {
        given:
        def agentState = new AgentState()
        def stateChanges = []
        def events = []
        
        // Subscribe to state changes
        agentState.currentStep.subscribe { StateChange<String> change ->
            stateChanges << change
        }
        
        // Subscribe to events
        eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
            events << event
        })
        
        when:
        def threads = []
        10.times { i ->
            threads << Thread.start {
                agentState.setCurrentStep("step$i")
            }
        }
        threads*.join()
        
        then:
        stateChanges.size() == 10
        events.size() == 10
        stateChanges.every { it.propertyName == "currentStep" }
        events.every { it.data.name == "currentStep" }
    }
    
    def "should integrate with TUI components"() {
        given:
        def events = []
        
        // Subscribe to agent events
        eventBus.subscribe(EventType.AGENT_STEP_STARTED, { Event event ->
            events << event
        })
        
        eventBus.subscribe(EventType.AGENT_STEP_COMPLETED, { Event event ->
            events << event
        })
        
        when:
        // Simulate agent step events
        eventBus.publish(EventType.AGENT_STEP_STARTED, [step: "analyze"])
        eventBus.publish(EventType.AGENT_STEP_COMPLETED, [step: "analyze", success: true])
        
        then:
        events.size() == 2
        events[0].type == EventType.AGENT_STEP_STARTED
        events[0].data.step == "analyze"
        events[1].type == EventType.AGENT_STEP_COMPLETED
        events[1].data.step == "analyze"
        events[1].data.success == true
    }
}