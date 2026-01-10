package unit.mcp

import mcp.OAuthTokenStorage
import mcp.OAuthTokens
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class OAuthTokenStorageTest {

    private Path tempDir
    private Path originalTokenFile
    private OAuthTokenStorage storage

    @BeforeEach
    void setUp() {
        tempDir = Files.createTempDirectory('glm-oauth-test')
        originalTokenFile = Path.of(System.getProperty('user.home'), '.glm', 'mcp-tokens.json')

        storage = new OAuthTokenStorage() {
            Path getTokenFilePath() {
                return tempDir.resolve('mcp-tokens.json')
            }
        }
    }

    @AfterEach
    void tearDown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    @Test
    void testSaveAndRetrieveTokens() {
        OAuthTokens tokens = new OAuthTokens()
        tokens.accessToken = 'test-access-token'
        tokens.refreshToken = 'test-refresh-token'
        tokens.tokenType = 'Bearer'
        tokens.expiresAt = new Date(System.currentTimeMillis() + 3600000)

        storage.saveTokens('test-server', tokens)

        OAuthTokens retrieved = storage.getTokens('test-server')
        assertNotNull(retrieved)
        assertEquals('test-access-token', retrieved.accessToken)
        assertEquals('test-refresh-token', retrieved.refreshToken)
    }

    @Test
    void testHasValidTokensWithAccessToken() {
        OAuthTokens tokens = new OAuthTokens()
        tokens.accessToken = 'test-token'

        storage.saveTokens('test-server', tokens)

        assertTrue(storage.hasValidTokens('test-server'))
    }

    @Test
    void testHasValidTokensWithExpiredToken() {
        OAuthTokens tokens = new OAuthTokens()
        tokens.accessToken = 'test-token'
        tokens.expiresAt = new Date(System.currentTimeMillis() - 1000)

        storage.saveTokens('test-server', tokens)

        assertFalse(storage.hasValidTokens('test-server'))
    }

    @Test
    void testHasValidTokensWithNull() {
        assertFalse(storage.hasValidTokens('nonexistent'))
    }

    @Test
    void testRemoveTokens() {
        OAuthTokens tokens = new OAuthTokens()
        tokens.accessToken = 'test-token'

        storage.saveTokens('test-server', tokens)
        assertTrue(storage.hasValidTokens('test-server'))

        storage.removeTokens('test-server')
        assertFalse(storage.hasValidTokens('test-server'))
    }

    @Test
    void testTokenExpiration() {
        OAuthTokens tokens = new OAuthTokens()
        assertFalse(tokens.isExpired())

        tokens.expiresAt = new Date(System.currentTimeMillis() - 1000)
        assertTrue(tokens.isExpired())
    }

    @Test
    void testNeedsRefresh() {
        OAuthTokens tokens = new OAuthTokens()

        assertFalse(tokens.needsRefresh())

        tokens.expiresAt = new Date(System.currentTimeMillis() + 600000)
        assertFalse(tokens.needsRefresh())

        tokens.expiresAt = new Date(System.currentTimeMillis() + 60000)
        assertFalse(tokens.needsRefresh())

        tokens.expiresAt = new Date(System.currentTimeMillis() + 1000)
        assertTrue(tokens.needsRefresh())
    }

}
