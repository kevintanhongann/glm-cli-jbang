package mcp

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpExchange
import java.util.concurrent.CountDownLatch
import java.net.InetSocketAddress

class OAuthCallbackServer {

    private int port = 19876
    private HttpServer server
    private CountDownLatch latch
    private String authCode
    private String state
    private String error

    OAuthCallbackServer(int port = 19876) {
        this.port = port
    }

    String waitForAuthCode(int timeoutSeconds = 60) {
        latch = new CountDownLatch(1)

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0)
            server.createContext("/callback", new CallbackHandler())
            server.createContext("/health", new HealthHandler())
            server.setExecutor(null)
            server.start()

            println "OAuth callback server started on port ${port}"

            if (!latch.await(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                error = "Timeout waiting for OAuth callback"
            }
        } catch (Exception e) {
            error = "Failed to start callback server: ${e.message}"
        }

        stop()
        return authCode
    }

    String getError() {
        return error
    }

    String getState() {
        return state
    }

    void stop() {
        if (server != null) {
            server.stop(0)
            server = null
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> params = parseQuery(exchange.getRequestURI().toString())

                authCode = params.get("code")
                state = params.get("state")
                String errorParam = params.get("error")
                String errorDescription = params.get("error_description")

                if (errorParam) {
                    error = "${errorParam}: ${errorDescription ?: 'No description'}"
                }

                String response
                if (authCode) {
                    response = """
                        <html>
                        <head><title>Authentication Successful</title></head>
                        <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                            <h1 style="color: green;">✓ Authentication Successful</h1>
                            <p>You can close this window and return to the terminal.</p>
                        </body>
                        </html>
                    """
                    exchange.sendResponseHeaders(200, response.length())
                } else {
                    response = """
                        <html>
                        <head><title>Authentication Failed</title></head>
                        <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                            <h1 style="color: red;">✗ Authentication Failed</h1>
                            <p>${error ?: 'Unknown error'}</p>
                        </body>
                        </html>
                    """
                    exchange.sendResponseHeaders(200, response.length())
                }

                OutputStream os = exchange.getResponseBody()
                os.write(response.getBytes())
                os.close()
            } catch (Exception e) {
                error = "Error handling callback: ${e.message}"
            } finally {
                latch.countDown()
            }
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        void handle(HttpExchange exchange) throws IOException {
            String response = "OK"
            exchange.sendResponseHeaders(200, response.length())
            OutputStream os = exchange.getResponseBody()
            os.write(response.getBytes())
            os.close()
        }
    }

    private static Map<String, String> parseQuery(String uri) {
        Map<String, String> params = new HashMap<>()
        if (uri.contains("?")) {
            String query = uri.substring(uri.indexOf("?") + 1)
            query.split("&").each { param ->
                def parts = param.split("=", 2)
                if (parts.length == 2) {
                    params.put(URLDecoder.decode(parts[0], "UTF-8"),
                              URLDecoder.decode(parts[1], "UTF-8"))
                }
            }
        }
        return params
    }

}
