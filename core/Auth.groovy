package core

import java.nio.file.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import models.AuthCredential

/**
 * Authentication storage and retrieval
 */
class Auth {
    private static final String AUTH_DIR = ".glm"
    private static final String AUTH_FILE = "auth.json"
    private static final File AUTH_PATH

    static {
        def homeDir = System.getProperty("user.home")
        AUTH_PATH = new File(homeDir, AUTH_DIR + "/" + AUTH_FILE)
    }

    /**
     * Store a credential for a provider
     */
    static void set(String provider, AuthCredential credential, Map<String, Object> metadata = [:]) {
        ensureAuthDir()
        def existing = all()
        def providerData = [
            type: credential.type,
            key: credential.key,
            provider: credential.provider,
            timestamp: System.currentTimeMillis()
        ]
        
        if (metadata) {
            providerData.putAll(metadata)
        }

        existing[provider] = providerData

        AUTH_PATH.text = JsonOutput.toJson(existing)
        AUTH_PATH.setReadable(true, true)
        AUTH_PATH.setWritable(true, true)
    }

    /**
     * Retrieve a credential for a provider
     */
    static AuthCredential get(String provider) {
        def all = all()
        def cred = all[provider]
        return cred ? new AuthCredential(
            type: cred.type,
            key: cred.key,
            provider: provider,
            timestamp: cred.timestamp
        ) : null
    }

    /**
     * Get all stored credentials
     */
    static Map<String, Map> all() {
        if (!AUTH_PATH.exists()) {
            return [:]
        }

        try {
            def slurper = new JsonSlurper()
            return slurper.parse(AUTH_PATH) as Map
        } catch (Exception e) {
            return [:]
        }
    }

    /**
     * Remove a credential for a provider
     */
    static void remove(String provider) {
        def existing = all()
        existing.remove(provider)
        AUTH_PATH.text = JsonOutput.toJson(existing)
    }

    /**
     * Check if a credential exists
     */
    static boolean has(String provider) {
        return get(provider) != null
    }

    /**
     * Clear all credentials
     */
    static void clear() {
        if (AUTH_PATH.exists()) {
            AUTH_PATH.delete()
        }
    }

    private static void ensureAuthDir() {
        def authDir = AUTH_PATH.parentFile
        if (!authDir.exists()) {
            authDir.mkdirs()
        }
    }
}
