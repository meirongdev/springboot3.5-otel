package com.example.shared.http;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for shared HTTP components.
 *
 * <p>This configuration registers HTTP filters and utilities that should be available in all
 * services, such as the {@link RequestCompletionLoggingFilter} for request/response logging and the
 * {@link BaggageContextFilter} for propagating business context via OTel Baggage.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SharedHttpAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(RequestCompletionLoggingFilter.class)
  public RequestCompletionLoggingFilter requestCompletionLoggingFilter() {
    return new RequestCompletionLoggingFilter();
  }

  @Bean
  @ConditionalOnMissingBean(BaggageContextFilter.class)
  @ConditionalOnBean(Tracer.class)
  public BaggageContextFilter baggageContextFilter(Tracer tracer) {
    return new BaggageContextFilter(tracer);
  }
}
