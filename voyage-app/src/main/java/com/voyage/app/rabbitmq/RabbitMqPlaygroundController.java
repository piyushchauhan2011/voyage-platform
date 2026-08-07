package com.voyage.app.rabbitmq;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rabbitmq/playground")
@ConditionalOnProperty(
    name = "application.rabbitmq.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMqPlaygroundController {

  private final RabbitMqPlaygroundService rabbitMqPlaygroundService;

  public RabbitMqPlaygroundController(RabbitMqPlaygroundService rabbitMqPlaygroundService) {
    this.rabbitMqPlaygroundService = rabbitMqPlaygroundService;
  }

  @PostMapping("/setup")
  public RabbitMqPlaygroundService.SetupResult setup() {
    return rabbitMqPlaygroundService.setup();
  }

  @GetMapping("/topology")
  public RabbitMqPlaygroundService.TopologyResult topology() {
    return rabbitMqPlaygroundService.topology();
  }

  @PostMapping("/publish")
  public RabbitMqPlaygroundService.PublishResult publish(
      @RequestBody(required = false) RabbitMqPlaygroundService.PublishRequest request) {
    String routingKey = request == null ? null : request.routingKey();
    String payload = request == null ? null : request.payload();
    return rabbitMqPlaygroundService.publish(routingKey, payload);
  }

  @PostMapping("/routing-demo")
  public RabbitMqPlaygroundService.RoutingDemoResult routingDemo() {
    return rabbitMqPlaygroundService.routingDemo();
  }

  @GetMapping("/consumed")
  public RabbitMqPlaygroundService.ConsumedResult consumed() {
    return rabbitMqPlaygroundService.consumed();
  }

  @PostMapping("/purge")
  public RabbitMqPlaygroundService.PurgeResult purge() {
    return rabbitMqPlaygroundService.purge();
  }

  @GetMapping("/compare")
  public RabbitMqPlaygroundService.CompareResult compare() {
    return rabbitMqPlaygroundService.compare();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleConflict(IllegalStateException exception) {
    return Map.of("error", exception.getMessage());
  }
}
