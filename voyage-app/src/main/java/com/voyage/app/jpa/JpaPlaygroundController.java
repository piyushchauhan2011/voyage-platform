package com.voyage.app.jpa;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jpa/playground")
public class JpaPlaygroundController {

  private final JpaPlaygroundService jpaPlaygroundService;

  public JpaPlaygroundController(JpaPlaygroundService jpaPlaygroundService) {
    this.jpaPlaygroundService = jpaPlaygroundService;
  }

  @PostMapping("/seed")
  public LabSeedResult seed() {
    return jpaPlaygroundService.seed();
  }

  @PostMapping("/lifecycle/persist")
  public LifecyclePersistResult persistHotel() {
    return jpaPlaygroundService.persistHotel();
  }

  @PostMapping("/lifecycle/detach-mutate")
  public LifecycleDetachResult detachAndMutate() {
    return jpaPlaygroundService.detachAndMutate();
  }

  @PostMapping("/loading/nplus1")
  public LoadingDemoResult nPlusOne() {
    return jpaPlaygroundService.nPlusOne();
  }

  @PostMapping("/loading/entity-graph")
  public LoadingDemoResult entityGraph() {
    return jpaPlaygroundService.entityGraph();
  }

  @PostMapping("/query/jpql")
  public QueryDemoResult queryJpql() {
    return jpaPlaygroundService.queryJpql();
  }

  @PostMapping("/query/criteria")
  public QueryDemoResult queryCriteria() {
    return jpaPlaygroundService.queryCriteria();
  }

  @PostMapping("/query/spec")
  public QueryDemoResult querySpec() {
    return jpaPlaygroundService.querySpec();
  }

  @PostMapping("/tx/booking-success")
  public BookingTxResult bookingSuccess() {
    return jpaPlaygroundService.bookingSuccess();
  }

  @PostMapping("/tx/booking-rollback")
  public BookingTxResult bookingRollback() {
    return jpaPlaygroundService.bookingRollback();
  }

  @PostMapping("/tx/propagation")
  public PropagationMapResult propagation() throws NoSuchMethodException {
    return jpaPlaygroundService.propagationMap();
  }

  @PostMapping("/tx/deadlock-retry")
  public LockContentionResult deadlockRetry() {
    return jpaPlaygroundService.lockContention();
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
