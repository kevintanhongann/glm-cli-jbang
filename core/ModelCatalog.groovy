package core

import java.nio.file.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ModelCatalog {
    private static final String CACHE_DIR = ".glm/cache"
    private static final String MODELS_CACHE_FILE = "models.json"
    private static final File CACHE_PATH
    private static final long CACHE_TTL_MS = 60 * 60 * 1000 // 1 hour

    static {
        def homeDir = System.getProperty("user.home")
        CACHE_PATH = new File(homeDir, CACHE_DIR + "/" + MODELS_CACHE_FILE)
    }

    static class ModelInfo {
        String id
        String name
        String description
        String endpoint
        Map<String, Object> cost
        Map<String, Object> limit
        String provider
    }

    static class ProviderInfo {
        String id
        String name
        String description
        String modelsUrl
        String endpoint
        String authType
    }

    private static final Map<String, ProviderInfo> PROVIDERS = [
        zai: [
            id: "zai",
            name: "Zai/Zhipu AI",
            description: "GLM-4 and Coding Plan API",
            modelsUrl: null,
            endpoint: "https://api.z.ai/api/paas/v4/chat/completions",
            authType: "jwt"
        ],
        opencode: [
            id: "opencode",
            name: "OpenCode Zen",
            description: "Big Pickle, GLM-4.7-free, and other curated models",
            modelsUrl: "https://opencode.ai/zen/v1/models",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            authType: "bearer"
        ]
    ]

    private static final List<ModelInfo> STATIC_OPENCODE_MODELS = [
        [
            id: "big-pickle",
            name: "Big Pickle",
            description: "Stealth model (Free)",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0, output: 0, cache_read: 0],
            limit: [context: 200000, output: 8192]
        ],
        [
            id: "glm-4.7-free",
            name: "GLM 4.7 Free",
            description: "Free version of GLM-4.7",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0, output: 0, cache_read: 0],
            limit: [context: 128000, output: 4096]
        ],
        [
            id: "glm-4.6",
            name: "GLM 4.6",
            description: "GLM-4.6 model",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0.60, output: 2.20, cache_read: 0.10],
            limit: [context: 128000, output: 4096]
        ],
        [
            id: "kimi-k2",
            name: "Kimi K2",
            description: "Moonshot Kimi K2 model",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0.40, output: 2.50],
            limit: [context: 128000, output: 8192]
        ],
        [
            id: "kimi-k2-thinking",
            name: "Kimi K2 Thinking",
            description: "Moonshot Kimi K2 Thinking model",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0.40, output: 2.50],
            limit: [context: 128000, output: 8192]
        ],
        [
            id: "qwen3-coder",
            name: "Qwen3 Coder 480B",
            description: "Alibaba Qwen3 Coder model",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0.45, output: 1.50],
            limit: [context: 128000, output: 8192]
        ],
        [
            id: "grok-code",
            name: "Grok Code Fast 1",
            description: "xAI Grok Code model (Free)",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0, output: 0, cache_read: 0],
            limit: [context: 128000, output: 8192]
        ],
        [
            id: "minimax-m2.1-free",
            name: "MiniMax M2.1",
            description: "MiniMax M2.1 model (Free)",
            provider: "opencode",
            endpoint: "https://opencode.ai/zen/v1/chat/completions",
            cost: [input: 0, output: 0, cache_read: 0],
            limit: [context: 128000, output: 4096]
        ],
        [
            id: "glm-4-flash",
            name: "GLM-4 Flash",
            description: "GLM-4 Flash model (Zai)",
            provider: "zai",
            endpoint: "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            cost: [input: 0.01, output: 0.01],
            limit: [context: 128000, output: 4096]
        ],
        [
            id: "glm-4.7",
            name: "GLM-4.7",
            description: "GLM-4.7 model (Zai)",
            provider: "zai",
            endpoint: "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            cost: [input: 0.05, output: 0.05],
            limit: [context: 128000, output: 4096]
        ]
    ]

    static Map<String, ProviderInfo> getProviders() {
        return new HashMap(PROVIDERS)
    }

    static ProviderInfo getProvider(String providerId) {
        return PROVIDERS[providerId]
    }

    static Map<String, ModelInfo> getAllModels() {
        Map<String, ModelInfo> allModels = [:]
        
        try {
            Map<String, Object> cached = loadFromCache()
            if (cached != null) {
                cached.each { key, value ->
                    def model = new ModelInfo(value)
                    allModels[key] = model
                }
            }
        } catch (Exception e) {
            println "Warning: Failed to load cached models, using static catalog: ${e.message}"
        }

        STATIC_OPENCODE_MODELS.each { modelData ->
            def model = new ModelInfo(modelData)
            allModels["${model.provider}/${model.id}"] = model
        }

        return allModels
    }

    static List<ModelInfo> getModelsForProvider(String providerId) {
        def allModels = getAllModels()
        return allModels.values().findAll { it.provider == providerId }.sort { a, b -> a.name <=> b.name }
    }

    static ModelInfo getModel(String modelId) {
        def allModels = getAllModels()
        return allModels[modelId]
    }

    static void refreshCache() {
        println "Refreshing model catalog..."

        PROVIDERS.each { providerId, providerInfo ->
            if (providerInfo.modelsUrl) {
                try {
                    println "  Fetching models from ${providerInfo.name}..."
                    fetchModelsFromUrl(providerInfo)
                } catch (Exception e) {
                    println "  Warning: Failed to fetch models from ${providerInfo.name}: ${e.message}"
                }
            }
        }

        println "  Model catalog refreshed"
    }

    private static void fetchModelsFromUrl(ProviderInfo provider) {
        def client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        def request = HttpRequest.newBuilder()
            .uri(URI.create(provider.modelsUrl))
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            def slurper = new JsonSlurper()
            def data = slurper.parseText(response.body())
            Map<String, ModelInfo> models = [:]

            if (data instanceof Map) {
                if (data.containsKey("data")) {
                    data.data.each { modelData ->
                        def model = parseModelData(modelData, provider)
                        if (model) {
                            models["${provider.id}/${model.id}"] = model
                        }
                    }
                } else {
                    data.each { modelId, modelData ->
                        def model = parseModelData(modelData, provider)
                        if (model) {
                            models["${provider.id}/${modelId}"] = model
                        }
                    }
                }
            } else if (data instanceof List) {
                data.each { modelData ->
                    def model = parseModelData(modelData, provider)
                    if (model) {
                        models["${provider.id}/${model.id}"] = model
                    }
                }
            }

            saveToCache(provider.id, models)
        } else {
            throw new RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
        }
    }

    private static ModelInfo parseModelData(Object modelData, ProviderInfo provider) {
        if (!(modelData instanceof Map)) return null

        def id = modelData.id ?: modelData.model
        if (!id) return null

        return new ModelInfo(
            id: id,
            name: modelData.name ?: id,
            description: modelData.description ?: "",
            provider: provider.id,
            endpoint: provider.endpoint,
            cost: modelData.cost ?: [:],
            limit: modelData.limit ?: [:]
        )
    }

    private static Map<String, Object> loadFromCache() {
        if (!CACHE_PATH.exists()) return null

        try {
            def slurper = new JsonSlurper()
            return slurper.parse(CACHE_PATH) as Map
        } catch (Exception e) {
            return null
        }
    }

    private static void saveToCache(String providerId, Map<String, ModelInfo> models) {
        ensureCacheDir()
        
        def existing = loadFromCache() ?: [:]
        models.each { key, model ->
            existing[key] = [
                id: model.id,
                name: model.name,
                description: model.description,
                provider: model.provider,
                endpoint: model.endpoint,
                cost: model.cost,
                limit: model.limit
            ]
        }

        CACHE_PATH.text = JsonOutput.toJson(existing)
    }

    private static void ensureCacheDir() {
        def cacheDir = CACHE_PATH.parentFile
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    static boolean isCacheValid() {
        if (!CACHE_PATH.exists()) return false
        def lastModified = CACHE_PATH.lastModified()
        return (System.currentTimeMillis() - lastModified) < CACHE_TTL_MS
    }

    static void printModels(List<ModelInfo> models, boolean verbose = false) {
        models.each { model ->
            def isFree = model.cost?.input == 0 && model.cost?.output == 0
            def freeTag = isFree ? " (Free)" : ""
            
            println "  ${model.provider}/${model.id}${freeTag}"
            println "    Name: ${model.name}"
            
            if (verbose) {
                if (model.description) {
                    println "    Description: ${model.description}"
                }
                if (model.limit?.context) {
                    println "    Context: ${model.limit.context} tokens"
                }
                if (model.cost?.input > 0 || model.cost?.output > 0) {
                    println "    Cost: \$${model.cost?.input ?: 0}/1M input, \$${model.cost?.output ?: 0}/1M output"
                }
            }
            println()
        }
    }
}
