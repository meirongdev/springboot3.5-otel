package com.example.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class GreetingServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private GreetingService greetingService;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    greetingService = new GreetingService(redisTemplate, ObservationRegistry.NOOP);
  }

  @Test
  void shouldReturnCachedGreetingOnCacheHit() {
    when(valueOps.get("greeting:en")).thenReturn("Hello, World!");

    Greeting result = greetingService.getGreeting("en");

    assertThat(result.language()).isEqualTo("en");
    assertThat(result.message()).isEqualTo("Hello, World!");
    // No SET call on cache hit
    verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void shouldFetchAndCacheGreetingOnCacheMiss() {
    when(valueOps.get("greeting:zh")).thenReturn(null);

    Greeting result = greetingService.getGreeting("zh");

    assertThat(result.language()).isEqualTo("zh");
    assertThat(result.message()).isEqualTo("你好，世界！");
    verify(valueOps).set(eq("greeting:zh"), eq("你好，世界！"), eq(Duration.ofMinutes(5)));
  }

  @Test
  void shouldReturnEnglishFallbackForUnknownLanguage() {
    when(valueOps.get("greeting:fr")).thenReturn(null);

    Greeting result = greetingService.getGreeting("fr");

    assertThat(result.language()).isEqualTo("en");
    assertThat(result.message()).isEqualTo("Hello, World!");
  }

  @Test
  void shouldReturnJapaneseGreetingOnCacheMiss() {
    when(valueOps.get("greeting:ja")).thenReturn(null);

    Greeting result = greetingService.getGreeting("ja");

    assertThat(result.language()).isEqualTo("ja");
    assertThat(result.message()).isEqualTo("こんにちは世界！");
    verify(valueOps).set(eq("greeting:ja"), eq("こんにちは世界！"), eq(Duration.ofMinutes(5)));
  }
}
