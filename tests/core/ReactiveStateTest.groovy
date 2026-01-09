package tests.core

import glm.core.ReactiveState
import glm.core.StateChange
import spock.lang.Specification
import spock.lang.Subject

class ReactiveStateTest extends Specification {
    
    @Subject
    ReactiveState<String> state
    
    def setup() {
        state = new ReactiveState<>("testState", "initial")
    }
    
    def "should get initial value"() {
        expect:
        state.get() == "initial"
    }
    
    def "should set and get new value"() {
        when:
        state.set("new value")
        
        then:
        state.get() == "new value"
    }
    
    def "should not notify listeners when value unchanged"() {
        given:
        def changeCount = 0
        state.subscribe { StateChange<String> change -> changeCount++ }
        
        when:
        state.set("initial")  // Same as initial value
        
        then:
        changeCount == 0
        state.get() == "initial"
    }
    
    def "should notify listeners when value changes"() {
        given:
        def changes = []
        state.subscribe { StateChange<String> change -> changes << change }
        
        when:
        state.set("changed")
        
        then:
        changes.size() == 1
        changes[0].propertyName == "testState"
        changes[0].oldValue == "initial"
        changes[0].newValue == "changed"
        changes[0].timestamp != null
    }
    
    def "should handle null values"() {
        given:
        def changes = []
        state.subscribe { StateChange<String> change -> changes << change }
        
        when:
        state.set(null)
        
        then:
        changes.size() == 1
        changes[0].oldValue == "initial"
        changes[0].newValue == null
        state.get() == null
    }
    
    def "should handle multiple listeners"() {
        given:
        def changes1 = []
        def changes2 = []
        state.subscribe { StateChange<String> change -> changes1 << change }
        state.subscribe { StateChange<String> change -> changes2 << change }
        
        when:
        state.set("multiple listeners")
        
        then:
        changes1.size() == 1
        changes2.size() == 1
        changes1[0].newValue == "multiple listeners"
        changes2[0].newValue == "multiple listeners"
    }
    
    def "should unsubscribe listeners"() {
        given:
        def changes1 = []
        def changes2 = []
        def handler1 = { StateChange<String> change -> changes1 << change }
        def handler2 = { StateChange<String> change -> changes2 << change }
        
        when:
        state.subscribe(handler1)
        state.subscribe(handler2)
        state.set("first change")
        state.unsubscribe(handler1)
        state.set("second change")
        
        then:
        changes1.size() == 1
        changes2.size() == 2
        changes1[0].newValue == "first change"
        changes2[0].newValue == "first change"
        changes2[1].newValue == "second change"
    }
    
    def "should update value using function"() {
        given:
        def changes = []
        state.subscribe { StateChange<String> change -> changes << change }
        
        when:
        state.update { String value -> value.toUpperCase() }
        
        then:
        changes.size() == 1
        changes[0].oldValue == "initial"
        changes[0].newValue == "INITIAL"
        state.get() == "INITIAL"
    }
    
    def "should handle complex state objects"() {
        given:
        def complexState = new ReactiveState<>("complex", [name: "test", count: 0])
        def changes = []
        complexState.subscribe { StateChange<Map> change -> changes << change }
        
        when:
        complexState.set([name: "updated", count: 1])
        
        then:
        changes.size() == 1
        changes[0].oldValue.name == "test"
        changes[0].newValue.name == "updated"
        changes[0].newValue.count == 1
    }
}