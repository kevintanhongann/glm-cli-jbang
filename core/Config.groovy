package core

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@JsonIgnoreProperties(ignoreUnknown = true)
class Config {
    @JsonProperty("api")
    ApiConfig api = new ApiConfig()

    @JsonProperty("behavior")
    BehaviorConfig behavior = new BehaviorConfig()

    static class ApiConfig {
        String key
        @JsonProperty("base_url")
        String baseUrl
    }

    static class BehaviorConfig {
        String language = "auto"
        @JsonProperty("safety_mode")
        String safetyMode = "ask" // ask, always_allow
        @JsonProperty("default_model")
        String defaultModel = "glm-4-flash"
    }

    static Config load() {
        Path configPath = Paths.get(System.getProperty("user.home"), ".glm", "config.toml")
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
