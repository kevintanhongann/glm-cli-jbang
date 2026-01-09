package tests.core

import glm.core.ReactiveState
import glm.core.StateRegistry
import spock.lang.Specification
import spock.lang.Subject

class StateRegistryTest extends Specification {
    
    @Subject
    StateRegistry registry = StateRegistry.instance
    
    def setup() {
        registry.clear()
    }
    
    def "should register and retrieve state"() {
        given:
        def state = new ReactiveState<String>("test", "value")
        
        when:
        registry.register("testKey", state)
        def retrieved = registry.get("testKey")
        
        then:
        retrieved == state
    }
    
    def "should get or create state"() {
        when:
        def state1 = registry.getOrCreate("testKey", { new ReactiveState<String>("test", "value") })
        def state2 = registry.getOrCreate("testKey", { new ReactiveState<String>("test", "different") })
        
        then:
        state1 == state2
        state1.get() == "value"
    }
    
    def "should list all state keys"() {
        given:
        registry.register("key1", new ReactiveState<String>("key1", "value1"))
        registry.register("key2", new ReactiveState<String>("key2", "value2"))
        
        when:
        def keys = registry.listKeys()
        
        then:
        keys.contains("key1")
        keys.contains("key2")
        keys.size() == 2
    }
    
    def "should clear all state"() {
        given:
        registry.register("key1", new ReactiveState<String>("key1", "value1"))
        registry.register("key2", new ReactiveState<String>("key2", "value2"))
        
        when:
        registry.clear()
        def keys = registry.listKeys()
        
        then:
        keys.isEmpty()
    }
    
    def "should handle concurrent access"() {
        given:
        def threads = []
        def states = []
        
        when:
        10.times { i ->
            threads << Thread.start {
                def state = new ReactiveState<String>("key$i", "value$i")
                registry.register("key$i", state)
                states << registry.get("key$i")
            }
        }
        threads*.join()
        
        then:
        states.size() == 10
        states.every { it != null }
    }
}