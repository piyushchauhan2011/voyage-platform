package com.voyage.app.seed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/seed")
@ConditionalOnProperty(name = "application.seed.enabled", havingValue = "true")
public class SeedController {

  private final DemoDataSeeder demoDataSeeder;

  public SeedController(DemoDataSeeder demoDataSeeder) {
    this.demoDataSeeder = demoDataSeeder;
  }

  @PostMapping("/demo")
  public DemoDataSeeder.SeedResult seedDemo() {
    try {
      return demoDataSeeder.seed(true);
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }
}
