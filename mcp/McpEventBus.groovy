package mcp

import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

class McpEventBus {

    private static final Sinks.Many<Object> sink = Sinks.many().multicast().directBestEffort()

    static void publish(Object event) {
        sink.tryEmitNext(event)
    }

    static Flux<Object> getEvents() {
        return sink.asFlux()
    }

    static Flux<McpConnectionEvent> getConnectionEvents() {
        return sink.asFlux()
            .filter { it instanceof McpConnectionEvent }
            .map { it as McpConnectionEvent }
    }

    static Flux<McpStatusChangeEvent> getStatusChangeEvents() {
        return sink.asFlux()
            .filter { it instanceof McpStatusChangeEvent }
            .map { it as McpStatusChangeEvent }
    }

}
