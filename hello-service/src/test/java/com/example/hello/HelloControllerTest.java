package com.example.hello;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = HelloController.class,
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = HttpClientConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class HelloControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HelloService helloService;

  @Test
  void shouldReturnHelloResponse() throws Exception {
    when(helloService.getHello(1L, "en"))
        .thenReturn(new HelloResponse(1L, "Alice", "Hello, World!", "en"));

    mockMvc
        .perform(get("/api/1").header("Accept-Language", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userName").value("Alice"))
        .andExpect(jsonPath("$.greeting").value("Hello, World!"));
  }

  @Test
  void shouldDefaultAcceptLanguageToEn() throws Exception {
    when(helloService.getHello(2L, "en"))
        .thenReturn(new HelloResponse(2L, "Bob", "Hello, World!", "en"));

    mockMvc
        .perform(get("/api/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("en"));
  }
}
