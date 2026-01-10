package mcp

class McpConnectionRetry {

    private int maxRetries = 3
    private long initialDelay = 1000
    private long maxDelay = 30000
    private double multiplier = 2.0

    McpConnectionRetry() {
        loadConfig()
    }

    private void loadConfig() {
        try {
            core.Config config = core.Config.load()
            def mcpConfig = config.mcp
            if (mcpConfig?.connection) {
                maxRetries = mcpConfig.connection.max_retries ?: 3
                initialDelay = mcpConfig.connection.initial_delay ?: 1000
                maxDelay = mcpConfig.connection.max_delay ?: 30000
            }
        } catch (Exception e) {
            // Use defaults
        }
    }

    <T> T executeWithRetry(RetryableOperation<T> operation) {
        int attempt = 0
        long delay = initialDelay

        while (true) {
            try {
                return operation.execute()
            } catch (Exception e) {
                attempt++
                if (attempt >= maxRetries) {
                    throw new McpConnectionException("Failed after ${maxRetries} attempts: ${e.message}", e)
                }

                System.err.println("Attempt ${attempt} failed: ${e.message}. Retrying in ${delay}ms...")

                sleep(delay)

                delay = Math.min((long) (delay * multiplier), maxDelay)
            }
        }
    }

    void executeWithRetry(Runnable operation) {
        int attempt = 0
        long delay = initialDelay

        while (true) {
            try {
                operation.run()
                return
            } catch (Exception e) {
                attempt++
                if (attempt >= maxRetries) {
                    throw new McpConnectionException("Failed after ${maxRetries} attempts: ${e.message}", e)
                }

                System.err.println("Attempt ${attempt} failed: ${e.message}. Retrying in ${delay}ms...")

                sleep(delay)

                delay = Math.min((long) (delay * multiplier), maxDelay)
            }
        }
    }

    interface RetryableOperation<T> {
        T execute() throws Exception
    }

}

class McpConnectionException extends RuntimeException {
    McpConnectionException(String message, Throwable cause) {
        super(message, cause)
    }
}
