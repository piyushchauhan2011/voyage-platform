package com.voyage.app.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Full-text hotel search against Elasticsearch. Prefer this for ranked text search; keep JPA Specs
 * for exact filters and pgvector for semantic ("near the sand") queries.
 */
@Service
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HotelSearchService {

  private final ElasticsearchOperations elasticsearchOperations;
  private final HotelIndexService hotelIndexService;
  private final HotelRepository hotelRepository;

  public HotelSearchService(
      ElasticsearchOperations elasticsearchOperations,
      HotelIndexService hotelIndexService,
      HotelRepository hotelRepository) {
    this.elasticsearchOperations = elasticsearchOperations;
    this.hotelIndexService = hotelIndexService;
    this.hotelRepository = hotelRepository;
  }

  public HotelSearchPage search(
      String q, String lang, String city, Double minPrice, Double maxPrice, Pageable pageable) {
    hotelIndexService.ensureIndex();

    String language = normalizeLang(lang);
    Pageable effectivePage =
        pageable == null || pageable.isUnpaged()
            ? PageRequest.of(0, 20)
            : PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50));

    List<String> fields = fieldsFor(language);
    boolean hasQuery = StringUtils.hasText(q);

    NativeQuery query =
        NativeQuery.builder()
            .withQuery(
                qb ->
                    qb.bool(
                        b -> {
                          if (hasQuery) {
                            b.must(
                                m ->
                                    m.multiMatch(
                                        mm ->
                                            mm.query(q.trim())
                                                .fields(fields)
                                                .type(TextQueryType.BestFields)
                                                .operator(Operator.Or)));
                          } else {
                            b.must(m -> m.matchAll(ma -> ma));
                          }
                          if (StringUtils.hasText(city)) {
                            String cityValue = city.trim();
                            b.filter(
                                f ->
                                    f.bool(
                                        cb ->
                                            cb.should(
                                                    s ->
                                                        s.match(
                                                            mq ->
                                                                mq.field("city").query(cityValue)))
                                                .should(
                                                    s ->
                                                        s.match(
                                                            mq ->
                                                                mq.field("cityTh")
                                                                    .query(cityValue)))
                                                .minimumShouldMatch("1")));
                          }
                          if (minPrice != null) {
                            b.filter(
                                f ->
                                    f.range(
                                        r ->
                                            r.number(n -> n.field("pricePerNight").gte(minPrice))));
                          }
                          if (maxPrice != null) {
                            b.filter(
                                f ->
                                    f.range(
                                        r ->
                                            r.number(n -> n.field("pricePerNight").lte(maxPrice))));
                          }
                          return b;
                        }))
            .withHighlightQuery(highlightQuery())
            .withPageable(effectivePage)
            .build();

    SearchHits<HotelDocument> hits = elasticsearchOperations.search(query, HotelDocument.class);

    List<HotelSearchHit> results = new ArrayList<>();
    for (SearchHit<HotelDocument> hit : hits) {
      HotelDocument doc = hit.getContent();
      results.add(
          new HotelSearchHit(
              doc.getId(),
              doc.getName(),
              doc.getNameTh(),
              doc.getCity(),
              doc.getCityTh(),
              doc.getDescription(),
              doc.getDescriptionTh(),
              doc.getAmenities(),
              doc.getPricePerNight(),
              doc.getImageUrl(),
              doc.getStarRating(),
              doc.getGuestRating(),
              hit.getScore(),
              flattenHighlights(hit)));
    }

    return new HotelSearchPage(
        results,
        hits.getTotalHits(),
        effectivePage.getPageNumber(),
        effectivePage.getPageSize(),
        language,
        hasQuery ? q.trim() : "",
        "Postgres stores hotels; Elasticsearch ranks them. Compare with /api/v1/hotels (JPA) and /ui/ai (pgvector).");
  }

  /**
   * As-you-type suggestions using {@code search_as_you_type} fields + {@code bool_prefix}. Locale
   * biases which language fields are boosted so Thai prefixes hit Thai analyzers.
   */
  public List<HotelSuggestion> suggest(String q, String lang, int size) {
    if (!StringUtils.hasText(q)) {
      return List.of();
    }
    hotelIndexService.ensureIndex();
    String language = normalizeLang(lang);
    int limit = Math.clamp(size, 1, 12);
    List<String> fields = suggestFieldsFor(language);

    NativeQuery query =
        NativeQuery.builder()
            .withQuery(
                qb ->
                    qb.multiMatch(
                        mm ->
                            mm.query(q.trim())
                                .fields(fields)
                                .type(TextQueryType.BoolPrefix)
                                .operator(Operator.Or)))
            .withPageable(PageRequest.of(0, limit))
            .build();

    SearchHits<HotelDocument> hits = elasticsearchOperations.search(query, HotelDocument.class);
    List<HotelSuggestion> suggestions = new ArrayList<>();
    for (SearchHit<HotelDocument> hit : hits) {
      HotelDocument doc = hit.getContent();
      suggestions.add(
          new HotelSuggestion(
              doc.getId(),
              doc.getName(),
              doc.getNameTh(),
              doc.getCity(),
              doc.getCityTh(),
              doc.getImageUrl(),
              doc.getPricePerNight(),
              hit.getScore()));
    }
    return suggestions;
  }

  public HotelDetail detail(Long id) {
    Hotel hotel =
        hotelRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + id));
    return HotelDetail.from(hotel);
  }

  private HighlightQuery highlightQuery() {
    HighlightParameters parameters =
        HighlightParameters.builder()
            .withPreTags("<mark>")
            .withPostTags("</mark>")
            .withFragmentSize(120)
            .withNumberOfFragments(1)
            .build();
    List<HighlightField> highlightFields =
        List.of(
            new HighlightField("name"),
            new HighlightField("nameTh"),
            new HighlightField("city"),
            new HighlightField("cityTh"),
            new HighlightField("description"),
            new HighlightField("descriptionTh"));
    return new HighlightQuery(new Highlight(parameters, highlightFields), HotelDocument.class);
  }

  private static List<String> flattenHighlights(SearchHit<HotelDocument> hit) {
    List<String> snippets = new ArrayList<>();
    hit.getHighlightFields()
        .forEach(
            (field, values) -> {
              if (values != null) {
                values.forEach(snippets::add);
              }
            });
    return snippets;
  }

  private static List<String> fieldsFor(String language) {
    if ("th".equals(language)) {
      return List.of(
          "nameTh^4",
          "cityTh^3",
          "descriptionTh^3",
          "name^1.5",
          "city^1",
          "description^1",
          "amenities^0.5");
    }
    return List.of(
        "name^4",
        "city^3",
        "description^3",
        "amenities^1",
        "nameTh^1.5",
        "cityTh^1",
        "descriptionTh^1");
  }

  private static List<String> suggestFieldsFor(String language) {
    if ("th".equals(language)) {
      return List.of(
          "nameThSuggest^4",
          "nameThSuggest._2gram^3",
          "nameThSuggest._3gram^2",
          "cityThSuggest^3",
          "cityThSuggest._2gram^2",
          "nameSuggest^1.2",
          "citySuggest^1");
    }
    return List.of(
        "nameSuggest^4",
        "nameSuggest._2gram^3",
        "nameSuggest._3gram^2",
        "citySuggest^3",
        "citySuggest._2gram^2",
        "nameThSuggest^1.2",
        "cityThSuggest^1");
  }

  private static String normalizeLang(String lang) {
    if (lang == null || lang.isBlank()) {
      return "en";
    }
    String normalized = lang.trim().toLowerCase(Locale.ROOT);
    return normalized.startsWith("th") ? "th" : "en";
  }

  public record HotelSearchHit(
      Long id,
      String name,
      String nameTh,
      String city,
      String cityTh,
      String description,
      String descriptionTh,
      String amenities,
      Double pricePerNight,
      String imageUrl,
      Integer starRating,
      Double guestRating,
      float score,
      List<String> highlights) {}

  public record HotelSearchPage(
      List<HotelSearchHit> content,
      long totalElements,
      int page,
      int size,
      String lang,
      String query,
      String lesson) {}

  public record HotelSuggestion(
      Long id,
      String label,
      String labelTh,
      String city,
      String cityTh,
      String imageUrl,
      Double pricePerNight,
      float score) {}

  public record HotelDetail(
      Long id,
      String name,
      String nameTh,
      String city,
      String cityTh,
      String description,
      String descriptionTh,
      String amenities,
      Double pricePerNight,
      String imageUrl,
      List<String> galleryUrls,
      Integer starRating,
      Double guestRating,
      Integer reviewCount,
      String address,
      String addressTh,
      String neighborhood,
      String neighborhoodTh,
      String checkInFrom,
      String checkOutUntil,
      String phone) {

    static HotelDetail from(Hotel hotel) {
      List<String> gallery = List.of();
      if (StringUtils.hasText(hotel.getGalleryUrls())) {
        gallery =
            Arrays.stream(hotel.getGalleryUrls().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
      }
      return new HotelDetail(
          hotel.getId(),
          hotel.getName(),
          hotel.getNameTh(),
          hotel.getCity(),
          hotel.getCityTh(),
          hotel.getDescription(),
          hotel.getDescriptionTh(),
          hotel.getAmenities(),
          hotel.getPricePerNight(),
          hotel.getImageUrl(),
          gallery,
          hotel.getStarRating(),
          hotel.getGuestRating(),
          hotel.getReviewCount(),
          hotel.getAddress(),
          hotel.getAddressTh(),
          hotel.getNeighborhood(),
          hotel.getNeighborhoodTh(),
          hotel.getCheckInFrom(),
          hotel.getCheckOutUntil(),
          hotel.getPhone());
    }
  }
}
