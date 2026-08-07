package com.voyage.app.redis;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "application.redis.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RedisPubSubRecorder implements MessageListener {

  private static final int MAX_MESSAGES = 20;

  private final Deque<RedisPubSubMessageView> recentMessages = new ConcurrentLinkedDeque<>();

  @Override
  public void onMessage(Message message, byte[] pattern) {
    recentMessages.addFirst(
        new RedisPubSubMessageView(
            new String(message.getChannel(), StandardCharsets.UTF_8),
            new String(message.getBody(), StandardCharsets.UTF_8),
            Instant.now()));

    while (recentMessages.size() > MAX_MESSAGES) {
      recentMessages.removeLast();
    }
  }

  public List<RedisPubSubMessageView> recentMessages() {
    return new ArrayList<>(recentMessages);
  }
}

record RedisPubSubMessageView(String channel, String payload, Instant receivedAt) {}
