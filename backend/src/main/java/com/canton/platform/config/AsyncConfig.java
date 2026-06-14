package com.canton.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool used by the in-memory event bus (see {@code events.EventBus})
 * to dispatch domain events asynchronously, simulating consumer groups
 * reading from a Kafka topic.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "eventDispatchExecutor")
    public TaskExecutor eventDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("event-bus-");
        executor.initialize();
        return executor;
    }
}
