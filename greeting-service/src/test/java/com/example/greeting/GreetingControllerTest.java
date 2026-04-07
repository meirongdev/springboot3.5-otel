package com.example.greeting;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GreetingController.class)
@AutoConfigureMockMvc(addFilters = false)
class GreetingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GreetingService greetingService;

  @Test
  void shouldReturnEnglishGreeting() throws Exception {
    when(greetingService.getGreeting("en")).thenReturn(new Greeting("en", "Hello, World!"));

    mockMvc
        .perform(get("/api/greetings").header("Accept-Language", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("en"))
        .andExpect(jsonPath("$.message").value("Hello, World!"));
  }

  @Test
  void shouldReturnChineseGreeting() throws Exception {
    when(greetingService.getGreeting("zh")).thenReturn(new Greeting("zh", "你好，世界！"));

    mockMvc
        .perform(get("/api/greetings").header("Accept-Language", "zh"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("zh"))
        .andExpect(jsonPath("$.message").value("你好，世界！"));
  }

  @Test
  void shouldReturnJapaneseGreeting() throws Exception {
    when(greetingService.getGreeting("ja")).thenReturn(new Greeting("ja", "こんにちは世界！"));

    mockMvc
        .perform(get("/api/greetings").header("Accept-Language", "ja"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("ja"))
        .andExpect(jsonPath("$.message").value("こんにちは世界！"));
  }

  @Test
  void shouldHonorWeightedAcceptLanguageHeader() throws Exception {
    when(greetingService.getGreeting("zh")).thenReturn(new Greeting("zh", "你好，世界！"));

    mockMvc
        .perform(get("/api/greetings").header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("zh"))
        .andExpect(jsonPath("$.message").value("你好，世界！"));
  }

  @Test
  void shouldDefaultToEnglish() throws Exception {
    when(greetingService.getGreeting("en")).thenReturn(new Greeting("en", "Hello, World!"));

    mockMvc
        .perform(get("/api/greetings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("en"));
  }
}
