package com.voyage.app.redis;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public abstract class RedisIntegrationTestSupport {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("application.redis.enabled", () -> true);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    registry.add("spring.data.redis.repositories.enabled", () -> false);
    registry.add("application.kafka.enabled", () -> false);
  }

  @BeforeEach
  void flushRedis() throws Exception {
    REDIS.execInContainer("redis-cli", "FLUSHDB");
  }
}
