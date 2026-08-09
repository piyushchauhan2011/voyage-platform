package com.voyage.app.ui;

import com.voyage.app.search.HotelSearchService;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/ui/search")
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SearchLabController {

  private final HotelSearchService hotelSearchService;

  public SearchLabController(ObjectProvider<HotelSearchService> hotelSearchServiceProvider) {
    this.hotelSearchService = hotelSearchServiceProvider.getIfAvailable();
  }

  @GetMapping
  public String lab(Model model, Locale locale) {
    model.addAttribute("currentLang", normalizeLang(locale));
    return "search-lab";
  }

  /**
   * HTMX fragment — server-rendered results so we teach Thymeleaf partials instead of client JSON
   * templating.
   */
  @GetMapping("/results")
  public String results(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "12") int size,
      Locale locale,
      Model model) {
    String lang = normalizeLang(locale);
    if (hotelSearchService == null) {
      model.addAttribute("searchDisabled", true);
      return "search/results :: results";
    }
    HotelSearchService.HotelSearchPage searchPage =
        hotelSearchService.search(
            q,
            lang,
            city,
            minPrice,
            maxPrice,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    model.addAttribute("searchPage", searchPage);
    model.addAttribute("currentLang", lang);
    model.addAttribute("searchDisabled", false);
    return "search/results :: results";
  }

  /** HTMX / fetch fragment for the hotel detail dialog body. */
  @GetMapping("/hotels/{id}")
  public String hotelDetail(@PathVariable Long id, Locale locale, Model model) {
    if (hotelSearchService == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Search is disabled");
    }
    String lang = normalizeLang(locale);
    model.addAttribute("hotel", hotelSearchService.detail(id));
    model.addAttribute("currentLang", lang);
    return "search/hotel-detail :: detail";
  }

  private static String normalizeLang(Locale locale) {
    return locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("th") ? "th" : "en";
  }
}
