package mcp

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

class OAuthTokenStorage {

    private static OAuthTokenStorage instance
    private final Path tokenFile
    private final ObjectMapper mapper = new ObjectMapper()
    private Map<String, OAuthTokens> tokens = new HashMap<>()

    private OAuthTokenStorage() {
        tokenFile = Paths.get(System.getProperty('user.home'), '.glm', 'mcp-tokens.json')
        loadTokens()
    }

    static synchronized OAuthTokenStorage getInstance() {
        if (instance == null) {
            instance = new OAuthTokenStorage()
        }
        return instance
    }

    private void loadTokens() {
        if (Files.exists(tokenFile)) {
            try {
                tokens = mapper.readValue(tokenFile.toFile(), Map)
                    .collectEntries { key, value -> [key as String, mapper.convertValue(value, OAuthTokens.class)] }
            } catch (Exception e) {
                System.err.println "Warning: Failed to load OAuth tokens: ${e.message}"
            }
        }
    }

    void saveTokens(String serverName, OAuthTokens newTokens) {
        tokens.put(serverName, newTokens)
        saveToFile()
    }

    OAuthTokens getTokens(String serverName) {
        return tokens.get(serverName)
    }

    void removeTokens(String serverName) {
        tokens.remove(serverName)
        saveToFile()
    }

    boolean hasValidTokens(String serverName) {
        OAuthTokens token = tokens.get(serverName)
        if (token == null) return false

        if (token.expiresAt != null) {
            return token.expiresAt.after(new Date())
        }

        return token.accessToken != null && !token.accessToken.isEmpty()
    }

    private void saveToFile() {
        try {
            Files.createDirectories(tokenFile.parent)
            mapper.writerWithDefaultPrettyPrinter().writeValue(tokenFile.toFile(), tokens)
        } catch (Exception e) {
            System.err.println "Warning: Failed to save OAuth tokens: ${e.message}"
        }
    }

}

class OAuthTokens {
    String accessToken
    String refreshToken
    String tokenType = 'Bearer'
    Date expiresAt
    Map<String, String> additionalFields = [:]

    boolean isExpired() {
        expiresAt != null && expiresAt.before(new Date())
    }

    boolean needsRefresh() {
        if (expiresAt == null) return false
        long fiveMinutes = System.currentTimeMillis() + 5 * 60 * 1000
        return expiresAt.getTime() < fiveMinutes
    }
}
