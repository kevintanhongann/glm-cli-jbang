package core

class AgentRegistry {
    private AgentType currentType
    private List<AgentType> visibleAgents

    AgentRegistry(AgentType initialType = AgentType.BUILD) {
        this.currentType = initialType
        this.visibleAgents = AgentConfig.getVisibleAgentTypes()
    }

    AgentType getCurrentAgent() {
        return currentType
    }

    void setAgent(AgentType type) {
        if (visibleAgents.contains(type)) {
            currentType = type
        }
    }

    void setAgentByName(String name) {
        def config = AgentConfig.forName(name)
        if (config && visibleAgents.contains(config.type)) {
            currentType = config.type
        }
    }

    void cycleAgent(int direction = 1) {
        if (visibleAgents.isEmpty()) {
            return
        }

        int currentIndex = visibleAgents.indexOf(currentType)
        
        if (currentIndex < 0) {
            currentIndex = 0
        }

        int nextIndex = (currentIndex + direction) % visibleAgents.size()
        
        if (nextIndex < 0) {
            nextIndex = visibleAgents.size() - 1
        }

        currentType = visibleAgents[nextIndex]
    }

    AgentConfig getCurrentAgentConfig() {
        return AgentConfig.forType(currentType)
    }

    List<AgentType> getVisibleAgents() {
        return new ArrayList<>(visibleAgents)
    }

    List<AgentConfig> getVisibleAgentConfigs() {
        return visibleAgents.collect { type ->
            AgentConfig.forType(type)
        }
    }

    String getCurrentAgentName() {
        return currentType.toString()
    }

    String getCurrentAgentDescription() {
        return getCurrentAgentConfig().description
    }

    boolean canSwitchTo(AgentType type) {
        return visibleAgents.contains(type)
    }
}
