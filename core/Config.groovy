package core

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@JsonIgnoreProperties(ignoreUnknown = true)
class Config {

    @JsonProperty('api')
    ApiConfig api = new ApiConfig()

    @JsonProperty('behavior')
    BehaviorConfig behavior = new BehaviorConfig()

    @JsonProperty('web_search')
    WebSearchConfig webSearch = new WebSearchConfig()

    @JsonProperty('rag')
    RagConfig rag = new RagConfig()

    @JsonProperty('tui')
    TuiConfig tui = new TuiConfig()

    @JsonProperty('experimental')
    ExperimentalConfig experimental = new ExperimentalConfig()

    @JsonProperty('tool_heuristics')
    ToolHeuristicsConfig toolHeuristics = new ToolHeuristicsConfig()

    @JsonProperty('parallel_execution')
    ParallelExecutionConfig parallelExecution = new ParallelExecutionConfig()

    @JsonProperty('provider')
    Map<String, ProviderConfig> provider = [:]

    @JsonProperty('instructions')
    List<String> instructions = []

    @JsonProperty('skills')
    SkillConfig skills = new SkillConfig()

    static class ApiConfig {

        String key
        @JsonProperty('base_url')
        String baseUrl

    }

    static class BehaviorConfig {

        String language = 'auto'
        @JsonProperty('safety_mode')
        String safetyMode = 'ask' // ask, always_allow
        @JsonProperty('default_model')
        String defaultModel = 'zai/glm-4-flash'
        @JsonProperty('max_steps')
        Integer maxSteps = 25  // Default: 25 steps (null = unlimited)
        @JsonProperty('recent_models')
        List<String> recentModels = []
        @JsonProperty('favorite_models')
        List<String> favoriteModels = []

    }

    static class ProviderConfig {

        @JsonProperty('endpoint')
        String endpoint

        @JsonProperty('auth_type')
        String authType = 'bearer' // bearer or jwt

    }

    static class WebSearchConfig {

        Boolean enabled = true
        @JsonProperty('default_count')
        Integer defaultCount = 10
        @JsonProperty('default_recency_filter')
        String defaultRecencyFilter = 'noLimit'

    }

    static class RagConfig {

        Boolean enabled = false
        @JsonProperty('cache_dir')
        String cacheDir = '~/.glm/embeddings'
        @JsonProperty('max_chunk_size')
        Integer maxChunkSize = 500
        @JsonProperty('min_score')
        Double minScore = 0.5

    }

    static class TuiConfig {

        @JsonProperty('colors_enabled')
        Boolean colorsEnabled = true
        @JsonProperty('diff_context_lines')
        Integer diffContextLines = 3
        @JsonProperty('agent_cycle_key')
        String agentCycleKey = 'tab'
        @JsonProperty('agent_cycle_reverse_key')
        String agentCycleReverseKey = 'shift+tab'

    }

    static class ExperimentalConfig {

        @JsonProperty('continue_loop_on_deny')
        Boolean continueLoopOnDeny = false  // If true, continues loop on permission deny; if false, stops loop

    }

    static class ToolHeuristicsConfig {

        Boolean enabled = true
        @JsonProperty('parallel_execution')
        Boolean parallelExecution = true
        @JsonProperty('max_parallel_tools')
        Integer maxParallelTools = 10
        @JsonProperty('suggest_explore_agent')
        Boolean suggestExploreAgent = true

    }

    static class ParallelExecutionConfig {

        Boolean enabled = true
        @JsonProperty('max_parallel_tools')
        Integer maxParallelTools = 10
        @JsonProperty('thread_pool_size')
        Integer threadPoolSize = 10
        @JsonProperty('progress_display')
        Boolean progressDisplay = true

    }

    static class SkillConfig {
        Boolean enabled = true
        @JsonProperty('skill_permissions')
        Map<String, String> skillPermissions = [:]

        boolean isSkillAllowed(String skillName) {
            if (!enabled) return false
            def permission = skillPermissions[skillName]
            if (permission) {
                return permission != 'deny'
            }
            return true
        }
    }

    List<String> getSkillPermissionPatterns() {
        return behavior?.skill_permissions ?: ['*']
    }

    boolean isSkillAllowed(String skillName) {
        def patterns = getSkillPermissionPatterns()
        for (pattern in patterns) {
            if (matchPattern(pattern, skillName)) {
                return pattern != 'deny'
            }
        }
        return true
    }

    private boolean matchPattern(String pattern, String skillName) {
        if (pattern == '*') return true
        if (pattern.endsWith('*')) {
            return skillName.startsWith(pattern[0..-2])
        }
        return skillName == pattern
    }

    static Config load() {
        Path configPath = Paths.get(System.getProperty('user.home'), '.glm', 'config.toml')
        if (Files.exists(configPath)) {
            try {
                TomlMapper mapper = new TomlMapper()
                return mapper.readValue(configPath.toFile(), Config.class)
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse config file: ${e.message}")
            }
        }
        return new Config()
    }

}
