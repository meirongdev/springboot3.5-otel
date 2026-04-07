package com.example.hello;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.Executor;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * Executor configuration for parallel downstream calls and {@code @Async} methods.
 *
 * <p>Wraps a virtual-thread executor with a {@link ContextPropagatingExecutor} that captures the
 * current Micrometer {@link ContextSnapshot} in the parent thread and restores it in each child
 * thread. This guarantees OTel context (traceId, spanId, baggage) propagates correctly — without
 * this, {@code CompletableFuture.supplyAsync(...)} or {@code @Async} methods would lose the tracing
 * context.
 *
 * <p><b>Note:</b> {@code ContextPropagatingTaskDecorator} (Spring Framework 6.2+) is not yet
 * available in Spring Boot 3.5.12, so we wrap the executor with Micrometer's {@link
 * ContextSnapshot} directly. Once Spring upgrades, this can be replaced with the built-in
 * decorator.
 */
@Configuration
public class ExecutorConfig implements AsyncConfigurer {

  @Bean(name = "taskExecutor")
  @Override
  public TaskExecutor getAsyncExecutor() {
    return new ContextPropagatingExecutor();
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
        LoggerFactory.getLogger(ExecutorConfig.class)
            .error("Unexpected async exception in [{}]", method.toGenericString(), ex);
  }

  /**
   * Wraps the default virtual-thread executor with Micrometer context propagation.
   *
   * <p>Captures {@link ContextSnapshot} (traceId, spanId, baggage, MDC) in the calling thread and
   * restores it in each virtual thread before executing the task.
   */
  private static class ContextPropagatingExecutor implements TaskExecutor {

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY =
        ContextSnapshotFactory.builder().build();

    private final Executor delegate =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void execute(Runnable task) {
      ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
      delegate.execute(
          () -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
              task.run();
            }
          });
    }
  }
}
