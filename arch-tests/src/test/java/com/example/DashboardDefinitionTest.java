package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class DashboardDefinitionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void logsDashboardShouldIncludeATempoPanel() throws Exception {
    JsonNode dashboard = loadJson("grafana/dashboards/logs-dashboard.json");

    boolean hasTempoPanel =
        StreamSupport.stream(dashboard.path("panels").spliterator(), false)
            .anyMatch(
                panel ->
                    "tempo".equals(panel.path("datasource").path("type").asText())
                        && StreamSupport.stream(panel.path("targets").spliterator(), false)
                            .map(target -> target.path("query").asText())
                            .anyMatch(query -> !query.isBlank()));

    assertThat(hasTempoPanel).isTrue();
  }

  @Test
  void servicesOverviewShouldUseServiceNameDimension() throws Exception {
    JsonNode dashboard = loadJson("grafana/dashboards/services-overview.json");

    JsonNode serviceVariable =
        StreamSupport.stream(dashboard.path("templating").path("list").spliterator(), false)
            .filter(variable -> "service".equals(variable.path("name").asText()))
            .findFirst()
            .orElseThrow();

    assertThat(serviceVariable.path("query").asText()).contains("service_name");

    List<String> expressions = new ArrayList<>();
    StreamSupport.stream(dashboard.path("panels").spliterator(), false)
        .forEach(
            panel ->
                StreamSupport.stream(panel.path("targets").spliterator(), false)
                    .map(target -> target.path("expr").asText())
                    .filter(expr -> !expr.isBlank())
                    .forEach(expressions::add));

    assertThat(expressions).allMatch(expr -> expr.contains("service_name"));
    assertThat(expressions).noneMatch(expr -> expr.contains("job="));
  }

  @Test
  void servicesOverviewShouldAvoidHistogramQuantileLatencyQueries() throws Exception {
    JsonNode dashboard = loadJson("grafana/dashboards/services-overview.json");

    List<String> expressions = new ArrayList<>();
    StreamSupport.stream(dashboard.path("panels").spliterator(), false)
        .forEach(
            panel ->
                StreamSupport.stream(panel.path("targets").spliterator(), false)
                    .map(target -> target.path("expr").asText())
                    .filter(expr -> !expr.isBlank())
                    .forEach(expressions::add));

    assertThat(expressions).anyMatch(expr -> expr.contains("http_server_requests_milliseconds_sum"));
    assertThat(expressions).noneMatch(expr -> expr.contains("histogram_quantile"));
  }

  @Test
  void sharedConfigShouldNotClaimHttpRequestHistogramBuckets() throws Exception {
    var environment = new StandardEnvironment();
    var loader = new YamlPropertySourceLoader();
    var configPath =
        Path.of("..").resolve("shared/src/main/resources/application.yaml").normalize();

    for (var propertySource : loader.load("shared", new FileSystemResource(configPath))) {
      environment.getPropertySources().addLast(propertySource);
    }

    Map<String, Boolean> histogramConfig =
        Binder.get(environment)
            .bind(
                "management.metrics.distribution.percentiles-histogram",
                Bindable.mapOf(String.class, Boolean.class))
            .orElse(Map.of());

    assertThat(histogramConfig).doesNotContainKey("http.server.requests");
  }

  private JsonNode loadJson(String relativePath) throws Exception {
    Path path = Path.of("..").resolve(relativePath).normalize();
    return objectMapper.readTree(path.toFile());
  }
}
