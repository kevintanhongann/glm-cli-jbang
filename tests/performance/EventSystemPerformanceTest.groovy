package tests.performance

import glm.core.Event
import glm.core.EventBus
import glm.core.EventType
import spock.lang.Specification
import spock.lang.Subject

class EventSystemPerformanceTest extends Specification {
    
    @Subject
    EventBus eventBus = EventBus.instance
    
    def setup() {
        eventBus.listeners.clear()
    }
    
    def "should handle high frequency event publishing"() {
        given:
        def eventCount = 10000
        def startTime = System.currentTimeMillis()
        def receivedEvents = []
        
        // Subscribe to events
        eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
            receivedEvents << event
        })
        
        when:
        eventCount.times {
            eventBus.publish(EventType.STATE_CHANGED, [count: it])
        }
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        receivedEvents.size() == eventCount
        duration < 1000  // Should complete in under 1 second
    }
    
    def "should handle multiple concurrent subscribers"() {
        given:
        def subscriberCount = 100
        def eventCount = 1000
        def startTime = System.currentTimeMillis()
        def receivedEvents = new int[subscriberCount]
        
        // Subscribe multiple listeners
        subscriberCount.times { i ->
            eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
                receivedEvents[i]++
            })
        }
        
        when:
        eventCount.times {
            eventBus.publish(EventType.STATE_CHANGED, [count: it])
        }
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        receivedEvents.every { it == eventCount }
        duration < 2000  // Should complete in under 2 seconds
    }
    
    def "should handle concurrent publishing and subscribing"() {
        given:
        def threadCount = 10
        def eventsPerThread = 1000
        def startTime = System.currentTimeMillis()
        def receivedEvents = []
        
        // Subscribe to events
        eventBus.subscribe(EventType.STATE_CHANGED, { Event event ->
            receivedEvents << event
        })
        
        when:
        def threads = []
        threadCount.times {
            threads << Thread.start {
                eventsPerThread.times { j ->
                    eventBus.publish(EventType.STATE_CHANGED, [thread: it, event: j])
                }
            }
        }
        threads*.join()
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then:
        receivedEvents.size() == threadCount * eventsPerThread
        duration < 3000  // Should complete in under 3 seconds
    }
    
    def "should handle event memory usage"() {
        given:
        def eventCount = 10000
        def initialMemory = Runtime.runtime.freeMemory()
        
        when:
        eventCount.times {
            eventBus.publish(EventType.STATE_CHANGED, [data: "test" * 100])  // Large data
        }
        
        then:
        def finalMemory = Runtime.runtime.freeMemory()
        def memoryUsed = initialMemory - finalMemory
        memoryUsed < 100 * 1024 * 1024  // Should not use more than 100MB
    }
    
    def "should handle event cleanup"() {
        given:
        def eventCount = 1000
        def handlers = []
        
        when:
        // Subscribe many handlers
        eventCount.times {
            def handler = { Event event -> /* do nothing */ }
            handlers << handler
            eventBus.subscribe(EventType.STATE_CHANGED, handler)
        }
        
        // Publish events
        eventCount.times {
            eventBus.publish(EventType.STATE_CHANGED, [count: it])
        }
        
        // Unsubscribe all handlers
        handlers.each { handler ->
            eventBus.unsubscribe(EventType.STATE_CHANGED, handler)
        }
        
        then:
        eventBus.listeners[EventType.STATE_CHANGED].size() == 0
    }
}