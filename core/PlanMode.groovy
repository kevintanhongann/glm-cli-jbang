package core

import groovy.transform.Canonical

@Canonical
class PlanMode {
    boolean active = false
    int phase = 0
    String currentTask = ""
    String planFilePath = ""
    List<String> keyFindings = []
    List<String> criticalFiles = []
    String proposedApproach = ""
    List<String> userQuestions = []
    boolean approved = false
    String thoroughness = "medium"

    void start(String task) {
        this.active = true
        this.phase = 1
        this.currentTask = task
        this.approved = false
    }

    void exit() {
        this.active = false
        this.phase = 0
        reset()
    }

    void reset() {
        this.keyFindings = []
        this.criticalFiles = []
        this.proposedApproach = ""
        this.userQuestions = []
    }

    String getPhaseName() {
        switch(phase) {
            case 1: return "Understanding"
            case 2: return "Planning"
            case 3: return "Synthesis"
            case 4: return "Execution"
            case 5: return "Complete"
            default: return "Off"
        }
    }

    boolean isInteractive() {
        return phase in [1, 2, 3]
    }

    boolean canExecute() {
        return phase == 4 && approved
    }
}
