package com.code.review.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventManager {
    private static EventManager instance;
    private static final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

    private EventManager() {
    }

    public static synchronized EventManager getInstance() {
        if (instance == null) {
            instance = new EventManager();
        }
        return instance;
    }

    public void addListener(String eventName, Consumer<Object> listener) {
        listeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(listener);
    }

    public void emit(String eventName, Object eventData) {
        List<Consumer<Object>> eventListeners = listeners.get(eventName);
        if (eventListeners != null) {
            eventListeners.forEach(listener -> listener.accept(eventData));
        }
    }
}