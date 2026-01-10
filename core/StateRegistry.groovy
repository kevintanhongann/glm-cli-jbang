package core

import java.util.concurrent.ConcurrentHashMap

@Singleton(strict = false)
class StateRegistry {
    private final Map<String, ReactiveState<?>> states = new ConcurrentHashMap<>()

    <T> ReactiveState<T> register(String name, T initialValue = null) {
        ReactiveState<T> state = new ReactiveState<>(name, initialValue)
        states.put(name, state)
        return state
    }

    <T> ReactiveState<T> get(String name) {
        return states.get(name) as ReactiveState<T>
    }

    <T> T getValue(String name) {
        ReactiveState<T> state = get(name)
        return state?.get()
    }

    <T> void setValue(String name, T value) {
        ReactiveState<T> state = get(name)
        if (state == null) {
            register(name, value)
        } else {
            state.set(value)
        }
    }

    void unregister(String name) {
        states.remove(name)
    }

    void clear() {
        states.clear()
    }
}
