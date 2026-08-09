package com.voyage.app.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import java.util.ArrayList;
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

  public HotelSearchService(
      ElasticsearchOperations elasticsearchOperations, HotelIndexService hotelIndexService) {
    this.elasticsearchOperations = elasticsearchOperations;
    this.hotelIndexService = hotelIndexService;
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
}
