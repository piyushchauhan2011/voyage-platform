package com.voyage.app.search;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN playground for the Elasticsearch search lab: seed bilingual hotels, rebuild the index, and
 * inspect status / query strategy.
 */
@RestController
@RequestMapping("/api/search/playground")
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SearchPlaygroundController {

  private final HotelSearchSeeder hotelSearchSeeder;
  private final HotelIndexService hotelIndexService;

  public SearchPlaygroundController(
      HotelSearchSeeder hotelSearchSeeder, HotelIndexService hotelIndexService) {
    this.hotelSearchSeeder = hotelSearchSeeder;
    this.hotelIndexService = hotelIndexService;
  }

  @GetMapping("/status")
  public HotelIndexService.StatusResult status() {
    return hotelIndexService.status();
  }

  @PostMapping("/seed")
  public HotelSearchSeeder.SeedResult seed(@RequestParam(required = false) Integer count) {
    return hotelSearchSeeder.seed(count);
  }

  @PostMapping("/reindex")
  public HotelIndexService.ReindexResult reindex() {
    return hotelIndexService.reindex();
  }

  @GetMapping("/explain")
  public Map<String, Object> explain(
      @RequestParam(required = false, defaultValue = "") String q,
      @RequestParam(required = false, defaultValue = "en") String lang) {
    return hotelIndexService.explain(q, lang);
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, String> handleUnavailable(IllegalStateException ex) {
    return Map.of("error", "search_unavailable", "message", ex.getMessage());
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleRuntime(RuntimeException ex) {
    return Map.of("error", "search_error", "message", ex.getMessage());
  }
}
