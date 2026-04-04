package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class OpenTelemetryResourceAttributesBindingTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("applicationConfigs")
  void shouldBindResourceAttributeKeysWithDots(String module, Path configPath) throws Exception {
    var environment = new StandardEnvironment();
    var loader = new YamlPropertySourceLoader();

    // Load shared OTel config first (imported by all services via spring.config.import)
    var sharedOtelPath = Path.of("..").resolve("shared/src/main/resources/application-otel.yaml");
    for (var propertySource : loader.load("shared-otel", new FileSystemResource(sharedOtelPath))) {
      environment.getPropertySources().addLast(propertySource);
    }

    // Load service-specific config
    var resolvedConfigPath = Path.of("..").resolve(configPath).normalize();
    for (var propertySource : loader.load(module, new FileSystemResource(resolvedConfigPath))) {
      environment.getPropertySources().addLast(propertySource);
    }

    Map<String, String> resourceAttributes =
        Binder.get(environment)
            .bind(
                "management.opentelemetry.resource-attributes",
                Bindable.mapOf(String.class, String.class))
            .orElse(Map.of());

    assertThat(resourceAttributes.keySet())
        .containsExactlyInAnyOrder(
            "service.name", "service.namespace", "service.version", "deployment.environment");
  }

  static Stream<Arguments> applicationConfigs() {
    return Stream.of(
        Arguments.of("shared", Path.of("shared/src/main/resources/application.yaml")),
        Arguments.of("hello-service", Path.of("hello-service/src/main/resources/application.yaml")),
        Arguments.of("user-service", Path.of("user-service/src/main/resources/application.yaml")),
        Arguments.of(
            "greeting-service", Path.of("greeting-service/src/main/resources/application.yaml")));
  }
}
