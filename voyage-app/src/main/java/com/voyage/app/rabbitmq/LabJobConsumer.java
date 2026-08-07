package com.voyage.app.rabbitmq;

import java.time.Instant;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "application.rabbitmq.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LabJobConsumer {

  private final LabJobRecorder labJobRecorder;

  public LabJobConsumer(LabJobRecorder labJobRecorder) {
    this.labJobRecorder = labJobRecorder;
  }

  @RabbitListener(queues = "${application.rabbitmq.queues.booking-confirm}")
  public void onBookingConfirm(
      LabJobMessage job,
      @Header(name = AmqpHeaders.RECEIVED_EXCHANGE, required = false) String exchange,
      @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey,
      @Header(name = AmqpHeaders.CONSUMER_QUEUE, required = false) String queue) {
    record(job, exchange, queue, routingKey);
  }

  @RabbitListener(queues = "${application.rabbitmq.queues.email-send}")
  public void onEmailSend(
      LabJobMessage job,
      @Header(name = AmqpHeaders.RECEIVED_EXCHANGE, required = false) String exchange,
      @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey,
      @Header(name = AmqpHeaders.CONSUMER_QUEUE, required = false) String queue) {
    record(job, exchange, queue, routingKey);
  }

  private void record(LabJobMessage job, String exchange, String queue, String routingKey) {
    labJobRecorder.record(
        new LabJobDelivery(
            job.jobId(),
            job.type(),
            job.payload(),
            exchange == null ? "" : exchange,
            queue == null ? "" : queue,
            routingKey == null ? "" : routingKey,
            Instant.now()));
  }
}
