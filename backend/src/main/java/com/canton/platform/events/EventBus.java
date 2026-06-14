package com.canton.platform.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Lightweight in-memory publish/subscribe event bus simulating a Kafka-style
 * message broker. Producers (services) publish {@link DomainEvent}s to a
 * topic; consumers (handlers in {@code events.handlers}) subscribe at
 * startup. Dispatch happens asynchronously on a dedicated thread pool so
 * publishing never blocks the originating REST request -- mirroring an
 * "event-driven architecture where transaction events trigger downstream
 * processes".
 */
@Component
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<String, List<Consumer<DomainEvent>>> subscribers = new ConcurrentHashMap<>();
    private final List<DomainEvent> history = new CopyOnWriteArrayList<>();
    private final TaskExecutor executor;

    public EventBus(@Qualifier("eventDispatchExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void subscribe(String topic, Consumer<DomainEvent> consumer) {
        subscribers.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(consumer);
        log.info("Subscribed handler to topic '{}'", topic);
    }

    public void publish(DomainEvent event) {
        history.add(event);
        log.info("Publishing event to topic '{}': {}", event.topic(), event);
        List<Consumer<DomainEvent>> consumers = subscribers.getOrDefault(event.topic(), List.of());
        for (Consumer<DomainEvent> consumer : consumers) {
            executor.execute(() -> {
                try {
                    consumer.accept(event);
                } catch (Exception ex) {
                    log.error("Event handler failed for topic '{}': {}", event.topic(), ex.getMessage(), ex);
                }
            });
        }
    }

    /** Recent event history (used by the settlement-monitor UI). */
    public List<DomainEvent> recentHistory(int limit) {
        int size = history.size();
        int from = Math.max(0, size - limit);
        List<DomainEvent> slice = new java.util.ArrayList<>(history.subList(from, size));
        java.util.Collections.reverse(slice);
        return slice;
    }
}
