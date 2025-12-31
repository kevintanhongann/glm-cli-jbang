package core

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.nio.charset.StandardCharsets
import models.ChatRequest
import models.Message
import com.fasterxml.jackson.databind.ObjectMapper
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

class GlmClient {
    private static final String BASE_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    private static final long EXPIRATION_SECONDS = 3600 // 1 hour
    private static final long CACHE_TTL_SECONDS = 3500 // Slightly less than expiration

    private final String apiKey
    private final HttpClient client
    private final ObjectMapper mapper
    
    private String cachedToken
    private long tokenExpiryTime = 0

    GlmClient(String apiKey) {
        this.apiKey = apiKey
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        this.mapper = new ObjectMapper()
    }

    GlmClient() {
        this.apiKey = loadApiKey()
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        this.mapper = new ObjectMapper()
    }

    private static String loadApiKey() {
        def credential = Auth.get("zai")
        if (credential != null) {
            return credential.key
        }

        def envVar = System.getenv("ZAI_API_KEY")
        if (envVar != null) {
            return envVar
        }

        throw new IllegalStateException("No API key found. Use 'glm auth login' to set up authentication or set ZAI_API_KEY environment variable")
    }

    private synchronized String getToken() {
        long now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime) {
            return cachedToken
        }

        String[] parts = apiKey.split("\\.")
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid API Key format")
        }

        String id = parts[0]
        String secret = parts[1]

        Algorithm algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8))
        
        Map<String, Object> headerClaims = new HashMap<>()
        headerClaims.put("alg", "HS256")
        headerClaims.put("sign_type", "SIGN")

        cachedToken = JWT.create()
            .withHeader(headerClaims)
            .withClaim("api_key", id)
            .withExpiresAt(new Date(now + EXPIRATION_SECONDS * 1000))
            .withClaim("timestamp", now)
            .sign(algorithm)

        tokenExpiryTime = now + (CACHE_TTL_SECONDS * 1000)
        return cachedToken
    }

    String sendMessage(ChatRequest request) {
        request.stream = false
        String jsonBody = mapper.writeValueAsString(request)
        String token = getToken()

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
             throw new RuntimeException("API Request failed with code ${response.statusCode()}: ${response.body()}")
        }

        return response.body()
    }

    void streamMessage(ChatRequest request, Closure onChunk) {
        request.stream = true
        String jsonBody = mapper.writeValueAsString(request)
        String token = getToken()

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
            .thenAccept { response ->
                if (response.statusCode() != 200) {
                    // Ideally read body to get error, but streaming response body is a stream
                    throw new RuntimeException("API Request failed with code ${response.statusCode()}")
                }
                
                response.body().forEach { line ->
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim()
                        if (data == "[DONE]") {
                            return
                        }
                        try {
                            def chatResponse = mapper.readValue(data, models.ChatResponse.class)
                            onChunk.call(chatResponse)
                        } catch (Exception e) {
                            // Ignore parsing errors for empty lines or keepalives
                        }
                    }
                }
            }
            .join() // Wait for completion
    }
}
