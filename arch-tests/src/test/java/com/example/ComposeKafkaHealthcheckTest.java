package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ComposeKafkaHealthcheckTest {

  @Test
  void kafkaHealthcheckShouldVerifyBrokerReadiness() throws Exception {
    Map<String, Object> compose = loadCompose();
    Map<String, Object> services = map(compose.get("services"), "services");
    Map<String, Object> kafka = map(services.get("kafka"), "services.kafka");
    Map<String, Object> healthcheck = map(kafka.get("healthcheck"), "services.kafka.healthcheck");

    List<String> healthcheckCommand =
        listOfStrings(healthcheck.get("test"), "services.kafka.healthcheck.test");
    String joinedCommand = String.join(" ", healthcheckCommand);

    assertThat(joinedCommand)
        .contains("kafka-broker-api-versions.sh")
        .contains("--bootstrap-server localhost:9092")
        .doesNotContain("nc -z");
  }

  /**
   * Local bootRun services connect to localhost:9092 (Docker port-mapped). If Kafka only advertises
   * kafka:9092, clients are redirected to the Docker-internal hostname which local processes cannot
   * resolve → UnknownHostException. An EXTERNAL listener advertised as localhost:<port> prevents
   * this.
   */
  @Test
  void kafkaAdvertisedListenersShouldIncludeLocalhostForLocalDevelopment() throws Exception {
    Map<String, Object> compose = loadCompose();
    Map<String, Object> services = map(compose.get("services"), "services");
    Map<String, Object> kafka = map(services.get("kafka"), "services.kafka");
    Map<String, Object> environment = map(kafka.get("environment"), "services.kafka.environment");

    String advertisedListeners = String.valueOf(environment.get("KAFKA_ADVERTISED_LISTENERS"));

    assertThat(advertisedListeners)
        .as(
            "KAFKA_ADVERTISED_LISTENERS must include a localhost entry so that local bootRun "
                + "services can connect without UnknownHostException for 'kafka' hostname")
        .contains("localhost:");
  }

  /**
   * The external listener port must be mapped to the host so that local bootRun services can reach
   * it.
   */
  @Test
  void kafkaExternalPortShouldBeMappedForLocalDevelopment() throws Exception {
    Map<String, Object> compose = loadCompose();
    Map<String, Object> services = map(compose.get("services"), "services");
    Map<String, Object> kafka = map(services.get("kafka"), "services.kafka");
    List<String> ports = listOfStrings(kafka.get("ports"), "services.kafka.ports");

    Map<String, Object> environment = map(kafka.get("environment"), "services.kafka.environment");
    String advertisedListeners = String.valueOf(environment.get("KAFKA_ADVERTISED_LISTENERS"));

    // Extract the localhost port from the advertised listeners (e.g., "localhost:29092")
    String localhostEntry =
        java.util.Arrays.stream(advertisedListeners.split(","))
            .filter(l -> l.contains("localhost:"))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("No localhost entry in KAFKA_ADVERTISED_LISTENERS"));
    String externalPort = localhostEntry.substring(localhostEntry.lastIndexOf(':') + 1);

    assertThat(ports)
        .as("Port %s (the external localhost listener) must be mapped to the host", externalPort)
        .anyMatch(p -> p.contains(externalPort + ":" + externalPort));
  }

  /**
   * The shared Kafka config default bootstrap-servers must use the external localhost listener so
   * that local bootRun services do not attempt to resolve the Docker-internal 'kafka' hostname.
   */
  @Test
  void sharedKafkaConfigDefaultBootstrapShouldUseLocalhostNotDockerInternalHostname()
      throws Exception {
    Path kafkaConfigPath =
        Path.of("..").resolve("shared/src/main/resources/application-kafka.yaml").normalize();
    Map<String, Object> config = new Yaml().load(Files.readString(kafkaConfigPath));

    Map<String, Object> spring = map(config.get("spring"), "spring");
    Map<String, Object> kafka = map(spring.get("kafka"), "spring.kafka");
    String bootstrapServers = String.valueOf(kafka.get("bootstrap-servers"));

    // Extract default from Spring placeholder ${VAR:default}
    assertThat(bootstrapServers)
        .as("bootstrap-servers must be a Spring placeholder with a default value")
        .startsWith("${")
        .contains(":");

    int colonIdx = bootstrapServers.indexOf(':');
    String defaultValue = bootstrapServers.substring(colonIdx + 1, bootstrapServers.length() - 1);

    assertThat(defaultValue)
        .as(
            "Default bootstrap-servers '%s' must resolve to localhost, not the Docker-internal "
                + "'kafka' hostname which causes UnknownHostException in local bootRun",
            defaultValue)
        .startsWith("localhost:")
        .doesNotStartWith("kafka:");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadCompose() throws Exception {
    Path composePath = Path.of("..").resolve("compose.yaml").normalize();
    return new Yaml().load(Files.readString(composePath));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value, String path) {
    assertThat(value).as(path).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  private List<String> listOfStrings(Object value, String path) {
    assertThat(value).as(path).isInstanceOf(List.class);
    return ((List<?>) value).stream().map(String::valueOf).toList();
  }
}
