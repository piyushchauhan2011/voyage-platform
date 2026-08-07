package com.voyage.app.rabbitmq;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "application.rabbitmq.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMqPlaygroundService {

  private static final Set<String> KNOWN_ROUTING_KEYS =
      Set.of(
          RabbitMqLabConfig.ROUTING_KEY_BOOKING_CONFIRM, RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND);

  private final RabbitTemplate rabbitTemplate;
  private final AmqpAdmin amqpAdmin;
  private final LabJobRecorder labJobRecorder;
  private final String exchangeName;
  private final String bookingConfirmQueue;
  private final String emailSendQueue;

  public RabbitMqPlaygroundService(
      RabbitTemplate rabbitTemplate,
      AmqpAdmin amqpAdmin,
      LabJobRecorder labJobRecorder,
      @Value("${application.rabbitmq.exchange}") String exchangeName,
      @Value("${application.rabbitmq.queues.booking-confirm}") String bookingConfirmQueue,
      @Value("${application.rabbitmq.queues.email-send}") String emailSendQueue) {
    this.rabbitTemplate = rabbitTemplate;
    this.amqpAdmin = amqpAdmin;
    this.labJobRecorder = labJobRecorder;
    this.exchangeName = exchangeName;
    this.bookingConfirmQueue = bookingConfirmQueue;
    this.emailSendQueue = emailSendQueue;
  }

  public SetupResult setup() {
    DirectExchange exchange = new DirectExchange(exchangeName, true, false);
    Queue bookingQueue = new Queue(bookingConfirmQueue, true);
    Queue emailQueue = new Queue(emailSendQueue, true);

    amqpAdmin.declareExchange(exchange);
    amqpAdmin.declareQueue(bookingQueue);
    amqpAdmin.declareQueue(emailQueue);
    amqpAdmin.declareBinding(
        BindingBuilder.bind(bookingQueue)
            .to(exchange)
            .with(RabbitMqLabConfig.ROUTING_KEY_BOOKING_CONFIRM));
    amqpAdmin.declareBinding(
        BindingBuilder.bind(emailQueue)
            .to(exchange)
            .with(RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND));

    return new SetupResult(
        exchangeName,
        "direct",
        List.of(
            new BindingView(bookingConfirmQueue, RabbitMqLabConfig.ROUTING_KEY_BOOKING_CONFIRM),
            new BindingView(emailSendQueue, RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND)),
        "Exchange receives publishes; the routing key selects which queue gets the message.",
        "Producer → Exchange → (routing key) → Queue → Consumer. Kafka has no exchange — producers write to a topic log.");
  }

  public TopologyResult topology() {
    List<QueueView> queues =
        List.of(
            queueView(bookingConfirmQueue, RabbitMqLabConfig.ROUTING_KEY_BOOKING_CONFIRM),
            queueView(emailSendQueue, RabbitMqLabConfig.ROUTING_KEY_EMAIL_SEND));
    return new TopologyResult(
        exchangeName,
        "direct",
        queues,
        "A direct exchange routes to queues whose binding key equals the message routing key.",
        "Active consumers often keep depths near zero — check /consumed to see deliveries.");
  }

  public PublishResult publish(String routingKey, String payload) {
    if (routingKey == null || routingKey.isBlank()) {
      throw new IllegalArgumentException("routingKey is required");
    }
    String trimmedKey = routingKey.trim();
    String body = payload == null || payload.isBlank() ? "lab-job" : payload.trim();
    boolean known = KNOWN_ROUTING_KEYS.contains(trimmedKey);

    LabJobMessage job =
        new LabJobMessage(UUID.randomUUID().toString(), trimmedKey, body, Instant.now());
    rabbitTemplate.convertAndSend(exchangeName, trimmedKey, job);

    String observation =
        known
            ? "Published to exchange '" + exchangeName + "' with routing key '" + trimmedKey + "'."
            : "Published with unbound routing key '"
                + trimmedKey
                + "'. RabbitMQ drops it — no queue is bound.";
    String tip =
        known
            ? "Poll GET /consumed to see the LabJobConsumer delivery (queue + routing key)."
            : "Try booking.confirm or email.send to hit a bound queue.";

    return new PublishResult(job.jobId(), exchangeName, trimmedKey, body, known, observation, tip);
  }

  public RoutingDemoResult routingDemo() {
    labJobRecorder.clear();

    PublishResult matched =
        publish(RabbitMqLabConfig.ROUTING_KEY_BOOKING_CONFIRM, "routing-demo-matched");
    PublishResult unmatched = publish("unknown.job", "routing-demo-unmatched");

    return new RoutingDemoResult(
        matched,
        unmatched,
        "Matching key booking.confirm binds to "
            + bookingConfirmQueue
            + ". Unbound key unknown.job is discarded by the broker.",
        "Kafka keeps every append in a topic log; RabbitMQ task queues only retain messages that route to a queue.");
  }

  public ConsumedResult consumed() {
    List<LabJobDelivery> deliveries = labJobRecorder.recentDeliveries();
    return new ConsumedResult(
        deliveries,
        "Each delivery shows the Consumer side: which queue handed the job and which routing key was used.",
        "Ack is automatic here (default listener). Failed handlers would nack/requeue depending on configuration.");
  }

  public PurgeResult purge() {
    amqpAdmin.purgeQueue(bookingConfirmQueue, false);
    amqpAdmin.purgeQueue(emailSendQueue, false);
    labJobRecorder.clear();
    return new PurgeResult(
        List.of(bookingConfirmQueue, emailSendQueue),
        "Lab queues purged and in-memory consumer recorder cleared.",
        "Use purge between demos so /consumed only shows the jobs you just published.");
  }

  public CompareResult compare() {
    List<CompareRow> rows =
        List.of(
            new CompareRow(
                "Model", "Event streaming / append-only log", "Task queues / background jobs"),
            new CompareRow(
                "Strength",
                "Large scale, analytics, long retention, replay",
                "Work distribution, per-message ack, flexible routing"),
            new CompareRow(
                "Voyage example",
                "hotel-events domain stream (/ui/kafka)",
                "voyage.jobs confirmation & email jobs (/ui/rabbitmq)"),
            new CompareRow(
                "Core routing idea", "Topic + partition key", "Exchange + routing key → queue"),
            new CompareRow(
                "Consumer view",
                "Consumer group reads offsets from the log",
                "Competing consumers pull/push from a queue"));
    return new CompareResult(
        rows,
        "Use Kafka when many consumers need the same history. Use RabbitMQ when workers should process discrete jobs once.",
        "Open /ui/kafka for the hotel event stream, then return here for task-queue routing.");
  }

  private QueueView queueView(String queueName, String routingKey) {
    QueueInformation info = amqpAdmin.getQueueInfo(queueName);
    long depth = info == null ? 0L : info.getMessageCount();
    return new QueueView(queueName, routingKey, depth);
  }

  public record BindingView(String queue, String routingKey) {}

  public record SetupResult(
      String exchange,
      String exchangeType,
      List<BindingView> bindings,
      String observation,
      String tip) {}

  public record QueueView(String name, String routingKey, long messageCount) {}

  public record TopologyResult(
      String exchange,
      String exchangeType,
      List<QueueView> queues,
      String observation,
      String tip) {}

  public record PublishResult(
      String jobId,
      String exchange,
      String routingKey,
      String payload,
      boolean routedToKnownBinding,
      String observation,
      String tip) {}

  public record RoutingDemoResult(
      PublishResult matchedPublish,
      PublishResult unmatchedPublish,
      String observation,
      String tip) {}

  public record ConsumedResult(List<LabJobDelivery> deliveries, String observation, String tip) {}

  public record PurgeResult(List<String> purgedQueues, String observation, String tip) {}

  public record CompareRow(String aspect, String kafka, String rabbitmq) {}

  public record CompareResult(List<CompareRow> rows, String observation, String tip) {}

  public record PublishRequest(String routingKey, String payload) {}
}
