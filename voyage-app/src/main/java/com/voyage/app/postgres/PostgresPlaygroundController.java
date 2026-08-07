package com.voyage.app.postgres;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/postgres/playground")
public class PostgresPlaygroundController {

  private final PostgresPlaygroundService postgresPlaygroundService;

  public PostgresPlaygroundController(PostgresPlaygroundService postgresPlaygroundService) {
    this.postgresPlaygroundService = postgresPlaygroundService;
  }

  @PostMapping("/seed")
  public SeedResult seed() {
    return postgresPlaygroundService.seed();
  }

  @PostMapping("/explain")
  public ExplainResult explain(@RequestBody ExplainRequest request) {
    return postgresPlaygroundService.explain(request == null ? null : request.scenario());
  }

  @GetMapping("/indexes")
  public Map<String, Boolean> listIndexes() {
    return postgresPlaygroundService.listIndexes();
  }

  @PostMapping("/indexes/{name}/drop")
  public IndexActionResult dropIndex(@PathVariable String name) {
    return postgresPlaygroundService.dropIndex(name);
  }

  @PostMapping("/indexes/{name}/create")
  public IndexActionResult createIndex(@PathVariable String name) {
    return postgresPlaygroundService.createIndex(name);
  }

  @PostMapping("/partitioning/setup")
  public PartitionSetupResult setupPartitioning() {
    return postgresPlaygroundService.setupPartitioning();
  }

  @GetMapping("/partitioning/explain")
  public ExplainResult explainPartitioning() {
    return postgresPlaygroundService.explainPartitioning();
  }

  @PostMapping("/locks/hold")
  public LockHoldResult holdLock(@RequestBody(required = false) LockHoldRequest request) {
    return postgresPlaygroundService.holdLock(request == null ? null : request.seconds());
  }

  @PostMapping("/isolation/demo")
  public IsolationDemoResult isolationDemo(@RequestBody IsolationDemoRequest request) {
    return postgresPlaygroundService.isolationDemo(request == null ? null : request.scenario());
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

record ExplainRequest(String scenario) {}

record LockHoldRequest(Integer seconds) {}

record IsolationDemoRequest(String scenario) {}
