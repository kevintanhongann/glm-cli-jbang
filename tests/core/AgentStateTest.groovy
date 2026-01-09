package tests.core

import glm.core.AgentState
import glm.core.ReactiveState
import glm.core.StateRegistry
import spock.lang.Specification
import spock.lang.Subject

class AgentStateTest extends Specification {
    
    @Subject
    AgentState agentState
    
    def setup() {
        StateRegistry.instance.clear()
        agentState = new AgentState()
    }
    
    def "should initialize with default values"() {
        expect:
        agentState.currentStep.get() == null
        agentState.isRunning.get() == false
        agentState.isPaused.get() == false
        agentState.isStopped.get() == false
        agentState.isCompacting.get() == false
        agentState.compactionProgress.get() == 0.0
        agentState.compactionStatus.get() == null
        agentState.loopCount.get() == 0
        agentState.maxIterations.get() == 10
        agentState.isDoomLoopDetected.get() == false
        agentState.doomLoopMessage.get() == null
        agentState.isPermissionRequired.get() == false
        agentState.permissionPrompt.get() == null
        agentState.isPermissionGranted.get() == false
    }
    
    def "should update current step"() {
        when:
        agentState.setCurrentStep("test_step")
        
        then:
        agentState.currentStep.get() == "test_step"
    }
    
    def "should toggle running state"() {
        when:
        agentState.setRunning(true)
        
        then:
        agentState.isRunning.get() == true
        
        when:
        agentState.setRunning(false)
        
        then:
        agentState.isRunning.get() == false
    }
    
    def "should toggle paused state"() {
        when:
        agentState.setPaused(true)
        
        then:
        agentState.isPaused.get() == true
        
        when:
        agentState.setPaused(false)
        
        then:
        agentState.isPaused.get() == false
    }
    
    def "should toggle stopped state"() {
        when:
        agentState.setStopped(true)
        
        then:
        agentState.isStopped.get() == true
        
        when:
        agentState.setStopped(false)
        
        then:
        agentState.isStopped.get() == false
    }
    
    def "should update loop count"() {
        when:
        agentState.incrementLoopCount()
        
        then:
        agentState.loopCount.get() == 1
        
        when:
        agentState.incrementLoopCount()
        
        then:
        agentState.loopCount.get() == 2
    }
    
    def "should handle compaction state"() {
        when:
        agentState.setCompacting(true, 0.5, "Compacting history...")
        
        then:
        agentState.isCompacting.get() == true
        agentState.compactionProgress.get() == 0.5
        agentState.compactionStatus.get() == "Compacting history..."
        
        when:
        agentState.setCompacting(false, 0.0, null)
        
        then:
        agentState.isCompacting.get() == false
        agentState.compactionProgress.get() == 0.0
        agentState.compactionStatus.get() == null
    }
    
    def "should handle doom loop detection"() {
        when:
        agentState.setDoomLoopDetected(true, "Loop detected")
        
        then:
        agentState.isDoomLoopDetected.get() == true
        agentState.doomLoopMessage.get() == "Loop detected"
        
        when:
        agentState.setDoomLoopDetected(false, null)
        
        then:
        agentState.isDoomLoopDetected.get() == false
        agentState.doomLoopMessage.get() == null
    }
    
    def "should handle permission state"() {
        when:
        agentState.setPermissionRequired(true, "Need permission to write file")
        
        then:
        agentState.isPermissionRequired.get() == true
        agentState.permissionPrompt.get() == "Need permission to write file"
        
        when:
        agentState.setPermissionGranted(true)
        
        then:
        agentState.isPermissionGranted.get() == true
        
        when:
        agentState.setPermissionRequired(false, null)
        agentState.setPermissionGranted(false)
        
        then:
        agentState.isPermissionRequired.get() == false
        agentState.permissionPrompt.get() == null
        agentState.isPermissionGranted.get() == false
    }
    
    def "should register states in registry"() {
        given:
        def registry = StateRegistry.instance
        
        when:
        agentState = new AgentState()
        
        then:
        registry.get("currentStep") != null
        registry.get("isRunning") != null
        registry.get("isPaused") != null
        registry.get("isStopped") != null
        registry.get("loopCount") != null
        registry.get("maxIterations") != null
    }
}