package core

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * JSON-RPC 2.0 protocol handler for LSP communication over stdio.
 * Handles message framing with Content-Length headers per LSP spec.
 */
class JsonRpcHandler {
    private final Process process
    private final BufferedReader reader
    private final OutputStream outputStream
    private final Map<Integer, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>()
    private final AtomicInteger requestId = new AtomicInteger(0)
    private volatile boolean running = false
    private Thread messageLoopThread
    
    /** Callback for server notifications (e.g., publishDiagnostics) */
    Closure onNotification = { method, params -> }
    
    JsonRpcHandler(Process process) {
        this.process = process
        this.reader = new BufferedReader(new InputStreamReader(process.inputStream))
        this.outputStream = process.outputStream
    }
    
    /**
     * Send a JSON-RPC request and wait for response.
     * @param method The method name
     * @param params The parameters
     * @param timeoutMs Timeout in milliseconds
     * @return The result from the server
     */
    Object sendRequest(String method, Map params, long timeoutMs = 5000) {
        int id = requestId.incrementAndGet()
        def future = new CompletableFuture<Object>()
        pendingRequests.put(id, future)
        
        def message = [
            jsonrpc: "2.0",
            id: id,
            method: method,
            params: params
        ]
        
        sendMessage(message)
        
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (Exception e) {
            pendingRequests.remove(id)
            throw new RuntimeException("LSP request failed: ${method}", e)
        }
    }
    
    /**
     * Send a JSON-RPC notification (no response expected).
     * @param method The method name
     * @param params The parameters
     */
    void sendNotification(String method, Map params) {
        def message = [
            jsonrpc: "2.0",
            method: method,
            params: params
        ]
        sendMessage(message)
    }
    
    /**
     * Send a message with proper Content-Length header.
     */
    private synchronized void sendMessage(Map message) {
        def json = JsonOutput.toJson(message)
        def bytes = json.getBytes("UTF-8")
        def header = "Content-Length: ${bytes.length}\r\n\r\n"
        
        outputStream.write(header.getBytes("UTF-8"))
        outputStream.write(bytes)
        outputStream.flush()
    }
    
    /**
     * Start the message loop to handle incoming messages.
     */
    void startMessageLoop() {
        if (running) return
        running = true
        
        messageLoopThread = Thread.start("LSP-MessageLoop") {
            try {
                while (running && process.alive) {
                    def message = readMessage()
                    if (message != null) {
                        handleMessage(message)
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("LSP message loop error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Read a single LSP message (with Content-Length header).
     */
    private Map readMessage() {
        // Read headers
        int contentLength = -1
        String line
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:")) {
                contentLength = line.substring(15).trim().toInteger()
            }
        }
        
        if (contentLength <= 0) return null
        
        // Read content
        char[] buffer = new char[contentLength]
        int read = 0
        while (read < contentLength) {
            int n = reader.read(buffer, read, contentLength - read)
            if (n < 0) return null
            read += n
        }
        
        def json = new String(buffer)
        return new JsonSlurper().parseText(json) as Map
    }
    
    /**
     * Handle an incoming message (response or notification).
     */
    private void handleMessage(Map message) {
        if (message.containsKey("id") && message.containsKey("result")) {
            // Response to a request
            def id = message.id as Integer
            def future = pendingRequests.remove(id)
            if (future != null) {
                future.complete(message.result)
            }
        } else if (message.containsKey("id") && message.containsKey("error")) {
            // Error response
            def id = message.id as Integer
            def future = pendingRequests.remove(id)
            if (future != null) {
                future.completeExceptionally(
                    new RuntimeException("LSP error: ${message.error}")
                )
            }
        } else if (message.containsKey("method") && !message.containsKey("id")) {
            // Notification from server
            onNotification(message.method, message.params)
        }
    }
    
    /**
     * Stop the message loop and cleanup.
     */
    void stop() {
        running = false
        try {
            process.destroyForcibly()
        } catch (Exception ignored) {}
    }
    
    boolean isAlive() {
        return process.alive
    }
}
