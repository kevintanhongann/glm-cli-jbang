package core

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

enum EventType {
    STATE_CHANGED,
    TOOL_EXECUTION_STARTED,
    TOOL_EXECUTION_COMPLETED,
    TOOL_EXECUTION_ERROR,
    AGENT_STEP_STARTED,
    AGENT_STEP_COMPLETED,
    AGENT_LOOP_COMPLETED,
    SESSION_CREATED,
    SESSION_UPDATED,
    MESSAGE_ADDED,
    ERROR_OCCURRED,
    PROGRESS_UPDATE
}

class Event {
    final EventType type
    final Map<String, Object> data
    final Instant timestamp

    Event(EventType type, Map<String, Object> data = [:]) {
        this.type = type
        this.data = data
        this.timestamp = Instant.now()
    }
}

@Singleton(strict = false)
class EventBus {
    private final Map<EventType, CopyOnWriteArrayList<Consumer<Event>>> listeners =
        new ConcurrentHashMap<>()

    void subscribe(EventType type, Consumer<Event> handler) {
        listeners.computeIfAbsent(type, { new CopyOnWriteArrayList<>() })
                 .add(handler)
    }

    void unsubscribe(EventType type, Consumer<Event> handler) {
        listeners.getOrDefault(type, new CopyOnWriteArrayList<>())
                 .remove(handler)
    }

    void publish(Event event) {
        listeners.getOrDefault(event.type, new CopyOnWriteArrayList<>())
                 .each { it.accept(event) }
    }

    void publish(EventType type, Map<String, Object> data = [:]) {
        publish(new Event(type, data))
    }
}
