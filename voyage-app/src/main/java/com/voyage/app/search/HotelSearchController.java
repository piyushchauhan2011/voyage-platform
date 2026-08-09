package com.voyage.app.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public hotel search API backed by Elasticsearch. Complements {@code GET /api/v1/hotels} (JPA
 * filters) — this endpoint returns ranked full-text hits with optional highlights.
 */
@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HotelSearchController {

  private final HotelSearchService hotelSearchService;

  public HotelSearchController(HotelSearchService hotelSearchService) {
    this.hotelSearchService = hotelSearchService;
  }

  @GetMapping("/hotels")
  public HotelSearchService.HotelSearchPage searchHotels(
      @RequestParam(required = false) String q,
      @RequestParam(required = false, defaultValue = "en") String lang,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "12") int size) {
    return hotelSearchService.search(
        q,
        lang,
        city,
        minPrice,
        maxPrice,
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
  }
}
