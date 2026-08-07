package com.voyage.app.rabbitmq;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prefers Testcontainers RabbitMQ. If the Docker API is unavailable (common with some Docker
 * Desktop socket setups), falls back to a broker on localhost:5672 (e.g. {@code docker compose up
 * -d rabbitmq}).
 *
 * <p>Uses a unique exchange/queue prefix so a running {@code spring-boot:run} app consuming {@code
 * voyage.jobs.*} cannot steal test messages.
 */
abstract class RabbitMqIntegrationTestSupport {

  private static final String LAB_PREFIX = "voyage.jobs.test." + System.nanoTime();
  private static final boolean DOCKER_AVAILABLE = isDockerAvailable();
  private static final GenericContainer<?> RABBITMQ =
      DOCKER_AVAILABLE
          ? new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management"))
              .withExposedPorts(5672)
          : null;

  static {
    if (RABBITMQ != null) {
      RABBITMQ.start();
    }
  }

  @BeforeAll
  static void requireBroker() {
    Assumptions.assumeTrue(
        DOCKER_AVAILABLE || isLocalBrokerReachable(),
        "RabbitMQ integration tests need Testcontainers Docker or localhost:5672");
  }

  @DynamicPropertySource
  static void rabbitMqProperties(DynamicPropertyRegistry registry) {
    registry.add("application.rabbitmq.enabled", () -> true);
    if (DOCKER_AVAILABLE) {
      registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
      registry.add("spring.rabbitmq.port", RABBITMQ::getFirstMappedPort);
    } else {
      registry.add("spring.rabbitmq.host", () -> "localhost");
      registry.add("spring.rabbitmq.port", () -> 5672);
    }
    registry.add("spring.rabbitmq.username", () -> "guest");
    registry.add("spring.rabbitmq.password", () -> "guest");
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> true);
    registry.add("application.rabbitmq.exchange", () -> LAB_PREFIX);
    registry.add(
        "application.rabbitmq.queues.booking-confirm", () -> LAB_PREFIX + ".booking.confirm");
    registry.add("application.rabbitmq.queues.email-send", () -> LAB_PREFIX + ".email.send");
    registry.add("application.kafka.enabled", () -> false);
    registry.add("application.redis.enabled", () -> false);
  }

  private static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean isLocalBrokerReachable() {
    try (java.net.Socket socket = new java.net.Socket()) {
      socket.connect(new java.net.InetSocketAddress("localhost", 5672), 500);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
