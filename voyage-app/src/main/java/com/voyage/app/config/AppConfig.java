package com.voyage.app.config;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Demonstrates explicit @Bean registration.
 *
 * <p>Spring scans @Configuration classes at startup, calls each @Bean method once, and stores the
 * returned object in the ApplicationContext. Any other bean that declares Clock as a dependency
 * will receive the same singleton instance.
 *
 * <p>Compare this to @Component-based registration: @Configuration + @Bean → you control
 * construction (useful when wiring third-party objects) @Component → Spring constructs the object
 * for you
 */
@Configuration
@EnableRetry
public class AppConfig {

  @Bean
  public Clock applicationClock() {
    return Clock.systemUTC();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // BCrypt applies an adaptive cost factor — slows brute-force as hardware improves
    return new BCryptPasswordEncoder();
  }

  @Bean
  @ConditionalOnProperty(
      name = "application.redis.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CacheManager cacheManager(
      RedisConnectionFactory redisConnectionFactory,
      @Value("${application.redis.cache.ttl.hotel-by-id:PT10M}") Duration hotelByIdTtl,
      @Value("${application.redis.cache.ttl.hotels-by-city:PT5M}") Duration hotelsByCityTtl) {
    GenericJacksonJsonRedisSerializer serializer =
        GenericJacksonJsonRedisSerializer.create(
            builder ->
                builder.enableDefaultTyping(
                    BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.voyage.app.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        .build()));

    RedisCacheConfiguration defaults =
        RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    cacheConfigurations.put("hotelById", defaults.entryTtl(hotelByIdTtl));
    cacheConfigurations.put("hotelsByCity", defaults.entryTtl(hotelsByCityTtl));

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaults)
        .withInitialCacheConfigurations(cacheConfigurations)
        .transactionAware()
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "application.redis.enabled", havingValue = "false")
  public CacheManager inMemoryCacheManager() {
    return new ConcurrentMapCacheManager("hotelById", "hotelsByCity");
  }

  @Bean
  @ConditionalOnProperty(
      name = "application.redis.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory redisConnectionFactory,
      MessageListener redisPubSubRecorder,
      @Value("${application.redis.pubsub.channel:voyage:notifications}")
          String redisPubSubChannel) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redisConnectionFactory);
    container.addMessageListener(redisPubSubRecorder, new ChannelTopic(redisPubSubChannel));
    return container;
  }

  @Bean
  @ConditionalOnProperty(
      name = "application.kafka.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ProducerFactory<String, String> hotelEventProducerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  @Bean
  @ConditionalOnProperty(
      name = "application.kafka.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public KafkaTemplate<String, String> hotelEventKafkaTemplate(
      ProducerFactory<String, String> hotelEventProducerFactory) {
    return new KafkaTemplate<>(hotelEventProducerFactory);
  }

  @Bean
  @ConditionalOnProperty(
      name = "application.kafka.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ConsumerFactory<String, String> hotelEventConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.consumer.group-id}") String consumerGroupId,
      @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(properties);
  }

  @Bean
  @ConditionalOnProperty(
      name = "application.kafka.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> hotelEventConsumerFactory,
      KafkaTemplate<String, String> hotelEventKafkaTemplate,
      @Value("${application.kafka.topic.hotel-events-dlt}") String hotelEventsDeadLetterTopic,
      @Value("${application.kafka.retry.attempts:3}") long retryAttempts,
      @Value("${application.kafka.retry.backoff-ms:400}") long retryBackoffMs,
      @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(hotelEventConsumerFactory);
    factory.setAutoStartup(autoStartup);

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            hotelEventKafkaTemplate,
            (ConsumerRecord<?, ?> record, Exception exception) ->
                new TopicPartition(hotelEventsDeadLetterTopic, record.partition()));

    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            recoverer, new FixedBackOff(retryBackoffMs, Math.max(0, retryAttempts - 1)));
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}
