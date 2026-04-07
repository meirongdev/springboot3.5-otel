package com.example.greeting;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Greeting service with Redis cache layer.
 *
 * <p>Demonstrates two custom instrumentation patterns from the blog post:
 *
 * <ul>
 *   <li>§四 场景①: {@code @Observed} on the public entry point creates a span automatically.
 *   <li>§四 场景②: {@link Observation#createNotStarted} wraps the cache-miss lookup path, giving
 *       precise control over the child span's boundary.
 * </ul>
 *
 * <p>Redis spans are generated automatically by the Lettuce driver ({@code db.system=redis}) for
 * every {@code GET} and {@code SET} call, demonstrating §五 Redis tracing.
 */
@Service
public class GreetingService {

  private static final Logger log = LoggerFactory.getLogger(GreetingService.class);

  private static final Map<String, Greeting> GREETINGS =
      Map.of(
          "zh", new Greeting("zh", "你好，世界！"),
          "ja", new Greeting("ja", "こんにちは世界！"),
          "en", new Greeting("en", "Hello, World!"));

  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final StringRedisTemplate redis;
  private final ObservationRegistry registry;

  public GreetingService(StringRedisTemplate redis, ObservationRegistry registry) {
    this.redis = redis;
    this.registry = registry;
  }

  /**
   * Returns a greeting for the given language.
   *
   * <p>§四 场景①: {@code @Observed} creates a parent span {@code greeting.resolve} that wraps the full
   * method, including both the Redis cache check and the optional fallback lookup.
   */
  @Observed(
      name = "greeting.resolve",
      contextualName = "resolveGreeting",
      lowCardinalityKeyValues = {"cache", "redis"})
  public Greeting getGreeting(String language) {
    String cacheKey = "greeting:" + language;

    // Redis GET — Lettuce auto-generates a db.redis span for this call (§五 Redis)
    String cachedMessage = redis.opsForValue().get(cacheKey);
    if (cachedMessage != null) {
      log.debug("Cache hit for language={}", language);
      return new Greeting(language, cachedMessage);
    }

    // Cache miss: §四 场景② — manual Observation gives precise control over the lookup span.
    // The resulting span is a child of the @Observed parent span above.
    return resolveFromSource(language, cacheKey);
  }

  /**
   * §四 场景②: Manual {@link Observation#createNotStarted} wraps a cache-miss lookup.
   *
   * <p>Use this pattern when you need to control span boundaries explicitly — for example, when the
   * operation spans multiple method calls or when {@code @Observed} is too coarse-grained.
   */
  private Greeting resolveFromSource(String language, String cacheKey) {
    Observation lookup =
        Observation.createNotStarted("greeting.lookup", registry)
            .lowCardinalityKeyValue("language", language)
            .start();

    try (Observation.Scope scope = lookup.openScope()) {
      Greeting greeting = GREETINGS.getOrDefault(language, GREETINGS.get("en"));

      // Redis SET — Lettuce auto-generates a db.redis span for this call (§五 Redis)
      redis.opsForValue().set(cacheKey, greeting.message(), CACHE_TTL);

      log.debug("Cache miss — stored greeting for language={}", language);
      return greeting;
    } catch (Exception e) {
      lookup.error(e);
      throw e;
    } finally {
      lookup.stop();
    }
  }
}
