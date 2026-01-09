package tests.core

import glm.core.Event
import glm.core.EventBus
import glm.core.EventType
import spock.lang.Specification
import spock.lang.Subject

class EventBusTest extends Specification {
    
    @Subject
    EventBus eventBus = EventBus.instance
    
    def setup() {
        // Clear any existing listeners
        eventBus.listeners.clear()
    }
    
    def "should subscribe and publish events"() {
        given:
        def receivedEvents = []
        def handler = { Event event -> receivedEvents << event }
        
        when:
        eventBus.subscribe(EventType.STATE_CHANGED, handler)
        eventBus.publish(EventType.STATE_CHANGED, [test: "data"])
        
        then:
        receivedEvents.size() == 1
        receivedEvents[0].type == EventType.STATE_CHANGED
        receivedEvents[0].data.test == "data"
    }
    
    def "should handle multiple listeners for same event type"() {
        given:
        def events1 = []
        def events2 = []
        def handler1 = { Event event -> events1 << event }
        def handler2 = { Event event -> events2 << event }
        
        when:
        eventBus.subscribe(EventType.STATE_CHANGED, handler1)
        eventBus.subscribe(EventType.STATE_CHANGED, handler2)
        eventBus.publish(EventType.STATE_CHANGED, [test: "data"])
        
        then:
        events1.size() == 1
        events2.size() == 1
        events1[0].data.test == "data"
        events2[0].data.test == "data"
    }
    
    def "should unsubscribe listeners"() {
        given:
        def events = []
        def handler = { Event event -> events << event }
        
        when:
        eventBus.subscribe(EventType.STATE_CHANGED, handler)
        eventBus.publish(EventType.STATE_CHANGED, [test: "data"])
        eventBus.unsubscribe(EventType.STATE_CHANGED, handler)
        eventBus.publish(EventType.STATE_CHANGED, [test: "data2"])
        
        then:
        events.size() == 1
        events[0].data.test == "data"
    }
    
    def "should handle events with custom data"() {
        given:
        def receivedEvents = []
        def handler = { Event event -> receivedEvents << event }
        
        when:
        eventBus.subscribe(EventType.AGENT_STEP_STARTED, handler)
        eventBus.publish(EventType.AGENT_STEP_STARTED, [
            step: "analyze_requirements",
            timestamp: System.currentTimeMillis()
        ])
        
        then:
        receivedEvents.size() == 1
        receivedEvents[0].type == EventType.AGENT_STEP_STARTED
        receivedEvents[0].data.step == "analyze_requirements"
    }
    
    def "should handle events without data"() {
        given:
        def receivedEvents = []
        def handler = { Event event -> receivedEvents << event }
        
        when:
        eventBus.subscribe(EventType.AGENT_LOOP_COMPLETED, handler)
        eventBus.publish(EventType.AGENT_LOOP_COMPLETED)
        
        then:
        receivedEvents.size() == 1
        receivedEvents[0].type == EventType.AGENT_LOOP_COMPLETED
        receivedEvents[0].data == [:]
    }
    
    def "should handle concurrent access"() {
        given:
        def events = []
        def handler = { Event event -> events << event }
        
        when:
        eventBus.subscribe(EventType.STATE_CHANGED, handler)
        
        // Simulate concurrent publishing
        def threads = []
        10.times {
            threads << Thread.start {
                eventBus.publish(EventType.STATE_CHANGED, [thread: it])
            }
        }
        threads*.join()
        
        then:
        events.size() == 10
        events.every { it.type == EventType.STATE_CHANGED }
    }
}