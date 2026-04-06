package com.example.hello;

import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * Executor configuration for parallel downstream calls and {@code @Async} methods.
 *
 * <p>Annotating {@code getAsyncExecutor()} with {@code @Bean} ensures Spring's CGLIB proxy returns
 * the same singleton — decorated with {@code ContextPropagatingTaskDecorator} — both to
 * {@code @Async} infrastructure and to callers that inject {@code TaskExecutor} directly. This
 * guarantees OTel context (traceId, spanId, baggage) propagates to child threads.
 */
@Configuration
public class ExecutorConfig implements AsyncConfigurer {

  @Bean(name = "taskExecutor")
  @Override
  public TaskExecutor getAsyncExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
        LoggerFactory.getLogger(ExecutorConfig.class)
            .error("Unexpected async exception in [{}]", method.toGenericString(), ex);
  }
}
