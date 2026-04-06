package com.example.hello;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/** Executor configuration for parallel downstream calls with OTel context propagation. */
@Configuration
public class ExecutorConfig {

  /**
   * Virtual thread executor for parallel downstream HTTP calls.
   *
   * <p>Spring Boot 3.5 automatically wraps this with {@code ApplicationTaskExecutor} which
   * propagates the Micrometer Observation context (trace/span/baggage) to child threads.
   *
   * <p>Using an explicit executor instead of the common ForkJoinPool ensures:
   *
   * <ul>
   *   <li>Virtual threads (not platform threads) for efficient I/O-bound work
   *   <li>OTel context propagation via Spring's task decorator
   *   <li>Clear separation of concerns for observability
   * </ul>
   */
  @Bean
  public TaskExecutor virtualTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
