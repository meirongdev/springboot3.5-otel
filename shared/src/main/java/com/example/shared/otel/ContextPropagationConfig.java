package com.example.shared.otel;

import io.micrometer.context.ContextSnapshot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Context propagation configuration for async tasks. */
@Configuration
public class ContextPropagationConfig {

  @Bean
  public TaskDecorator contextPropagatingTaskDecorator() {
    return runnable -> {
      ContextSnapshot contextSnapshot = ContextSnapshot.captureAll();
      return () -> {
        try (var scope = contextSnapshot.setThreadLocals()) {
          runnable.run();
        }
      };
    };
  }

  @Bean
  public ThreadPoolTaskExecutor taskExecutor(TaskDecorator contextPropagatingTaskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setTaskDecorator(contextPropagatingTaskDecorator);
    executor.setThreadNamePrefix("async-");
    executor.initialize();
    return executor;
  }
}
