package core

import java.util.ArrayList
import java.util.function.Consumer
import java.util.function.Function
import java.time.Instant

class ReactiveState<T> {
    private T value
    private final List<Consumer<StateChange<T>>> listeners = new ArrayList<>()
    private final String name

    ReactiveState(String name, T initialValue = null) {
        this.name = name
        this.value = initialValue
    }

    T get() {
        return value
    }

    void set(T newValue) {
        T oldValue = this.value
        if (oldValue == newValue && oldValue != null) {
            return
        }
        this.value = newValue
        notifyListeners(oldValue, newValue)
    }

    void update(Function<T, T> updater) {
        set(updater.apply(value))
    }

    void subscribe(Consumer<StateChange<T>> listener) {
        listeners.add(listener)
    }

    void unsubscribe(Consumer<StateChange<T>> listener) {
        listeners.remove(listener)
    }

    private void notifyListeners(T oldValue, T newValue) {
        StateChange<T> change = new StateChange<>(name, oldValue, newValue)
        listeners.each { it.accept(change) }
        EventBus.instance.publish(EventType.STATE_CHANGED, [name: name, change: change])
    }
}

class StateChange<T> {
    final String propertyName
    final T oldValue
    final T newValue
    final Instant timestamp

    StateChange(String propertyName, T oldValue, T newValue) {
        this.propertyName = propertyName
        this.oldValue = oldValue
        this.newValue = newValue
        this.timestamp = Instant.now()
    }
}
