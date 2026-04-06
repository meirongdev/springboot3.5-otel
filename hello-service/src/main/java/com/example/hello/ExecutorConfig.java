package com.example.hello;

import java.util.concurrent.Executors;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/** Executor configuration for parallel downstream calls with OTel context propagation. */
@Configuration
public class ExecutorConfig implements AsyncConfigurer {

  /**
   * Virtual thread executor for parallel downstream HTTP calls and {@code @Async} methods.
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
  @Bean(name = "taskExecutor")
  public TaskExecutor taskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public TaskExecutor getAsyncExecutor() {
    return taskExecutor();
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
        org.slf4j.LoggerFactory.getLogger(ExecutorConfig.class)
            .error(
                "Unexpected async exception in @Async method [{}]", method.toGenericString(), ex);
  }
}
