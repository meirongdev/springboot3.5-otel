package com.example.greeting;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class GreetingServiceContractTest {

  @Autowired private WebApplicationContext context;

  // Mock Redis to avoid requiring a live Redis connection during contract tests.
  @MockitoBean private StringRedisTemplate redisTemplate;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setup() {
    // Simulate a cache miss so GreetingService falls through to the in-memory lookup.
    ValueOperations<String, String> mockOps = org.mockito.Mockito.mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(mockOps);
    when(mockOps.get(anyString())).thenReturn(null);

    RestAssuredMockMvc.webAppContextSetup(context);
  }
}
